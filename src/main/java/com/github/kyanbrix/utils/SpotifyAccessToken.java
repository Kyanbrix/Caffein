package com.github.kyanbrix.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;

import java.time.Duration;

/**
 * An app-only Spotify token (client credentials flow) for artwork lookups: search
 * and artist details don't need a user login. It's kept in memory until it expires.
 * <p>
 * When Spotify refuses the app (e.g. "Active premium subscription required for the
 * owner of the app"), callers report it through {@link #backOff} and lookups skip
 * the Web API for a while instead of failing on every artist. Artist photos don't
 * depend on this token: they come from Spotify's public embed pages.
 */
public final class SpotifyAccessToken {

    private static final Logger log = LoggerFactory.getLogger(SpotifyAccessToken.class);

    /** Refresh a minute early so a token never expires halfway through a page of lookups. */
    private static final long EXPIRY_MARGIN_MS = 60_000L;
    private static final Duration BACK_OFF = Duration.ofMinutes(15);
    /** Spotify refuses apps whose owner has no Premium; that won't fix itself in minutes. */
    private static final Duration PREMIUM_BACK_OFF = Duration.ofHours(6);

    private static final Object LOCK = new Object();
    private static String cachedToken;
    private static long cachedTokenExpiry;
    private static long blockedUntil;

    private SpotifyAccessToken() {}

    /** A valid access token, or null when Spotify isn't configured, refused the app recently, or the request failed. */
    public static String get() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (now < blockedUntil) {
                return null;
            }
            if (cachedToken != null && now < cachedTokenExpiry - EXPIRY_MARGIN_MS) {
                return cachedToken;
            }

            String clientId = System.getenv("SPOTIFY_CLIENT_ID");
            String clientSecret = System.getenv("SPOTIFY_CLIENT_SECRET");
            if (clientId == null || clientSecret == null) {
                log.warn("SPOTIFY_CLIENT_ID or SPOTIFY_CLIENT_SECRET is not set; skipping Spotify artwork");
                blockedUntil = now + BACK_OFF.toMillis();
                return null;
            }

            try {
                ClientCredentials credentials = new SpotifyApi.Builder()
                        .setClientId(clientId)
                        .setClientSecret(clientSecret)
                        .build()
                        .clientCredentials().build().execute();

                cachedToken = credentials.getAccessToken();
                cachedTokenExpiry = now + credentials.getExpiresIn() * 1000L;
                return cachedToken;
            } catch (Exception e) {
                log.error("Failed to get a Spotify access token; skipping Spotify for {} minutes", BACK_OFF.toMinutes(), e);
                blockedUntil = now + BACK_OFF.toMillis();
                return null;
            }
        }
    }

    /** Called when Spotify refuses a request outright (HTTP 403 or 429), so lookups stop trying for a while. */
    public static void backOff(String reason) {
        Duration pause = reason != null && reason.toLowerCase().contains("premium") ? PREMIUM_BACK_OFF : BACK_OFF;
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (now >= blockedUntil) {
                log.warn("Spotify refused the request ({}); skipping the Spotify Web API for {} minutes", reason, pause.toMinutes());
            }
            blockedUntil = now + pause.toMillis();
        }
    }
}
