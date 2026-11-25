package com.github.kyanbrix.api.OpenAI.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.kyanbrix.component.slashcommand.Wiki;
import com.github.kyanbrix.utils.WikiLinks;
import com.github.kyanbrix.utils.cache.WikiCacheManager;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Backs the chatbot's {@code aqw_item_guide} tool: builds the whole "how to get it" tree for an
 * item in one call, so the model doesn't need a round trip per material.
 * <p>
 * From the item's wiki page it follows the merge materials ("Merge the following:"), the quests that
 * reward it ("Reward from the following quests:", read from the quest-giver's page), and those quests'
 * required items, several levels deep. Pages on the same level are fetched in parallel, and every
 * page is read at most once per guide. The result is an indented plain-text tree.
 */
public final class AqwGuideService {

    private static final Logger log = LoggerFactory.getLogger(AqwGuideService.class);

    // Root item is depth 0. Four levels covers chains like NSOD -> Blade -> Energized Blade -> Unenhanced Doom Blade.
    private static final int MAX_DEPTH = 4;
    private static final int MAX_PAGES = 100;
    private static final int MAX_QUESTS_PER_ITEM = 5;
    private static final int MAX_RESULT_CHARS = 18_000;

    private static final int LOCATION_LIMIT = 350;
    private static final int OTHER_SOURCE_LIMIT = 250;
    private static final int QUEST_TEXT_LIMIT = 600;
    private static final int SHORT_QUEST_TEXT_LIMIT = 250;
    private static final int ROOT_NOTES_LIMIT = 900;

    // "[Name](url) x25" in merge and quest requirement lists; the name may contain escaped brackets.
    private static final Pattern LINKED_AMOUNT = Pattern.compile("\\[((?:\\\\.|[^\\]\\\\])*)]\\(([^)\\s]*)\\)\\s*x\\s*([\\d,]+)");
    private static final Pattern PLAIN_AMOUNT = Pattern.compile("^(.+?)\\s*x\\s*([\\d,]+)$");
    private static final Pattern LINK = Pattern.compile("\\[((?:\\\\.|[^\\]\\\\])*)]\\(([^)\\s]*)\\)");

    // Few threads, to stay polite to the wiki
    private static final ExecutorService FETCHER = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "aqw-guide-fetch");
        thread.setDaemon(true);
        return thread;
    });

    private static final Cache<String, String> GUIDES = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    // Parsed pages, shared across guides so common materials (Void Aura...) are read once an hour.
    private static final Cache<String, ItemInfo> ITEMS = Caffeine.newBuilder()
            .maximumSize(3000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
    private static final Cache<String, QuestInfo> QUESTS = Caffeine.newBuilder()
            .maximumSize(3000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    /** {@code slug} is null when the material has no wiki page (temporary quest items, for example). */
    private record Material(String name, String slug, String amount) {}

    private record QuestRef(String name, String pageSlug) {
        String key() {
            return pageSlug + "#" + WikiCacheManager.normalize(name);
        }
    }

    private record ItemInfo(String name, String url, List<String> flags, String location, String cost,
                            List<List<Material>> mergeOptions, List<QuestRef> quests,
                            List<String> otherSources, String notes) {}

    private record QuestInfo(String name, String pageName, String text, List<Material> required) {}

    private AqwGuideService() {}

    public static String guide(String query) {

        String trimmed = query == null ? "" : query.strip();
        String normalized = WikiCacheManager.normalize(trimmed);
        if (normalized.isEmpty()) {
            return "found: false\nreason: empty query";
        }

        AqwWikiService.Resolved resolved;
        try {
            resolved = AqwWikiService.resolve(trimmed, normalized);
        } catch (SQLException e) {
            log.error("aqw_item_guide database lookup failed for '{}'", trimmed, e);
            return "found: false\nnote: the bot's wiki database is unavailable right now.";
        }
        if (resolved == null) {
            return "found: false\nquery: " + trimmed + "\nnote: no item page found. Use aqw_wiki to find the exact name.";
        }

        String guide = GUIDES.getIfPresent(resolved.slug());
        if (guide == null) {
            long start = System.currentTimeMillis();
            Builder builder = new Builder();
            builder.expand(resolved.slug());
            ItemInfo root = builder.items.get(resolved.slug());
            if (root == null) {
                return "found: false\nnote: found the page for \"" + trimmed + "\" but couldn't read it right now.";
            }
            guide = builder.render(resolved.slug());
            GUIDES.put(resolved.slug(), guide);
            log.info("Built AQW guide for {} from {} pages in {} ms",
                    resolved.slug(), builder.pagesRead, System.currentTimeMillis() - start);
        }

        StringBuilder result = new StringBuilder("found: true\n");
        if (resolved.note() != null) {
            result.append("note: ").append(resolved.note()).append('\n');
        }
        return result.append(guide).toString();
    }

    /** One guide's state: the pages it has read and the tree it renders. */
    private static final class Builder {

        private final Map<String, ItemInfo> items = new HashMap<>();
        private final Map<String, QuestInfo> quests = new HashMap<>();
        private final Map<String, Integer> depthOf = new HashMap<>();
        // How many places in the tree need each material; more than one makes it "shared"
        private final Map<String, Integer> refCount = new HashMap<>();
        private int pagesRead;
        private boolean budgetHit;
        // Render settings and state for the current attempt
        private int questTextLimit = QUEST_TEXT_LIMIT;
        private boolean unreadShown;

        void expand(String rootSlug) {

            Set<String> frontier = new LinkedHashSet<>(List.of(rootSlug));
            depthOf.put(rootSlug, 0);

            for (int depth = 0; depth <= MAX_DEPTH && !frontier.isEmpty(); depth++) {
                loadItems(frontier);
                if (depth == MAX_DEPTH) break;

                // Quests that reward this level's items, read from their quest-giver pages
                List<QuestRef> questRefs = new ArrayList<>();
                for (String slug : frontier) {
                    ItemInfo item = items.get(slug);
                    if (item != null) {
                        item.quests().stream().limit(MAX_QUESTS_PER_ITEM).forEach(questRefs::add);
                    }
                }
                loadQuests(questRefs);

                Set<String> next = new LinkedHashSet<>();
                int childDepth = depth + 1;
                for (String slug : frontier) {
                    ItemInfo item = items.get(slug);
                    if (item == null) continue;
                    if (!item.mergeOptions().isEmpty()) {
                        item.mergeOptions().getFirst().forEach(material -> addChild(next, material, childDepth));
                    }
                    for (QuestRef ref : item.quests().stream().limit(MAX_QUESTS_PER_ITEM).toList()) {
                        QuestInfo quest = quests.get(ref.key());
                        if (quest != null) {
                            quest.required().forEach(material -> addChild(next, material, childDepth));
                        }
                    }
                }
                frontier = next;
            }
        }

        private void addChild(Set<String> next, Material material, int depth) {
            if (material.slug() == null) return;
            refCount.merge(material.slug(), 1, Integer::sum);
            if (depthOf.containsKey(material.slug())) return;
            depthOf.put(material.slug(), depth);
            next.add(material.slug());
        }

        /** Reads the item pages not yet known, in parallel, within the page budget. */
        private void loadItems(Set<String> slugs) {
            Map<String, CompletableFuture<ItemInfo>> pending = new HashMap<>();
            for (String slug : slugs) {
                ItemInfo cached = ITEMS.getIfPresent(slug);
                if (cached != null) {
                    items.put(slug, cached);
                } else if (pagesRead < MAX_PAGES) {
                    pagesRead++;
                    pending.put(slug, CompletableFuture.supplyAsync(() -> readItem(slug), FETCHER));
                } else {
                    budgetHit = true;
                }
            }
            pending.forEach((slug, future) -> {
                ItemInfo item = future.join();
                if (item != null) {
                    items.put(slug, item);
                    ITEMS.put(slug, item);
                }
            });
        }

        /** Reads each quest-giver page once and pulls out the requested quests. */
        private void loadQuests(List<QuestRef> refs) {
            Map<String, List<QuestRef>> byPage = new HashMap<>();
            for (QuestRef ref : refs) {
                QuestInfo cached = QUESTS.getIfPresent(ref.key());
                if (cached != null) {
                    quests.put(ref.key(), cached);
                } else if (!quests.containsKey(ref.key())) {
                    byPage.computeIfAbsent(ref.pageSlug(), key -> new ArrayList<>()).add(ref);
                }
            }

            Map<String, CompletableFuture<Document>> pending = new HashMap<>();
            for (String pageSlug : byPage.keySet()) {
                if (pagesRead >= MAX_PAGES) {
                    budgetHit = true;
                    break;
                }
                pagesRead++;
                pending.put(pageSlug, CompletableFuture.supplyAsync(() -> AqwWikiService.fetch(pageSlug), FETCHER));
            }

            pending.forEach((pageSlug, future) -> {
                Document document = future.join();
                if (document == null) return;
                for (QuestRef ref : byPage.get(pageSlug)) {
                    QuestInfo quest = readQuest(document, ref);
                    if (quest != null) {
                        quests.put(ref.key(), quest);
                        QUESTS.put(ref.key(), quest);
                    }
                }
            });
        }

        String render(String rootSlug) {
            ItemInfo root = items.get(rootSlug);
            StringBuilder out = new StringBuilder();
            out.append("guide for: ").append(root.name()).append('\n');
            out.append("url: ").append(root.url()).append('\n');
            out.append("format: each indented line is what the line above needs. Materials needed in several places "
                    + "are explained once under \"shared materials\" at the end.\n");
            if (!root.notes().isEmpty()) {
                out.append("notes: ").append(root.notes()).append('\n');
            }

            // Render the whole tree. If it's too long, shorten the quest descriptions first, then
            // show fewer levels, so the deepest steps are what's left out, never the main ones.
            String tree = null;
            int[][] attempts = {{MAX_DEPTH, QUEST_TEXT_LIMIT}, {MAX_DEPTH, SHORT_QUEST_TEXT_LIMIT},
                    {3, SHORT_QUEST_TEXT_LIMIT}, {2, SHORT_QUEST_TEXT_LIMIT}, {1, SHORT_QUEST_TEXT_LIMIT}};
            for (int[] attempt : attempts) {
                unreadShown = false;
                tree = renderTree(rootSlug, attempt[0], attempt[1]);
                if (out.length() + tree.length() <= MAX_RESULT_CHARS) break;
            }
            out.append(tree);

            if (budgetHit && unreadShown) {
                out.append("\n[some deeper materials weren't read (page limit). Use aqw_item_guide on one of them for the rest.]");
            }
            if (out.length() > MAX_RESULT_CHARS) {
                return AqwWikiService.truncate(out.toString(), MAX_RESULT_CHARS)
                        + "\n[guide cut off. Use aqw_item_guide on a sub-material for the rest.]";
            }
            return out.toString();
        }

        /**
         * The main tree, then a "shared materials" section. A material needed in several places
         * (Void Aura for NSOD) is explained once there instead of wherever it first appears, which
         * could be too deep in the tree to be shown at all.
         */
        private String renderTree(String rootSlug, int maxLevel, int questTextLimit) {
            this.questTextLimit = questTextLimit;
            StringBuilder out = new StringBuilder();
            Set<String> shown = new HashSet<>();
            Set<String> shared = new LinkedHashSet<>();

            renderItem(out, rootSlug, null, 0, 0, maxLevel, shown, shared, true);

            Set<String> rendered = new HashSet<>();
            while (true) {
                String next = shared.stream().filter(slug -> !rendered.contains(slug)).findFirst().orElse(null);
                if (next == null) break;
                if (rendered.isEmpty()) out.append("\nshared materials (needed in several places above):\n");
                rendered.add(next);
                renderItem(out, next, null, 0, depthOf.getOrDefault(next, 1), maxLevel, shown, shared, true);
            }
            return out.toString();
        }

        /**
         * @param depth   indentation steps
         * @param level   how many materials down from the guide's item; past {@code maxLevel} a
         *                material is listed without its own steps
         * @param here    explain the item at this spot even if it's shared
         */
        private void renderItem(StringBuilder out, String slug, String amount, int depth, int level,
                                int maxLevel, Set<String> shown, Set<String> shared, boolean here) {
            ItemInfo item = items.get(slug);
            String pad = "  ".repeat(depth);

            out.append(pad).append("- ").append(item.name());
            if (amount != null) out.append(" x").append(amount);
            if (!item.flags().isEmpty()) out.append(" [").append(String.join(", ", item.flags())).append(']');

            if (!here && refCount.getOrDefault(slug, 0) > 1) {
                out.append(" (see shared materials)\n");
                shared.add(slug);
                return;
            }
            if (shown.contains(slug)) {
                out.append(" (see above)\n");
                return;
            }
            if (level > maxLevel) {
                out.append(" (has more steps: use aqw_item_guide on it)\n");
                return;
            }
            shown.add(slug);
            out.append('\n');

            String inner = pad + "  ";
            if (!item.location().isEmpty()) out.append(inner).append("get from: ").append(item.location()).append('\n');
            if (!item.cost().isEmpty()) out.append(inner).append("costs: ").append(item.cost()).append('\n');

            if (!item.mergeOptions().isEmpty()) {
                out.append(inner).append("merge:\n");
                for (Material material : item.mergeOptions().getFirst()) {
                    renderMaterial(out, material, depth + 2, level + 1, maxLevel, shown, shared);
                }
                for (List<Material> option : item.mergeOptions().subList(1, item.mergeOptions().size())) {
                    out.append(inner).append("or merge: ").append(materialList(option)).append('\n');
                }
            }

            if (!item.quests().isEmpty()) {
                out.append(inner).append("quest rewards:\n");
                List<QuestRef> refs = item.quests();
                for (QuestRef ref : refs.stream().limit(MAX_QUESTS_PER_ITEM).toList()) {
                    QuestInfo quest = quests.get(ref.key());
                    out.append(inner).append("- quest \"").append(ref.name()).append('"');
                    if (quest == null) {
                        out.append(" (on ").append(ref.pageSlug()).append(", not read)\n");
                        continue;
                    }
                    out.append(" from ").append(quest.pageName());
                    if (!quest.text().isEmpty()) out.append(": ").append(AqwWikiService.truncate(quest.text(), questTextLimit));
                    out.append('\n');
                    if (!quest.required().isEmpty()) {
                        out.append(inner).append("  needs:\n");
                        for (Material material : quest.required()) {
                            renderMaterial(out, material, depth + 3, level + 1, maxLevel, shown, shared);
                        }
                    }
                }
                if (refs.size() > MAX_QUESTS_PER_ITEM) {
                    out.append(inner).append("- also from: ").append(String.join("; ",
                            refs.subList(MAX_QUESTS_PER_ITEM, refs.size()).stream().map(QuestRef::name).toList())).append('\n');
                }
            }

            for (String other : item.otherSources()) {
                out.append(inner).append("also: ").append(other).append('\n');
            }
        }

        private void renderMaterial(StringBuilder out, Material material, int depth, int level, int maxLevel,
                                    Set<String> shown, Set<String> shared) {
            if (material.slug() != null && items.containsKey(material.slug())) {
                renderItem(out, material.slug(), material.amount(), depth, level, maxLevel, shown, shared, false);
            } else {
                // No wiki page (temporary quest items) or not read within the page budget
                out.append("  ".repeat(depth)).append("- ").append(material.name());
                if (material.amount() != null) out.append(" x").append(material.amount());
                if (material.slug() != null) unreadShown = true;
                out.append(material.slug() == null ? "\n" : " (not expanded)\n");
            }
        }
    }

    private static ItemInfo readItem(String slug) {
        Document document = AqwWikiService.fetch(slug);
        if (document == null) {
            return null;
        }

        String url = WikiLinks.WIKI_BASE_URL + slug;
        Element title = document.getElementById("page-title");
        String name = title == null || title.text().isBlank() ? slug : title.text().strip();

        Wiki.WikiPage page = Wiki.parsePage(document);
        if (page == null) {
            return new ItemInfo(name, url, List.of(), "", "", List.of(), List.of(), List.of(), "");
        }

        List<String> flags = new ArrayList<>();
        if (!page.type().isBlank()) flags.add(page.type());
        if (!page.rarity().isBlank()) flags.add(page.rarity());
        flags.addAll(page.badges());
        if ("Member only".equals(page.access())) flags.add("Member only");

        // Price is either a plain cost ("200 AC"), merge lists, quest lists, or other sources, in bullets
        List<List<Material>> mergeOptions = new ArrayList<>();
        List<QuestRef> quests = new ArrayList<>();
        List<String> otherSources = new ArrayList<>();
        List<String> costs = new ArrayList<>();

        String block = "";
        StringBuilder other = null;
        for (String raw : page.price().split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.equals("**OR**")) continue;

            if (line.startsWith("• ")) {
                if (other != null) otherSources.add(AqwWikiService.truncate(other.toString(), OTHER_SOURCE_LIMIT));
                other = null;
                String header = line.substring(2).toLowerCase(Locale.ROOT);
                if (header.startsWith("merge the following")) {
                    block = "merge";
                    mergeOptions.add(new ArrayList<>());
                } else if (header.contains("following quest")) {
                    block = "quest";
                } else {
                    block = "other";
                    other = new StringBuilder(AqwWikiService.plain(line.substring(2)));
                }
            } else if (line.startsWith("◦ ")) {
                String item = line.substring(2);
                switch (block) {
                    case "merge" -> {
                        Material material = material(item);
                        if (material != null) mergeOptions.getLast().add(material);
                    }
                    case "quest" -> {
                        Matcher link = LINK.matcher(item);
                        if (link.find()) {
                            String pageSlug = WikiLinks.slugOf(link.group(2));
                            if (pageSlug != null) quests.add(new QuestRef(unescape(link.group(1)), pageSlug));
                        }
                    }
                    case "other" -> other.append(other.toString().endsWith(":") ? " " : "; ").append(AqwWikiService.plain(item));
                    default -> { }
                }
            } else if (block.isEmpty() && !line.equalsIgnoreCase("N/A")) {
                // "N/A (Reward from the '[Quest](npc-quests)' quest)" names a single quest inline
                boolean questLinked = false;
                if (line.toLowerCase(Locale.ROOT).contains("quest")) {
                    Matcher link = LINK.matcher(line);
                    while (link.find()) {
                        String pageSlug = WikiLinks.slugOf(link.group(2));
                        if (pageSlug != null && pageSlug.contains("quest")) {
                            quests.add(new QuestRef(unescape(link.group(1)), pageSlug));
                            questLinked = true;
                        }
                    }
                }
                if (!questLinked) costs.add(AqwWikiService.plain(line));
            }
        }
        if (other != null) otherSources.add(AqwWikiService.truncate(other.toString(), OTHER_SOURCE_LIMIT));

        String location = String.join("; ", AqwWikiService.plain(page.location()).replace("• ", "").lines()
                .map(String::strip).filter(line -> !line.isEmpty()).toList());

        return new ItemInfo(
                name,
                url,
                flags,
                AqwWikiService.truncate(location, LOCATION_LIMIT),
                String.join("; ", costs),
                mergeOptions.stream().filter(option -> !option.isEmpty()).map(List::copyOf).toList(),
                List.copyOf(quests),
                List.copyOf(otherSources),
                AqwWikiService.truncate(String.join("; ", AqwWikiService.plain(page.notes()).lines()
                        .map(line -> line.replaceFirst("^[\\s\\p{Z}]*[•◦][\\s\\p{Z}]*", "").strip())
                        .filter(line -> !line.isEmpty()).toList()).replace(":;", ":"), ROOT_NOTES_LIMIT));
    }

    /**
     * Finds one quest's tab on a quest-giver page. Its text up to "Rewards:" (requirements, the
     * story text that says where to farm, and the items required) becomes one line, and the
     * required items with wiki pages become materials to follow.
     */
    private static QuestInfo readQuest(Document document, QuestRef ref) {
        Element content = document.getElementById("page-content");
        if (content == null) {
            return null;
        }

        Element title = document.getElementById("page-title");
        String pageName = title == null || title.text().isBlank() ? ref.pageSlug() : title.text().strip();
        String wanted = WikiCacheManager.normalize(ref.name());

        Element panel = null;
        for (Element navset : content.select(".yui-navset")) {
            Elements tabs = navset.select("> ul.yui-nav > li");
            Element panels = navset.selectFirst("> .yui-content");
            if (panels == null) continue;
            for (int i = 0; i < tabs.size() && i < panels.children().size(); i++) {
                if (WikiCacheManager.normalize(tabs.get(i).text()).equals(wanted)) {
                    panel = panels.child(i);
                    break;
                }
            }
            if (panel != null) break;
        }
        // A page without tabs describes a single quest in its body
        if (panel == null) {
            if (!content.select(".yui-navset").isEmpty()) return null;
            panel = content;
        }

        // Requirements and notes ("daily", "once only"), then the first story paragraph, which says
        // where to farm. The NPC's completion line after it, and the items list (shown as entries), are left out.
        List<String> lines = new ArrayList<>();
        boolean storySeen = false;
        for (String line : AqwWikiService.pageText(panel).split("\n")) {
            if (line.startsWith("Rewards:") || line.startsWith("Items Required")) break;
            if (line.startsWith("## ") || line.startsWith("Thanks to")) continue;
            boolean labelled = line.matches("^[A-Za-z ]{2,30}:.*") || line.startsWith("- ");
            if (!labelled) {
                if (storySeen) continue;
                storySeen = true;
            }
            lines.add(line);
        }
        String text = AqwWikiService.truncate(String.join(" ", lines).replace(": - ", ": ").replace(" - ", "; "), QUEST_TEXT_LIMIT);

        List<Material> required = new ArrayList<>();
        Element label = panel.selectFirst("strong:matchesOwn((?i)^\\s*items required\\s*:?\\s*$), b:matchesOwn((?i)^\\s*items required\\s*:?\\s*$)");
        if (label != null) {
            for (String line : Wiki.valueOf(label).split("\n")) {
                // Nested lines under an item ("◦ Dropped by: ...") describe it; they aren't more requirements
                if (line.matches("^[\\s\\p{Z}]+\\S.*") || line.strip().startsWith("◦")) continue;
                String entry = line.replaceFirst("^[\\s\\p{Z}]*[•◦][\\s\\p{Z}]*", "");
                if (AqwWikiService.plain(entry).endsWith(":")) continue;
                Material material = material(entry);
                if (material != null) required.add(material);
            }
        }

        return new QuestInfo(ref.name(), pageName, text, List.copyOf(required));
    }

    /** "[Void Aura](url) x800" or "Bone Dust x25"; null for lines that aren't an item and amount. */
    private static Material material(String text) {
        Matcher linked = LINKED_AMOUNT.matcher(text);
        if (linked.find()) {
            return new Material(unescape(linked.group(1)), WikiLinks.slugOf(linked.group(2)), linked.group(3));
        }
        String plainText = AqwWikiService.plain(text);
        Matcher plain = PLAIN_AMOUNT.matcher(plainText);
        if (plain.matches()) {
            return new Material(plain.group(1).strip(), null, plain.group(2));
        }
        Matcher link = LINK.matcher(text);
        if (link.find()) {
            return new Material(unescape(link.group(1)), WikiLinks.slugOf(link.group(2)), null);
        }
        return plainText.isBlank() || plainText.equalsIgnoreCase("N/A") ? null : new Material(plainText, null, null);
    }

    private static String materialList(List<Material> materials) {
        return String.join(", ", materials.stream()
                .map(material -> material.amount() == null ? material.name() : material.name() + " x" + material.amount()).toList());
    }

    private static String unescape(String linkText) {
        return linkText.replaceAll("\\\\([\\[\\]])", "$1").replaceAll("^'+|'+$", "").strip();
    }
}
