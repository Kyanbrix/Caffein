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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Wiki implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(Wiki.class);

    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int FETCH_TIMEOUT_MS = 15_000;

    private static final Color DEFAULT_COLOR = new Color(0xFF8C00);
    private static final Color RARE_COLOR = new Color(0xE74C3C);
    private static final Color AC_COLOR = new Color(0xF1C40F);
    private static final Color LEGEND_COLOR = new Color(0x9B59B6);
    private static final Color SEASONAL_COLOR = new Color(0x2ECC71);

    private static final String LOCATION_LABEL = label("locations?");
    private static final String DESCRIPTION_LABEL = label("description");
    private static final String PRICE_LABEL = label("price");
    private static final String SELLBACK_LABEL = label("sellback");
    private static final String RARITY_LABEL = label("rarity");
    private static final String TYPE_LABEL = label("type");
    private static final String LEVEL_LABEL = label("base level|level");
    private static final String DAMAGE_LABEL = label("base damage|weapon damage");
    private static final String RANK_LABEL = label("rank needed|rank required");
    private static final String STAT_MODEL_LABEL = label("stat model");
    private static final String NOTES_LABEL = label("notes?");
    private static final String SPECIAL_EFFECTS_LABEL = label("special effects?");

    // Embeds are capped at 6000 characters in total, so the free-text parts get
    // their own budgets to leave room for everything else.
    private static final int DESCRIPTION_LIMIT = 1500;
    private static final int NOTES_LIMIT = 700;
    private static final int SPECIAL_EFFECTS_LIMIT = 500;
    private static final int SELLBACK_LIMIT = 512;

    /** The wiki's header icons (image-tags/aclarge.png etc.), keyed by file name minus the size suffix. */
    private static final Map<String, String> BADGE_NAMES = Map.of(
            "ac", "AC",
            "legend", "Legend",
            "rare", "Rare",
            "pseudo", "Pseudo Rare",
            "seasonal", "Seasonal",
            "special", "Special Offer",
            "future", "Future");

    /** Wikidot page tags that name the item's type. */
    private static final Map<String, String> ITEM_TYPES = Map.ofEntries(
            Map.entry("armor", "Armor"), Map.entry("class", "Class"), Map.entry("helm", "Helm"),
            Map.entry("back", "Cape"), Map.entry("pet", "Pet"), Map.entry("misc", "Misc"),
            Map.entry("axe", "Axe"), Map.entry("bow", "Bow"), Map.entry("dagger", "Dagger"),
            Map.entry("gauntlet", "Gauntlet"), Map.entry("gun", "Gun"), Map.entry("mace", "Mace"),
            Map.entry("polearm", "Polearm"), Map.entry("staff", "Staff"), Map.entry("sword", "Sword"),
            Map.entry("wand", "Wand"), Map.entry("whip", "Whip"), Map.entry("necklace", "Necklace"),
            Map.entry("grounditem", "Ground Item"), Map.entry("floor", "Floor Item"),
            Map.entry("wall", "Wall Item"), Map.entry("house", "House"));

    /** Wikidot page tags that say how the item is obtained. */
    private static final Map<String, String> SOURCES = Map.of(
            "shopitem", "Shop",
            "mergeitem", "Merge Shop",
            "drop", "Monster Drop",
            "questreward", "Quest Reward");

    // Wiki page fetches run here instead of on JDA's event thread, so a slow
    // wiki response doesn't stall every other command the bot is handling.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "wiki-lookup");
        thread.setDaemon(true);
        return thread;
    });

    private record WikiRow(String itemName, String url) {}

    private record WikiPage(
            String description,
            String locationLabel,
            String location,
            String price,
            String sellback,
            String rarity,
            String type,
            String level,
            String damage,
            String rank,
            String statModel,
            String specialEffects,
            String notes,
            List<String> badges,
            List<String> sources,
            String access,
            String imageUrl) {}

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
        return parsePage(document);
    }

    private WikiPage parsePage(Document document) {
        Element pageContent = document.getElementById("page-content");
        if (pageContent == null) {
            return null;
        }

        // Page tags sit below the content, outside #page-content
        List<String> pageTags = document.select(".page-tags a").eachText();

        String type = parseValue(pageContent, TYPE_LABEL);
        if (type.isEmpty()) {
            type = pageTags.stream().map(ITEM_TYPES::get).filter(Objects::nonNull).findFirst().orElse("");
        }

        String access = pageTags.contains("legend") ? "Member only"
                : pageTags.contains("freeplayer") ? "Free player"
                : null;

        return new WikiPage(
                parseValue(pageContent, DESCRIPTION_LABEL),
                parseLocationLabel(pageContent),
                parseValue(pageContent, LOCATION_LABEL),
                parseValue(pageContent, PRICE_LABEL),
                parseValue(pageContent, SELLBACK_LABEL),
                parseValue(pageContent, RARITY_LABEL).replaceFirst("(?i)\\s*rarity$", ""),
                type,
                parseValue(pageContent, LEVEL_LABEL),
                parseValue(pageContent, DAMAGE_LABEL),
                parseValue(pageContent, RANK_LABEL),
                parseValue(pageContent, STAT_MODEL_LABEL),
                parseValue(pageContent, SPECIAL_EFFECTS_LABEL),
                parseValue(pageContent, NOTES_LABEL),
                parseBadges(pageContent),
                pageTags.stream().map(SOURCES::get).filter(Objects::nonNull).distinct().toList(),
                access,
                findItemImage(pageContent));
    }

    /** Whole-text match, so "Price:" is found but a sentence that merely starts with "Price" isn't. */
    private static String label(String names) {
        return "(?i)^\\s*(?:" + names + ")\\s*:?\\s*$";
    }

    /** Uses the wiki's own wording ("Location" or "Locations") for the field name. */
    private String parseLocationLabel(Element pageContent) {
        Element label = findLabel(pageContent, LOCATION_LABEL);
        if (label == null) {
            return "Location";
        }
        return label.text().replace(":", "").trim();
    }

    private String parseValue(Element pageContent, String labelRegex) {
        Element label = findLabel(pageContent, labelRegex);
        return label == null ? "" : valueOf(label);
    }

    /**
     * Class pages list each skill with its own "Type:", "Rank Needed:" and
     * "Notes:" labels, so labels inside the skill blocks are skipped.
     */
    private Element findLabel(Element pageContent, String labelRegex) {
        for (Element label : pageContent.select("strong:matchesOwn(" + labelRegex + "), b:matchesOwn(" + labelRegex + ")")) {
            if (label.closest(".skills, .collapsible-block") == null) {
                return label;
            }
        }
        return null;
    }

    /**
     * The wiki writes a labelled value inline, as a list under the label, or
     * both (e.g. "Price: N/A" followed by the merge requirements):
     * <pre>
     *   &lt;p&gt;&lt;strong&gt;Location:&lt;/strong&gt; &lt;a&gt;Shop&lt;/a&gt; - &lt;a&gt;Map&lt;/a&gt;&lt;br/&gt; ...
     *
     *   &lt;p&gt;&lt;strong&gt;Locations:&lt;/strong&gt;&lt;/p&gt;
     *   &lt;ul&gt;&lt;li&gt;&lt;a&gt;Shop&lt;/a&gt; - &lt;a&gt;Map&lt;/a&gt;&lt;/li&gt; ... &lt;/ul&gt;
     * </pre>
     * The inline text runs up to the next line break or label. A list only
     * belongs to the label if the label's line is the last one in its paragraph.
     */
    private String valueOf(Element label) {
        List<Node> inlineNodes = new ArrayList<>();
        Element listStart = null;
        boolean endsParagraph = true;

        for (Node node = label.nextSibling(); node != null; node = node.nextSibling()) {
            if (node instanceof Element el) {
                if (isList(el)) {
                    listStart = el;
                    break;
                }
                if (isLabel(el) || (el.tagName().equals("br") && !onlyBlankAfter(el))) {
                    endsParagraph = false;
                    break;
                }
            }
            inlineNodes.add(node);
        }

        Element parent = label.parent();
        if (listStart == null && endsParagraph && parent != null && parent.tagName().equals("p")) {
            listStart = parent.nextElementSibling();
        }

        List<String> parts = new ArrayList<>();
        // Covers "<strong>Location</strong>: ..." where the colon sits outside the bold text
        String inline = renderInline(inlineNodes).replaceFirst("^:\\s*", "");
        if (!inline.isEmpty()) {
            parts.add(inline);
        }
        parts.addAll(renderListsFrom(listStart));
        return String.join("\n", parts);
    }

    /** Renders the list at {@code start}, plus any "OR" alternatives the wiki chains after it. */
    private List<String> renderListsFrom(Element start) {
        List<String> parts = new ArrayList<>();
        Element list = start;

        while (list != null && isList(list)) {
            String rendered = renderList(list, 0);
            if (!rendered.isEmpty()) {
                parts.add(rendered);
            }

            // "<p><strong>OR</strong></p><ul>..." offers a second way to get the item
            Element next = list.nextElementSibling();
            Element afterNext = next == null ? null : next.nextElementSibling();
            if (next != null && next.tagName().equals("p") && next.text().trim().equalsIgnoreCase("or")
                    && afterNext != null && isList(afterNext)) {
                parts.add("**OR**");
                list = afterNext;
            } else {
                list = null;
            }
        }
        return parts;
    }

    private static boolean isList(Element el) {
        return el.tagName().equals("ul") || el.tagName().equals("ol");
    }

    /** A bold "Something:" that starts the next field, as opposed to bold text inside a value. */
    private static boolean isLabel(Element el) {
        if (!el.tagName().equals("strong") && !el.tagName().equals("b")) {
            return false;
        }
        return el.text().trim().endsWith(":")
                || (el.nextSibling() instanceof TextNode tn && tn.text().stripLeading().startsWith(":"));
    }

    private static boolean onlyBlankAfter(Node node) {
        for (Node next = node.nextSibling(); next != null; next = next.nextSibling()) {
            boolean blankText = next instanceof TextNode tn && tn.isBlank();
            boolean lineBreak = next instanceof Element el && el.tagName().equals("br");
            if (!blankText && !lineBreak) {
                return false;
            }
        }
        return true;
    }

    /** One bullet per list item, with nested lists (e.g. merge requirements) indented under their parent. */
    private String renderList(Element list, int depth) {
        String indent = " ".repeat(depth);
        String bullet = depth == 0 ? "• " : "◦ ";
        List<String> lines = new ArrayList<>();

        for (Element item : list.children()) {
            if (!item.tagName().equals("li")) {
                continue;
            }

            List<Node> ownContent = new ArrayList<>();
            List<Element> nestedLists = new ArrayList<>();
            for (Node child : item.childNodes()) {
                if (child instanceof Element el && isList(el)) {
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

    /**
     * The icons above the item (AC, Rare, Seasonal, event icons...). Only the
     * ones before the first label count; icons further down sit next to links
     * in the notes and describe other items.
     */
    private List<String> parseBadges(Element pageContent) {
        Set<String> badges = new LinkedHashSet<>();
        for (Element el : pageContent.select("img[src*=/image-tags/], strong, b")) {
            if (!el.tagName().equals("img")) {
                break;
            }
            badges.add(badgeName(el));
        }
        return new ArrayList<>(badges);
    }

    private static String badgeName(Element img) {
        String src = img.attr("src");
        String file = src.substring(src.lastIndexOf('/') + 1).replaceFirst("\\.\\w+$", "");
        String key = file.replaceFirst("(large|small)$", "");

        String known = BADGE_NAMES.get(key);
        if (known != null) {
            return known;
        }

        // Event icons link to the event's page, whose slug is a readable name
        // ("talk-like-a-pirate-day") where the file name isn't ("tlapd").
        Element parent = img.parent();
        if (parent != null && parent.tagName().equals("a")) {
            String href = parent.attr("href");
            String slug = href.substring(href.lastIndexOf('/') + 1);
            if (!slug.isBlank()) {
                return titleCase(slug);
            }
        }
        return titleCase(key);
    }

    private static String titleCase(String slug) {
        return Arrays.stream(slug.split("[-_\\s]+"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
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
                .setDescription(buildDescription(page))
                .setColor(colorFor(page))
                .setFooter(page.access() == null ? "AQW Wiki" : page.access() + " • AQW Wiki");

        addInlineField(embed, "🏷️ Type", page.type());
        addInlineField(embed, "💎 Rarity", page.rarity());
        addInlineField(embed, "🧭 Obtained From", String.join(", ", page.sources()));
        addInlineField(embed, "📈 Level", page.level());
        addInlineField(embed, "⚔️ Damage", page.damage());
        addInlineField(embed, "🎖️ Rank Needed", page.rank());
        addInlineField(embed, "📊 Stat Model", page.statModel());
        addValueField(embed, "💰 Price", page.price(), MessageEmbed.VALUE_MAX_LENGTH);
        addValueField(embed, "💸 Sellback", page.sellback(), SELLBACK_LIMIT);

        String location = page.location().isEmpty() ? "N/A" : page.location();
        embed.addField("📍 " + page.locationLabel(), truncateLines(location, MessageEmbed.VALUE_MAX_LENGTH), false);

        if (!page.specialEffects().isEmpty()) {
            embed.addField("\u2728 Special Effects", truncateLines(page.specialEffects(), SPECIAL_EFFECTS_LIMIT), false);
        }
        if (!page.notes().isEmpty()) {
            embed.addField("📝 Notes", truncateLines(page.notes(), NOTES_LIMIT), false);
        }

        if (page.imageUrl() != null && !page.imageUrl().isBlank()) {
            embed.setImage(page.imageUrl());
        }

        return embed.build();
    }

    /** Badges on top, then the in-game description as a quote. */
    private String buildDescription(WikiPage page) {
        List<String> sections = new ArrayList<>();

        if (!page.badges().isEmpty()) {
            sections.add(page.badges().stream().map(badge -> "`" + badge + "`").collect(Collectors.joining(" ")));
        }
        if (!page.description().isEmpty()) {
            String quoted = truncateLines(page.description(), DESCRIPTION_LIMIT).lines()
                    .map(line -> "> " + line)
                    .collect(Collectors.joining("\n"));
            sections.add(quoted);
        }

        return sections.isEmpty() ? null : String.join("\n\n", sections);
    }

    private static Color colorFor(WikiPage page) {
        List<String> badges = page.badges();
        if (badges.contains("Rare") || badges.contains("Pseudo Rare")) return RARE_COLOR;
        if (badges.contains("AC")) return AC_COLOR;
        if (badges.contains("Legend") || "Member only".equals(page.access())) return LEGEND_COLOR;
        if (badges.contains("Seasonal")) return SEASONAL_COLOR;
        return DEFAULT_COLOR;
    }

    private static void addInlineField(EmbedBuilder embed, String name, String value) {
        if (value != null && !value.isBlank()) {
            embed.addField(name, truncate(value, MessageEmbed.VALUE_MAX_LENGTH), true);
        }
    }

    /** Short values sit inline next to the stats; multi-line ones (merge lists) get the full width. */
    private static void addValueField(EmbedBuilder embed, String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return;
        }
        boolean multiLine = value.contains("\n");
        embed.addField(name, truncateLines(value, maxLength), !multiLine);
    }

    /**
     * Drops whole lines rather than cutting mid-line, so a long list never
     * ends in a half-written markdown link.
     */
    private static String truncateLines(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        String[] lines = text.split("\n");
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (String line : lines) {
            String more = "\n…and " + (lines.length - shown - 1) + " more";
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
        return out + "\n…and " + (lines.length - shown) + " more";
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
