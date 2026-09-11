package com.github.kyanbrix.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kyanbrix.component.slashcommand.responses.ItunesSearchResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class ItunesSearchTrack {

    private final OkHttpClient client;
    private static final Logger log = LoggerFactory.getLogger(ItunesSearchTrack.class);
    ObjectMapper mapper;

    private final String artistName;
    private final String song;
    private ItunesSearchResponse itunesSearchResponse;


    public ItunesSearchTrack(String artistName, String song) {
        this.artistName = artistName;
        this.song = song;
        client = new OkHttpClient();
        mapper = new ObjectMapper();

    }

    public String searchTrackImage() {

        ObjectMapper mapper = new ObjectMapper();

        String query = URLEncoder.encode(artistName + " " + song, StandardCharsets.UTF_8);
        String url = "https://itunes.apple.com/search?term=" + query + "&entity=song&limit=1";

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {

                ItunesSearchResponse itunesSearchResponse = mapper.readValue(response.body().string(), ItunesSearchResponse.class);


                return itunesSearchResponse.getResults().getFirst().get600x600ArtworkUrl();

            }
        } catch (Exception e) {
            log.error("Error during user top tracks",e);
        }
        return null; // Fallback image if not found
    }

    public String  formatDurationFromTrack() {
        ObjectMapper mapper = new ObjectMapper();

        String query = URLEncoder.encode(artistName + " " + song, StandardCharsets.UTF_8);
        String url = "https://itunes.apple.com/search?term=" + query + "&entity=song&limit=1";

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {

                ItunesSearchResponse itunesSearchResponse = mapper.readValue(response.body().string(), ItunesSearchResponse.class);

                return itunesSearchResponse.getResults().getFirst().getFormattedDuration();

            }
        } catch (Exception e) {
            log.error("Error during user top tracks",e);
        }
        return null;
    }



}
