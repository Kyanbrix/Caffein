package com.github.kyanbrix.features.leveling.utilities;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class LeaderBoardCard {

    private static final int WIDTH       = 700;
    private static final int HEADER_H    = 88;
    private static final int ROW_H       = 74;
    private static final int AVATAR_SIZE = 50;
    private static final int PADDING     = 16;

    private static final Color BG_DARK       = new Color(0x1C1109);  // dark espresso
    private static final Color BG_ROW_ODD    = new Color(0x231710);  // roast brown
    private static final Color BG_ROW_EVEN   = new Color(0x1C1109);
    private static final Color HEADER_BG     = new Color(0x2C1A0E);  // mocha header

    // Accents
    private static final Color CARAMEL       = new Color(0xD4843A);  // caramel drizzle
    private static final Color CREAM         = new Color(0xF5E6C8);  // latte cream
    private static final Color LATTE         = new Color(0xC8A97A);  // latte tan
    private static final Color MUTED         = new Color(0x8A7060);  // muted mocha
    private static final Color DIVIDER       = new Color(0x3A2518);  // dark divider
    private static final Color BAR_TRACK     = new Color(0x3A2518);
    private static final Color BAR_FILL      = new Color(0xC47C2B);  // caramel fill
    private static final Color BAR_SHINE     = new Color(0xE8A44A);  // highlight

    // Rank medals
    private static final Color GOLD          = new Color(0xF0C040);
    private static final Color SILVER        = new Color(0xC8C8C8);
    private static final Color BRONZE        = new Color(0xC87A32);

    public static void generate(List<Entry> entries, String path) throws IOException {
        byte[] b = generateBytes(entries);
        try (FileOutputStream f = new FileOutputStream(path)) { f.write(b); }
        System.out.println("[Café LB] Saved → " + path);
    }

    private static void drawHeader(Graphics2D g, int entryCount) {
        // Header background with gradient
        GradientPaint headerGrad = new GradientPaint(
                0, 0,            new Color(0x3A2010),
                0, HEADER_H,     new Color(0x231408)
        );
        g.setPaint(headerGrad);
        g.fillRect(0, 0, WIDTH, HEADER_H);

        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(0, HEADER_H - 1, WIDTH, HEADER_H - 1);
        g.setStroke(new BasicStroke(1f));

        // Coffee cup icon (drawn, no external assets)
        drawCoffeeCup(g, 26, 20, 42);

        // Title text
        g.setFont(serifBold(26));
        g.setColor(CREAM);
        g.drawString("Café au Chat Leaderboard", 82, 40);

        // Subtitle
        g.setFont(serif(14));
        g.setColor(LATTE);
        g.drawString("Top " + entryCount + " cafe regulars", 84, 62);

        // Decorative dots right side
        for (int i = 0; i < 5; i++) {
            int dx = WIDTH - 40 + (i % 2) * 6;
            int dy = 24 + i * 9;
            g.setColor(new Color(CARAMEL.getRed(), CARAMEL.getGreen(), CARAMEL.getBlue(), 80 + i * 20));
            g.fillOval(dx, dy, 4, 4);
        }
    }

    private static void drawCoffeeCup(Graphics2D g, int x, int y, int size) {
        int s = size;
        // Steam wisps
        g.setColor(new Color(LATTE.getRed(), LATTE.getGreen(), LATTE.getBlue(), 100));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // left wisp
        CubicCurve2D wL = new CubicCurve2D.Float(
                x + s*0.3f, y,
                x + s*0.15f, y - s*0.25f,
                x + s*0.45f, y - s*0.45f,
                x + s*0.3f,  y - s*0.65f
        );
        g.draw(wL);
        // right wisp
        CubicCurve2D wR = new CubicCurve2D.Float(
                x + s*0.6f, y,
                x + s*0.75f, y - s*0.25f,
                x + s*0.45f, y - s*0.45f,
                x + s*0.6f,  y - s*0.65f
        );
        g.draw(wR);
        g.setStroke(new BasicStroke(1f));

        // Cup body
        int cupY = y + (int)(s * 0.1f);
        int cupW = (int)(s * 0.75f);
        int cupH = (int)(s * 0.55f);
        int cx = x + (s - cupW) / 2;

        GradientPaint cupGrad = new GradientPaint(cx, cupY, CARAMEL, cx + cupW, cupY + cupH, new Color(0x8B4513));
        g.setPaint(cupGrad);
        // Trapezoid cup shape
        int[] xPts = { cx + 4, cx + cupW - 4, cx + cupW - 8, cx + 8 };
        int[] yPts = { cupY, cupY, cupY + cupH, cupY + cupH };
        g.fillPolygon(xPts, yPts, 4);

        // Rim
        g.setColor(CREAM);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(cx, cupY, cx + cupW, cupY);
        g.setStroke(new BasicStroke(1f));

        // Saucer
        g.setColor(LATTE);
        g.fillRoundRect(cx - 4, cupY + cupH + 1, cupW + 8, 5, 4, 4);

        // Handle
        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(2f));
        g.drawArc(cx + cupW - 6, cupY + 4, 14, 16, -90, 180);
        g.setStroke(new BasicStroke(1f));

        // Coffee liquid inside
        g.setColor(new Color(0x3C1A0A));
        g.fillRect(cx + 5, cupY + 3, cupW - 14, 7);
    }

    public static byte[] generateBytes(List<Entry> entries) throws IOException {
        int height = HEADER_H + ROW_H * entries.size() + PADDING;
        BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hq(g);

        // ── Base background ──
        g.setColor(BG_DARK);
        g.fillRect(0, 0, WIDTH, height);

        // ── Subtle wood-grain texture overlay ──
        drawWoodGrain(g, WIDTH, height);

        // ── Header ──
        drawHeader(g, entries.size());

        // ── Rows ──
        for (int i = 0; i < entries.size(); i++) {
            drawRow(g, entries.get(i), i);
        }

        // ── Outer border ──
        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(1, 1, WIDTH - 2, height - 2);
        g.setStroke(new BasicStroke(1f));

        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    private static void hq(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,       RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,  RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,          RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,      RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,  RenderingHints.VALUE_FRACTIONALMETRICS_ON);
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

    private static Color rankColor(int rank) {
        return switch (rank) {
            case 1  -> GOLD;
            case 2  -> SILVER;
            case 3  -> BRONZE;
            default -> CREAM;
        };
    }

    private static Font serifBold(int size) {
        return new Font("Serif", Font.BOLD, size);
    }

    private static Font serif(int size) {
        return new Font("Serif", Font.PLAIN, size);
    }

    private static void drawWoodGrain(Graphics2D g, int w, int h) {
        // Subtle diagonal lines mimicking wood grain
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < w + h; i += 18) {
            int alpha = 6 + (i % 3) * 3;
            g.setColor(new Color(180, 120, 60, alpha));
            g.drawLine(i, 0, 0, i);
            g.drawLine(w, i - w, i, h);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private static BufferedImage placeholder(String username, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hq(g);

        int hash = Math.abs(username.hashCode());
        // Café warm tones for placeholders
        Color[] palettes = {
                new Color(0x7B3F00), new Color(0x9B5A00), new Color(0xB56B2A),
                new Color(0x5C3317), new Color(0x8B4513), new Color(0x6B3A1F)
        };
        g.setColor(palettes[hash % palettes.length]);
        g.fillOval(0, 0, size, size);

        g.setFont(serifBold(20));
        g.setColor(CREAM);
        String init = username.substring(0, 1).toUpperCase();
        FontMetrics fm = g.getFontMetrics();
        g.drawString(init, (size - fm.stringWidth(init)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
        return img;
    }


    private static void drawBar(Graphics2D g, int x, int y, int w, Entry e) {
        float pct = e.xpNeeded > 0 ? Math.min(1f, (float) e.xp / e.xpNeeded) : 0f;
        int filled = (int)(w * pct);
        int bh = 5;

        // Track
        g.setColor(BAR_TRACK);
        g.fillRoundRect(x, y, w, bh, bh, bh);

        if (filled > 0) {
            int fw = Math.max(filled, bh);
            // Gradient fill
            GradientPaint barGrad = new GradientPaint(x, y, BAR_FILL, x + fw, y, BAR_SHINE);
            g.setPaint(barGrad);
            g.fillRoundRect(x, y, fw, bh, bh, bh);

            // Shine line on top
            g.setColor(new Color(255, 255, 255, 40));
            g.fillRoundRect(x, y, fw, 2, 2, 2);
        }
    }

    private static void drawAvatar(Graphics2D g, Entry e, int x, int y) {
        BufferedImage av = null;
        if (e.avatarUrl != null && !e.avatarUrl.isEmpty()) {
            try {
                av = resize(ImageIO.read(new URL(e.avatarUrl)), AVATAR_SIZE, AVATAR_SIZE);
            } catch (Exception ignored) {}
        }
        if (av == null) av = placeholder(e.username, AVATAR_SIZE);

        // Shadow behind avatar
        g.setColor(new Color(0, 0, 0, 80));
        g.fillOval(x + 2, y + 3, AVATAR_SIZE, AVATAR_SIZE);

        g.drawImage(toCircle(av, AVATAR_SIZE), x, y, null);

        // Ring — caramel for all, gold/silver/bronze for top 3
        Color ring = e.rank <= 3 ? rankColor(e.rank) : CARAMEL;
        g.setColor(ring);
        g.setStroke(new BasicStroke(e.rank <= 3 ? 2.5f : 1.5f));
        g.drawOval(x, y, AVATAR_SIZE - 1, AVATAR_SIZE - 1);
        g.setStroke(new BasicStroke(1f));
    }

    private static void drawRow(Graphics2D g, Entry e, int idx) {
        int rowY = HEADER_H + idx * ROW_H;
        boolean isOdd = idx % 2 != 0;

        // Row background
        g.setColor(isOdd ? BG_ROW_ODD : BG_ROW_EVEN);
        g.fillRect(0, rowY, WIDTH, ROW_H);

        // Top-3 warm glow stripe on left
        if (e.rank <= 3) {
            GradientPaint glow = new GradientPaint(
                    0,   rowY, new Color(rankColor(e.rank).getRed(), rankColor(e.rank).getGreen(), rankColor(e.rank).getBlue(), 180),
                    120, rowY, new Color(0, 0, 0, 0)
            );
            g.setPaint(glow);
            g.fillRect(0, rowY, 120, ROW_H);

            // Left accent bar
            g.setColor(rankColor(e.rank));
            g.fillRect(0, rowY + 8, 3, ROW_H - 16);
        }

        // Divider
        if (idx > 0) {
            g.setColor(DIVIDER);
            g.fillRect(0, rowY, WIDTH, 1);
        }

        // Avatar
        int avX = 14, avY = rowY + (ROW_H - AVATAR_SIZE) / 2;
        drawAvatar(g, e, avX, avY);

        // ── Text block ──
        int textX = avX + AVATAR_SIZE + 14;
        int textY = rowY + 28;

        // Rank
        g.setFont(serifBold(18));
        g.setColor(rankColor(e.rank));
        String rankStr = "#" + e.rank;
        g.drawString(rankStr, textX, textY);
        int rW = g.getFontMetrics().stringWidth(rankStr);

        // Dot
        g.setColor(MUTED);
        g.setFont(serif(18));
        String dot = " • ";
        g.drawString(dot, textX + rW, textY);
        int dW = g.getFontMetrics().stringWidth(dot);

        // Username
        g.setColor(CREAM);
        g.setFont(serifBold(18));
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

        // XP label (right side)
        String xpLabel = e.xp + " / " + e.xpNeeded + " xp";
        g.setFont(serif(11));
        g.setColor(MUTED);
        int lW = g.getFontMetrics().stringWidth(xpLabel);
        g.drawString(xpLabel, WIDTH - lW - 14, rowY + ROW_H - 16);

        // Progress bar
        int barX = textX;
        int barY = rowY + ROW_H - 14;
        int barW = WIDTH - barX - 14;
        drawBar(g, barX, barY, barW, e);
    }

}
