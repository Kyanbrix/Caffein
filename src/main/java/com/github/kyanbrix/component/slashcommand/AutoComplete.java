package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.utils.cache.WikiCacheManager;
import com.github.kyanbrix.utils.cache.WikiCacheManager.WikiEntry;
import io.gitlab.rxp90.jsymspell.SymSpell;
import io.gitlab.rxp90.jsymspell.api.SuggestItem;
import io.gitlab.rxp90.jsymspell.exceptions.NotInitializedException;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AutoComplete extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AutoComplete.class);

    // Discord shows at most 25 autocomplete choices
    private static final int MAX_CHOICES = 25;

    // Below this many matches the typo-corrected search is appended too, so a
    // near-miss like "necrotik" still surfaces the item it was meant to be.
    private static final int TYPO_FALLBACK_THRESHOLD = 5;

    // Match tiers, best first
    private static final int EXACT = 0;
    private static final int STARTS_WITH = 1;
    private static final int WORD_STARTS = 2;
    private static final int ACRONYM_PREFIX = 3;
    private static final int CONTAINS = 4;
    private static final int COMPACT_CONTAINS = 5;
    private static final int NO_MATCH = -1;

    private record Match(WikiEntry entry, int tier) {}

    private static final Comparator<Match> RANKING = Comparator
            .comparingInt(Match::tier)
            // Among equally good matches the shortest name is the closest to
            // what was typed ("Doom Blade" over "Doom Blade of the Void Lord")
            .thenComparingInt(match -> match.entry().name().length())
            .thenComparing(match -> match.entry().name(), String.CASE_INSENSITIVE_ORDER);

    @Override
    public void onCommandAutoCompleteInteraction(@NonNull CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("wiki") || !event.getFocusedOption().getName().equals("search")) {
            return;
        }

        String query = WikiCacheManager.normalize(event.getFocusedOption().getValue());
        List<Command.Choice> choices = suggest(query).stream()
                // Discord rejects choices over 100 characters, and one bad choice
                // would fail the whole reply, so those names are left out.
                .filter(name -> name.length() <= Command.Choice.MAX_STRING_VALUE_LENGTH)
                .limit(MAX_CHOICES)
                .map(name -> new Command.Choice(name, name))
                .toList();

        event.replyChoices(choices).queue();
    }

    private List<String> suggest(String query) {
        List<WikiEntry> entries = WikiCacheManager.getEntries();
        if (query.isEmpty()) {
            return entries.stream().limit(MAX_CHOICES).map(WikiEntry::name).toList();
        }

        Set<String> results = new LinkedHashSet<>();

        // Acronym shortcuts and curated aliases (e.g. "nsod" -> Necrotic Sword
        // of Doom) always come first. An acronym is never typed with spaces.
        if (!query.contains(" ")) {
            results.addAll(WikiCacheManager.getItemsByAcronym(query));
        }

        results.addAll(search(entries, query));

        if (results.size() < TYPO_FALLBACK_THRESHOLD && query.length() >= 3) {
            String corrected = correctTypo(query);
            if (corrected != null && !corrected.equals(query)) {
                results.addAll(search(entries, corrected));
            }
        }

        return new ArrayList<>(results);
    }

    /** Ranks every cached item against the query and returns the best names. */
    private List<String> search(List<WikiEntry> entries, String query) {
        String[] terms = query.split(" ");
        String compactQuery = query.replace(" ", "");

        List<Match> matches = new ArrayList<>();
        for (WikiEntry entry : entries) {
            int tier = tier(entry, query, terms, compactQuery);
            if (tier != NO_MATCH) {
                matches.add(new Match(entry, tier));
            }
        }

        return matches.stream()
                .sorted(RANKING)
                .limit(MAX_CHOICES)
                .map(match -> match.entry().name())
                .toList();
    }

    private static int tier(WikiEntry entry, String query, String[] terms, String compactQuery) {
        String name = entry.normalized();

        if (name.equals(query)) return EXACT;
        if (name.startsWith(query)) return STARTS_WITH;

        boolean allAtWordStart = true;
        boolean allContained = true;
        for (String term : terms) {
            if (!name.contains(term)) {
                allContained = false;
                allAtWordStart = false;
                break;
            }
            if (!name.startsWith(term) && !name.contains(" " + term)) {
                allAtWordStart = false;
            }
        }

        if (allAtWordStart) return WORD_STARTS;
        // A partly typed acronym, e.g. "nso" on the way to "nsod". Ranked above
        // mid-word matches, which a short single token is rarely meant as.
        if (terms.length == 1 && query.length() >= 3 && entry.acronym().startsWith(query)) return ACRONYM_PREFIX;
        if (allContained) return CONTAINS;
        // "8bit" / "darkcaster" typed without the spaces the name has
        if (compactQuery.length() >= 3 && entry.compact().contains(compactQuery)) return COMPACT_CONTAINS;
        return NO_MATCH;
    }

    /**
     * Corrects spelling across the whole (possibly multi-word) search phrase
     * using SymSpell's compound lookup. The dictionary holds individual words,
     * so the corrected phrase goes back through the normal search. Returns
     * null if SymSpell isn't ready or finds nothing.
     */
    private String correctTypo(String query) {
        SymSpell symSpell = WikiCacheManager.getSymSpell();
        if (symSpell == null) {
            log.warn("SymSpell engine not initialized yet; skipping typo fallback for '{}'", query);
            return null;
        }

        try {
            List<SuggestItem> suggestions = symSpell.lookupCompound(query, 2, true);
            return suggestions.isEmpty() ? null : WikiCacheManager.normalize(suggestions.get(0).getSuggestion());
        } catch (NotInitializedException e) {
            log.warn("SymSpell lookupCompound failed for '{}': {}", query, e.getMessage());
            return null;
        }
    }
}
