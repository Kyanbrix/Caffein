package com.github.kyanbrix.component.slashcommand;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.kyanbrix.utils.WikiLinks;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The /wiki view for quest pages: one quest at a time, with Previous/Next and
 * a menu to move through every quest on the page.
 * <p>
 * A quest page holds one or more chains. Each chain is a header (location, NPC,
 * requirements) followed by a tab view with one tab per quest. What a quest
 * lists differs from page to page, so every bold label is shown under its own
 * name instead of picking out known fields.
 */
public class QuestView {

    private static final Logger log = LoggerFactory.getLogger(QuestView.class);

    /** First part of the button custom IDs: {@code quest:<action>:<index>:<slug>}. */
    public static final String BUTTON_PREFIX = "quest";
    /** The quest menu's custom ID is this plus the page slug; each option's value is a quest index. */
    public static final String SELECT_PREFIX = "quest:pick:";

    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int FETCH_TIMEOUT_MS = 15_000;
    private static final Color ACCENT_COLOR = new Color(0xFF8C00);

    // All text in a components message shares a 4000 character budget:
    // 700 + 1300 + 1900 = 3900.
    private static final int HEADER_LIMIT = 700;
    private static final int QUEST_LIMIT = 1300;
    private static final int SECTIONS_LIMIT = 1900;
    private static final int DESCRIPTION_LIMIT = 800;
    private static final int COMPLETION_LIMIT = 250;
    private static final int MIN_SECTION_LENGTH = 40;

    // Discord menus hold at most 25 options
    private static final int MENU_SIZE = 25;

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    // Event icons are plain names ("staffbirthday.png"); the rarity tags end in large/small
    private static final Pattern SIZED_ICON = Pattern.compile("(?i)(large|small)\\.\\w+$");

    private static final Map<String, String> SECTION_EMOJIS = Map.of(
            "items required", "📦",
            "rewards", "🎁",
            "you may also choose one of", "🎲",
            "you may also receive at random", "🎰",
            "items", "🎒");

    // Filled when /wiki shows a quest page, so paging doesn't download the page again
    private static final Cache<String, QuestPage> PAGES = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(200)
            .build();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "quest-view");
        thread.setDaemon(true);
        return thread;
    });

    /** A bold label and its value as Discord markdown, links kept. */
    private record Field(String label, String value) {}

    /** {@code info} holds the quest's own requirements and notes; {@code sections} everything else, in page order. */
    private record Quest(String name, List<Field> info, String description, String completion, List<Field> sections) {}

    private record Chain(String title, List<Field> header, List<Quest> quests) {}

    private record QuestPage(String name, String url, List<String> badges, String iconUrl, List<Chain> chains) {

        List<Entry> entries() {
            return chains.stream()
                    .flatMap(chain -> chain.quests().stream().map(quest -> new Entry(chain, quest)))
                    .toList();
        }
    }

    /** One quest with the chain it belongs to; Previous/Next walk these across chains. */
    private record Entry(Chain chain, Quest quest) {}

    /** What a run of sibling elements holds. {@code completion} is null without a "When Completed" block. */
    private record Block(List<Field> fields, List<String> paragraphs, String completion) {}

    private QuestView() {}

    public static boolean isQuestPage(Document document) {
        return document.select(".page-tags a").eachText().contains("quest");
    }

    /** Answers a deferred /wiki reply with the first quest on the page. */
    static void reply(InteractionHook hook, Document document, String url) {
        String slug = WikiLinks.slugOf(url);
        QuestPage page = parse(document, slug == null ? url : WikiLinks.WIKI_BASE_URL + slug);
        if (page.entries().isEmpty()) {
            hook.sendMessage("No quests are listed on this page.")
                    .addComponents(ActionRow.of(Button.link(page.url(), "View Wiki")))
                    .queue();
            return;
        }

        if (slug != null) {
            PAGES.put(slug, page);
        }
        hook.sendMessageComponents(render(page, slug, 0)).useComponentsV2().queue();
    }

    /**
     * Previous/Next, routed here from the ButtonManager. The page is identified
     * by its slug in the button ID, so the buttons keep working after a restart.
     */
    public static void handleButton(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":", 4);
        int index = parts.length == 4 ? parseIndex(parts[2]) : -1;
        if (index < 0 || !WikiLinks.isValidSlug(parts[3])) {
            event.reply("This button is no longer valid.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        show(event.getHook(), parts[3], index);
    }

    /** The "Jump to a quest" menu, routed here from StringSelectionComponent. */
    public static void handleSelect(StringSelectInteractionEvent event) {
        String slug = event.getComponentId().substring(SELECT_PREFIX.length());
        int index = event.getValues().isEmpty() ? -1 : parseIndex(event.getValues().getFirst());
        if (index < 0 || !WikiLinks.isValidSlug(slug)) {
            event.reply("That option is no longer valid.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        show(event.getHook(), slug, index);
    }

    private static int parseIndex(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void show(InteractionHook hook, String slug, int index) {
        EXECUTOR.submit(() -> {
            try {
                QuestPage page = load(slug);
                if (page.entries().isEmpty()) {
                    hook.sendMessage("No quests are listed on this page.").setEphemeral(true).queue();
                    return;
                }
                hook.editOriginalComponents(render(page, slug, index)).useComponentsV2().queue();
            } catch (IOException e) {
                log.warn("Failed to fetch quest page {}: {}", slug, e.getMessage());
                hook.sendMessage("Couldn't reach the AQW wiki right now. Try again in a bit.").setEphemeral(true).queue();
            } catch (RuntimeException e) {
                log.error("Failed to show quests for {}", slug, e);
                hook.sendMessage("Something went wrong while loading that quest.").setEphemeral(true).queue();
            }
        });
    }

    private static QuestPage load(String slug) throws IOException {
        QuestPage cached = PAGES.getIfPresent(slug);
        if (cached != null) {
            return cached;
        }

        String url = WikiLinks.WIKI_BASE_URL + slug;
        Document document = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(FETCH_TIMEOUT_MS)
                .get();

        QuestPage page = parse(document, url);
        PAGES.put(slug, page);
        return page;
    }

    private static QuestPage parse(Document document, String url) {
        Element title = document.getElementById("page-title");
        String name = title == null || title.text().isBlank() ? "Quests" : title.text().trim();

        Element pageContent = document.getElementById("page-content");
        if (pageContent == null) {
            return new QuestPage(name, url, List.of(), null, List.of());
        }

        return new QuestPage(name, url, Wiki.parseBadges(pageContent), findIcon(pageContent),
                parseChains(pageContent, name));
    }

    /**
     * <pre>
     *   &lt;p&gt;&lt;a name="Riddle"&gt;&lt;/a&gt;&lt;/p&gt;
     *   &lt;p&gt;&lt;strong&gt;Quest Location:&lt;/strong&gt; ...&lt;br/&gt;&lt;strong&gt;Quests Begun From:&lt;/strong&gt; ...&lt;/p&gt;
     *   &lt;div class="yui-navset"&gt; one tab per quest &lt;/div&gt;
     *   &lt;hr/&gt;
     *   &lt;p&gt;&lt;strong&gt;Quest Location:&lt;/strong&gt; ...   (the next chain)
     * </pre>
     * A page without any tabs describes a single quest in its body.
     */
    private static List<Chain> parseChains(Element pageContent, String pageName) {
        // "Jump to: <a href="#Riddle">Broken Woods (Riddle)</a>" names the chain under <a name="Riddle">
        Map<String, String> jumpLinks = new HashMap<>();
        for (Element link : pageContent.select("a[href^=#]")) {
            jumpLinks.putIfAbsent(link.attr("href").substring(1), link.text().trim());
        }

        List<Chain> chains = new ArrayList<>();
        for (Element navset : pageContent.select("div.yui-navset")) {
            // Tabs inside a quest's tab belong to that quest
            if (navset.parents().stream().anyMatch(parent -> parent.hasClass("yui-content"))) {
                continue;
            }

            List<Element> header = headerOf(navset, pageContent);
            List<Field> fields = readBlock(header).fields();
            List<Quest> quests = parseTabs(navset);
            if (!quests.isEmpty()) {
                chains.add(new Chain(chainTitle(header, fields, jumpLinks), fields, quests));
            }
        }

        if (chains.isEmpty()) {
            Block block = readBlock(pageContent.children());
            List<Field> header = new ArrayList<>();
            List<Field> rest = new ArrayList<>();
            for (Field field : block.fields()) {
                (isChainField(field.label()) ? header : rest).add(field);
            }
            Quest quest = toQuest(pageName, new Block(rest, block.paragraphs(), block.completion()));
            chains.add(new Chain(chainTitle(List.of(), header, jumpLinks), header, List.of(quest)));
        }
        return chains;
    }

    /** The elements between this tab view and the one before it, or the top of the page. */
    private static List<Element> headerOf(Element navset, Element pageContent) {
        // A tab view wrapped in a div is the first thing in it, so climb to the level its header sits at
        Element anchor = navset;
        while (anchor.previousElementSibling() == null && anchor.parent() != null && anchor.parent() != pageContent) {
            anchor = anchor.parent();
        }

        List<Element> header = new ArrayList<>();
        for (Element el = anchor.previousElementSibling(); el != null; el = el.previousElementSibling()) {
            if (!el.select("div.yui-navset").isEmpty()) {
                break;
            }
            header.addFirst(el);
        }
        return header;
    }

    /** Tabs and their panels pair up by position, as in {@link Wiki#findItemImages}. */
    private static List<Quest> parseTabs(Element navset) {
        List<String> names = navset.select("> ul.yui-nav > li").eachText();
        Element content = navset.selectFirst("> div.yui-content");
        if (content == null) {
            return List.of();
        }

        List<Quest> quests = new ArrayList<>();
        List<Element> panels = content.children();
        for (int i = 0; i < panels.size(); i++) {
            String name = i < names.size() && !names.get(i).isBlank() ? names.get(i).trim() : "Quest " + (i + 1);
            quests.add(toQuest(name, readBlock(panels.get(i).children())));
        }
        return quests;
    }

    private static Quest toQuest(String name, Block block) {
        List<Field> info = new ArrayList<>();
        List<Field> sections = new ArrayList<>();
        for (Field field : block.fields()) {
            (isInfo(field.label()) ? info : sections).add(field);
        }
        return new Quest(name, info, String.join("\n", block.paragraphs()), block.completion(), sections);
    }

    /**
     * Reads a run of sibling elements, e.g. one quest's tab:
     * <pre>
     *   &lt;p&gt;&lt;strong&gt;Requirements:&lt;/strong&gt; Must have completed the previous quest.&lt;/p&gt;
     *   &lt;p&gt;The quest text...&lt;/p&gt;
     *   &lt;div class="collapsible-block"&gt; + When Completed ... &lt;/div&gt;
     *   &lt;p&gt;&lt;strong&gt;Items Required:&lt;/strong&gt;&lt;/p&gt;
     *   &lt;ul&gt;&lt;li&gt;Everlasting Scale x1&lt;ul&gt;&lt;li&gt;Dropped by ...&lt;/li&gt;&lt;/ul&gt;&lt;/li&gt;&lt;/ul&gt;
     * </pre>
     * Every label becomes a field whatever its name. A list after a labelled
     * paragraph is the value of its last label, read by {@link Wiki#valueOf}.
     */
    private static Block readBlock(List<Element> elements) {
        List<Field> fields = new ArrayList<>();
        List<String> paragraphs = new ArrayList<>();
        String completion = null;
        Set<Element> consumed = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Element el : elements) {
            if (consumed.contains(el)) {
                continue;
            }

            if (el.tagName().equals("p")) {
                List<Element> labels = el.select("strong, b").stream().filter(Wiki::isLabel).toList();
                if (labels.isEmpty()) {
                    String text = Wiki.renderInline(el.childNodes());
                    if (!text.isEmpty() && !isCredit(el)) {
                        paragraphs.add(text);
                    }
                    continue;
                }

                for (Element label : labels) {
                    addField(fields, label.text().replace(":", "").trim(), Wiki.valueOf(label));
                }
                consumed.addAll(listsAfter(el));
            } else if (el.hasClass("collapsible-block")) {
                Element link = el.selectFirst(".collapsible-block-folded a");
                Element content = el.selectFirst(".collapsible-block-content");
                String title = link == null ? ""
                        : link.text().replace(' ', ' ').replaceFirst("^\\s*[+\\-]\\s*", "").strip();
                String text = content == null ? "" : content.text().strip();

                if (title.equalsIgnoreCase("when completed")) {
                    completion = text.isEmpty() ? null : text;
                } else {
                    addField(fields, title.isEmpty() ? "More" : title, MarkdownSanitizer.escape(text));
                }
            } else if (isList(el)) {
                // A list no label claimed
                addField(fields, "Details", String.join("\n", Wiki.renderListsFrom(el)));
            }
        }

        return new Block(fields, paragraphs, completion);
    }

    private static void addField(List<Field> fields, String label, String value) {
        if (!label.isEmpty() && !value.isBlank()) {
            fields.add(new Field(label, value));
        }
    }

    /** The list after a paragraph, plus any "OR" alternatives chained after it, as {@link Wiki#valueOf} reads them. */
    private static List<Element> listsAfter(Element paragraph) {
        List<Element> taken = new ArrayList<>();
        Element next = paragraph.nextElementSibling();

        while (next != null && isList(next)) {
            taken.add(next);
            Element or = next.nextElementSibling();
            Element afterOr = or == null ? null : or.nextElementSibling();
            if (or != null && or.tagName().equals("p") && or.text().trim().equalsIgnoreCase("or")
                    && afterOr != null && isList(afterOr)) {
                taken.add(or);
                next = afterOr;
            } else {
                next = null;
            }
        }
        return taken;
    }

    private static boolean isList(Element el) {
        return el.tagName().equals("ul") || el.tagName().equals("ol");
    }

    /** "Thanks to ..." lines credit whoever added the quest to the wiki. */
    private static boolean isCredit(Element paragraph) {
        return paragraph.text().trim().toLowerCase(Locale.ROOT).startsWith("thanks to");
    }

    private static boolean isInfo(String label) {
        String key = label.toLowerCase(Locale.ROOT);
        return key.startsWith("requirement") || key.startsWith("note");
    }

    private static boolean isChainField(String label) {
        String key = label.toLowerCase(Locale.ROOT);
        return key.startsWith("quest location") || key.contains("begun from");
    }

    /** The "Jump to" name of the chain's anchor, else its location, else "Quests". */
    private static String chainTitle(List<Element> header, List<Field> fields, Map<String, String> jumpLinks) {
        String title = null;
        for (Element el : header) {
            for (Element anchor : el.select("a[name]")) {
                String linked = jumpLinks.get(anchor.attr("name"));
                if (linked != null && !linked.isBlank()) {
                    title = linked;
                }
            }
        }
        if (title != null) {
            return title;
        }

        for (Field field : fields) {
            if (field.label().toLowerCase(Locale.ROOT).startsWith("quest location")) {
                String location = plainText(field.value());
                if (!location.isEmpty() && !location.equalsIgnoreCase("n/a")) {
                    return location;
                }
            }
        }
        return "Quests";
    }

    /** Markdown links and bullets stripped, lines joined with commas. */
    private static String plainText(String markdown) {
        return MARKDOWN_LINK.matcher(markdown).replaceAll("$1").lines()
                .map(line -> line.strip().replaceFirst("^[•◦]\\s*", ""))
                .filter(line -> !line.isEmpty())
                .collect(Collectors.joining(", "));
    }

    /** The event icon above the quests (e.g. Staff Birthday); the small rarity tags are skipped. */
    private static String findIcon(Element pageContent) {
        for (Element el : pageContent.select("img[src*=/image-tags/], strong, b")) {
            if (!el.tagName().equals("img")) {
                break;
            }
            if (!SIZED_ICON.matcher(el.attr("src")).find()) {
                return WikiLinks.validMediaUrl(el.absUrl("src"));
            }
        }
        return null;
    }

    /** {@code slug} is null when the page can't be addressed in a custom ID; then only the wiki link is shown. */
    private static Container render(QuestPage page, String slug, int requestedIndex) {
        List<Entry> entries = page.entries();
        // The page may have changed since the buttons were made
        int index = Math.clamp(requestedIndex, 0, entries.size() - 1);
        Entry entry = entries.get(index);

        List<ContainerChildComponent> components = new ArrayList<>();
        boolean manyChains = page.chains().size() > 1;
        TextDisplay header = TextDisplay.of(headerText(page, entry.chain(), manyChains, index, entries.size()));
        components.add(page.iconUrl() == null ? header : Section.of(Thumbnail.fromUrl(page.iconUrl()), header));
        components.add(Separator.createDivider(Separator.Spacing.SMALL));
        components.add(TextDisplay.of(questText(entry.quest())));

        String sections = sectionsText(entry.quest().sections());
        if (!sections.isEmpty()) {
            components.add(Separator.createDivider(Separator.Spacing.SMALL));
            components.add(TextDisplay.of(sections));
        }

        components.add(Separator.createDivider(Separator.Spacing.LARGE));
        if (slug != null && entries.size() > 1) {
            components.add(ActionRow.of(questMenu(entries, slug, manyChains, index)));
        }
        components.add(navigation(page, slug, index, entries.size()));

        return Container.of(components).withAccentColor(ACCENT_COLOR);
    }

    /**
     * Page name, badges, which chain and quest this is, then the chain's location
     * and NPC. The chain is only named when there's more than one, since otherwise
     * its name just repeats the location below it.
     */
    private static String headerText(QuestPage page, Chain chain, boolean manyChains, int index, int total) {
        StringBuilder text = new StringBuilder("## 📜 ").append(MarkdownSanitizer.escape(page.name()));

        if (!page.badges().isEmpty()) {
            text.append('\n').append(page.badges().stream()
                    .map(badge -> "`" + badge + "`")
                    .collect(Collectors.joining(" ")));
        }

        List<String> position = new ArrayList<>();
        if (manyChains) {
            position.add(MarkdownSanitizer.escape(chain.title()));
        }
        if (total > 1) {
            position.add("Quest " + (index + 1) + " of " + total);
        }
        if (!position.isEmpty()) {
            text.append("\n-# ").append(String.join(" · ", position));
        }

        for (Field field : chain.header()) {
            text.append('\n').append(fieldText(field));
        }
        return Wiki.truncateLines(text.toString(), HEADER_LIMIT);
    }

    private static String questText(Quest quest) {
        StringBuilder text = new StringBuilder("### ").append(MarkdownSanitizer.escape(quest.name()));

        for (Field field : quest.info()) {
            text.append('\n').append(fieldText(field));
        }

        if (!quest.description().isEmpty()) {
            text.append('\n').append(Wiki.truncateLines(quest.description(), DESCRIPTION_LIMIT).lines()
                    .map(line -> "> " + line)
                    .collect(Collectors.joining("\n")));
        }

        if (quest.completion() != null) {
            text.append("\n-# ✅ When completed: ")
                    .append(Wiki.truncate(MarkdownSanitizer.escape(quest.completion()), COMPLETION_LIMIT));
        }

        return Wiki.truncateLines(text.toString(), QUEST_LIMIT);
    }

    /**
     * Items Required, Rewards and whatever else the quest lists. Each section
     * gets an even share of what's left, so a long drop list can't push the
     * rewards out.
     */
    private static String sectionsText(List<Field> sections) {
        List<String> parts = new ArrayList<>();
        int remaining = SECTIONS_LIMIT;

        for (int i = 0; i < sections.size(); i++) {
            // 2 for the blank line between sections
            int share = remaining / (sections.size() - i) - 2;
            if (share < MIN_SECTION_LENGTH) {
                break;
            }

            Field section = sections.get(i);
            String text = "**" + emojiFor(section.label()) + " " + MarkdownSanitizer.escape(section.label()) + "**\n"
                    + section.value();
            String shown = Wiki.truncateLines(text, share);
            parts.add(shown);
            remaining -= shown.length() + 2;
        }
        return String.join("\n\n", parts);
    }

    /** "**📍 Quest Location:** value" inline, or the value's list under the label. */
    private static String fieldText(Field field) {
        String label = "**" + emojiFor(field.label()) + " " + MarkdownSanitizer.escape(field.label()) + ":**";
        return label + (field.value().contains("\n") ? "\n" : " ") + field.value();
    }

    private static String emojiFor(String label) {
        String key = label.toLowerCase(Locale.ROOT);
        if (key.startsWith("quest location")) return "📍";
        if (key.contains("begun from")) return "🗣️";
        if (key.startsWith("requirement")) return "🔒";
        if (key.startsWith("note")) return "📝";
        return SECTION_EMOJIS.getOrDefault(key, "📋");
    }

    /** Up to 25 quests around the current one, each described by its chain when there are several. */
    private static StringSelectMenu questMenu(List<Entry> entries, String slug, boolean manyChains, int index) {
        int start = Math.clamp(index - MENU_SIZE / 2, 0, Math.max(0, entries.size() - MENU_SIZE));
        int end = Math.min(entries.size(), start + MENU_SIZE);

        List<SelectOption> options = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Entry entry = entries.get(i);
            SelectOption option = SelectOption.of(
                            Wiki.truncate((i + 1) + ". " + entry.quest().name(), SelectOption.LABEL_MAX_LENGTH),
                            String.valueOf(i))
                    .withDefault(i == index);
            if (manyChains) {
                option = option.withDescription(Wiki.truncate(entry.chain().title(), SelectOption.DESCRIPTION_MAX_LENGTH));
            }
            options.add(option);
        }

        return StringSelectMenu.create(SELECT_PREFIX + slug)
                .setPlaceholder("Jump to a quest…")
                .addOptions(options)
                .build();
    }

    /** Previous / counter / Next only when there's more than one quest; the wiki link always. */
    private static ActionRow navigation(QuestPage page, String slug, int index, int total) {
        Button wikiLink = Button.link(page.url(), "View Wiki");
        if (slug == null || total == 1) {
            return ActionRow.of(wikiLink);
        }

        return ActionRow.of(
                Button.secondary(buttonId("prev", index - 1, slug), "Previous").withDisabled(index == 0),
                Button.secondary(buttonId("page", index, slug), (index + 1) + "/" + total).withDisabled(true),
                Button.secondary(buttonId("next", index + 1, slug), "Next").withDisabled(index == total - 1),
                wikiLink);
    }

    private static String buttonId(String action, int index, String slug) {
        return BUTTON_PREFIX + ":" + action + ":" + index + ":" + slug;
    }
}
