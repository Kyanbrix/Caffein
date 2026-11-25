package com.github.kyanbrix.api.lastfm;

/** The time ranges Last.fm's user.getTop* methods accept. */
public enum LastFmPeriod {

    WEEK("7day", "Weekly", "Last 7 days"),
    MONTH("1month", "Monthly", "Last 30 days"),
    QUARTER("3month", "Quarterly", "Last 3 months"),
    HALF_YEAR("6month", "Half-yearly", "Last 6 months"),
    YEAR("12month", "Yearly", "Last 12 months"),
    OVERALL("overall", "Overall", "All time");

    private final String code;
    private final String label;
    private final String description;

    LastFmPeriod(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    /** The value Last.fm's API takes, also used in button and menu IDs. */
    public String code() { return code; }
    public String label() { return label; }
    public String description() { return description; }

    /** The period for an API code, or null if it isn't one. */
    public static LastFmPeriod fromCode(String code) {
        for (LastFmPeriod period : values()) {
            if (period.code.equals(code)) return period;
        }
        return null;
    }
}
