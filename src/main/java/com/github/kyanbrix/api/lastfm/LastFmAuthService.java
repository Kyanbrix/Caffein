package com.github.kyanbrix.api.lastfm;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.kyanbrix.utils.Constant;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * The Last.fm web auth flow: the bot hands out a login URL whose callback carries
 * a signed Discord ID, and the callback server trades the token Last.fm adds for a session.
 */
public final class LastFmAuthService {

    /** How long a login link stays valid after /fm link hands it out. */
    public static final Duration LINK_LIFETIME = Duration.ofMinutes(15);

    private static final String CALLBACK_URL = "https://api.kyanbrix.com/api/lastfm/callback";

    public record LastFmSession(String username, String key) {}

    private LastFmAuthService() {}

    /**
     * The Last.fm login page for this Discord user. The callback's {@code sig}
     * stops anyone from crafting a callback that links their Last.fm to someone else.
     */
    public static String createAuthUrl(long discordId) {
        long timestamp = System.currentTimeMillis() / 1000;
        String callback = CALLBACK_URL
                + "?discord_id=" + discordId
                + "&ts=" + timestamp
                + "&sig=" + stateSignature(discordId, timestamp);

        return "https://www.last.fm/api/auth/?api_key=" + System.getenv("LAST_FM_API_KEY")
                + "&cb=" + URLEncoder.encode(callback, StandardCharsets.UTF_8);
    }

    /** Whether a callback's Discord ID, timestamp and signature came from {@link #createAuthUrl} and haven't expired. */
    public static boolean verifyState(long discordId, long timestamp, String signature) {
        long age = System.currentTimeMillis() / 1000 - timestamp;
        if (age < 0 || age > LINK_LIFETIME.toSeconds() || signature == null) {
            return false;
        }

        byte[] expected = stateSignature(discordId, timestamp).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
    }

    /** Trades the token from the callback for the user's Last.fm name and session key. */
    public static LastFmSession getSessionInfo(String token) throws LastFmException {
        String apiKey = System.getenv("LAST_FM_API_KEY");
        String apiSecret = System.getenv("LAST_FM_SECRET_KEY");

        Map<String, String> params = Map.of(
                "method", "auth.getSession",
                "api_key", apiKey,
                "token", token
        );

        HttpUrl url = HttpUrl.get(Constant.LAST_FM_API_URL).newBuilder()
                .addQueryParameter("method", "auth.getSession")
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("token", token)
                .addQueryParameter("api_sig", LastFmSignature.sign(params, apiSecret))
                .addQueryParameter("format", "json")
                .build();

        Request request = new Request.Builder().url(url).build();

        try (Response response = LastFmClient.HTTP.newCall(request).execute()) {
            JsonNode root = LastFmClient.MAPPER.readTree(response.body().string());
            if (root.has("error")) {
                throw new LastFmException(root.path("error").asInt(), root.path("message").asText());
            }
            if (!response.isSuccessful()) {
                throw new LastFmException(LastFmException.UNKNOWN, "Last.fm rejected the token: HTTP " + response.code());
            }

            JsonNode session = root.path("session");
            String name = session.path("name").asText("");
            String key = session.path("key").asText("");
            if (name.isEmpty() || key.isEmpty()) {
                throw new LastFmException(LastFmException.UNKNOWN, "Last.fm returned no session");
            }
            return new LastFmSession(name, key);
        } catch (IOException e) {
            throw new LastFmException("Request to Last.fm failed", e);
        }
    }

    private static String stateSignature(long discordId, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(System.getenv("LAST_FM_SECRET_KEY").getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((discordId + ":" + timestamp).getBytes(StandardCharsets.UTF_8));
            // 16 bytes is plenty against guessing and keeps the callback URL short
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }
}
