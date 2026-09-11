package com.github.kyanbrix.component.slashcommand.data;

import java.awt.*;

public class TrackMetadata {

    private final String artworkUrl;
    private final String durationMs;
    private final Color accentColor;

    public TrackMetadata(String artworkUrl, String durationMs, Color accentColor) {
        this.artworkUrl = artworkUrl;
        this.durationMs = durationMs;
        this.accentColor = accentColor;
    }

    public String getArtworkUrl() { return artworkUrl; }
    public String getDurationMs() { return durationMs; }
    public Color getAccentColor() { return accentColor; }


}
