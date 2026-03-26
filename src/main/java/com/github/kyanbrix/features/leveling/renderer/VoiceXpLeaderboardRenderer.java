package com.github.kyanbrix.features.leveling.renderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;

public class VoiceXpLeaderboardRenderer {
    private static final int WIDTH       = 700;
    private static final int HEADER_H    = 88;
    private static final int ROW_H       = 74;
    private static final int AVATAR_SIZE = 50;
    private static final int PADDING     = 14;

    // ── Café Palette ───────────────────────────────────────────────────
    private static final Color BG_DARK     = new Color(0x1C1109);
    private static final Color ROW_ODD     = new Color(0x23160C);
    private static final Color ROW_EVEN    = new Color(0x1C1109);
    private static final Color DIVIDER     = new Color(0x3A2514);
    private static final Color CREAM       = new Color(0xF5E6C8);
    private static final Color LATTE       = new Color(0xC8A97A);
    private static final Color CARAMEL     = new Color(0xD4843A);
    private static final Color MUTED       = new Color(0x8A7060);
    private static final Color GOLD        = new Color(0xF0C040);
    private static final Color SILVER      = new Color(0xC8C8C8);
    private static final Color BRONZE      = new Color(0xC87A32);

    // Voice-specific teal accent (distinct from chat amber)
    private static final Color VOICE_ACCENT = new Color(0x2EB48C);
    private static final Color VOICE_DARK   = new Color(0x0A4132);


    public static class Entry {
        public final int    rank;
        public final String username;
        public final int    level;
        public final int    voiceXP;
        public final String avatarUrl;

        public Entry(int rank, String username, int level, int voiceXP, String avatarUrl) {
            this.rank      = rank;
            this.username  = username;
            this.level     = level;
            this.voiceXP   = voiceXP;
            this.avatarUrl = avatarUrl;
        }
    }

    public static void generate(List<Entry> entries, String path) throws IOException {
        byte[] b = generateBytes(entries);
        try (FileOutputStream f = new FileOutputStream(path)) { f.write(b); }
    }

    public static byte[] generateBytes(List<Entry> entries) throws IOException {
        int height = HEADER_H + ROW_H * entries.size() + PADDING;
        BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hq(g);

        g.setColor(BG_DARK);
        g.fillRect(0, 0, WIDTH, height);

        drawWoodGrain(g, WIDTH, height);
        drawHeader(g, entries.size());

        for (int i = 0; i < entries.size(); i++) {
            drawRow(g, entries.get(i), i);
        }

        // Outer border
        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(1, 1, WIDTH - 2, height - 2);
        g.setStroke(new BasicStroke(1f));

        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    private static void drawHeader(Graphics2D g, int count) {
        GradientPaint grad = new GradientPaint(0, 0, new Color(0x37230F), 0, HEADER_H, BG_DARK);
        g.setPaint(grad);
        g.fillRect(0, 0, WIDTH, HEADER_H);

        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(0, HEADER_H - 1, WIDTH, HEADER_H - 1);
        g.setStroke(new BasicStroke(1f));

        // Headphone icon
        drawHeadphone(g, 22, 20, 34);

        // Title
        g.setFont(serifBold(22));
        g.setColor(CREAM);
        g.drawString("Voice Leaderboard", 70, 38);

        // Subtitle
        g.setFont(serif(14));
        g.setColor(LATTE);
        g.drawString("Top " + count + " most active voice users  \uD83C\uDFA7", 72, 62);

        // Deco dots
        for (int i = 0; i < 5; i++) {
            int dx = WIDTH - 38 + (i % 2) * 7;
            int dy = 16 + i * 10;
            g.setColor(new Color(CARAMEL.getRed(), CARAMEL.getGreen(), CARAMEL.getBlue(), 80 + i * 20));
            g.fillOval(dx - 3, dy - 3, 6, 6);
        }
    }

    private static void drawHeadphone(Graphics2D g, int x, int y, int sz) {
        int cx = x + sz / 2;
        int cy = y + sz / 2;

        // Headband arc
        g.setColor(VOICE_ACCENT);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(x, y, sz, sz, 200, 140);
        g.setStroke(new BasicStroke(1f));

        // Left ear cup
        g.setColor(VOICE_ACCENT);
        g.fillRoundRect(x - 2, cy - 4, 11, 14, 4, 4);

        // Right ear cup
        g.fillRoundRect(x + sz - 9, cy - 4, 11, 14, 4, 4);

        // Inner ear highlight
        g.setColor(VOICE_DARK);
        g.fillRoundRect(x, cy - 2, 7, 10, 3, 3);
        g.fillRoundRect(x + sz - 7, cy - 2, 7, 10, 3, 3);
    }

    private static void drawRow(Graphics2D g, Entry e, int idx) {
        int rowY = HEADER_H + idx * ROW_H;

        g.setColor(idx % 2 == 0 ? ROW_EVEN : ROW_ODD);
        g.fillRect(0, rowY, WIDTH, ROW_H);

        if (idx > 0) {
            g.setColor(DIVIDER);
            g.fillRect(0, rowY, WIDTH, 1);
        }

        // Top-3 glow + accent bar
        if (e.rank <= 3) {
            Color rc = rankColor(e.rank);
            GradientPaint glow = new GradientPaint(
                    0, rowY, new Color(rc.getRed(), rc.getGreen(), rc.getBlue(), 45),
                    120, rowY, new Color(0, 0, 0, 0)
            );
            g.setPaint(glow);
            g.fillRect(0, rowY, 120, ROW_H);
            g.setColor(rc);
            g.fillRect(0, rowY + 8, 3, ROW_H - 16);
        }

        // Avatar
        int avX = 14, avY = rowY + (ROW_H - AVATAR_SIZE) / 2;
        drawAvatar(g, e, avX, avY);

        // ── Text ──
        int textX = avX + AVATAR_SIZE + 14;
        int textY = rowY + 28;

        // Rank
        g.setFont(serifBold(17));
        g.setColor(rankColor(e.rank));
        String rankStr = "#" + e.rank;
        g.drawString(rankStr, textX, textY);
        int rW = g.getFontMetrics().stringWidth(rankStr);

        // Dot
        g.setColor(MUTED);
        g.setFont(serif(17));
        String dot = " • ";
        g.drawString(dot, textX + rW, textY);
        int dW = g.getFontMetrics().stringWidth(dot);

        // Username
        g.setColor(CREAM);
        g.setFont(serifBold(17));
        String uname = "@" + e.username;
        g.drawString(uname, textX + rW + dW, textY);
        int uW = g.getFontMetrics().stringWidth(uname);

        // Dot + Level
        g.setColor(MUTED);
        g.setFont(serif(15));
        g.drawString(dot, textX + rW + dW + uW, textY);
        int d2W = g.getFontMetrics().stringWidth(dot);

        g.setColor(LATTE);
        g.setFont(serifBold(15));
        g.drawString("LVL: " + e.level, textX + rW + dW + uW + d2W, textY);

        // Voice XP badge — teal pill
        String badge = "  " + formatXP(e.voiceXP) + " XP  ";
        g.setFont(serif(11));
        int bW = g.getFontMetrics().stringWidth(badge) + 4;
        int bH = 18;
        int bX = WIDTH - bW - 14;
        int bY = rowY + 8;
        g.setColor(VOICE_DARK);
        g.fillRoundRect(bX, bY, bW, bH, 8, 8);
        g.setColor(VOICE_ACCENT);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(bX, bY, bW, bH, 8, 8);
        g.drawString(badge, bX + 2, bY + 13);
    }


    private static void drawAvatar(Graphics2D g, Entry e, int x, int y) {
        BufferedImage av = null;
        if (e.avatarUrl != null && !e.avatarUrl.isEmpty()) {
            try {
                av = resize(ImageIO.read(URI.create(e.avatarUrl).toURL()), AVATAR_SIZE, AVATAR_SIZE);
            } catch (Exception ignored) {}
        }
        if (av == null) av = placeholder(e.username, AVATAR_SIZE);

        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(x + 2, y + 3, AVATAR_SIZE, AVATAR_SIZE);

        g.drawImage(toCircle(av, AVATAR_SIZE), x, y, null);

        Color ring = e.rank <= 3 ? rankColor(e.rank) : CARAMEL;
        g.setColor(ring);
        g.setStroke(new BasicStroke(e.rank <= 3 ? 2.5f : 1.5f));
        g.drawOval(x, y, AVATAR_SIZE - 1, AVATAR_SIZE - 1);
        g.setStroke(new BasicStroke(1f));
    }

    private static void drawWoodGrain(Graphics2D g, int w, int h) {
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < w + h; i += 20) {
            int alpha = 5 + (i % 3) * 2;
            g.setColor(new Color(180, 120, 60, alpha));
            g.drawLine(i, 0, 0, i);
            g.drawLine(w, i - w, i, h);
        }
    }
    private static String formatXP(int xp) {
        return xp >= 1000 ? String.format("%.1fK", xp / 1000.0) : String.valueOf(xp);
    }

    private static Color rankColor(int rank) {
        return switch (rank) {
            case 1  -> GOLD;
            case 2  -> SILVER;
            case 3  -> BRONZE;
            default -> CREAM;
        };
    }

    private static BufferedImage placeholder(String username, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hq(g);
        Color[] palette = {
                new Color(0x7B3F00), new Color(0x9B5A00), new Color(0xB56B2A),
                new Color(0x5C3317), new Color(0x8B4513), new Color(0x6B3A1F)
        };
        g.setColor(palette[Math.abs(username.hashCode()) % palette.length]);
        g.fillOval(0, 0, size, size);
        g.setFont(serifBold(18));
        g.setColor(new Color(0xF5E6C8));
        String init = username.substring(0, 1).toUpperCase();
        FontMetrics fm = g.getFontMetrics();
        g.drawString(init, (size - fm.stringWidth(init)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
        return img;
    }

    private static BufferedImage toCircle(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        hq(g);
        g.setClip(new Ellipse2D.Float(0, 0, size, size));
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }
    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        hq(g);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static Font serifBold(int size) { return new Font("Serif", Font.BOLD, size);  }
    private static Font serif(int size)     { return new Font("Serif", Font.PLAIN, size); }

    private static void hq(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

}
