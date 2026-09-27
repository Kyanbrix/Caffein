package com.github.kyanbrix.utils.cache;

import com.github.kyanbrix.Caffein;
import io.gitlab.rxp90.jsymspell.SymSpell;
import io.gitlab.rxp90.jsymspell.SymSpellBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WikiCacheManager {

    private static final Logger log = LoggerFactory.getLogger(WikiCacheManager.class);

    /**
     * An item name plus search-ready forms of it, computed once per cache load
     * instead of on every autocomplete keystroke.
     *
     * @param normalized lowercase, apostrophes dropped, other punctuation as
     *                   spaces: "Yulgar's 8-Bit Axe" -> "yulgars 8 bit axe"
     * @param compact    normalized without spaces, so "8bit" finds "8-Bit"
     */
    public record WikiEntry(String name, String normalized, String compact, String acronym) {}

    private static volatile List<String> cache = Collections.emptyList();
    private static volatile List<WikiEntry> entries = Collections.emptyList();
    private static volatile Map<String, String> originalCaseMap = Collections.emptyMap();
    private static volatile Map<String, List<String>> acronymMap = Collections.emptyMap();
    private static volatile SymSpell symSpell;

    // Manual overrides for community nicknames that don't follow the standard
    // first-letter-of-each-word acronym pattern buildAcronym() derives
    // automatically (a real acronym like "nsod" for "Necrotic Sword of Doom"
    // is already covered by that logic and doesn't need an entry here).
    // Key: the shortcut, lowercase, no spaces.
    // Value: the item name(s) it should resolve to, as they appear on the
    // wiki - matched case-insensitively against the real cache below, so
    // exact casing here doesn't matter, but the spelling does.
    // Add real entries as you come across nicknames that need one, e.g.:
    // Map.entry("dl", List.of("Doomlord Armor")),
    // Map.entry("lod", List.of("Legion of Doom Blade", "Legion of Doom Cape"))
    private static final Map<String, List<String>> MANUAL_ALIASES = Map.ofEntries(
            Map.entry("loo",List.of("Lord of Order")),
            Map.entry("nsod",List.of("Necrotic Sword of Doom (Sword)","Necrotic Sword of Doom (IoDA)")),
            Map.entry("blod",List.of("Blinding Light of Destiny (Axe)")),
            Map.entry("vhl",List.of("Void Highlord (Class)","Void Highlord (IODA) (class)","Void Highlord armor","Void highlord tester")),
            Map.entry("tk",List.of("Timekeeper")),
            Map.entry("cav",List.of("Chaos Avenger")),
            Map.entry("dot",List.of("Dragon of Time (Class)")),
            Map.entry("lr",List.of("Legion Revenant (Class)","Legion Revenant (Armor)")),
            Map.entry("lc",List.of("Lightcaster (class)" ,"Lightcaster (armor) (ac)"))
    );

    public static void loadCache() {

        try (Connection connection = Caffein.getInstance().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT item_name FROM wiki ORDER BY item_name ASC")) {

            List<String> updatedWiki = new ArrayList<>();
            while (resultSet.next()) {
                updatedWiki.add(resultSet.getString("item_name"));
            }

            // Build the lowercase -> original-case lookup
            Map<String, String> updatedCaseMap = updatedWiki.stream()
                    .collect(Collectors.toMap(
                            String::toLowerCase,
                            name -> name,
                            (existing, replacement) -> existing,
                            HashMap::new
                    ));

            // Build a WORD-level frequency dictionary for SymSpell, not a
            // whole-item-name dictionary. SymSpell compares edit distance
            // between the input and each dictionary entry as full strings, so
            // a dictionary of whole item names (often 15-30+ characters) can
            // never match a single mistyped word like "theif" within
            // maxDictionaryEditDistance(2) - the strings are just too far
            // apart in length. Tokenizing every item name into individual
            // words, and counting how many items each word appears in, gives
            // SymSpell a real word dictionary to correct single-word typos
            // against (and lets lookupCompound() correct multi-word queries
            // too).
            Map<String, Long> unigrams = new HashMap<>();
            for (String name : updatedWiki) {
                for (String word : name.toLowerCase().split("[^a-z0-9']+")) {
                    if (word.isBlank()) {
                        continue;
                    }
                    unigrams.merge(word, 1L, Long::sum);
                }
            }

            SymSpell updatedSymSpell = new SymSpellBuilder()
                    .setUnigramLexicon(unigrams)
                    .setMaxDictionaryEditDistance(2)
                    .createSymSpell();

            // Build an acronym shortcut map, e.g. "Necrotic Sword of Doom" -> "nsod",
            // "Blinding Light of Destiny" -> "blod". Multiple items can share the
            // same acronym, so each acronym maps to a list of item names rather
            // than a single one. Acronyms shorter than 2 letters are skipped -
            // single-word items don't have a meaningful acronym and a 1-letter
            // "acronym" would match far too many items to be useful.
            Map<String, List<String>> updatedAcronymMap = new HashMap<>();
            for (String name : updatedWiki) {
                String acronym = buildAcronym(name);
                if (acronym.length() >= 2) {
                    updatedAcronymMap.computeIfAbsent(acronym, key -> new ArrayList<>()).add(name);
                }
            }

            // Manual aliases take full priority over auto-derived acronyms for
            // the same shortcut, since they're a deliberate curated mapping
            // rather than a side effect of first-letter matching. Each target
            // name is resolved against the real item list so a typo in the
            // alias list just logs a warning instead of silently breaking.
            for (Map.Entry<String, List<String>> alias : MANUAL_ALIASES.entrySet()) {
                List<String> resolvedNames = new ArrayList<>();
                for (String targetName : alias.getValue()) {
                    String resolved = updatedCaseMap.get(targetName.toLowerCase());
                    if (resolved != null) {
                        resolvedNames.add(resolved);
                    } else {
                        log.warn("Wiki alias '{}' points at unknown item '{}' - check spelling or re-scrape",
                                alias.getKey(), targetName);
                    }
                }
                if (!resolvedNames.isEmpty()) {
                    updatedAcronymMap.put(alias.getKey().toLowerCase(), resolvedNames);
                }
            }

            List<WikiEntry> updatedEntries = new ArrayList<>(updatedWiki.size());
            for (String name : updatedWiki) {
                String normalized = normalize(name);
                updatedEntries.add(new WikiEntry(name, normalized, normalized.replace(" ", ""), buildAcronym(name)));
            }

            // Swap everything together at the end so readers never see a cache/map/engine
            // that are out of sync with each other.
            cache = Collections.unmodifiableList(updatedWiki);
            entries = Collections.unmodifiableList(updatedEntries);
            originalCaseMap = Collections.unmodifiableMap(updatedCaseMap);
            acronymMap = Collections.unmodifiableMap(updatedAcronymMap);
            symSpell = updatedSymSpell;

            log.info("WIKI Cache Loaded {} items, {} words for SymSpell, {} acronym shortcuts",
                    updatedWiki.size(), unigrams.size(), updatedAcronymMap.size());

        } catch (SQLException e) {
            log.error("Failed to load WIKI Cache", e);
        }
    }

    /**
     * Builds the acronym for an item name by taking the first letter/digit of
     * each whitespace-separated word (skipping any leading punctuation), e.g.
     * "Necrotic Sword of Doom" -> "nsod".
     */
    private static String buildAcronym(String name) {
        StringBuilder acronym = new StringBuilder();
        for (String word : name.split("\\s+")) {
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (Character.isLetterOrDigit(c)) {
                    acronym.append(Character.toLowerCase(c));
                    break;
                }
            }
        }
        return acronym.toString();
    }

    /**
     * The form both item names and search input are compared in: lowercase,
     * apostrophes removed ("yulgars" matches "Yulgar's"), and every other
     * non-alphanumeric run turned into a single space.
     */
    public static String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("['\u2019`]", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    public static List<WikiEntry> getEntries() {
        return entries;
    }

    public static List<String> getCache() {
        return cache;
    }

    public static SymSpell getSymSpell() {
        return symSpell;
    }

    public static String getOriginalCase(String lowerName) {
        return originalCaseMap.getOrDefault(lowerName, lowerName);
    }

    /** Returns the item name(s) matching this exact acronym, e.g. "nsod" -> ["Necrotic Sword of Doom"]. */
    public static List<String> getItemsByAcronym(String lowerAcronym) {
        return acronymMap.getOrDefault(lowerAcronym, Collections.emptyList());
    }
}