package com.github.kyanbrix.api.OpenAI.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.kyanbrix.component.slashcommand.ClassSkills;
import com.github.kyanbrix.component.slashcommand.Shop;
import com.github.kyanbrix.component.slashcommand.Wiki;
import com.github.kyanbrix.utils.WikiLinks;
import com.github.kyanbrix.utils.cache.ShopCacheManager;
import com.github.kyanbrix.utils.cache.WikiCacheManager;
import com.github.kyanbrix.utils.cache.WikiSearch;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Backs the chatbot's {@code aqw_wiki} tool. It reads any page of the AQW wiki (items, classes,
 * shops, monsters, maps, NPCs, quests) and returns a detailed plain-text summary the model can
 * turn into a guide.
 * <p>
 * Pages are found in this order: a wiki URL; an exact item name or nickname (the {@code wiki} table);
 * an exact shop name (the {@code aqwshop} table); the page name turned into a Wikidot address
 * ("Nulgath's Quests" -> /nulgath-s-quests); and last, the closest item name. The wiki's own
 * search is switched off, so there's no full-text search.
 */
public final class AqwWikiService {

    private static final Logger log = LoggerFactory.getLogger(AqwWikiService.class);

    private static final int MAX_SUGGESTIONS = 5;
    private static final int MAX_RESULT_CHARS = 8000;

    // Item fields
    private static final int SHORT_LIMIT = 300;
    private static final int LOCATION_LIMIT = 1500;
    private static final int PRICE_LIMIT = 1500;
    private static final int LONG_TEXT_LIMIT = 1200;

    private static final int SKILL_TEXT_LIMIT = 450;
    private static final int SHOP_ITEMS_LIMIT = 6000;
    // Whole page text kept in the cache; fit() picks the part that is actually sent.
    private static final int MAX_PAGE_CHARS = 60_000;
    private static final int MAX_DISAMBIGUATION_LINKS = 30;

    // Wiki values come back as Discord markdown; links are reduced to their text to save tokens.
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[((?:\\\\.|[^\\]\\\\])*)]\\([^)\\s]*\\)");
    private static final Pattern ESCAPED_BRACKET = Pattern.compile("\\\\([\\[\\]])");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    // Pages rarely change, so a repeated question doesn't scrape the wiki again.
    // Keyed by page slug; failed fetches aren't cached.
    private static final Cache<String, String> SUMMARIES = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    // Guessed addresses that turned out not to exist, so the same guess isn't fetched again.
    private static final Cache<String, Boolean> MISSING = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    // The page fetched while checking a guessed address, so summarize() doesn't download it twice.
    private static final Cache<String, Document> RECENT_PAGES = Caffeine.newBuilder()
            .maximumSize(20)
            .expireAfterWrite(Duration.ofMinutes(2))
            .build();

    record Resolved(String slug, String note) {}

    private AqwWikiService() {}

    /**
     * @param focus a heading or keyword to jump to when the page is too long to return whole
     *              (a quest name, "Items Dropped"...); blank starts at the top
     */
    public static String search(String query, String focus) {

        String trimmed = query == null ? "" : query.strip();
        String normalized = WikiCacheManager.normalize(trimmed);
        if (normalized.isEmpty()) {
            return "found: false\nreason: empty query";
        }

        Resolved resolved;
        try {
            resolved = resolve(trimmed, normalized);
        } catch (SQLException e) {
            log.error("aqw_wiki database lookup failed for '{}'", trimmed, e);
            return "found: false\nnote: the bot's wiki database is unavailable right now.";
        }

        if (resolved == null) {
            return notFound(trimmed, normalized);
        }

        String summary = SUMMARIES.getIfPresent(resolved.slug());
        if (summary == null) {
            Document document = fetch(resolved.slug());
            if (document == null) {
                return "found: false\nnote: found the page for \"" + trimmed + "\" but couldn't read it right now.";
            }
            summary = summarize(document, WikiLinks.WIKI_BASE_URL + resolved.slug());
            SUMMARIES.put(resolved.slug(), summary);
        }

        StringBuilder result = new StringBuilder("found: true\n");
        if (resolved.note() != null) {
            result.append("note: ").append(resolved.note()).append('\n');
        }
        result.append(fit(summary, focus == null ? "" : focus.strip(), MAX_RESULT_CHARS - result.length()));
        return result.toString();
    }

    /**
     * Returns the whole summary if it fits. Otherwise keeps the name/url/type header and returns
     * the part starting at {@code focus} (or the top), plus the page's section headings so the
     * model can ask for another part.
     */
    private static String fit(String summary, String focus, int budget) {

        if (summary.length() <= budget) {
            return summary;
        }

        List<String> lines = summary.lines().toList();
        int bodyStart = 0;
        while (bodyStart < lines.size() && lines.get(bodyStart).matches("(name|url|page type): .*")) {
            bodyStart++;
        }

        List<String> sections = lines.stream()
                .filter(line -> line.startsWith("## "))
                .map(line -> line.substring(3))
                .distinct()
                .toList();

        int start = bodyStart;
        boolean focused = false;
        if (!focus.isEmpty()) {
            String needle = focus.toLowerCase(Locale.ROOT);
            int heading = -1;
            int mention = -1;
            for (int i = bodyStart; i < lines.size(); i++) {
                String lower = lines.get(i).toLowerCase(Locale.ROOT);
                if (!lower.contains(needle)) continue;
                if (lower.startsWith("## ")) { heading = i; break; }
                if (mention < 0) mention = i;
            }
            int found = heading >= 0 ? heading : mention;
            if (found >= 0) {
                start = found;
                focused = true;
            }
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < bodyStart; i++) {
            out.append(lines.get(i)).append('\n');
        }

        String sectionList = sections.isEmpty() ? "" : truncate(String.join("; ", sections), 800);
        String footer = "\n[page cut off here. "
                + (sectionList.isEmpty() ? "" : "Sections: " + sectionList + ". ")
                + "Call aqw_wiki again with focus set to a section name or keyword to read another part.]";
        if (!focus.isEmpty() && !focused) {
            out.append("note: \"").append(focus).append("\" wasn't found on this page; showing the start.\n");
        } else if (focused && start > bodyStart) {
            out.append("[…skipped to \"").append(focus).append("\"]\n");
        }

        String body = String.join("\n", lines.subList(start, lines.size()));
        int room = Math.max(500, budget - out.length() - footer.length());
        out.append(truncate(body, room)).append(footer);
        return out.toString();
    }

    static Resolved resolve(String query, String normalized) throws SQLException {

        // 1. A wiki link
        if (WikiLinks.isWikiPage(query)) {
            String slug = WikiLinks.slugOf(query);
            return slug == null ? null : new Resolved(slug, null);
        }

        // 2. An exact item name or nickname
        List<String> items = WikiSearch.suggestItems(normalized, MAX_SUGGESTIONS);
        if (!items.isEmpty() && isExact(items.getFirst(), normalized)) {
            Resolved item = itemPage(items.getFirst(), items);
            if (item != null) return item;
        }

        // 3. An exact shop name
        List<String> shops = WikiSearch.rank(ShopCacheManager.getEntries(), normalized, MAX_SUGGESTIONS);
        if (!shops.isEmpty() && WikiCacheManager.normalize(shops.getFirst()).equals(normalized)) {
            String slug = WikiLinks.slugOf(Shop.findShopUrl(shops.getFirst()));
            if (slug != null) return new Resolved(slug, null);
        }

        // 4. Any other page, by the address Wikidot gives its name
        String guessed = slugFor(query);
        if (!guessed.isEmpty() && WikiLinks.isValidSlug(guessed) && pageExists(guessed)) {
            return new Resolved(guessed, null);
        }

        // 5. The closest item name
        if (!items.isEmpty()) {
            Resolved item = itemPage(items.getFirst(), items);
            if (item != null) {
                return new Resolved(item.slug(), "closest match for \"" + query + "\" is " + items.getFirst()
                        + "; check it's what was meant." + otherMatches(items));
            }
        }
        return null;
    }

    private static Resolved itemPage(String itemName, List<String> matches) throws SQLException {
        Wiki.WikiRow row = Wiki.findItem(itemName);
        String slug = row == null ? null : WikiLinks.slugOf(row.url());
        if (slug == null) {
            return null;
        }
        String others = otherMatches(matches);
        return new Resolved(slug, others.isEmpty() ? null : others.strip());
    }

    private static boolean isExact(String itemName, String normalized) {
        return WikiCacheManager.normalize(itemName).equals(normalized)
                || WikiCacheManager.getItemsByAcronym(normalized).contains(itemName);
    }

    private static String otherMatches(List<String> matches) {
        return matches.size() > 1 ? " Other matches: " + String.join("; ", matches.subList(1, matches.size())) : "";
    }

    private static String notFound(String query, String normalized) {
        Set<String> suggestions = new LinkedHashSet<>(WikiSearch.suggestItems(normalized, MAX_SUGGESTIONS));
        suggestions.addAll(WikiSearch.rank(ShopCacheManager.getEntries(), normalized, MAX_SUGGESTIONS));

        return "found: false\nquery: " + query + "\n"
                + (suggestions.isEmpty() ? "" : "similar names: " + String.join("; ", suggestions) + "\n")
                + "note: pass the exact page name (e.g. \"Nulgath the Archfiend\", \"Void Highlord Merge Shop\", "
                + "\"Tercessuinotlim\"), or find the aqwwiki.wikidot.com page with web_search and pass its URL.";
    }

    /** "Nulgath's Quests" -> "nulgath-s-quests", the address Wikidot gives a page with that name. */
    private static String slugFor(String name) {
        return NON_SLUG.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("^-+|-+$", "");
    }

    private static boolean pageExists(String slug) {
        if (SUMMARIES.getIfPresent(slug) != null) return true;
        if (MISSING.getIfPresent(slug) != null) return false;
        return fetch(slug) != null;
    }

    /** The page, or null if it doesn't exist or can't be reached. */
    static Document fetch(String slug) {
        Document recent = RECENT_PAGES.getIfPresent(slug);
        if (recent != null) {
            return recent;
        }

        try {
            Document document = Wiki.fetchDocument(WikiLinks.WIKI_BASE_URL + slug);
            RECENT_PAGES.put(slug, document);
            return document;
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                MISSING.put(slug, true);
            } else {
                log.warn("aqw_wiki got HTTP {} for {}", e.getStatusCode(), slug);
            }
            return null;
        } catch (IOException e) {
            log.warn("aqw_wiki couldn't fetch {}: {}", slug, e.getMessage());
            return null;
        }
    }

    private static String summarize(Document document, String url) {

        List<String> tags = Wiki.pageTags(document);
        Element title = document.getElementById("page-title");
        String name = title == null || title.text().isBlank() ? url : title.text().strip();

        StringBuilder out = new StringBuilder();
        line(out, "name", name, SHORT_LIMIT);
        line(out, "url", url, SHORT_LIMIT);

        if (tags.contains("shop")) {
            line(out, "page type", "shop", SHORT_LIMIT);
            String items = Shop.describeItems(document, url, SHOP_ITEMS_LIMIT);
            out.append(items != null ? items : pageText(document));
        } else if (Wiki.isItemPage(tags) && !tags.contains("quest")) {
            line(out, "page type", tags.contains("class") ? "class" : "item", SHORT_LIMIT);
            appendItem(out, document);
        } else {
            line(out, "page type", pageType(tags), SHORT_LIMIT);
            out.append(pageText(document));
            if (tags.contains("disambiguation")) {
                appendLinks(out, document);
            }
        }
        return out.toString().strip();
    }

    private static String pageType(List<String> tags) {
        for (String type : List.of("quest", "monster", "location", "npc", "disambiguation")) {
            if (tags.contains(type)) return type;
        }
        return "page";
    }

    private static void appendItem(StringBuilder out, Document document) {

        Wiki.WikiPage page = Wiki.parsePage(document);
        if (page == null) {
            out.append(pageText(document));
            return;
        }

        line(out, "type", page.type(), SHORT_LIMIT);
        line(out, "rarity", page.rarity(), SHORT_LIMIT);
        line(out, "access", page.access(), SHORT_LIMIT);
        line(out, "tags", String.join(", ", page.badges()), SHORT_LIMIT);
        line(out, "obtained from", String.join(", ", page.sources()), SHORT_LIMIT);
        line(out, page.locationLabel().toLowerCase(Locale.ROOT), page.location(), LOCATION_LIMIT);
        line(out, "price", page.price(), PRICE_LIMIT);
        line(out, "sellback", page.sellback(), SHORT_LIMIT);
        line(out, "level", page.level(), SHORT_LIMIT);
        line(out, "damage", page.damage(), SHORT_LIMIT);
        line(out, "rank needed", page.rank(), SHORT_LIMIT);
        line(out, "stat model", page.statModel(), SHORT_LIMIT);
        line(out, "special effects", page.specialEffects(), LONG_TEXT_LIMIT);
        line(out, "description", page.description(), LONG_TEXT_LIMIT);
        line(out, "notes", page.notes(), LONG_TEXT_LIMIT);

        String skills = ClassSkills.describeSkills(document, SKILL_TEXT_LIMIT);
        if (skills != null) {
            out.append("skills:\n").append(skills).append('\n');
        }
    }

    /** Disambiguation pages only list other pages, so their addresses are given for a follow-up lookup. */
    private static void appendLinks(StringBuilder out, Document document) {
        Element content = document.getElementById("page-content");
        if (content == null) return;

        out.append("\npages:\n");
        int count = 0;
        for (Element link : content.select("a[href]")) {
            String url = link.absUrl("href");
            if (!WikiLinks.isWikiPage(url) || link.text().isBlank()) continue;
            out.append("- ").append(link.text().strip()).append(": ").append(url).append('\n');
            if (++count >= MAX_DISAMBIGUATION_LINKS) break;
        }
    }

    /**
     * The page body as indented plain text: headings, "- " list items (nested ones as "◦"),
     * table rows joined with " | ", and each tab's name above its content. Collapsed and
     * expanded titles of the same block, images, ads and scripts are dropped.
     */
    static String pageText(Document document) {
        return pageText(document.getElementById("page-content"));
    }

    /** Same as {@link #pageText(Document)} for one part of a page, such as a single quest's tab. */
    static String pageText(Element source) {
        if (source == null) {
            return "";
        }

        Element content = source.clone();
        content.select("script, style, img, iframe, .wd-adunit, .hover-tooltip, .hover-tooltip-box, "
                + ".collapsible-block-folded, .collapsible-block-unfolded-link").remove();

        // Tabs: write each tab's name above its panel, then drop the tab bar
        for (Element navset : content.select(".yui-navset")) {
            Elements tabs = navset.select("> ul.yui-nav > li");
            Element panels = navset.selectFirst("> .yui-content");
            if (panels != null) {
                for (int i = 0; i < panels.children().size() && i < tabs.size(); i++) {
                    panels.child(i).prependElement("h3").text(tabs.get(i).text());
                }
            }
            navset.select("> ul.yui-nav").remove();
        }

        // Collapse the HTML's own whitespace first, so only the breaks added below remain
        for (Element el : content.getAllElements()) {
            for (TextNode text : el.textNodes()) {
                text.text(text.getWholeText().replaceAll("\\s+", " "));
            }
        }

        content.select("br").forEach(br -> br.replaceWith(new TextNode("\n")));
        content.select("h1, h2, h3, h4, h5, h6").forEach(heading -> {
            heading.prependChild(new TextNode("\n## "));
            heading.appendChild(new TextNode("\n"));
        });
        content.select("li").forEach(item -> {
            long depth = item.parents().stream().filter(p -> p.tagName().equals("ul") || p.tagName().equals("ol")).count();
            item.prependChild(new TextNode(depth <= 1 ? "\n- " : "\n◦ "));
        });
        content.select("td, th").forEach(cell -> {
            if (cell.elementSiblingIndex() > 0) cell.prependChild(new TextNode(" | "));
        });
        content.select("p, div, table, tr, ul, ol").forEach(block -> {
            block.prependChild(new TextNode("\n"));
            block.appendChild(new TextNode("\n"));
        });

        StringBuilder out = new StringBuilder();
        String previous = null;
        for (String raw : content.wholeText().split("\n")) {
            String line = raw.strip().replaceAll(" {2,}", " ");
            if (line.isEmpty() || line.equals("|") || line.equals(previous) || isPlaceholder(line)) continue;
            out.append(line).append('\n');
            previous = line;
        }
        return truncate(out.toString().strip(), MAX_PAGE_CHARS) + "\n";
    }

    /** Template rows the wiki never filled in, like "Message | {$message}" or "Animations | ???". */
    private static boolean isPlaceholder(String line) {
        return line.contains("{$") || line.endsWith("| ???");
    }

    /** Appends "label: value", skipping empty values. Multi-line values start on their own line. */
    private static void line(StringBuilder out, String label, String value, int limit) {
        if (value == null || value.isBlank()) {
            return;
        }

        String text = truncate(plain(value), limit);
        out.append(label).append(':').append(text.contains("\n") ? "\n" : " ").append(text).append('\n');
    }

    static String plain(String markdown) {
        String text = MARKDOWN_LINK.matcher(markdown).replaceAll("$1");
        return ESCAPED_BRACKET.matcher(text).replaceAll("$1").strip();
    }

    static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit - 1).stripTrailing() + "…";
    }
}
