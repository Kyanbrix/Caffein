package com.github.kyanbrix.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The two MusicBrainz lookups artist photos need. MusicBrainz allows one request
 * per second per client and answers 503 beyond that, so every call here waits its
 * turn; callers should cache what they get. Everything blocks.
 */
final class MusicBrainzClient {

    private static final Logger log = LoggerFactory.getLogger(MusicBrainzClient.class);

    private static final String API = "https://musicbrainz.org/ws/2/";
    /** MusicBrainz requires a User-Agent that names the application and a way to reach its operator. */
    private static final String USER_AGENT = "CaffeinBot/1.0 (https://github.com/Kyanbrix/Caffein)";
    private static final long MIN_INTERVAL_MS = 1_100;
    /** Below this, MusicBrainz's search score means a loose match rather than the song asked for. */
    private static final int MIN_SCORE = 90;

    private static final Pattern MBID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SPOTIFY_ARTIST_URL = Pattern.compile("^https?://open\\.spotify\\.com/artist/([0-9A-Za-z]{22})(?:[/?].*)?$");

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Object THROTTLE = new Object();
    private static long nextRequestAt;

    /** An artist as MusicBrainz credits them, with the exact casing MusicBrainz uses. */
    record CreditedArtist(String mbid, String name) {}

    private MusicBrainzClient() {}

    /**
     * Who's credited on a song: the best-scoring recording titled {@code title} whose
     * credits include an artist named {@code artistName}. Last.fm merges artists whose
     * names differ only in casing, so the name is compared ignoring case here, and the
     * song decides which of them it really is. Null when nothing matches well enough.
     */
    static CreditedArtist creditedArtist(String title, String artistName) {
        String query = "recording:\"" + escape(title) + "\" AND artist:\"" + escape(artistName) + "\"";
        JsonNode result = get(HttpUrl.get(API + "recording").newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("limit", "10")
                .addQueryParameter("fmt", "json")
                .build());
        return result == null ? null : pickCredited(result, artistName);
    }

    /** {@link #creditedArtist}'s choice from a recording search response; split out to test it. */
    static CreditedArtist pickCredited(JsonNode searchResult, String artistName) {
        // Results come best score first
        for (JsonNode recording : searchResult.path("recordings")) {
            if (recording.path("score").asInt() < MIN_SCORE) {
                break;
            }
            for (JsonNode credit : recording.path("artist-credit")) {
                JsonNode artist = credit.path("artist");
                String mbid = artist.path("id").asText("");
                String creditedAs = credit.path("name").asText("");
                String name = artist.path("name").asText("");
                if (MBID.matcher(mbid).matches()
                        && (creditedAs.equalsIgnoreCase(artistName) || name.equalsIgnoreCase(artistName))) {
                    return new CreditedArtist(mbid, name);
                }
            }
        }
        return null;
    }

    /** The Spotify artist ID MusicBrainz links this artist to, or null if it has none. */
    static String spotifyId(String mbid) {
        if (!MBID.matcher(mbid).matches()) return null;
        JsonNode artist = get(HttpUrl.get(API + "artist/" + mbid).newBuilder()
                .addQueryParameter("inc", "url-rels")
                .addQueryParameter("fmt", "json")
                .build());
        return artist == null ? null : spotifyIdFromRelations(artist);
    }

    /** The first open.spotify.com/artist link in an artist's URL relations; split out to test it. */
    static String spotifyIdFromRelations(JsonNode artist) {
        for (JsonNode relation : artist.path("relations")) {
            Matcher link = SPOTIFY_ARTIST_URL.matcher(relation.path("url").path("resource").asText(""));
            if (link.matches()) {
                return link.group(1);
            }
        }
        return null;
    }

    /** Lucene phrase escaping: only backslashes and quotes are special inside quotes. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** One rate-limited GET, retried once on 503 (MusicBrainz's "slow down"); null on failure. */
    private static JsonNode get(HttpUrl url) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            waitForTurn();
            Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.code() == 503 && attempt == 1) {
                    log.debug("MusicBrainz asked us to slow down; retrying {}", url);
                    continue;
                }
                if (!response.isSuccessful()) {
                    log.warn("MusicBrainz {} returned HTTP {}", url, response.code());
                    return null;
                }
                return mapper.readTree(response.body().string());
            } catch (IOException e) {
                log.warn("MusicBrainz request {} failed: {}", url, e.getMessage());
                return null;
            }
        }
        return null;
    }

    /** Blocks until this caller may send its request, keeping all callers at one request per {@value #MIN_INTERVAL_MS} ms. */
    private static void waitForTurn() {
        long sendAt;
        synchronized (THROTTLE) {
            long now = System.currentTimeMillis();
            sendAt = Math.max(now, nextRequestAt);
            nextRequestAt = sendAt + MIN_INTERVAL_MS;
        }
        long wait = sendAt - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
