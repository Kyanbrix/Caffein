package com.github.kyanbrix.component.command;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.utils.WikiItem;
import com.github.kyanbrix.utils.cache.WikiCacheManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Usage: {@code !scrap <category url>}, e.g.
 * {@code !scrap https://aqwwiki.wikidot.com/helmets-hoods}
 * (a page-specific link like {@code .../helmets-hoods/p/18} also works — the
 * page suffix is stripped and every page from 1 to the last is scraped).
 */
public class WikiScraper implements ICommand {

    private static final Logger log = LoggerFactory.getLogger(WikiScraper.class);

    // TODO: replace with the real AQW wiki host. Rejecting anything else closes
    // off the SSRF risk of letting users pass an arbitrary URL to scrape.
    private static final String ALLOWED_HOST = "aqwwiki.wikidot.com";

    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int TIMEOUT_MS = 45_000;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 2_000;
    private static final long BASE_DELAY_MS = 1_000;
    private static final long DELAY_JITTER_MS = 700;

    // Safety net in case pager parsing ever misreads the page count.
    private static final int MAX_PAGES = 500;

    private static final Pattern PAGER_OF_N = Pattern.compile("of\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_PAGE_SUFFIX = Pattern.compile("/p/\\d+/?$");
    private static final Pattern MATCHES_OWN_OF_N = Pattern.compile("(?i)of\\s+\\d+");

    // Keeps scraping + DB writes off the JDA gateway event thread, so a slow
    // page load or long multi-page run doesn't stall event dispatch for the bot.
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wiki-scraper");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void accept(MessageReceivedEvent event) {

        // Restrict to server admins. Remove this block if your command
        // dispatcher already gates "scrap" upstream (e.g. an owner-only list).
        if (event.isFromGuild()) {
            Member member = event.getMember();
            if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
                event.getChannel().sendMessage("You need Administrator permission to run this.").queue();
                return;
            }
        }

        // Only lowercase enough to detect the prefix; keep the URL's original
        // casing since wiki paths can be case-sensitive.
        String rawContent = event.getMessage().getContentRaw();
        String givenUrl = removePrefixCommand(rawContent).trim();
        String baseUrl = stripPageSuffix(givenUrl);

        if (!isAllowedUrl(baseUrl)) {
            event.getChannel().sendMessage(
                    "That URL isn't allowed. Only links to " + ALLOWED_HOST + " can be scraped.").queue();
            return;
        }

        EXECUTOR.submit(() -> scrapeAllPages(event, baseUrl));
    }

    private static String stripPageSuffix(String url) {
        return TRAILING_PAGE_SUFFIX.matcher(url).replaceFirst("");
    }

    private boolean isAllowedUrl(String urlToParse) {
        try {
            URI uri = URI.create(urlToParse);
            return "https".equalsIgnoreCase(uri.getScheme()) && ALLOWED_HOST.equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void scrapeAllPages(MessageReceivedEvent event, String baseUrl) {
        // Cookies are carried across requests, and each request's Referer points
        // at the previous page, so this looks like normal browsing rather than a
        // burst of identical anonymous requests — the usual trigger for a site's
        // rate limiting / anti-bot checks kicking in and causing timeouts.
        Map<String, String> cookies = new HashMap<>();

        try {
            String firstPageUrl = pageUrl(baseUrl, 1);
            Document firstPage = fetchWithRetry(firstPageUrl, baseUrl, cookies);
            int totalPages = detectTotalPages(firstPage, baseUrl);

            log.info("Wiki Scraper: {} has {} page(s)", baseUrl, totalPages);
            event.getChannel().sendMessage(
                    "Scraping " + totalPages + " page(s) from " + baseUrl + " ...").queue();

            int totalScraped = 0;
            int totalInserted = 0;
            int failedPages = 0;
            String previousUrl = baseUrl;

            for (int page = 1; page <= totalPages; page++) {
                String url = pageUrl(baseUrl, page);
                Document document;

                try {
                    document = (page == 1) ? firstPage : fetchWithRetry(url, previousUrl, cookies);
                } catch (IOException e) {
                    failedPages++;
                    log.error("Wiki Scraper: giving up on page {} after {} attempts: {}",
                            page, MAX_RETRIES, e.getMessage());
                    previousUrl = url;
                    continue;
                }
                previousUrl = url;

                List<WikiItem> items = extractItems(document);
                totalScraped += items.size();

                if (!items.isEmpty()) {
                    try {
                        totalInserted += insertItems(items);
                    } catch (SQLException e) {
                        failedPages++;
                        log.error("Wiki Scraper: failed to insert page {} ({} items)", page, items.size(), e);
                    }
                }

                log.info("Wiki Scraper: page {}/{} -> {} item(s) found", page, totalPages, items.size());

                if (page < totalPages) {
                    Thread.sleep(BASE_DELAY_MS + ThreadLocalRandom.current().nextLong(DELAY_JITTER_MS));
                }
            }

            WikiCacheManager.loadCache();

            String summary = "Done. Scraped " + totalScraped + " items across " + totalPages + " page(s)"
                    + (failedPages > 0 ? " (" + failedPages + " page(s) failed)" : "")
                    + ", " + totalInserted + " new.";
            event.getChannel().sendMessage(summary).queue();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Wiki Scraper interrupted for {}", baseUrl);
        } catch (Exception e) {
            log.error("Wiki Scraper failed for {}", baseUrl, e);
            event.getChannel().sendMessage("Scrape failed \u2014 check the logs.").queue();
        }
    }

    private static String pageUrl(String baseUrl, int page) {
        return baseUrl + "/p/" + page;
    }

    /**
     * Fetches a page, retrying on IOException (timeouts, connection resets, etc.)
     * with increasing backoff. Cookies from each response are folded back into
     * the shared cookie map so the "session" persists across the whole run.
     */
    private Document fetchWithRetry(String url, String referer, Map<String, String> cookies)
            throws IOException, InterruptedException {
        IOException lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Connection.Response response = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .referrer(referer)
                        .cookies(cookies)
                        .timeout(TIMEOUT_MS)
                        .execute();
                cookies.putAll(response.cookies());
                return response.parse();
            } catch (IOException e) {
                lastError = e;
                log.warn("Wiki Scraper: attempt {}/{} failed for {}: {}", attempt, MAX_RETRIES, url, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(RETRY_BACKOFF_MS * attempt);
                }
            }
        }

        throw lastError;
    }

    private List<WikiItem> extractItems(Document document) {
        Elements elements = document.select(".list-pages-item p > a");
        List<WikiItem> items = new ArrayList<>();
        for (Element link : elements) {
            items.add(new WikiItem(link.text(), link.absUrl("href")));
        }
        return items;
    }

    /**
     * Reads the last page number for this category. Combines two independent
     * signals and takes the highest, since either one alone can undercount:
     *
     * 1. An explicit "page X of N" label, wherever it appears on the page.
     * 2. Every pager link's *href* (not its visible text) that points back at
     *    this category's own "/p/N" pagination URL. This is what actually
     *    catches a "last »" link — Wikidot's pager only prints a window of
     *    nearby page numbers (e.g. "1 2 3 4 5 6 ... last »"), so a link whose
     *    visible text is "last" rather than a number was being ignored by a
     *    text-only scan, which is why detection previously stopped at the
     *    highest number actually printed (6) instead of the real last page.
     */
    private int detectTotalPages(Document document, String baseUrl) {
        int highest = 1;

        for (Element el : document.select(":matchesOwn(" + MATCHES_OWN_OF_N.pattern() + ")")) {
            Matcher matcher = PAGER_OF_N.matcher(el.ownText());
            if (matcher.find()) {
                highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
            }
        }

        Pattern hrefPattern = Pattern.compile(
                Pattern.quote(baseUrl) + "/p/(\\d+)/?$", Pattern.CASE_INSENSITIVE);
        for (Element link : document.select("a[href]")) {
            Matcher matcher = hrefPattern.matcher(link.absUrl("href"));
            if (matcher.find()) {
                highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
            }
        }

        return clamp(highest);
    }

    private static int clamp(int totalPages) {
        if (totalPages > MAX_PAGES) {
            log.warn("Wiki Scraper: detected {} pages, capping at {}", totalPages, MAX_PAGES);
            return MAX_PAGES;
        }
        return Math.max(totalPages, 1);
    }

    private int insertItems(List<WikiItem> wikiItems) throws SQLException {
        int totalInserted = 0;
        int batchSize = 1000;
        int count = 0;

        try (java.sql.Connection connection = Caffein.getInstance().getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO wiki (item_name, url) VALUES (?, ?) ON CONFLICT (item_name) DO NOTHING")) {

                for (WikiItem item : wikiItems) {
                    statement.setString(1, item.name());
                    statement.setString(2, item.url());
                    statement.addBatch();

                    count++;
                    if (count % batchSize == 0) {
                        totalInserted += parseInsertedCount(statement.executeBatch());
                    }
                }
                totalInserted += parseInsertedCount(statement.executeBatch());

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                log.warn("Wiki Scraper insert batch rolled back", e);
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }

        return totalInserted;
    }

    private static int parseInsertedCount(int[] results) {
        int inserted = 0;
        for (int res : results) {
            // PostgreSQL returns 1 for inserted row, 0 if skipped by ON CONFLICT
            if (res > 0) {
                inserted += res;
            }
        }
        return inserted;
    }

    @Override
    public String commandName() {
        return "scrap";
    }
}