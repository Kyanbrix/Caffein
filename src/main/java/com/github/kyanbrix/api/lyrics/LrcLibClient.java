package com.github.kyanbrix.api.lyrics;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.kyanbrix.component.slashcommand.responses.LrcLibResponse;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Lyrics from lrclib.net. Results are cached by LRCLIB ID so paging through a
 * song's lyrics doesn't fetch them again for every page.
 */
public final class LrcLibClient {

    private static final String BASE_URL = "https://lrclib.net/api/";
    private static final String USER_AGENT = "CaffeinBot/1.0";

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Cache<Integer, LrcLibResponse> BY_ID = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    private LrcLibClient() {}

    /**
     * The best match for a track: the first result with plain lyrics, or an
     * instrumental one if that's all there is. Empty when LRCLIB has nothing.
     */
    public static Optional<LrcLibResponse> search(String track, String artist) throws IOException {
        HttpUrl url = HttpUrl.get(BASE_URL + "search").newBuilder()
                .addQueryParameter("track_name", track)
                .addQueryParameter("artist_name", artist)
                .build();

        LrcLibResponse[] results;
        try (Response response = HTTP.newCall(request(url)).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("LRCLIB search returned HTTP " + response.code());
            }
            results = MAPPER.readValue(response.body().string(), LrcLibResponse[].class);
        }

        Optional<LrcLibResponse> best = Arrays.stream(results)
                .filter(result -> hasLyrics(result) || result.instrumental)
                .min(Comparator.comparing(result -> !hasLyrics(result)));
        best.ifPresent(result -> BY_ID.put(result.id, result));
        return best;
    }

    /** A song by its LRCLIB ID, for paging. Empty if LRCLIB no longer has it. */
    public static Optional<LrcLibResponse> get(int id) throws IOException {
        LrcLibResponse cached = BY_ID.getIfPresent(id);
        if (cached != null) {
            return Optional.of(cached);
        }

        try (Response response = HTTP.newCall(request(HttpUrl.get(BASE_URL + "get/" + id))).execute()) {
            if (response.code() == 404) {
                return Optional.empty();
            }
            if (!response.isSuccessful()) {
                throw new IOException("LRCLIB get returned HTTP " + response.code());
            }
            LrcLibResponse result = MAPPER.readValue(response.body().string(), LrcLibResponse.class);
            BY_ID.put(id, result);
            return Optional.of(result);
        }
    }

    public static boolean hasLyrics(LrcLibResponse result) {
        return result.plainLyrics != null && !result.plainLyrics.isBlank();
    }

    private static Request request(HttpUrl url) {
        return new Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build();
    }
}
