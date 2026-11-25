package com.github.kyanbrix.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.ForbiddenException;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.util.Arrays;

/** Album art from Spotify's search, using the app's token ({@link SpotifyAccessToken}). */
public record SpotifySearchAlbumImage(String name, String artist) {

    private static final Logger log = LoggerFactory.getLogger(SpotifySearchAlbumImage.class);

    /** The album art of the first search result by this artist, or null if there's none or Spotify fails. */
    public String getSongImage() {
        String accessToken = SpotifyAccessToken.get();
        if (accessToken == null) {
            return null;
        }

        try {
            SpotifyApi spotifyApi = new SpotifyApi.Builder().setAccessToken(accessToken).build();
            String query = "track:" + quoteless(name) + " artist:" + quoteless(artist);

            for (Track song : spotifyApi.searchTracks(query).limit(10).build().execute().getItems()) {
                boolean byArtist = Arrays.stream(song.getArtists())
                        .map(ArtistSimplified::getName)
                        .anyMatch(artist::equalsIgnoreCase);
                if (!byArtist) {
                    continue;
                }

                // Spotify lists album images largest first
                for (Image albumImage : song.getAlbum().getImages()) {
                    if (albumImage.getUrl() != null && !albumImage.getUrl().isEmpty()) {
                        return albumImage.getUrl();
                    }
                }
            }
        } catch (ForbiddenException e) {
            SpotifyAccessToken.backOff(e.getMessage());
        } catch (Exception e) {
            log.warn("Spotify search for '{} - {}' failed: {}", artist, name, e.getMessage());
        }

        return null;
    }

    /** Spotify's field filters break on quotes, so they're dropped from the search terms. */
    static String quoteless(String value) {
        return value.replace("\"", "");
    }
}
