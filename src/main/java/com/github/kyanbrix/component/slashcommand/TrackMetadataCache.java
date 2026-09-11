package com.github.kyanbrix.component.slashcommand;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.kyanbrix.component.slashcommand.data.TrackMetadata;
import com.github.kyanbrix.utils.ImageColorExtractor;
import com.github.kyanbrix.utils.ItunesSearchTrack;
import com.github.kyanbrix.utils.SpotifySearchAlbumImage;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class TrackMetadataCache {

    private static final Cache<String, TrackMetadata> CACHE = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(5, TimeUnit.DAYS)
            .build();

    // Private constructor prevents class instantiation
    private TrackMetadataCache() {}

    public static TrackMetadata getOrFetchMetadata(String artist, String trackName) {
        String cacheKey = (artist + "-" + trackName).toLowerCase();

        return CACHE.get(cacheKey, key -> {
            SpotifySearchAlbumImage spotifyData = new SpotifySearchAlbumImage(trackName, artist);
            ItunesSearchTrack itunesData = new ItunesSearchTrack(artist, trackName);

            String artworkUrl = spotifyData.getSongImage();

            if (artworkUrl == null && itunesData.searchTrackImage()!= null) {
                artworkUrl = itunesData.searchTrackImage();
            }

            Color accentColor = (artworkUrl != null)
                    ? ImageColorExtractor.getColor(artworkUrl)
                    : new Color(43, 45, 49); // Dark Discord background fallback

            return new TrackMetadata(artworkUrl, itunesData.formatDurationFromTrack(), accentColor);
        });
    }

}
