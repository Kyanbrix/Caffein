package com.github.kyanbrix.component.slashcommand.data;

import java.awt.*;

/**
 * Artwork and an accent colour taken from it, for a track's card.
 *
 * @param artworkUrl  null when no source had art for the track
 * @param accentColor never null; falls back to Last.fm red
 */
public record TrackMetadata(String artworkUrl, Color accentColor) {
}
