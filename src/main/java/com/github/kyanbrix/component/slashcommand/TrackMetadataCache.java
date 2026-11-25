package com.github.kyanbrix.component.slashcommand;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.kyanbrix.component.slashcommand.data.TrackMetadata;
import com.github.kyanbrix.utils.Constant;
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

    /**
     * Artwork for a track, trying the art Last.fm already sent first, then
     * Spotify, then iTunes. Blocks on network calls on a cache miss.
     *
     * @param lastFmImageUrl the image from the Last.fm response, or null if it had none
     */
    public static TrackMetadata getOrFetchMetadata(String artist, String trackName, String lastFmImageUrl) {
        // Case-sensitive: differently cased names can be different artists
        String cacheKey = artist + "\u0000" + trackName;

        TrackMetadata cached = CACHE.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        String artworkUrl = lastFmImageUrl;
        if (artworkUrl == null) {
            artworkUrl = new SpotifySearchAlbumImage(trackName, artist).getSongImage();
        }
        if (artworkUrl == null) {
            artworkUrl = ItunesSearchTrack.findArtwork(artist, trackName);
        }

        if (artworkUrl == null) {
            // Not cached: the sources may just have been unreachable this time
            return new TrackMetadata(null, Constant.LAST_FM_RED);
        }

        Color accentColor = ImageColorExtractor.getColor(artworkUrl);
        TrackMetadata metadata = new TrackMetadata(artworkUrl, accentColor != null ? accentColor : Constant.LAST_FM_RED);
        CACHE.put(cacheKey, metadata);
        return metadata;
    }

}
