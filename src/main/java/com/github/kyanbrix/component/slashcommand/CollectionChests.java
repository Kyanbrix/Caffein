package com.github.kyanbrix.component.slashcommand;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.kyanbrix.utils.LocationEntry;
import com.github.kyanbrix.utils.WikiLinks;
import com.github.kyanbrix.utils.cache.WikiCacheManager;
import com.github.kyanbrix.utils.cache.WikiCacheManager.WikiEntry;
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
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * /chests: every AQW collection chest, newest first, five per page with the
 * chest's image, price, rarity, what it unlocks and where to buy it. A menu
 * under each page opens a chest's shop in the /shop browser.
 */
public class CollectionChests implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(CollectionChests.class);

    /** First part of the pagination buttons' custom IDs: {@code chests:<action>:<page>}. */
    public static final String BUTTON_PREFIX = "chests";
    /** Custom ID of the "see what's inside" menu; its option values are shop slugs. */
    public static final String SELECT_ID = "chests:inside";

    private static final String LIST_URL = WikiLinks.WIKI_BASE_URL + "list-of-all-collection-chests";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int FETCH_TIMEOUT_MS = 15_000;
    private static final Duration LIST_MAX_AGE = Duration.ofHours(6);

    private static final int CHESTS_PER_PAGE = 5;
    private static final Color ACCENT_COLOR = new Color(0xF1C40F);

    // All text in a components message shares a 4000 character budget:
    // 200 + 5 * 740 = 3900.
    private static final int HEADER_LIMIT = 200;
    private static final int CHEST_TEXT_LIMIT = 740;
    private static final int DESCRIPTION_LIMIT = 240;
    private static final int LOCATION_LIMIT = 180;

    /** Wikidot page tags that name what kind of item the chest is. */
    private static final Map<String, String> CHEST_TYPES = Map.of(
            "pet", "Pet", "back", "Cape", "misc", "Misc", "grounditem", "Ground Item",
            "helm", "Helm", "house", "House", "floor", "Floor Item", "wall", "Wall Item");

    private record Chest(String name, String url) {}

    /**
     * Any field the wiki leaves out is an empty string (or null for the image
     * and the unlocked shop).
     */
    private record ChestDetails(String imageUrl, List<String> badges, String type, String price, String rarity,
                                String description, String locations, String unlocksName, String unlocksUrl) {

        static final ChestDetails MISSING = new ChestDetails(null, List.of(), "", "", "", "", "", null, null);
    }

    // The list itself: newest first, refreshed every few hours
    private static volatile List<Chest> chests = List.of();
    private static volatile List<WikiEntry> entries = List.of();
    private static volatile Instant loadedAt = Instant.EPOCH;
    private static final AtomicBoolean refreshing = new AtomicBoolean();

    // Chest page URL -> its details
    private static final Cache<String, ChestDetails> DETAILS = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(12))
            .maximumSize(500)
            .build();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "chests-lookup");
        thread.setDaemon(true);
        return thread;
    });

    // A page's chest pages are fetched in parallel on their own pool, so a
    // lookup never waits on a slot it holds itself.
    private static final ExecutorService DETAILS_EXECUTOR = Executors.newFixedThreadPool(CHESTS_PER_PAGE, runnable -> {
        Thread thread = new Thread(runnable, "chests-details");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {
        String name = event.getOption("name", OptionMapping::getAsString);

        event.deferReply().queue();
        EXECUTOR.submit(() -> {
            InteractionHook hook = event.getHook();
            try {
                List<Chest> all = loadChests();
                int page = 1;
                if (name != null && !name.isBlank()) {
                    int index = indexOf(all, name.trim());
                    if (index < 0) {
                        hook.sendMessage("No such collection chest found! Kindly select one of the suggested options.").queue();
                        return;
                    }
                    page = index / CHESTS_PER_PAGE + 1;
                }
                hook.sendMessageComponents(render(all, page)).useComponentsV2().queue();
            } catch (IOException e) {
                log.warn("Failed to fetch the collection chest list: {}", e.getMessage());
                hook.sendMessage("Couldn't reach the AQW wiki right now. Try again in a bit.")
                        .addComponents(ActionRow.of(Button.link(LIST_URL, "View Wiki")))
                        .queue();
            } catch (RuntimeException e) {
                log.error("Failed to show collection chests", e);
                hook.sendMessage("Something went wrong while loading the collection chests.").queue();
            }
        });
    }

    /** Pagination, routed here from the ButtonManager. */
    public static void handleButton(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        int page;
        try {
            page = parts.length == 3 ? Integer.parseInt(parts[2]) : -1;
        } catch (NumberFormatException e) {
            page = -1;
        }
        if (page < 0) {
            event.reply("This button is no longer valid.").setEphemeral(true).queue();
            return;
        }

        int targetPage = page;
        event.deferEdit().queue();
        EXECUTOR.submit(() -> {
            InteractionHook hook = event.getHook();
            try {
                hook.editOriginalComponents(render(loadChests(), targetPage)).useComponentsV2().queue();
            } catch (IOException e) {
                log.warn("Failed to fetch the collection chest list: {}", e.getMessage());
                hook.sendMessage("Couldn't reach the AQW wiki right now. Try again in a bit.").setEphemeral(true).queue();
            } catch (RuntimeException e) {
                log.error("Failed to paginate collection chests", e);
                hook.sendMessage("Something went wrong while loading that page.").setEphemeral(true).queue();
            }
        });
    }

    /** "See what's inside": posts the chosen chest's shop in the /shop browser. */
    public static void handleSelect(StringSelectInteractionEvent event) {
        String slug = event.getValues().isEmpty() ? "" : event.getValues().getFirst();
        if (!WikiLinks.isValidSlug(slug)) {
            event.reply("That option is no longer valid.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        EXECUTOR.submit(() -> {
            try {
                Shop.showShop(event.getHook(), slug);
            } catch (RuntimeException e) {
                log.error("Failed to show the shop {} for a collection chest", slug, e);
                event.getHook().sendMessage("Something went wrong while loading that shop.").queue();
            }
        });
    }

    /** Chest names for autocomplete. Empty until the list has loaded once; asking starts that load. */
    static List<WikiEntry> entries() {
        if (entries.isEmpty()) {
            preload();
        }
        return entries;
    }

    /** Loads the list in the background, so the first /chests and its autocomplete don't wait on the wiki. */
    public static void preload() {
        EXECUTOR.submit(() -> {
            try {
                loadChests();
            } catch (IOException e) {
                log.warn("Couldn't preload the collection chest list: {}", e.getMessage());
            }
        });
    }

    private static List<Chest> loadChests() throws IOException {
        List<Chest> current = chests;
        if (!current.isEmpty() && loadedAt.plus(LIST_MAX_AGE).isAfter(Instant.now())) {
            return current;
        }

        // Only one refresh at a time; everyone else keeps using the list they have
        if (!current.isEmpty() && !refreshing.compareAndSet(false, true)) {
            return current;
        }

        try {
            Document document = Jsoup.connect(LIST_URL)
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MS)
                    .get();

            List<Chest> parsed = parseList(document);
            if (parsed.isEmpty()) {
                log.warn("The collection chest list page had no chests; the page layout may have changed");
                if (!current.isEmpty()) {
                    return current;
                }
                throw new IOException("No chests found on " + LIST_URL);
            }

            chests = parsed;
            entries = parsed.stream().map(chest -> {
                String normalized = WikiCacheManager.normalize(chest.name());
                return new WikiEntry(chest.name(), normalized, normalized.replace(" ", ""), "");
            }).toList();
            loadedAt = Instant.now();
            log.info("Collection chest list loaded: {} chests", parsed.size());
            return parsed;
        } catch (IOException e) {
            // A stale list beats no list
            if (!current.isEmpty()) {
                log.warn("Couldn't refresh the collection chest list, using the old one: {}", e.getMessage());
                return current;
            }
            throw e;
        } finally {
            refreshing.set(false);
        }
    }

    /** The wiki lists chests oldest first, one link per line; they're shown newest first. */
    private static List<Chest> parseList(Document document) {
        Element pageContent = document.getElementById("page-content");
        if (pageContent == null) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        List<Chest> parsed = new ArrayList<>();
        for (Element link : pageContent.select(".list-pages-box a[href]")) {
            String name = link.text().trim();
            String url = link.absUrl("href");
            if (!name.isEmpty() && WikiLinks.slugOf(url) != null && seen.add(url)) {
                parsed.add(new Chest(name, url));
            }
        }

        Collections.reverse(parsed);
        return List.copyOf(parsed);
    }

    private static int indexOf(List<Chest> all, String name) {
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static Container render(List<Chest> all, int requestedPage) {
        int totalPages = Math.max(1, Math.ceilDiv(all.size(), CHESTS_PER_PAGE));
        // The list may have changed since the buttons were made
        int page = Math.clamp(requestedPage, 1, totalPages);

        int start = (page - 1) * CHESTS_PER_PAGE;
        List<Chest> pageChests = all.subList(start, Math.min(start + CHESTS_PER_PAGE, all.size()));
        List<ChestDetails> details = fetchDetails(pageChests);

        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(TextDisplay.of(Wiki.truncate(
                "## 🎁 AQW Collection Chests\n-# " + all.size() + " chests · newest first", HEADER_LIMIT)));
        components.add(Separator.createDivider(Separator.Spacing.SMALL));

        for (int i = 0; i < pageChests.size(); i++) {
            if (i > 0) {
                components.add(Separator.createDivider(Separator.Spacing.SMALL));
            }

            ChestDetails chestDetails = details.get(i);
            TextDisplay text = TextDisplay.of(chestText(start + i + 1, pageChests.get(i), chestDetails));
            components.add(chestDetails.imageUrl() == null
                    ? text
                    : Section.of(Thumbnail.fromUrl(chestDetails.imageUrl()), text));
        }

        components.add(Separator.createDivider(Separator.Spacing.LARGE));

        StringSelectMenu insideMenu = insideMenu(pageChests, details);
        if (insideMenu != null) {
            components.add(ActionRow.of(insideMenu));
        }
        components.add(navigation(page, totalPages));

        return Container.of(components).withAccentColor(ACCENT_COLOR);
    }

    private static String chestText(int number, Chest chest, ChestDetails details) {
        StringBuilder text = new StringBuilder("### ")
                .append(number).append(". ")
                .append(new LocationEntry(chest.name(), chest.url()).toMarkdown());

        if (!details.badges().isEmpty()) {
            text.append('\n').append(details.badges().stream()
                    .map(badge -> "`" + badge + "`")
                    .collect(Collectors.joining(" ")));
        }

        List<String> stats = new ArrayList<>();
        if (!details.price().isEmpty()) stats.add("💰 **" + MarkdownSanitizer.escape(details.price()) + "**");
        if (!details.rarity().isEmpty()) stats.add("💎 " + MarkdownSanitizer.escape(details.rarity()));
        if (!details.type().isEmpty()) stats.add("🏷️ " + details.type());
        if (!stats.isEmpty()) {
            text.append('\n').append(String.join(" · ", stats));
        }

        if (!details.description().isEmpty()) {
            text.append("\n> ").append(Wiki.truncate(MarkdownSanitizer.escape(details.description()), DESCRIPTION_LIMIT));
        }

        if (details.unlocksName() != null) {
            text.append("\n📦 Unlocks ").append(new LocationEntry(details.unlocksName(), details.unlocksUrl()).toMarkdown());
        }

        if (!details.locations().isEmpty()) {
            text.append("\n-# 📍 ").append(Wiki.truncate(MarkdownSanitizer.escape(details.locations()), LOCATION_LIMIT));
        }

        if (details == ChestDetails.MISSING) {
            text.append("\n-# Couldn't load this chest's details right now.");
        }

        return Wiki.truncate(text.toString(), CHEST_TEXT_LIMIT);
    }

    /** One option per chest on the page that unlocks a shop; null when none do. */
    private static StringSelectMenu insideMenu(List<Chest> pageChests, List<ChestDetails> details) {
        Map<String, SelectOption> options = new LinkedHashMap<>();
        for (int i = 0; i < pageChests.size(); i++) {
            ChestDetails chestDetails = details.get(i);
            String slug = chestDetails.unlocksUrl() == null ? null : WikiLinks.slugOf(chestDetails.unlocksUrl());
            // Menu values must be unique, and two chests can open the same shop
            if (slug == null || options.containsKey(slug)) {
                continue;
            }

            options.put(slug, SelectOption.of(
                            Wiki.truncate(pageChests.get(i).name(), SelectOption.LABEL_MAX_LENGTH), slug)
                    .withDescription(Wiki.truncate("Opens " + chestDetails.unlocksName(), SelectOption.DESCRIPTION_MAX_LENGTH))
                    .withEmoji(Emoji.fromUnicode("U+1F4E6")));
        }

        if (options.isEmpty()) {
            return null;
        }
        return StringSelectMenu.create(SELECT_ID)
                .setPlaceholder("See what's inside a chest…")
                .addOptions(options.values())
                .build();
    }

    /** Each action gets its own ID since Discord requires IDs to be unique within a message. */
    private static ActionRow navigation(int page, int totalPages) {
        return ActionRow.of(
                Button.secondary(buttonId("first", 1), Emoji.fromUnicode("U+23EA")).withDisabled(page == 1),
                Button.secondary(buttonId("prev", page - 1), "Previous").withDisabled(page == 1),
                Button.secondary(buttonId("page", page), page + "/" + totalPages).withDisabled(true),
                Button.secondary(buttonId("next", page + 1), "Next").withDisabled(page == totalPages),
                Button.secondary(buttonId("last", totalPages), Emoji.fromUnicode("U+23E9")).withDisabled(page == totalPages));
    }

    private static String buttonId(String action, int page) {
        return BUTTON_PREFIX + ":" + action + ":" + page;
    }

    private static List<ChestDetails> fetchDetails(List<Chest> pageChests) {
        List<CompletableFuture<ChestDetails>> futures = pageChests.stream()
                .map(chest -> CompletableFuture.supplyAsync(() -> details(chest), DETAILS_EXECUTOR)
                        .exceptionally(e -> {
                            log.warn("Failed to read the chest page for '{}': {}", chest.name(), e.getMessage());
                            return ChestDetails.MISSING;
                        }))
                .toList();

        return futures.stream().map(CompletableFuture::join).toList();
    }

    private static ChestDetails details(Chest chest) {
        ChestDetails cached = DETAILS.getIfPresent(chest.url());
        if (cached != null) {
            return cached;
        }

        Document document;
        try {
            document = Jsoup.connect(chest.url())
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MS)
                    .get();
        } catch (IOException e) {
            // Not cached, so the next page view tries again
            log.debug("Failed to fetch chest page {}: {}", chest.url(), e.getMessage());
            return ChestDetails.MISSING;
        }

        ChestDetails details = parseDetails(document);
        DETAILS.put(chest.url(), details);
        return details;
    }

    /**
     * Chest pages are ordinary item pages:
     * <pre>
     *   &lt;img aclarge.png&gt;&lt;img rarelarge.png&gt;
     *   &lt;p&gt;&lt;strong&gt;Locations:&lt;/strong&gt;&lt;/p&gt;
     *   &lt;ul&gt;&lt;li&gt;&lt;a&gt;Featured Collection Chest&lt;/a&gt; - &lt;a&gt;Battleon&lt;/a&gt;&lt;/li&gt; ...&lt;/ul&gt;
     *   &lt;p&gt;&lt;strong&gt;Price:&lt;/strong&gt; 10,000 AC&lt;br/&gt;
     *      &lt;strong&gt;Rarity:&lt;/strong&gt; Rare Rarity&lt;br/&gt;
     *      &lt;strong&gt;Description:&lt;/strong&gt; ...&lt;br/&gt;
     *      &lt;strong&gt;Notes:&lt;/strong&gt;&lt;/p&gt;
     *   &lt;ul&gt;&lt;li&gt;Opens the &lt;a&gt;Some Collection&lt;/a&gt; shop when clicked on.&lt;/li&gt; ...&lt;/ul&gt;
     * </pre>
     */
    private static ChestDetails parseDetails(Document document) {
        Element pageContent = document.getElementById("page-content");
        if (pageContent == null) {
            return ChestDetails.MISSING;
        }

        Map<String, Element> labels = new HashMap<>();
        for (Element label : pageContent.select("strong, b")) {
            if (Wiki.isLabel(label) && label.closest(".collapsible-block") == null) {
                labels.putIfAbsent(label.text().replace(":", "").trim().toLowerCase(Locale.ROOT), label);
            }
        }

        Element locationLabel = labels.containsKey("locations") ? labels.get("locations") : labels.get("location");
        Element unlocks = findUnlockedShop(pageContent);

        List<String> tags = document.select(".page-tags a").eachText();
        String type = tags.stream().map(CHEST_TYPES::get).filter(Objects::nonNull).findFirst().orElse("");

        return new ChestDetails(
                Wiki.findItemImages(pageContent).stream()
                        .map(WikiLinks::validMediaUrl)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null),
                Wiki.parseBadges(pageContent),
                type,
                valueOf(labels.get("price")),
                valueOf(labels.get("rarity")).replaceFirst("(?i)\\s*rarity$", ""),
                valueOf(labels.get("description")),
                locationLabel == null ? "" : locations(locationLabel),
                unlocks == null ? null : unlocks.text().trim(),
                unlocks == null ? null : unlocks.absUrl("href"));
    }

    private static String valueOf(Element label) {
        return label == null ? "" : Wiki.plainValue(label);
    }

    /**
     * The shop link in the "Opens the &lt;a&gt;X&lt;/a&gt; shop when clicked on"
     * note. Reads the first link after the word "opens", since the same
     * paragraph can hold other links before it.
     */
    private static Element findUnlockedShop(Element pageContent) {
        for (Element el : pageContent.select(":containsOwn(opens)")) {
            if (el.closest(".collapsible-block") != null) {
                continue;
            }

            boolean afterOpens = false;
            for (Node node : el.childNodes()) {
                if (node instanceof TextNode tn && tn.text().toLowerCase(Locale.ROOT).contains("opens")) {
                    afterOpens = true;
                } else if (afterOpens && node instanceof Element child) {
                    if (child.tagName().equals("br")) {
                        break;
                    }
                    Element link = child.tagName().equals("a") ? child : child.selectFirst("a[href]");
                    if (link != null && !link.attr("href").isBlank()) {
                        return link;
                    }
                }
            }
        }
        return null;
    }

    /**
     * "Featured Collection Chest — Battleon, Game Menu", one entry per shop,
     * whether the wiki writes the location inline or as a list (sometimes with
     * the maps nested under the shop).
     */
    private static String locations(Element label) {
        Map<String, Set<String>> byShop = new LinkedHashMap<>();

        List<Element> inlineLinks = new ArrayList<>();
        for (Node node = label.nextSibling(); node != null; node = node.nextSibling()) {
            if (node instanceof Element el) {
                if (el.tagName().equals("br") || Wiki.isLabel(el)) {
                    break;
                }
                inlineLinks.addAll(el.tagName().equals("a") ? List.of(el) : el.select("a[href]"));
            }
        }
        addLocation(byShop, inlineLinks.stream().map(Element::text).toList());

        Element parent = label.parent();
        Element list = parent == null ? null : parent.nextElementSibling();
        if (inlineLinks.isEmpty() && list != null && (list.tagName().equals("ul") || list.tagName().equals("ol"))) {
            for (Element item : list.select("> li")) {
                List<String> names = new ArrayList<>(item.select("> a").eachText());
                for (Element nested : item.select("> ul > li, > ol > li")) {
                    Element nestedLink = nested.selectFirst("a");
                    names.add(nestedLink == null ? nested.ownText() : nestedLink.text());
                }
                if (names.isEmpty()) {
                    names.add(item.ownText());
                }
                addLocation(byShop, names);
            }
        }

        // Shops in the same maps share one entry, so six founder shops in the
        // Book of Lore read as one line instead of repeating the map six times
        Map<String, List<String>> byMaps = new LinkedHashMap<>();
        byShop.forEach((shop, maps) ->
                byMaps.computeIfAbsent(String.join(", ", maps), key -> new ArrayList<>()).add(shop));

        return byMaps.entrySet().stream()
                .map(entry -> {
                    String shops = shortList(entry.getValue());
                    return entry.getKey().isEmpty() ? shops : shops + " — " + entry.getKey();
                })
                .collect(Collectors.joining(" · "));
    }

    /** "A, B" or "A, B +4 more", so a long shop list can't push the map names out of the line. */
    private static String shortList(List<String> names) {
        if (names.size() <= 2) {
            return String.join(", ", names);
        }
        return names.getFirst() + ", " + names.get(1) + " +" + (names.size() - 2) + " more";
    }

    /** The first name is the shop or NPC, the rest are the maps it's in. */
    private static void addLocation(Map<String, Set<String>> byShop, List<String> names) {
        List<String> cleaned = names.stream().map(String::trim).filter(name -> !name.isEmpty()).toList();
        if (cleaned.isEmpty()) {
            return;
        }
        byShop.computeIfAbsent(cleaned.getFirst(), key -> new LinkedHashSet<>())
                .addAll(cleaned.subList(1, cleaned.size()));
    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("chests", "Browse every AQW collection chest")
                .addOption(OptionType.STRING, "name", "Jump to a specific chest", false, true)
                .setContexts(InteractionContextType.GUILD, InteractionContextType.PRIVATE_CHANNEL)
                .setIntegrationTypes(IntegrationType.USER_INSTALL, IntegrationType.GUILD_INSTALL);
    }
}
