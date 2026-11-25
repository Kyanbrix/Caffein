package com.github.kyanbrix.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kyanbrix.component.slashcommand.responses.ItunesSearchResponse;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/** Album art from the iTunes Search API, the last fallback when Last.fm and Spotify have none. */
public final class ItunesSearchTrack {

    private static final Logger log = LoggerFactory.getLogger(ItunesSearchTrack.class);

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    private ItunesSearchTrack() {}

    /** 600x600 artwork for the best match, or null if iTunes has none or the request fails. */
    public static String findArtwork(String artistName, String song) {
        HttpUrl url = HttpUrl.get("https://itunes.apple.com/search").newBuilder()
                .addQueryParameter("term", artistName + " " + song)
                .addQueryParameter("entity", "song")
                .addQueryParameter("limit", "1")
                .build();

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("iTunes search for '{} - {}' returned HTTP {}", artistName, song, response.code());
                return null;
            }

            ItunesSearchResponse result = mapper.readValue(response.body().string(), ItunesSearchResponse.class);
            if (result.getResults() == null || result.getResults().isEmpty()) {
                return null;
            }
            return result.getResults().getFirst().get600x600ArtworkUrl();
        } catch (Exception e) {
            log.warn("iTunes search for '{} - {}' failed: {}", artistName, song, e.getMessage());
            return null;
        }
    }
}
