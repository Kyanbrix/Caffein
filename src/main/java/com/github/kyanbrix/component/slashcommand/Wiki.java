package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.utils.LocationEntry;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Wiki implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(Wiki.class);

    private static final Color EMBED_COLOR = new Color(0xFF8C00);
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int FETCH_TIMEOUT_MS = 15_000;

    // Whole-text match, so "Location:" is found but a sentence that merely
    // starts with "Location" isn't. The colon may sit outside the bold text.
    private static final String LOCATION_LABEL = "(?i)^\\s*locations?\\s*:?\\s*$";
    private static final String DESCRIPTION_LABEL = "(?i)^\\s*description\\s*:?\\s*$";

    // Wiki page fetches run here instead of on JDA's event thread, so a slow
    // wiki response doesn't stall every other command the bot is handling.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "wiki-lookup");
        thread.setDaemon(true);
        return thread;
    });

    private record WikiRow(String itemName, String url) {}

    private record WikiPage(String description, String locationLabel, String locationText, String imageUrl) {}

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {
        String userSearched = event.getOption("search", OptionMapping::getAsString);
        if (userSearched == null || userSearched.isBlank()) {
            event.reply("Please enter an item to search for.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        EXECUTOR.submit(() -> handle(event, userSearched.trim()));
    }

    private void handle(SlashCommandInteraction event, String userSearched) {
        WikiRow row;
        try {
            row = findItem(userSearched);
        } catch (SQLException e) {
            log.error("Database error looking up wiki item '{}'", userSearched, e);
            event.getHook().sendMessage("An error occurred while looking up that item.").queue();
            return;
        }

        if (row == null) {
            event.getHook().sendMessage("No such item found! Kindly select one of the suggested options.").queue();
            return;
        }

        WikiPage page;
        try {
            page = fetchPage(row.url());
        } catch (IOException e) {
            log.warn("Failed to fetch wiki page {} for '{}': {}", row.url(), row.itemName(), e.getMessage());
            event.getHook().sendMessage("Couldn't reach the AQW wiki right now. Try again in a bit, or open the page directly.")
                    .addComponents(ActionRow.of(Button.of(ButtonStyle.LINK, row.url(), "View Wiki")))
                    .queue();
            return;
        }

        if (page == null) {
            event.getHook().sendMessage("Could not parse page content for this item.")
                    .addComponents(ActionRow.of(Button.of(ButtonStyle.LINK, row.url(), "View Wiki")))
                    .queue();
            return;
        }

        event.getHook().sendMessageEmbeds(buildEmbed(row, page))
                .addComponents(ActionRow.of(Button.of(ButtonStyle.LINK, row.url(), "View Wiki")))
                .queue();
    }

    /**
     * Case-insensitive lookup, so a name typed by hand still works even if the
     * user didn't pick it from autocomplete. Returns the real stored name so the
     * embed title uses the wiki's own casing.
     */
    private WikiRow findItem(String itemName) throws SQLException {
        try (Connection connection = Caffein.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT item_name, url FROM wiki WHERE LOWER(item_name) = LOWER(?) LIMIT 1")) {

            ps.setString(1, itemName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new WikiRow(rs.getString("item_name"), rs.getString("url")) : null;
            }
        }
    }

    private WikiPage fetchPage(String url) throws IOException {
        Document document = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(FETCH_TIMEOUT_MS)
                .get();

        Element pageContent = document.getElementById("page-content");
        if (pageContent == null) {
            return null;
        }

        return new WikiPage(
                parseDescription(pageContent),
                parseLocationLabel(pageContent),
                parseLocationText(pageContent),
                findItemImage(pageContent));
    }

    /** Uses the wiki's own wording ("Location" or "Locations") for the field name. */
    private String parseLocationLabel(Element pageContent) {
        Element label = findLabel(pageContent, LOCATION_LABEL);
        if (label == null) {
            return "Location";
        }
        return label.text().replace(":", "").trim();
    }

    /**
     * Renders the location(s) as the wiki shows them: links become markdown
     * links, and the text between them (" - ", ", ", "(Merge)" etc.) is kept
     * inline, so "Shop - Battleon" stays on one line. Items sold in several
     * places list them as bullets under the label, one location per line.
     */
    private String parseLocationText(Element pageContent) {
        String text = parseLabeledValue(pageContent, LOCATION_LABEL);
        return text.isEmpty() ? "N/A" : text;
    }

    private String parseDescription(Element pageContent) {
        String text = parseLabeledValue(pageContent, DESCRIPTION_LABEL);
        return text.isEmpty() ? "N/A" : text;
    }

    /**
     * The wiki writes a labelled value in one of two ways:
     * <pre>
     *   &lt;p&gt;&lt;strong&gt;Location:&lt;/strong&gt; &lt;a&gt;Shop&lt;/a&gt; - &lt;a&gt;Map&lt;/a&gt;&lt;br/&gt; ...
     *
     *   &lt;p&gt;&lt;strong&gt;Locations:&lt;/strong&gt;&lt;/p&gt;
     *   &lt;ul&gt;&lt;li&gt;&lt;a&gt;Shop&lt;/a&gt; - &lt;a&gt;Map&lt;/a&gt;&lt;/li&gt; ... &lt;/ul&gt;
     * </pre>
     * The inline form is tried first; if the label has nothing after it, the
     * list that follows its paragraph is used instead.
     */
    private String parseLabeledValue(Element pageContent, String labelRegex) {
        Element label = findLabel(pageContent, labelRegex);
        if (label == null) {
            return "";
        }

        // Covers "<strong>Location</strong>: ..." where the colon sits outside the bold text
        String inline = renderInline(nodesAfterLabel(label)).replaceFirst("^:\\s*", "");
        if (!inline.isEmpty()) {
            return inline;
        }

        Element list = listAfterLabel(label);
        return list == null ? "" : renderList(list, 0);
    }

    private Element findLabel(Element pageContent, String labelRegex) {
        return pageContent.selectFirst("strong:matchesOwn(" + labelRegex + "), b:matchesOwn(" + labelRegex + ")");
    }

    /**
     * Returns every sibling after the label up to the next line break or the
     * next bold label.
     */
    private List<Node> nodesAfterLabel(Element label) {
        List<Node> nodes = new ArrayList<>();
        for (Node node = label.nextSibling(); node != null; node = node.nextSibling()) {
            if (node instanceof Element el && (el.tagName().equals("br") || el.tagName().equals("strong"))) {
                break;
            }
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * The list belonging to a label that ends its paragraph, e.g.
     * "&lt;p&gt;&lt;strong&gt;Locations:&lt;/strong&gt;&lt;/p&gt;&lt;ul&gt;...". Returns null if
     * anything else follows the label, since the list would then belong to
     * some other part of the page.
     */
    private Element listAfterLabel(Element label) {
        for (Node node = label.nextSibling(); node != null; node = node.nextSibling()) {
            boolean blankText = node instanceof TextNode tn && tn.isBlank();
            boolean lineBreak = node instanceof Element el && el.tagName().equals("br");
            if (!blankText && !lineBreak) {
                return null;
            }
        }

        Element parent = label.parent();
        Element next = parent == null ? null : parent.nextElementSibling();
        return next != null && (next.tagName().equals("ul") || next.tagName().equals("ol")) ? next : null;
    }

    /** One bullet per list item, with nested lists (e.g. "Requires ...") indented under their parent. */
    private String renderList(Element list, int depth) {
        String indent = "\u2003".repeat(depth);
        String bullet = depth == 0 ? "\u2022 " : "\u25E6 ";
        List<String> lines = new ArrayList<>();

        for (Element item : list.children()) {
            if (!item.tagName().equals("li")) {
                continue;
            }

            List<Node> ownContent = new ArrayList<>();
            List<Element> nestedLists = new ArrayList<>();
            for (Node child : item.childNodes()) {
                if (child instanceof Element el && (el.tagName().equals("ul") || el.tagName().equals("ol"))) {
                    nestedLists.add(el);
                } else {
                    ownContent.add(child);
                }
            }

            String text = renderInline(ownContent);
            if (!text.isEmpty()) {
                lines.add(indent + bullet + text);
            }
            for (Element nested : nestedLists) {
                String nestedText = renderList(nested, depth + 1);
                if (!nestedText.isEmpty()) {
                    lines.add(nestedText);
                }
            }
        }

        return String.join("\n", lines);
    }

    /** Turns a run of HTML nodes into one line of Discord markdown, keeping links clickable. */
    private String renderInline(List<Node> nodes) {
        StringBuilder out = new StringBuilder();
        appendInline(nodes, out);

        // Collapse the HTML's stray whitespace/newlines into single spaces
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    // Recurses into wrappers like <span>/<em>/<strong>, so a link nested inside
    // formatting still comes out as a link instead of flattened text.
    private void appendInline(List<Node> nodes, StringBuilder out) {
        for (Node node : nodes) {
            if (node instanceof TextNode tn) {
                out.append(tn.text());
            } else if (node instanceof Element el) {
                switch (el.tagName()) {
                    case "a" -> out.append(new LocationEntry(el.text(), el.attr("abs:href")).toMarkdown());
                    case "br" -> out.append(' ');
                    case "img", "script", "style" -> { }
                    default -> appendInline(el.childNodes(), out);
                }
            }
        }
    }

    /** The item preview image: first image in the tab view if there is one, otherwise the first non-icon image. */
    private String findItemImage(Element pageContent) {
        Element tabImage = pageContent.selectFirst(".yui-content img[src]");
        if (tabImage != null) {
            return tabImage.attr("abs:src");
        }

        for (Element img : pageContent.select("img[src]")) {
            String src = img.attr("src");
            if (!src.contains("/image-tags/") && !src.contains("/classes-skills/") && !src.contains("/sys-images/")) {
                return img.attr("abs:src");
            }
        }
        return null;
    }

    private MessageEmbed buildEmbed(WikiRow row, WikiPage page) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(truncate(row.itemName(), MessageEmbed.TITLE_MAX_LENGTH), row.url())
                .setDescription(truncate(page.description(), MessageEmbed.DESCRIPTION_MAX_LENGTH))
                .addField(page.locationLabel(), truncateLines(page.locationText(), MessageEmbed.VALUE_MAX_LENGTH), false)
                .setColor(EMBED_COLOR);

        if (page.imageUrl() != null && !page.imageUrl().isBlank()) {
            embed.setImage(page.imageUrl());
        }

        return embed.build();
    }

    /**
     * Drops whole lines rather than cutting mid-line, so a long location list
     * never ends in a half-written markdown link.
     */
    private static String truncateLines(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        String[] lines = text.split("\n");
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (String line : lines) {
            String more = "\n\u2026and " + (lines.length - shown - 1) + " more";
            if (out.length() + line.length() + 1 + more.length() > maxLength) {
                break;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(line);
            shown++;
        }

        if (shown == 0) {
            return truncate(text, maxLength);
        }
        return out + "\n\u2026and " + (lines.length - shown) + " more";
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("wiki", "AQW Wiki")
                .addOption(OptionType.STRING, "search", "Search an item from aqw wiki page", true, true)
                .setContexts(InteractionContextType.GUILD, InteractionContextType.PRIVATE_CHANNEL)
                .setIntegrationTypes(IntegrationType.USER_INSTALL, IntegrationType.GUILD_INSTALL);
    }
}