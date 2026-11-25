package com.github.kyanbrix.component.slashcommand;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.kyanbrix.utils.WikiLinks;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * The "Show Skills" view for /wiki results that are classes: every skill with
 * its icon, stats and description, a few per page.
 */
public class ClassSkills {

    private static final Logger log = LoggerFactory.getLogger(ClassSkills.class);

    /** First part of the button custom IDs: {@code classskills:<action>:<page>:<slug>}. */
    public static final String BUTTON_PREFIX = "classskills";

    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int FETCH_TIMEOUT_MS = 15_000;

    // Classes have 7-9 skills, so the usual 8 fit on two pages without cutting descriptions
    private static final int SKILLS_PER_PAGE = 4;
    private static final Color ACCENT_COLOR = new Color(0x9B59B6);

    // All text in a components message shares a 4000 character budget:
    // 300 + 4 * 920 = 3980.
    private static final int HEADER_LIMIT = 300;
    private static final int SKILL_TEXT_LIMIT = 920;
    private static final int DESCRIPTION_LIMIT = 500;
    private static final int RANGE_LIMIT = 150;
    private static final int NOTES_LIMIT = 400;

    // Filled when /wiki shows a class, so the first click doesn't download the page again
    private static final Cache<String, ClassPage> PAGES = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(200)
            .build();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "class-skills");
        thread.setDaemon(true);
        return thread;
    });

    /** Any field the wiki leaves out is an empty string. */
    private record Skill(String name, String iconUrl, String rank, String manaCost, String cooldown,
                         String type, String range, String description, List<String> notes) {}

    private record ClassPage(String name, String url, String imageUrl, List<Skill> skills) {}

    private ClassSkills() {}

    /**
     * The "Show Skills" button for a /wiki result, or null if the page lists no
     * skills. Reads the page /wiki already downloaded and keeps the result for
     * the click.
     */
    static Button showButton(Document document, String url) {
        String slug = WikiLinks.slugOf(url);
        if (slug == null) {
            return null;
        }

        ClassPage page = parse(document, WikiLinks.WIKI_BASE_URL + slug);
        if (page.skills().isEmpty()) {
            return null;
        }

        PAGES.put(slug, page);
        return Button.primary(buttonId("show", 1, slug), "Show Skills").withEmoji(Emoji.fromUnicode("U+1F4DC"));
    }

    /**
     * "show" posts the skills as a new message in the channel; "prev" and
     * "next" page through that message. The class is identified by its slug in
     * the button ID, so the buttons keep working after a restart.
     */
    public static void handleButton(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":", 4);
        if (parts.length != 4 || !WikiLinks.isValidSlug(parts[3])) {
            event.reply("This button is no longer valid.").setEphemeral(true).queue();
            return;
        }

        int page;
        try {
            page = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            event.reply("This button is no longer valid.").setEphemeral(true).queue();
            return;
        }

        String slug = parts[3];
        boolean opening = parts[1].equals("show");
        if (opening) {
            event.deferReply().queue();
        } else {
            event.deferEdit().queue();
        }

        EXECUTOR.submit(() -> {
            InteractionHook hook = event.getHook();
            try {
                ClassPage classPage = load(slug);
                if (classPage.skills().isEmpty()) {
                    hook.sendMessage("No skills are listed for this class on the wiki.").setEphemeral(true).queue();
                    return;
                }

                Container container = render(classPage, slug, page);
                if (opening) {
                    hook.sendMessageComponents(container).useComponentsV2().queue();
                } else {
                    hook.editOriginalComponents(container).useComponentsV2().queue();
                }
            } catch (IOException e) {
                log.warn("Failed to fetch class page {}: {}", slug, e.getMessage());
                hook.sendMessage("Couldn't reach the AQW wiki right now. Try again in a bit.").setEphemeral(true).queue();
            } catch (RuntimeException e) {
                log.error("Failed to show skills for {}", slug, e);
                hook.sendMessage("Something went wrong while loading the skills.").setEphemeral(true).queue();
            }
        });
    }

    private static ClassPage load(String slug) throws IOException {
        ClassPage cached = PAGES.getIfPresent(slug);
        if (cached != null) {
            return cached;
        }

        String url = WikiLinks.WIKI_BASE_URL + slug;
        Document document = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(FETCH_TIMEOUT_MS)
                .get();

        ClassPage page = parse(document, url);
        PAGES.put(slug, page);
        return page;
    }

    private static ClassPage parse(Document document, String url) {
        Element title = document.getElementById("page-title");
        String name = title == null || title.text().isBlank() ? "Class" : title.text().trim();

        Element pageContent = document.getElementById("page-content");
        if (pageContent == null) {
            return new ClassPage(name, url, null, List.of());
        }

        String image = Wiki.findItemImages(pageContent).stream()
                .map(WikiLinks::validMediaUrl)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return new ClassPage(name, url, image, parseSkills(pageContent));
    }

    /**
     * Each skill is a collapsible block, usually inside a "skills" div (a few
     * older pages leave the div out):
     * <pre>
     *   &lt;div class="collapsible-block"&gt;
     *     &lt;div class="collapsible-block-folded"&gt;&lt;a&gt;+&amp;nbsp;Malevolent&amp;nbsp;Burst&lt;/a&gt;&lt;/div&gt;
     *     ...
     *     &lt;div class="collapsible-block-content"&gt;
     *       &lt;div class="image-container"&gt;&lt;img src="classes-skills/LRAA.png"&gt;&lt;/div&gt;
     *       &lt;p&gt;&lt;strong&gt;&lt;span&gt;Malevolent Burst&lt;/span&gt;&lt;/strong&gt;&lt;br/&gt;
     *          &lt;strong&gt;Rank Needed:&lt;/strong&gt; 0&lt;br/&gt; ...
     *          &lt;strong&gt;Notes:&lt;/strong&gt;&lt;/p&gt;
     *       &lt;ul&gt;&lt;li&gt;...&lt;/li&gt;&lt;/ul&gt;
     * </pre>
     * Only blocks with a "Rank Needed" field are skills; other collapsibles on
     * the page (trivia, alternate versions) are skipped.
     */
    private static List<Skill> parseSkills(Element pageContent) {
        List<Skill> skills = new ArrayList<>();

        for (Element content : pageContent.select(".collapsible-block-content")) {
            // select() includes the element itself, so more than one match means
            // this block wraps other blocks; those inner blocks are read on their own.
            if (content.select(".collapsible-block-content").size() > 1) {
                continue;
            }

            Map<String, String> fields = readFields(content);
            String rank = firstOf(fields, "rank needed", "rank required");
            if (rank == null) {
                continue;
            }

            Element icon = content.selectFirst("img[src]");
            skills.add(new Skill(
                    skillName(content),
                    icon == null ? null : WikiLinks.validMediaUrl(icon.absUrl("src")),
                    rank,
                    Objects.requireNonNullElse(firstOf(fields, "mana cost"), ""),
                    Objects.requireNonNullElse(firstOf(fields, "cooldown"), ""),
                    Objects.requireNonNullElse(firstOf(fields, "type"), ""),
                    Objects.requireNonNullElse(firstOf(fields, "range"), ""),
                    Objects.requireNonNullElse(firstOf(fields, "description"), ""),
                    notes(content, fields)));
        }

        return List.copyOf(skills);
    }

    /** Label (lowercase, no colon) -> its inline value. The first label of a name wins. */
    private static Map<String, String> readFields(Element content) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Element label : content.select("strong, b")) {
            if (!Wiki.isLabel(label)) {
                continue;
            }
            String key = label.text().replace(":", "").trim().toLowerCase(Locale.ROOT);
            fields.putIfAbsent(key, Wiki.plainValue(label));
        }
        return fields;
    }

    private static String firstOf(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            if (fields.containsKey(key)) {
                return fields.get(key);
            }
        }
        return null;
    }

    /** "Note: one thing" inline, or "Notes:" at the end of the paragraph followed by a list. */
    private static List<String> notes(Element content, Map<String, String> fields) {
        String inline = firstOf(fields, "notes", "note");
        if (inline != null && !inline.isEmpty()) {
            return List.of(inline);
        }

        for (Element label : content.select("strong, b")) {
            String key = label.text().replace(":", "").trim().toLowerCase(Locale.ROOT);
            if (!Wiki.isLabel(label) || !(key.equals("notes") || key.equals("note"))) {
                continue;
            }

            Element parent = label.parent();
            Element list = parent == null ? null : parent.nextElementSibling();
            if (list != null && (list.tagName().equals("ul") || list.tagName().equals("ol"))) {
                return list.select("> li").stream()
                        .map(li -> li.text().trim())
                        .filter(text -> !text.isEmpty())
                        .toList();
            }
        }
        return List.of();
    }

    /** The collapsible's title ("+&nbsp;Malevolent&nbsp;Burst"), or the bold name at the top of the block. */
    private static String skillName(Element content) {
        Element block = content.closest(".collapsible-block");
        Element link = block == null ? null : block.selectFirst(".collapsible-block-folded a");
        if (link != null) {
            String name = link.text().replace(' ', ' ').replaceFirst("^\\s*[+\\-]\\s*", "").strip();
            if (!name.isEmpty()) {
                return name;
            }
        }

        for (Element bold : content.select("strong, b")) {
            if (!Wiki.isLabel(bold) && !bold.text().isBlank()) {
                return bold.text().trim();
            }
        }
        return "Skill";
    }

    /**
     * The class's skills as plain text for the chatbot's aqw_wiki tool, one skill per entry;
     * null if the page lists none. {@code descriptionLimit} caps each skill's description and notes.
     */
    public static String describeSkills(Document document, int descriptionLimit) {
        Element pageContent = document.getElementById("page-content");
        List<Skill> skills = pageContent == null ? List.of() : parseSkills(pageContent);
        if (skills.isEmpty()) {
            return null;
        }

        StringBuilder out = new StringBuilder();
        for (Skill skill : skills) {
            List<String> stats = new ArrayList<>();
            if (!skill.rank().isEmpty()) stats.add("rank " + skill.rank());
            if (!skill.manaCost().isEmpty()) stats.add("mana " + skill.manaCost());
            if (!skill.cooldown().isEmpty()) stats.add("cooldown " + skill.cooldown());
            if (!skill.type().isEmpty()) stats.add(skill.type());
            if (!skill.range().isEmpty()) stats.add("range " + skill.range());

            out.append("- ").append(skill.name());
            if (!stats.isEmpty()) out.append(" (").append(String.join(", ", stats)).append(')');
            if (!skill.description().isEmpty()) out.append(": ").append(Wiki.truncate(skill.description(), descriptionLimit));
            if (!skill.notes().isEmpty()) {
                out.append(" Notes: ").append(Wiki.truncate(String.join("; ", skill.notes()), descriptionLimit));
            }
            out.append('\n');
        }
        return out.toString().strip();
    }

    private static Container render(ClassPage classPage, String slug, int requestedPage) {
        List<Skill> skills = classPage.skills();
        int totalPages = Math.ceilDiv(skills.size(), SKILLS_PER_PAGE);
        int page = Math.clamp(requestedPage, 1, totalPages);

        int start = (page - 1) * SKILLS_PER_PAGE;
        List<Skill> pageSkills = skills.subList(start, Math.min(start + SKILLS_PER_PAGE, skills.size()));

        List<ContainerChildComponent> components = new ArrayList<>();
        TextDisplay header = TextDisplay.of(header(classPage));
        components.add(classPage.imageUrl() == null ? header : Section.of(Thumbnail.fromUrl(classPage.imageUrl()), header));
        components.add(Separator.createDivider(Separator.Spacing.SMALL));

        for (int i = 0; i < pageSkills.size(); i++) {
            if (i > 0) {
                components.add(Separator.createDivider(Separator.Spacing.SMALL));
            }

            Skill skill = pageSkills.get(i);
            TextDisplay text = TextDisplay.of(skillText(skill));
            components.add(skill.iconUrl() == null ? text : Section.of(Thumbnail.fromUrl(skill.iconUrl()), text));
        }

        components.add(Separator.createDivider(Separator.Spacing.LARGE));
        components.add(navigation(classPage, slug, page, totalPages));

        return Container.of(components).withAccentColor(ACCENT_COLOR);
    }

    private static String header(ClassPage classPage) {
        int count = classPage.skills().size();
        String text = "## 📜 " +MarkdownSanitizer.escape(classPage.name()) + "\n"
                + "-# " + count + (count == 1 ? " skill" : " skills");
        return Wiki.truncate(text, HEADER_LIMIT);
    }

    private static String skillText(Skill skill) {
        StringBuilder text = new StringBuilder("### ").append(MarkdownSanitizer.escape(skill.name()));

        List<String> stats = new ArrayList<>();
        if (!skill.rank().isEmpty()) stats.add("🎖️ Rank " + skill.rank());
        if (!skill.manaCost().isEmpty()) stats.add("💧 " + skill.manaCost());
        if (!skill.cooldown().isEmpty()) stats.add("⏱️ " + skill.cooldown());
        if (!skill.type().isEmpty()) stats.add("🏷️ " + skill.type());
        if (!stats.isEmpty()) {
            text.append('\n').append(stats.stream().map(MarkdownSanitizer::escape).collect(Collectors.joining(" · ")));
        }

        if (!skill.description().isEmpty()) {
            text.append("\n> ").append(Wiki.truncate(MarkdownSanitizer.escape(skill.description()), DESCRIPTION_LIMIT));
        }

        if (!skill.range().isEmpty()) {
            text.append("\n-# 🎯 ").append(Wiki.truncate(MarkdownSanitizer.escape(skill.range()), RANGE_LIMIT));
        }

        if (!skill.notes().isEmpty()) {
            text.append('\n').append(notesText(skill.notes()));
        }

        return Wiki.truncate(text.toString(), SKILL_TEXT_LIMIT);
    }

    /** As many whole notes as fit, then a count of the rest, all in small text. */
    private static String notesText(List<String> notes) {
        StringBuilder text = new StringBuilder();
        int shown = 0;
        for (String note : notes) {
            String line = "-# 📝 " + MarkdownSanitizer.escape(note);
            if (shown > 0 && text.length() + 1 + line.length() > NOTES_LIMIT) {
                break;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(shown == 0 ? Wiki.truncate(line, NOTES_LIMIT) : line);
            shown++;
        }

        if (shown < notes.size()) {
            text.append("\n-# …and ").append(notes.size() - shown).append(" more on the wiki");
        }
        return text.toString();
    }

    /** Previous / page counter / Next only when there's more than one page; the wiki link always. */
    private static ActionRow navigation(ClassPage classPage, String slug, int page, int totalPages) {
        Button wikiLink = Button.link(classPage.url(), "View Wiki");
        if (totalPages == 1) {
            return ActionRow.of(wikiLink);
        }

        return ActionRow.of(
                Button.secondary(buttonId("prev", page - 1, slug), "Previous").withDisabled(page == 1),
                Button.secondary(buttonId("page", page, slug), page + "/" + totalPages).withDisabled(true),
                Button.secondary(buttonId("next", page + 1, slug), "Next").withDisabled(page == totalPages),
                wikiLink);
    }

    private static String buttonId(String action, int page, String slug) {
        return BUTTON_PREFIX + ":" + action + ":" + page + ":" + slug;
    }
}
