package com.github.kyanbrix.api.lastfm;

/**
 * A failed Last.fm call: either an HTTP error or a JSON body with an {@code error} code.
 * See https://www.last.fm/api/errorcodes for the codes.
 */
public class LastFmException extends Exception {

    /** No code from Last.fm, e.g. the request never reached it or the body wasn't JSON. */
    public static final int UNKNOWN = -1;
    public static final int INVALID_PARAMETERS = 6;
    public static final int OPERATION_FAILED = 8;
    public static final int RATE_LIMITED = 29;
    public static final int PRIVATE_PROFILE = 17;

    private final int code;

    public LastFmException(int code, String message) {
        super(message);
        this.code = code;
    }

    public LastFmException(String message, Throwable cause) {
        super(message, cause);
        this.code = UNKNOWN;
    }

    public int getCode() { return code; }

    /** A message that's fine to show to the user who ran the command. */
    public String userMessage(String lastFmUsername) {
        return switch (code) {
            // Error 6 is also what Last.fm sends for a user that doesn't exist, which happens after a rename
            case INVALID_PARAMETERS -> "Last.fm doesn't know the user **" + lastFmUsername
                    + "** anymore. If the account was renamed, relink it with `/fm link`.";
            case PRIVATE_PROFILE -> "**" + lastFmUsername + "** keeps their listening history private on Last.fm.";
            case RATE_LIMITED -> "Last.fm is rate limiting the bot right now. Try again in a minute.";
            default -> "Couldn't reach Last.fm right now. Try again in a bit.";
        };
    }
}
