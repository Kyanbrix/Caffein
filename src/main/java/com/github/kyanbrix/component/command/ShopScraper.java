package com.github.kyanbrix.component.command;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.utils.WikiItem;
import com.github.kyanbrix.utils.cache.ShopCacheManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Usage: {@code ?scrapshops}. Fills the aqwshop table from the wiki's shop
 * index, which lists every shop on one page. Existing shops are left as they are.
 */
public class ShopScraper implements ICommand {

    private static final Logger log = LoggerFactory.getLogger(ShopScraper.class);

    private static final String SHOPS_URL = "https://aqwwiki.wikidot.com/shops";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int TIMEOUT_MS = 45_000;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "shop-scraper");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void accept(MessageReceivedEvent event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.getChannel().sendMessage("You need Administrator permission to run this.").queue();
            return;
        }

        event.getChannel().sendMessage("Scraping the shop list...").queue();
        EXECUTOR.submit(() -> scrape(event));
    }

    private void scrape(MessageReceivedEvent event) {
        try {
            Document document = Jsoup.connect(SHOPS_URL)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    // The index is one large page; don't let jsoup cut it short
                    .maxBodySize(0)
                    .get();

            List<WikiItem> shops = document.select(".list-pages-item p > a[href]").stream()
                    .map(link -> new WikiItem(link.text().trim(), link.absUrl("href")))
                    .filter(shop -> !shop.name().isEmpty() && !shop.url().isEmpty())
                    .toList();

            if (shops.isEmpty()) {
                event.getChannel().sendMessage("No shops found on " + SHOPS_URL + ", the page layout may have changed.").queue();
                return;
            }

            int inserted = insertShops(shops);
            ShopCacheManager.loadCache();

            event.getChannel().sendMessage("Done. Found " + shops.size() + " shops, " + inserted + " new.").queue();

        } catch (IOException e) {
            log.error("Shop Scraper failed to fetch {}", SHOPS_URL, e);
            event.getChannel().sendMessage("Couldn't reach the AQW wiki — check the logs.").queue();
        } catch (SQLException e) {
            log.error("Shop Scraper failed to save shops", e);
            event.getChannel().sendMessage("Saving the shops failed — check the logs.").queue();
        }
    }

    private int insertShops(List<WikiItem> shops) throws SQLException {
        try (Connection connection = Caffein.getInstance().getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO aqwshop (shop_name, url) VALUES (?, ?) ON CONFLICT (shop_name) DO NOTHING")) {

                for (WikiItem shop : shops) {
                    statement.setString(1, shop.name());
                    statement.setString(2, shop.url());
                    statement.addBatch();
                }

                int inserted = 0;
                for (int result : statement.executeBatch()) {
                    // PostgreSQL returns 1 for an inserted row, 0 if skipped by ON CONFLICT
                    if (result > 0) {
                        inserted += result;
                    }
                }

                connection.commit();
                return inserted;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    @Override
    public String commandName() {
        return "scrapshops";
    }
}
