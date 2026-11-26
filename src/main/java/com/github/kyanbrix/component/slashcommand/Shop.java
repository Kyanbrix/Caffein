package com.github.kyanbrix.component.slashcommand;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.utils.LocationEntry;
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
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * /shop: browses a shop's items from the AQW wiki, five per page, each with the
 * item's own preview image as a thumbnail.
 */
public class Shop implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(Shop.class);

    /** First part of the pagination buttons' custom IDs: {@code shop:<action>:<page>:<filter>:<slug>}. */
    public static final String BUTTON_PREFIX = "shop";
    /** Custom ID prefix of the type filter menu: {@code shopfilter:<slug>}. */
    public static final String FILTER_PREFIX = "shopfilter:";
    private static final String NO_FILTER = "all";

    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int FETCH_TIMEOUT_MS = 15_000;

    private static final int ITEMS_PER_PAGE = 5;
    private static final Color ACCENT_COLOR = new Color(0xFF8C00);

    // All text in a components message shares a 4000 character budget:
    // 400 + 5 * 680 = 3800, leaving room to spare.
    private static final int HEADER_LIMIT = 400;
    private static final int ITEM_TEXT_LIMIT = 680;
    private static final int PRICE_LIMIT = 350;

    // Parsed shops, so paging through one doesn't re-download its page on every click
    private static final Cache<String, ShopPage> SHOPS = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(200)
            .build();

    // Item page URL -> its preview image, or "" when the page has none
    private static final Cache<String, String> ITEM_IMAGES = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(12))
            .maximumSize(5_000)
            .build();

    // Shop lookups run off JDA's event thread; a page's item images are fetched in
    // parallel on their own pool so a lookup never waits on a slot it holds itself.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "shop-lookup");
        thread.setDaemon(true);
        return thread;
    });

    private static final ExecutorService IMAGE_EXECUTOR = Executors.newFixedThreadPool(ITEMS_PER_PAGE, runnable -> {
        Thread thread = new Thread(runnable, "shop-images");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Item types for the filter menu, in the order the menu lists them (with
     * "All items" that's 25 options, Discord's maximum). The code goes in button
     * IDs, so it's kept to three letters to leave room for the slug. The icon
     * names are the shop tables' type icons: "Dagger_Table.png" -> "dagger".
     */
    private enum ItemType {
        CLASS("cls", "Class", "class"),
        ARMOR("arm", "Armor", "armor"),
        HELM("hel", "Helm", "helmet", "helm"),
        CAPE("cap", "Cape", "cloak", "cape", "back"),
        PET("pet", "Pet", "pet"),
        SWORD("swd", "Sword", "sword"),
        DAGGER("dag", "Dagger", "dagger"),
        AXE("axe", "Axe", "axe"),
        MACE("mac", "Mace", "mace"),
        POLEARM("pol", "Polearm", "polearm"),
        STAFF("stf", "Staff", "staff"),
        WAND("wnd", "Wand", "wand"),
        GAUNTLET("gnt", "Gauntlet", "gauntlet"),
        GUN("gun", "Gun", "gun"),
        BOW("bow", "Bow", "bow"),
        WHIP("whp", "Whip", "whip"),
        WEAPON("wpn", "Weapon", "weapons", "weapon"),
        NECKLACE("nek", "Necklace", "necklace"),
        MISC("msc", "Misc", "misc"),
        GROUND("gnd", "Ground Item", "ground"),
        FLOOR("flr", "Floor Item", "floor"),
        WALL("wal", "Wall Item", "wall"),
        HOUSE("hou", "House", "house"),
        OTHER("oth", "Other");

        final String code;
        final String label;
        private final List<String> iconNames;

        ItemType(String code, String label, String... iconNames) {
            this.code = code;
            this.label = label;
            this.iconNames = List.of(iconNames);
        }

        /** Null for "all", or for a code that isn't a type. */
        static ItemType fromCode(String code) {
            for (ItemType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return null;
        }

        static boolean isFilterCode(String code) {
            return NO_FILTER.equals(code) || fromCode(code) != null;
        }

        /**
         * From the row's type icon, or failing that its tab ("Pets",
         * "Classes / Armors"...), which is less precise for weapons.
         */
        static ItemType of(String iconSrc, String tab) {
            if (iconSrc != null) {
                String file = iconSrc.substring(iconSrc.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                String name = file.replaceFirst("\\.\\w+$", "").replaceFirst("_table$", "");
                for (ItemType type : values()) {
                    if (type.iconNames.contains(name)) {
                        return type;
                    }
                }
            }

            String lowerTab = tab == null ? "" : tab.toLowerCase(Locale.ROOT);
            if (lowerTab.contains("pet")) return PET;
            if (lowerTab.contains("helm")) return HELM;
            if (lowerTab.contains("back") || lowerTab.contains("cape")) return CAPE;
            if (lowerTab.contains("armor") || lowerTab.contains("class")) return ARMOR;
            if (lowerTab.contains("necklace")) return NECKLACE;
            if (lowerTab.contains("house")) return HOUSE;
            if (lowerTab.contains("misc")) return MISC;
            if (lowerTab.contains("weapon")) return WEAPON;
            return OTHER;
        }
    }

    /**
     * @param category the shop tab the item is listed under ("Weapons", "Helms"...), or null
     * @param iconUrl  the item type icon from the shop table, used when the item has no image
     */
    private record ShopItem(String name, String url, ItemType type, String category, String price,
                            List<String> badges, String iconUrl) {}

    private record ShopPage(String name, String url, List<String> badges, List<ShopItem> items) {}

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {
        String shopName = event.getOption("name", OptionMapping::getAsString);
        if (shopName == null || shopName.isBlank()) {
            event.reply("Please enter a shop to browse.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        EXECUTOR.submit(() -> {
            try {
                handle(event.getHook(), shopName.trim());
            } catch (RuntimeException e) {
                log.error("Failed to show shop '{}'", shopName, e);
                event.getHook().sendMessage("Something went wrong while loading that shop.").queue();
            }
        });
    }

    private void handle(InteractionHook hook, String shopName) {
        String url;
        try {
            url = findShopUrl(shopName);
        } catch (SQLException e) {
            log.error("Database error looking up shop '{}'", shopName, e);
            hook.sendMessage("An error occurred while looking up that shop.").queue();
            return;
        }

        if (url == null) {
            hook.sendMessage("No such shop found! Kindly select one of the suggested options.").queue();
            return;
        }

        String slug = WikiLinks.slugOf(url);
        if (slug == null) {
            log.warn("aqwshop row '{}' has an unusable url: {}", shopName, url);
            hook.sendMessage("That shop's wiki link is invalid.").queue();
            return;
        }

        showShop(hook, slug);
    }

    /**
     * Sends the first page of a shop as the reply to a deferred interaction.
     * Also used by /chests to show what a chest unlocks.
     */
    static void showShop(InteractionHook hook, String slug) {
        ShopPage shop;
        try {
            shop = loadShop(slug);
        } catch (HttpStatusException e) {
            log.warn("Wiki returned {} for shop {}", e.getStatusCode(), e.getUrl());
            hook.sendMessage(e.getStatusCode() == 404
                    ? "That shop's wiki page doesn't exist anymore."
                    : "Couldn't reach the AQW wiki right now. Try again in a bit.").queue();
            return;
        } catch (IOException e) {
            log.warn("Failed to fetch shop page {}: {}", slug, e.getMessage());
            hook.sendMessage("Couldn't reach the AQW wiki right now. Try again in a bit, or open the page directly.")
                    .addComponents(ActionRow.of(Button.link(WikiLinks.WIKI_BASE_URL + slug, "View Wiki")))
                    .queue();
            return;
        }

        if (shop.items().isEmpty()) {
            hook.sendMessage("**" + MarkdownSanitizer.escape(shop.name()) + "** has no items listed on the wiki.")
                    .addComponents(ActionRow.of(Button.link(shop.url(), "View Wiki")))
                    .queue();
            return;
        }

        hook.sendMessageComponents(render(shop, slug, 1, null)).useComponentsV2().queue();
    }

    /**
     * Pagination for a /shop message, routed here from the ButtonManager. The
     * shop is identified by its slug in the button ID, so the buttons keep
     * working after the cache expires or the bot restarts.
     */
    public static void handleButton(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":", 4);
        if (parts.length != 4) {
            event.reply("This button is no longer valid.").setEphemeral(true).queue();
            return;
        }

        // Buttons from before the type filter existed have no filter code
        String rest = parts[3];
        String filterCode = NO_FILTER;
        int colon = rest.indexOf(':');
        if (colon > 0 && ItemType.isFilterCode(rest.substring(0, colon))) {
            filterCode = rest.substring(0, colon);
            rest = rest.substring(colon + 1);
        }

        int page;
        try {
            page = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            page = -1;
        }
        if (page < 0 || !WikiLinks.isValidSlug(rest)) {
            event.reply("This button is no longer valid.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        editPage(event.getHook(), rest, page, ItemType.fromCode(filterCode));
    }

    /** The type filter menu: shows page 1 of the chosen type. */
    public static void handleFilter(StringSelectInteractionEvent event) {
        String slug = event.getComponentId().substring(FILTER_PREFIX.length());
        String code = event.getValues().isEmpty() ? NO_FILTER : event.getValues().getFirst();
        if (!WikiLinks.isValidSlug(slug) || !ItemType.isFilterCode(code)) {
            event.reply("This menu is no longer valid.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        editPage(event.getHook(), slug, 1, ItemType.fromCode(code));
    }

    private static void editPage(InteractionHook hook, String slug, int page, ItemType filter) {
        EXECUTOR.submit(() -> {
            try {
                ShopPage shop = loadShop(slug);
                if (shop.items().isEmpty()) {
                    hook.sendMessage("This shop no longer has any items listed on the wiki.").setEphemeral(true).queue();
                    return;
                }
                hook.editOriginalComponents(render(shop, slug, page, filter)).useComponentsV2().queue();
            } catch (IOException e) {
                log.warn("Failed to fetch shop page {} for pagination: {}", slug, e.getMessage());
                hook.sendMessage("Couldn't reach the AQW wiki right now. Try again in a bit.").setEphemeral(true).queue();
            } catch (RuntimeException e) {
                log.error("Failed to paginate shop {}", slug, e);
                hook.sendMessage("Something went wrong while loading that page.").setEphemeral(true).queue();
            }
        });
    }

    /**
     * Case-insensitive, so a name typed by hand still works if it wasn't picked
     * from autocomplete. An exact-case match wins if two names differ only in case.
     */
    public static String findShopUrl(String shopName) throws SQLException {
        try (Connection connection = Caffein.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT url FROM aqwshop WHERE LOWER(shop_name) = LOWER(?) ORDER BY shop_name = ? DESC LIMIT 1")) {

            ps.setString(1, shopName);
            ps.setString(2, shopName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("url") : null;
            }
        }
    }

    private static ShopPage loadShop(String slug) throws IOException {
        ShopPage cached = SHOPS.getIfPresent(slug);
        if (cached != null) {
            return cached;
        }

        String url = WikiLinks.WIKI_BASE_URL + slug;
        Document document = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(FETCH_TIMEOUT_MS)
                .get();

        ShopPage shop = parseShop(document, url, slug);
        SHOPS.put(slug, shop);
        return shop;
    }

    /**
     * Every shop page lists its items in tabbed tables, one tab per item type:
     * <pre>
     *   &lt;tr&gt;&lt;td&gt;&lt;img src="Sword_Table.png"&gt;&lt;/td&gt;
     *       &lt;td&gt;&lt;a href="/item"&gt;Item&lt;/a&gt; &lt;img src="acsmall.png"&gt;&lt;/td&gt;
     *       &lt;td&gt;200 AC&lt;/td&gt;&lt;/tr&gt;
     * </pre>
     * Header rows only have &lt;th&gt; cells, so they're skipped.
     */
    private static ShopPage parseShop(Document document, String url, String slug) {
        Element title = document.getElementById("page-title");
        String name = title == null || title.text().isBlank() ? slug : title.text().trim();

        Element pageContent = document.getElementById("page-content");
        if (pageContent == null) {
            return new ShopPage(name, url, List.of(), List.of());
        }

        List<ShopItem> items = new ArrayList<>();
        for (Element row : pageContent.select("table.wiki-content-table tr")) {
            Elements cells = row.select("> td");
            if (cells.size() < 2) {
                continue;
            }

            // Some tables leave out the type icon column
            boolean hasIcon = cells.size() >= 3;
            Element iconCell = hasIcon ? cells.get(0) : null;
            Element nameCell = cells.get(hasIcon ? 1 : 0);
            Element priceCell = cells.get(hasIcon ? 2 : 1);

            Element link = nameCell.selectFirst("a[href]");
            String itemName = (link == null ? nameCell.text() : link.text()).trim();
            if (itemName.isEmpty()) {
                continue;
            }

            Element icon = iconCell == null ? null : iconCell.selectFirst("img[src]");
            String category = categoryOf(row);
            items.add(new ShopItem(
                    itemName,
                    link == null ? null : link.absUrl("href"),
                    ItemType.of(icon == null ? null : icon.attr("src"), category),
                    category,
                    renderPrice(priceCell),
                    nameCell.select("img[src*=/image-tags/]").stream().map(Wiki::badgeName).distinct().toList(),
                    icon == null ? null : WikiLinks.validMediaUrl(icon.absUrl("src"))));
        }

        return new ShopPage(name, url, Wiki.parseBadges(pageContent), List.copyOf(items));
    }

    /**
     * The shop's items as plain text for the chatbot's aqw_wiki tool, one line per item with its
     * type, tags and price; null if the page lists no items. Stops at {@code maxChars}.
     */
    public static String describeItems(Document document, String url, int maxChars) {
        ShopPage shop = parseShop(document, url, Objects.requireNonNullElse(WikiLinks.slugOf(url), url));
        if (shop.items().isEmpty()) {
            return null;
        }

        StringBuilder out = new StringBuilder("items (" + shop.items().size() + "):\n");
        int shown = 0;
        for (ShopItem item : shop.items()) {
            StringBuilder line = new StringBuilder("- ").append(item.name());

            List<String> details = new ArrayList<>();
            if (item.type() != ItemType.OTHER) details.add(item.type().label);
            details.addAll(item.badges());
            if (!details.isEmpty()) line.append(" [").append(String.join(", ", details)).append(']');

            // Merge prices come back as escaped "• Material xN" lines; flattened to one line here.
            String price = item.price().replace("• ", "").replace("\n", "; ").replaceAll("\\\\([*_~`|>\\\\])", "$1");
            if (!price.isBlank()) line.append(" — ").append(price);
            line.append('\n');

            if (out.length() + line.length() > maxChars) {
                out.append("… and ").append(shop.items().size() - shown).append(" more items");
                break;
            }
            out.append(line);
            shown++;
        }
        return out.toString().strip();
    }

    /** The name of the tab whose panel holds this row, matched by position like the wiki's tab view. */
    private static String categoryOf(Element row) {
        for (Element panel = row.parent(); panel != null; panel = panel.parent()) {
            Element content = panel.parent();
            if (content == null || !content.hasClass("yui-content")) {
                continue;
            }

            Element navset = content.parent();
            if (navset == null) {
                return null;
            }
            Elements tabs = navset.select("> ul.yui-nav > li");
            int index = panel.elementSiblingIndex();
            return index < tabs.size() ? tabs.get(index).text().trim() : null;
        }
        return null;
    }

    /** One line per requirement for merge prices ("Bonecastle Amulet x25"), otherwise a single line ("200 AC"). */
    private static String renderPrice(Element cell) {
        Element copy = cell.clone();
        copy.select("br").forEach(br -> br.replaceWith(new TextNode("\n")));
        copy.select("p, li").forEach(block -> block.prependText("\n"));

        List<String> lines = copy.wholeText().lines()
                .map(line -> line.replaceAll("\\s+", " ").trim())
                .map(line -> line.endsWith(",") ? line.substring(0, line.length() - 1).trim() : line)
                .filter(line -> !line.isEmpty())
                .map(MarkdownSanitizer::escape)
                .toList();

        if (lines.size() == 1) {
            return lines.getFirst();
        }
        return lines.stream().map(line -> "• " + line).collect(Collectors.joining("\n"));
    }

    /** @param requestedFilter the type to show, or null for every item */
    private static Container render(ShopPage shop, String slug, int requestedPage, ItemType requestedFilter) {
        List<ShopItem> filtered = requestedFilter == null
                ? shop.items()
                : shop.items().stream().filter(item -> item.type() == requestedFilter).toList();
        // The shop may have changed since the menu was made; show everything then
        ItemType filter = filtered.isEmpty() ? null : requestedFilter;
        List<ShopItem> items = filtered.isEmpty() ? shop.items() : filtered;

        int totalItems = items.size();
        int totalPages = Math.ceilDiv(totalItems, ITEMS_PER_PAGE);
        int page = Math.clamp(requestedPage, 1, totalPages);

        int start = (page - 1) * ITEMS_PER_PAGE;
        List<ShopItem> pageItems = items.subList(start, Math.min(start + ITEMS_PER_PAGE, totalItems));
        List<String> images = fetchImages(pageItems);

        // Chest and 0 AC shops list the same price on every item; it's said once in the header instead
        String sharedPrice = sharedPrice(shop.items());

        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(Section.of(Button.link(shop.url(), "View Wiki"),
                TextDisplay.of(header(shop, totalItems, filter, sharedPrice))));
        components.add(Separator.createDivider(Separator.Spacing.SMALL));

        for (int i = 0; i < pageItems.size(); i++) {
            if (i > 0) {
                components.add(Separator.createDivider(Separator.Spacing.SMALL));
            }

            TextDisplay text = TextDisplay.of(itemText(start + i + 1, pageItems.get(i), sharedPrice == null));
            String image = images.get(i);
            components.add(image == null ? text : Section.of(Thumbnail.fromUrl(image), text));
        }

        components.add(Separator.createDivider(Separator.Spacing.LARGE));

        ActionRow filterMenu = filterMenu(shop, slug, filter);
        if (filterMenu != null) {
            components.add(filterMenu);
        }
        components.add(navigation(slug, filter, page, totalPages));

        return Container.of(components).withAccentColor(ACCENT_COLOR);
    }

    /** The price every item has, or null if they differ (or there's only one item, where it's no clearer). */
    private static String sharedPrice(List<ShopItem> items) {
        if (items.size() < 2) {
            return null;
        }
        String first = items.getFirst().price();
        if (first.isEmpty() || first.contains("\n")) {
            return null;
        }
        return items.stream().allMatch(item -> item.price().equals(first)) ? first : null;
    }

    private static String header(ShopPage shop, int shownCount, ItemType filter, String sharedPrice) {
        StringBuilder text = new StringBuilder("## ").append(MarkdownSanitizer.escape(shop.name()));
        if (!shop.badges().isEmpty()) {
            text.append('\n').append(badges(shop.badges()));
        }

        int total = shop.items().size();
        text.append("\n-# ");
        if (filter == null) {
            text.append(total).append(total == 1 ? " item" : " items");
        } else {
            text.append(shownCount).append(" of ").append(total).append(" items · ").append(filter.label);
        }
        if (sharedPrice != null) {
            text.append(" · all cost ").append(sharedPrice);
        }
        return Wiki.truncate(text.toString(), HEADER_LIMIT);
    }

    /**
     * Only the types this shop has, each with its item count; null when the
     * shop has just one type, since there'd be nothing to narrow down.
     */
    private static ActionRow filterMenu(ShopPage shop, String slug, ItemType current) {
        Map<ItemType, Long> counts = shop.items().stream()
                .collect(Collectors.groupingBy(ShopItem::type, () -> new EnumMap<>(ItemType.class), Collectors.counting()));
        if (counts.size() < 2) {
            return null;
        }

        List<SelectOption> options = new ArrayList<>();
        options.add(SelectOption.of("All items", NO_FILTER)
                .withDescription(countLabel(shop.items().size()))
                .withDefault(current == null));
        counts.forEach((type, count) -> options.add(SelectOption.of(type.label, type.code)
                .withDescription(countLabel(count))
                .withDefault(type == current)));

        return ActionRow.of(StringSelectMenu.create(FILTER_PREFIX + slug)
                .setPlaceholder("Filter by type…")
                .addOptions(options)
                .build());
    }

    private static String countLabel(long count) {
        return count + (count == 1 ? " item" : " items");
    }

    private static String itemText(int number, ShopItem item, boolean showPrice) {
        StringBuilder text = new StringBuilder("### ")
                .append(number).append(". ")
                .append(new LocationEntry(item.name(), item.url()).toMarkdown());

        if (!item.badges().isEmpty()) {
            text.append('\n').append(badges(item.badges()));
        }

        if (showPrice && !item.price().isEmpty()) {
            String price = Wiki.truncateLines(item.price(), PRICE_LIMIT);
            text.append(price.contains("\n") ? "\n**Price:**\n" : "\n**Price:** ").append(price);
        }

        // The tab name is only a fallback for types the icons don't cover
        String typeLabel = item.type() != ItemType.OTHER ? item.type().label : item.category();
        if (typeLabel != null && !typeLabel.isEmpty()) {
            text.append("\n-# ").append(MarkdownSanitizer.escape(typeLabel));
        }

        return Wiki.truncate(text.toString(), ITEM_TEXT_LIMIT);
    }

    private static String badges(List<String> badges) {
        return badges.stream().map(badge -> "`" + badge + "`").collect(Collectors.joining(" "));
    }

    /**
     * Each action gets its own ID prefix since Discord requires IDs to be unique
     * within a message, and e.g. "first" and "previous" both point at page 1 on page 2.
     */
    private static ActionRow navigation(String slug, ItemType filter, int page, int totalPages) {
        String code = filter == null ? NO_FILTER : filter.code;
        return ActionRow.of(
                Button.secondary(buttonId("first", 1, code, slug), Emoji.fromUnicode("U+23EA")).withDisabled(page == 1),
                Button.secondary(buttonId("prev", page - 1, code, slug), "Previous").withDisabled(page == 1),
                Button.secondary(buttonId("page", page, code, slug), page + "/" + totalPages).withDisabled(true),
                Button.secondary(buttonId("next", page + 1, code, slug), "Next").withDisabled(page == totalPages),
                Button.secondary(buttonId("last", totalPages, code, slug), Emoji.fromUnicode("U+23E9")).withDisabled(page == totalPages));
    }

    /** "shop:next:2:dag:some-shop": at most 4 + 5 + 3 + 3 + 80 (slug) + 4 colons = 99 of Discord's 100 characters. */
    private static String buttonId(String action, int page, String filterCode, String slug) {
        return BUTTON_PREFIX + ":" + action + ":" + page + ":" + filterCode + ":" + slug;
    }

    /** The thumbnail for each item, in order; an entry is null when there's no usable image at all. */
    private static List<String> fetchImages(List<ShopItem> items) {
        List<CompletableFuture<String>> futures = items.stream()
                .map(item -> CompletableFuture.supplyAsync(() -> itemImage(item), IMAGE_EXECUTOR)
                        .exceptionally(e -> {
                            log.warn("Failed to find an image for '{}': {}", item.name(), e.getMessage());
                            return item.iconUrl();
                        }))
                .toList();

        return futures.stream().map(CompletableFuture::join).toList();
    }

    /** The item's preview image from its own wiki page, falling back to the shop table's type icon. */
    private static String itemImage(ShopItem item) {
        if (item.url() == null || !WikiLinks.isWikiPage(item.url())) {
            return item.iconUrl();
        }

        String image = ITEM_IMAGES.getIfPresent(item.url());
        if (image == null) {
            try {
                Document document = Jsoup.connect(item.url())
                        .userAgent(USER_AGENT)
                        .timeout(FETCH_TIMEOUT_MS)
                        .get();

                Element pageContent = document.getElementById("page-content");
                List<String> images = pageContent == null ? List.of() : Wiki.findItemImages(pageContent);
                image = images.stream().map(WikiLinks::validMediaUrl).filter(Objects::nonNull).findFirst().orElse("");
            } catch (HttpStatusException e) {
                // A missing page stays missing; anything else is worth retrying next time
                if (e.getStatusCode() != 404) {
                    log.debug("Wiki returned {} for item page {}", e.getStatusCode(), item.url());
                    return item.iconUrl();
                }
                image = "";
            } catch (IOException e) {
                log.debug("Failed to fetch item page {}: {}", item.url(), e.getMessage());
                return item.iconUrl();
            }
            ITEM_IMAGES.put(item.url(), image);
        }

        return image.isEmpty() ? item.iconUrl() : image;
    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("shop", "Browse a shop from the AQW wiki")
                .addOption(OptionType.STRING, "name", "The shop to browse", true, true)
                .setContexts(InteractionContextType.GUILD, InteractionContextType.PRIVATE_CHANNEL)
                .setIntegrationTypes(IntegrationType.USER_INSTALL, IntegrationType.GUILD_INSTALL);
    }
}
