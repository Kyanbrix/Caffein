package com.github.kyanbrix.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/** Checks for wiki page links and image URLs before they go into button IDs or components. */
public final class WikiLinks {

    public static final String WIKI_HOST = "aqwwiki.wikidot.com";
    public static final String WIKI_BASE_URL = "https://" + WIKI_HOST + "/";

    // Wikidot page names are letters, digits, dashes and category colons. Anything
    // else in a stored URL or a button ID is rejected before it's fetched.
    private static final Pattern SLUG = Pattern.compile("[A-Za-z0-9:_-]+");
    // Slugs ride along in button IDs, which are capped at 100 characters, so this
    // leaves 20 for the prefix, action and page number.
    private static final int MAX_SLUG_LENGTH = 80;
    private static final int MAX_MEDIA_URL_LENGTH = 2048;

    private WikiLinks() {}

    /** "https://aqwwiki.wikidot.com/some-page" (or "/some-page") -> "some-page"; null if it isn't a usable wiki page. */
    public static String slugOf(String url) {
        if (url == null) {
            return null;
        }

        try {
            URI uri = new URI(url.trim());
            if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(WIKI_HOST)) {
                return null;
            }

            String path = uri.getPath();
            if (path == null) {
                return null;
            }

            String slug = path.replaceAll("^/+|/+$", "");
            return isValidSlug(slug) ? slug : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public static boolean isValidSlug(String slug) {
        return slug.length() <= MAX_SLUG_LENGTH && SLUG.matcher(slug).matches();
    }

    public static boolean isWikiPage(String url) {
        try {
            return WIKI_HOST.equalsIgnoreCase(new URI(url).getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Discord rejects the whole message if a media URL is malformed, so every
     * thumbnail goes through here first. Returns null if it can't be used.
     */
    public static String validMediaUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String cleaned = url.trim().replace(" ", "%20");
        if (cleaned.length() > MAX_MEDIA_URL_LENGTH) {
            return null;
        }

        try {
            URI uri = new URI(cleaned);
            boolean web = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            return web && uri.getHost() != null ? cleaned : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
