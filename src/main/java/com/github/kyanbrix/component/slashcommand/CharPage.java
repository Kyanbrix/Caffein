package com.github.kyanbrix.component.slashcommand;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.exceptions.ParsingException;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /charpage: renders a player's AQW character page as a PNG.
 * <p>
 * The char page draws the character with a Flash movie (characterB.swf) fed
 * the character's look through flashvars. There's no image to download, so
 * render-charpage.js plays that movie in Ruffle inside headless Chromium and
 * screenshots it.
 */
public class CharPage implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(CharPage.class);

    private static final String CHAR_PAGE_URL = "https://account.aq.com/CharPage?id=";
    // JSON arrays the char page loads when its Achievements and Inventory sections are opened
    private static final String BADGES_URL = "https://account.aq.com/CharPage/Badges?ccid=";
    private static final String INVENTORY_URL = "https://account.aq.com/CharPage/Inventory?ccid=";
    private static final Pattern CCID = Pattern.compile("var ccid = (\\d+);");
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int FETCH_TIMEOUT_MS = 15_000;

    private static final String NODE_BIN = System.getenv().getOrDefault("NODE_BIN", "node");
    private static final String NODE_SCRIPT_PATH = "render-charpage.js";
    // Heavy gear can need a failed 3x attempt before the 2x render succeeds
    private static final int RENDER_TIMEOUT_SECONDS = 100;

    private static final String IMAGE_NAME = "charpage.png";
    private static final Color EMBED_COLOR = new Color(0xF5C542);

    /** Char page labels shown in the embed, in page order. */
    private static final List<String> FIELDS = List.of(
            "Level", "Class", "Weapon", "Armor", "Helm", "Cape", "Pet", "Misc", "Faction", "Guild");

    /** Cosmetic slots in the char page's flashvars ({@code strCust<slot>Name}), which the page only shows in the Flash movie. */
    private static final List<String> COSMETIC_SLOTS = List.of("Weapon", "Armor", "Helm", "Cape");
    private static final String WIKI_URL = "http://aqwwiki.wikidot.com/";

    // Every render starts a whole Chromium, so they run one at a time off
    // JDA's event thread and queue up behind each other.
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "charpage-render");
        thread.setDaemon(true);
        return thread;
    });

    private record Character(String name, String title, String ccid, String swfUrl, String flashvars,
                             Map<String, String> details, String cosmetics) {}

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        if (name == null || name.isBlank()) {
            event.reply("Please enter a character name.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        EXECUTOR.submit(() -> handle(event, name.trim()));
    }

    private void handle(SlashCommandInteraction event, String name) {
        String pageUrl = CHAR_PAGE_URL + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        Button viewButton = Button.of(ButtonStyle.LINK, pageUrl, "View Char Page");

        Document document;
        try {
            document = Jsoup.connect(pageUrl)
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MS)
                    .get();
        } catch (IOException e) {
            log.warn("Failed to fetch char page for '{}': {}", name, e.getMessage());
            event.getHook().sendMessage("Couldn't reach the AQW char page right now. Try again in a bit.").queue();
            return;
        }

        // Missing, wiped or disabled characters get an alert box instead of the Flash movie
        Element alert = document.getElementById("serveralert");
        if (alert != null) {
            String reason = alert.text().isBlank() ? "Not Found!" : alert.text().trim();
            event.getHook().sendMessage("**" + name + "**: " + reason).queue();
            return;
        }

        Character character = parse(document, name);
        if (character == null) {
            event.getHook().sendMessage("Couldn't read that character's page.")
                    .addComponents(ActionRow.of(viewButton))
                    .queue();
            return;
        }

        if (character.ccid() != null) {
            addCount(character, "Achievements", BADGES_URL, "badgeID");
            addCount(character, "Inventory", INVENTORY_URL, "intCharId");
        }

        byte[] image;
        try {
            image = render(character);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to render char page for '{}'", name, e);
            event.getHook().sendMessage("Couldn't render that character right now.")
                    .addComponents(ActionRow.of(viewButton))
                    .queue();
            return;
        }

        event.getHook().sendMessageEmbeds(buildEmbed(character, pageUrl))
                .addFiles(FileUpload.fromData(image, IMAGE_NAME))
                .addComponents(ActionRow.of(viewButton))
                .queue();
    }

    private Character parse(Document document, String fallbackName) {
        Element embed = document.selectFirst("embed[src][flashvars]");
        if (embed == null) {
            return null;
        }

        Element header = document.selectFirst(".card-header h1");
        Element title = document.selectFirst(".card-header h4");

        // The page's own script holds the character ID the Badges/Inventory endpoints want
        Matcher ccid = CCID.matcher(document.html());

        Map<String, String> details = new LinkedHashMap<>();
        for (Element label : document.select(".card-body label")) {
            String key = label.text().replace(":", "").trim();
            if (FIELDS.contains(key)) {
                details.put(key, valueOf(label));
            }
        }

        return new Character(
                header == null ? fallbackName : header.text().trim(),
                title == null ? "" : title.text().trim(),
                ccid.find() ? ccid.group(1) : null,
                embed.attr("abs:src"),
                embed.attr("flashvars"),
                details,
                cosmetics(embed.attr("flashvars")));
    }

    /** One line per cosmetic slot that has an item, linked to its wiki page. */
    private String cosmetics(String flashvars) {
        Map<String, String> vars = new LinkedHashMap<>();
        for (String pair : flashvars.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                vars.put(pair.substring(0, eq), decode(pair.substring(eq + 1)));
            }
        }

        StringBuilder out = new StringBuilder();
        for (String slot : COSMETIC_SLOTS) {
            String name = vars.getOrDefault("strCust" + slot + "Name", "").trim();
            if (!name.isEmpty()) {
                out.append("**").append(slot).append(":** ")
                        .append('[').append(name).append("](").append(wikiLink(name)).append(")\n");
            }
        }
        return out.toString().trim();
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;   // a stray '%' that isn't an escape
        }
    }

    private String wikiLink(String name) {
        // Parentheses would end the markdown link early
        return WIKI_URL + name.replace(" ", "%20").replace("'", "%27").replace("(", "%28").replace(")", "%29");
    }

    /**
     * Adds the size of one of the char page's JSON lists as a detail, or nothing if it can't be read.
     * A player who hides the list gets a single placeholder entry whose {@code idKey} is 0 instead.
     */
    private void addCount(Character character, String key, String url, String idKey) {
        try {
            String body = Jsoup.connect(url + character.ccid())
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MS)
                    .ignoreContentType(true)
                    .execute()
                    .body();
            DataArray list = DataArray.fromJson(body);
            boolean hidden = list.length() == 1 && list.getObject(0).getLong(idKey, -1) == 0;
            character.details().put(key, hidden ? "Hidden" : String.format("%,d", list.length()));
        } catch (IOException | ParsingException e) {
            // The endpoints answer "error" instead of a list for some characters
            log.warn("Failed to read {} for '{}': {}", key, character.name(), e.getMessage());
        }
    }

    /**
     * Values follow their label up to the next {@code <br>}, e.g.
     * {@code <label>Class:</label> <a href='http://aqwwiki.wikidot.com/Void Highlord'>Void Highlord</a><br />}.
     * Linked values keep a link to their wiki page; empty slots link to the wiki's front page and are dropped.
     */
    private String valueOf(Element label) {
        StringBuilder out = new StringBuilder();
        for (Node node = label.nextSibling(); node != null; node = node.nextSibling()) {
            if (node instanceof Element el) {
                if (el.tagName().equals("br") || el.tagName().equals("label")) {
                    break;
                }
                if (el.tagName().equals("a")) {
                    if (!el.text().isBlank()) {
                        String href = el.attr("href").replace(" ", "%20").replace("'", "%27");
                        out.append('[').append(el.text().trim()).append("](").append(href).append(')');
                    }
                    continue;
                }
                out.append(el.text());
            } else if (node instanceof TextNode tn) {
                out.append(tn.text());
            }
        }
        return out.toString().trim();
    }

    private byte[] render(Character character) throws IOException, InterruptedException {
        Path tempOutput = Files.createTempFile("charpage-", ".png");
        try {
            ProcessBuilder pb = new ProcessBuilder(NODE_BIN, NODE_SCRIPT_PATH, tempOutput.toString());
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String payload = DataObject.empty()
                    .put("swf", character.swfUrl())
                    .put("flashvars", character.flashvars())
                    .toString();
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            boolean finished = process.waitFor(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Renderer timed out after " + RENDER_TIMEOUT_SECONDS + "s");
            }

            if (process.exitValue() != 0) {
                String stderr = readAll(process.getErrorStream());
                throw new IOException("Renderer failed (exit " + process.exitValue() + "): " + stderr);
            }

            return Files.readAllBytes(tempOutput);
        } finally {
            Files.deleteIfExists(tempOutput);
        }
    }

    private String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        in.transferTo(buffer);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private MessageEmbed buildEmbed(Character character, String pageUrl) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(character.name(), pageUrl)
                .setColor(EMBED_COLOR)
                .setImage("attachment://" + IMAGE_NAME)
                .setFooter("AQW Character Page");

        if (!character.title().isEmpty()) {
            embed.setDescription("*" + character.title() + "*");
        }

        character.details().forEach((key, value) -> {
            if (!value.isEmpty() && value.length() <= MessageEmbed.VALUE_MAX_LENGTH) {
                embed.addField(key, value, true);
            }
        });

        String cosmetics = character.cosmetics();
        if (!cosmetics.isEmpty() && cosmetics.length() <= MessageEmbed.VALUE_MAX_LENGTH) {
            embed.addField("Cosmetics", cosmetics, false);
        }

        return embed.build();
    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("charpage", "Show an AQW character page")
                .addOption(OptionType.STRING, "name", "Character name", true)
                .setContexts(InteractionContextType.GUILD, InteractionContextType.PRIVATE_CHANNEL)
                .setIntegrationTypes(IntegrationType.USER_INSTALL, IntegrationType.GUILD_INSTALL);
    }
}
