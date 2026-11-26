package com.github.kyanbrix.api.lastfm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kyanbrix.utils.Constant;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The read-only Last.fm API calls the bot makes. Everything here blocks, so call
 * it from an executor, never from the JDA event thread.
 */
public final class LastFmClient {

    /** Shared by everything that talks to Last.fm, so connections are pooled. */
    static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build();

    // Last.fm sends a single object instead of a one-element array when there's only one result
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String USER_AGENT = "CaffeinBot/1.0";

    /** The grey star Last.fm serves when it has no art for a track. */
    private static final String PLACEHOLDER_IMAGE_ID = "2a96cbd8b46e442fc41c2b86b821562f";

    private LastFmClient() {}

    /**
     * The user's latest scrobbles. While something is playing, it comes first
     * and Last.fm adds it on top of {@code limit}.
     */
    public static LastFmRecentTracksResponse recentTracks(String user, int limit) throws LastFmException {
        HttpUrl url = baseUrl("user.getrecenttracks")
                .addQueryParameter("user", user)
                .addQueryParameter("limit", String.valueOf(limit))
                .build();
        return get(url, LastFmRecentTracksResponse.class);
    }

    public static LastFmTopTracksResponse topTracks(String user, LastFmPeriod period, int page, int limit) throws LastFmException {
        HttpUrl url = baseUrl("user.gettoptracks")
                .addQueryParameter("user", user)
                .addQueryParameter("period", period.code())
                .addQueryParameter("page", String.valueOf(page))
                .addQueryParameter("limit", String.valueOf(limit))
                .build();
        return get(url, LastFmTopTracksResponse.class);
    }

    public static LastFmTopArtistsResponse topArtists(String user, LastFmPeriod period, int page, int limit) throws LastFmException {
        HttpUrl url = baseUrl("user.gettopartists")
                .addQueryParameter("user", user)
                .addQueryParameter("period", period.code())
                .addQueryParameter("page", String.valueOf(page))
                .addQueryParameter("limit", String.valueOf(limit))
                .build();
        return get(url, LastFmTopArtistsResponse.class);
    }

    /**
     * The artist's MusicBrainz ID from artist.getInfo, or an empty string if Last.fm
     * has none. user.getTopArtists leaves it out for some artists that do have one.
     * Autocorrect stays off so the name isn't swapped for a different artist's.
     */
    public static String artistMbid(String artist) throws LastFmException {
        HttpUrl url = baseUrl("artist.getInfo")
                .addQueryParameter("artist", artist)
                .addQueryParameter("autocorrect", "0")
                .build();
        return get(url, JsonNode.class).path("artist").path("mbid").asText("");
    }

    /** Track details plus {@code user}'s play count and loved status for it. */
    public static LastFmTrackInfoResponse trackInfo(String artist, String track, String user) throws LastFmException {
        HttpUrl url = baseUrl("track.getInfo")
                .addQueryParameter("artist", artist)
                .addQueryParameter("track", track)
                .addQueryParameter("username", user)
                .addQueryParameter("autocorrect", "1")
                .build();
        return get(url, LastFmTrackInfoResponse.class);
    }

    public static String profileUrl(String user) {
        return "https://www.last.fm/user/" + user;
    }

    /** The largest usable image in a Last.fm image list (they're ordered small to large). */
    static String largestImage(List<String> urls) {
        if (urls == null) return null;
        for (int i = urls.size() - 1; i >= 0; i--) {
            String url = urls.get(i);
            if (url != null && !url.isBlank() && !url.contains(PLACEHOLDER_IMAGE_ID)) {
                return url;
            }
        }
        return null;
    }

    /** Last.fm sends numbers as strings; anything unparseable counts as 0. */
    static long parseLong(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static HttpUrl.Builder baseUrl(String method) {
        return HttpUrl.get(Constant.LAST_FM_API_URL).newBuilder()
                .addQueryParameter("method", method)
                .addQueryParameter("api_key", System.getenv("LAST_FM_API_KEY"))
                .addQueryParameter("format", "json");
    }

    private static <T> T get(HttpUrl url, Class<T> type) throws LastFmException {
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build();

        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();

            // Errors come back as {"error": 6, "message": "..."}, sometimes with a 200
            JsonNode root;
            try {
                root = MAPPER.readTree(text);
            } catch (IOException e) {
                throw new LastFmException(LastFmException.UNKNOWN, "HTTP " + response.code() + " with a non-JSON body");
            }
            if (root != null && root.has("error")) {
                throw new LastFmException(root.path("error").asInt(LastFmException.UNKNOWN), root.path("message").asText());
            }
            if (!response.isSuccessful() || root == null) {
                throw new LastFmException(LastFmException.UNKNOWN, "HTTP " + response.code());
            }

            return MAPPER.treeToValue(root, type);
        } catch (IOException e) {
            throw new LastFmException("Request to Last.fm failed", e);
        }
    }
}
