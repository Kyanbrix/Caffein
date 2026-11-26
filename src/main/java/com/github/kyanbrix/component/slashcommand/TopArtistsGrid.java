package com.github.kyanbrix.component.slashcommand;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Draws the /fm top artists card: a grid of artist photos, each with the artist's
 * name and plays along the bottom. The grid's shape follows the number of artists
 * (see {@link #columns}), and a short last row is centred.
 */
final class TopArtistsGrid {

    /** One artist on the card; {@code photo} is null when no source had one. The rank isn't drawn, only used in alt text. */
    record Tile(int rank, String name, long plays, BufferedImage photo) {}

    private static final int TILE = 300;
    private static final int GUTTER = 3;
    private static final int PADDING = 14;

    private static final Color BACKGROUND = new Color(0x111214);
    private static final Color PLAYS_COLOR = new Color(0xD4D4D8);

    private static final int NAME_MAX_SIZE = 26;
    private static final int NAME_MIN_SIZE = 18;
    private static final Font PLAYS_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 17);

    private TopArtistsGrid() {}

    /**
     * How many tiles per row: a single row up to 3, then 2×2, 3 across up to 9
     * (3×3 at most), and 4 across beyond that (4×3 for the maximum of 12).
     */
    static int columns(int tiles) {
        if (tiles <= 3) return Math.max(1, tiles);
        if (tiles == 4) return 2;
        if (tiles <= 9) return 3;
        return 4;
    }

    /** The card as a JPEG, sized to fit the tiles. */
    static byte[] render(List<Tile> tiles) throws IOException {
        int columns = columns(tiles.size());
        int rows = Math.max(1, Math.ceilDiv(tiles.size(), columns));
        int width = columns * TILE + (columns - 1) * GUTTER;
        int height = rows * TILE + (rows - 1) * GUTTER;

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            g.setColor(BACKGROUND);
            g.fillRect(0, 0, width, height);

            for (int i = 0; i < tiles.size(); i++) {
                int row = i / columns;
                // A short last row is centred rather than left with a gap on the right
                int inRow = Math.min(columns, tiles.size() - row * columns);
                int offset = (columns - inRow) * (TILE + GUTTER) / 2;
                int x = offset + (i % columns) * (TILE + GUTTER);
                int y = row * (TILE + GUTTER);
                drawTile((Graphics2D) g.create(x, y, TILE, TILE), tiles.get(i));
            }
        } finally {
            g.dispose();
        }

        return toJpeg(canvas);
    }

    private static void drawTile(Graphics2D g, Tile tile) {
        try {
            if (tile.photo() != null) {
                g.drawImage(squareThumbnail(tile.photo()), 0, 0, null);
            } else {
                drawPlaceholder(g, tile.name());
            }

            // Darken the bottom so white text reads on any photo
            g.setPaint(new GradientPaint(0, TILE * 0.45f, new Color(0, 0, 0, 0), 0, TILE, new Color(0, 0, 0, 217)));
            g.fillRect(0, 0, TILE, TILE);

            String plays = String.format(Locale.US, "%,d %s", tile.plays(), tile.plays() == 1 ? "play" : "plays");
            g.setFont(PLAYS_FONT);
            FontMetrics playsMetrics = g.getFontMetrics();
            int playsBaseline = TILE - PADDING - playsMetrics.getDescent();
            drawShadowedText(g, plays, PADDING, playsBaseline, PLAYS_COLOR);

            int maxWidth = TILE - 2 * PADDING;
            Font nameFont = fittingFont(g, tile.name(), maxWidth);
            g.setFont(nameFont);
            String name = ellipsize(g.getFontMetrics(), tile.name(), maxWidth);
            int nameBaseline = playsBaseline - playsMetrics.getAscent() - 4;
            drawShadowedText(g, name, PADDING, nameBaseline, Color.WHITE);
        } finally {
            g.dispose();
        }
    }

    /**
     * Crops the photo to a square, biased upwards since faces tend to be in the
     * top half, then scales it down in halving steps, which looks far sharper
     * than one big bicubic jump from 1000px to 300px.
     */
    private static BufferedImage squareThumbnail(BufferedImage photo) {
        int side = Math.min(photo.getWidth(), photo.getHeight());
        int sx = (photo.getWidth() - side) / 2;
        int sy = (photo.getHeight() - side) / 4;
        BufferedImage current = copy(photo, sx, sy, side, side, side);

        while (current.getWidth() / 2 >= TILE) {
            int next = current.getWidth() / 2;
            current = copy(current, 0, 0, current.getWidth(), current.getHeight(), next);
        }
        return current.getWidth() == TILE ? current : copy(current, 0, 0, current.getWidth(), current.getHeight(), TILE);
    }

    private static BufferedImage copy(BufferedImage source, int sx, int sy, int sw, int sh, int size) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, size, size, sx, sy, sx + sw, sy + sh, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** A diagonal gradient picked from the name's hash, with the artist's initials. */
    private static void drawPlaceholder(Graphics2D g, String name) {
        float hue = (name.toLowerCase(Locale.ROOT).hashCode() & 0xFFFF) / (float) 0xFFFF;
        g.setPaint(new GradientPaint(0, 0, Color.getHSBColor(hue, 0.55f, 0.55f),
                TILE, TILE, Color.getHSBColor((hue + 0.12f) % 1f, 0.65f, 0.25f)));
        g.fillRect(0, 0, TILE, TILE);

        String initials = initials(name);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 96));
        FontMetrics metrics = g.getFontMetrics();
        int x = (TILE - metrics.stringWidth(initials)) / 2;
        // A little above centre, so the initials clear the name at the bottom
        int y = (TILE - metrics.getHeight()) / 2 + metrics.getAscent() - 24;
        g.setColor(new Color(255, 255, 255, 150));
        g.drawString(initials, x, y);
    }

    private static void drawShadowedText(Graphics2D g, String text, int x, int baseline, Color color) {
        g.setColor(new Color(0, 0, 0, 140));
        g.drawString(text, x + 1, baseline + 2);
        g.setColor(color);
        g.drawString(text, x, baseline);
    }

    /** The largest name size from 26px down to 18px that fits; 18px if none does (it's ellipsized then). */
    private static Font fittingFont(Graphics2D g, String text, int maxWidth) {
        for (int size = NAME_MAX_SIZE; size > NAME_MIN_SIZE; size -= 2) {
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, size);
            if (g.getFontMetrics(font).stringWidth(text) <= maxWidth) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, NAME_MIN_SIZE);
    }

    private static String ellipsize(FontMetrics metrics, String text, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + "…") > maxWidth) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end).stripTrailing() + "…";
    }

    /** Up to two initials from the first two words, e.g. "Daft Punk" to "DP", "ABBA" to "A". */
    private static String initials(String name) {
        StringBuilder initials = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            initials.appendCodePoint(Character.toUpperCase(word.codePointAt(0)));
            if (initials.codePointCount(0, initials.length()) == 2) break;
        }
        return initials.isEmpty() ? "?" : initials.toString();
    }

    private static byte[] toJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(0.9f);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // In memory: ImageIO.createImageOutputStream would buffer through a temp file on disk
        try (ImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
