package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.utils.cache.ShopCacheManager;
import com.github.kyanbrix.utils.cache.WikiCacheManager;
import com.github.kyanbrix.utils.cache.WikiCacheManager.WikiEntry;
import com.github.kyanbrix.utils.cache.WikiSearch;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class AutoComplete extends ListenerAdapter {

    // Discord shows at most 25 autocomplete choices
    private static final int MAX_CHOICES = 25;

    @Override
    public void onCommandAutoCompleteInteraction(@NonNull CommandAutoCompleteInteractionEvent event) {
        String option = event.getFocusedOption().getName();
        String query = WikiCacheManager.normalize(event.getFocusedOption().getValue());

        List<String> names;
        if (event.getName().equals("wiki") && option.equals("search")) {
            names = WikiSearch.suggestItems(query, MAX_CHOICES);
        } else if (event.getName().equals("shop") && option.equals("name")) {
            names = suggestShops(query);
        } else if (event.getName().equals("chests") && option.equals("name")) {
            names = suggestChests(query);
        } else {
            return;
        }

        List<Command.Choice> choices = names.stream()
                // Discord rejects choices over 100 characters, and one bad choice
                // would fail the whole reply, so those names are left out.
                .filter(name -> name.length() <= Command.Choice.MAX_STRING_VALUE_LENGTH)
                .limit(MAX_CHOICES)
                .map(name -> new Command.Choice(name, name))
                .toList();

        event.replyChoices(choices).queue();
    }

    /**
     * Shops use the same ranking as items. There's no typo fallback: the SymSpell
     * dictionary is built from item names, not shop names.
     */
    private List<String> suggestShops(String query) {
        List<WikiEntry> entries = ShopCacheManager.getEntries();
        if (query.isEmpty()) {
            return entries.stream().limit(MAX_CHOICES).map(WikiEntry::name).toList();
        }
        return WikiSearch.rank(entries, query, MAX_CHOICES);
    }

    /** Newest chests first when nothing is typed yet, since that's the list's own order. */
    private List<String> suggestChests(String query) {
        List<WikiEntry> entries = CollectionChests.entries();
        if (query.isEmpty()) {
            return entries.stream().limit(MAX_CHOICES).map(WikiEntry::name).toList();
        }
        return WikiSearch.rank(entries, query, MAX_CHOICES);
    }
}
