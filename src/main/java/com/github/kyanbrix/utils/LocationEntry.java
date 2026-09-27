package com.github.kyanbrix.utils;

/**
 * A single location an item can be obtained from. The URL is normalized once
 * at construction (relative wiki paths made absolute, wikidot http upgraded to
 * https, non-web links dropped), so callers never deal with raw hrefs.
 */
public record LocationEntry(String name, String url) {

    private static final String WIKI_BASE = "https://aqwwiki.wikidot.com";

    public LocationEntry {
        name = name == null ? "" : name.trim();
        url = normalizeUrl(url);
    }

    public boolean hasUrl() {
        return url != null;
    }

    /** Discord-safe markdown: a masked link when there's a URL, plain text otherwise. */
    public String toMarkdown() {
        String safeName = escapeLinkText(name);
        return hasUrl() ? "[" + safeName + "](" + escapeLinkUrl(url) + ")" : safeName;
    }

    private static String normalizeUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String trimmed = raw.trim();
        if (trimmed.startsWith("//")) return "https:" + trimmed;
        if (trimmed.startsWith("/")) return WIKI_BASE + trimmed;
        if (trimmed.startsWith("http://aqwwiki.wikidot.com")) return "https://" + trimmed.substring("http://".length());
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;

        // javascript:, mailto:, #anchors etc. aren't useful links in an embed
        return null;
    }

    // Square brackets in the text would end the masked link early.
    private static String escapeLinkText(String text) {
        return text.replace("[", "\\[").replace("]", "\\]");
    }

    // Parentheses and spaces in the URL would break Discord's (url) part.
    private static String escapeLinkUrl(String link) {
        return link.replace(" ", "%20").replace("(", "%28").replace(")", "%29");
    }
}