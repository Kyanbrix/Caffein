package com.github.kyanbrix.features.leveling.utilities;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

public class LevelUpCard {
    private static final int WIDTH     = 700;
    private static final int HEIGHT    = 220;
    private static final int BANNER_H  = 54;
    private static final int AV_SIZE   = 100;
    private static final int AV_X      = 28;
    private static final int AV_Y      = BANNER_H + (HEIGHT - BANNER_H - AV_SIZE) / 2;
    private static final int TEXT_X    = AV_X + AV_SIZE + 22;
    private static final int CORNER    = 20;
    private static final int BAR_H     = 18;

    // ── Café Palette ───────────────────────────────────────────────────
    private static final Color BG_LEFT      = new Color(0x1C1109);
    private static final Color BG_MID       = new Color(0x261A0E);
    private static final Color CREAM        = new Color(0xF5E6C8);
    private static final Color LATTE        = new Color(0xC8A97A);
    private static final Color CARAMEL      = new Color(0xD4843A);
    private static final Color GOLD         = new Color(0xF0C040);
    private static final Color MUTED        = new Color(0x8A7060);
    private static final Color TEAL         = new Color(0x2EC4B6);
    private static final Color TEAL_DARK    = new Color(0x1C5A54);
    private static final Color BAR_TRACK    = new Color(0x2A1E16);
    private static final Color BAR_FILL_L   = new Color(0x2EC4B6);
    private static final Color BAR_FILL_R   = new Color(0x6EDDD6);
    private static final Color BANNER_TOP   = new Color(0x2C1A0E);
    private static final Color BORDER_COLOR = new Color(0xD4843A);

    public static byte[] generate(LevelUpData d) throws IOException {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hq(g);

        // Clip everything to rounded rect
        RoundRectangle2D card = new RoundRectangle2D.Float(0, 0, WIDTH, HEIGHT, CORNER, CORNER);
        g.setClip(card);

        drawBackground(g);
        drawWoodGrain(g);
        drawBanner(g);
        drawAccentPanel(g);
        drawAvatar(g, d);
        drawText(g, d);
        drawBar(g, d);

        // Remove clip before drawing border
        g.setClip(null);
        drawBorder(g);

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();

    }

    private static void drawBackground(Graphics2D g) {
        GradientPaint bg = new GradientPaint(0, 0, BG_LEFT, WIDTH, 0, BG_MID);
        g.setPaint(bg);
        g.fillRect(0, 0, WIDTH, HEIGHT);
    }

    private static void drawWoodGrain(Graphics2D g) {
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < WIDTH + HEIGHT; i += 20) {
            int alpha = 5 + (i % 3) * 2;
            g.setColor(new Color(180, 120, 60, alpha));
            g.drawLine(i, 0, 0, i);
            g.drawLine(WIDTH, i - WIDTH, i, HEIGHT);
        }
    }

    private static void drawBanner(Graphics2D g) {
        // Banner gradient top → bottom
        GradientPaint bannerGrad = new GradientPaint(0, 0, BANNER_TOP, 0, BANNER_H, BG_LEFT);
        g.setPaint(bannerGrad);
        g.fillRect(0, 0, WIDTH, BANNER_H);

        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(0, BANNER_H - 1, WIDTH, BANNER_H - 1);
        g.setStroke(new BasicStroke(1f));

        // Small coffee cup icon
        drawCoffeeCup(g, 28, 14, 22);

        // "✦  LEVEL UP!  ✦" title
        g.setFont(serifBold(24));
        g.setColor(GOLD);
        g.drawString("\u2736  LEVEL UP!  \u2736", 64, 37);

        // Decorative dots on right
        for (int i = 0; i < 6; i++) {
            int dx = WIDTH - 28 + (i % 2) * 8;
            int dy = 10 + i * 7;
            int alpha = 80 + i * 20;
            g.setColor(new Color(CARAMEL.getRed(), CARAMEL.getGreen(), CARAMEL.getBlue(), alpha));
            g.fillOval(dx - 3, dy - 3, 6, 6);
        }
    }

    private static void drawAccentPanel(Graphics2D g) {
        for (int x = WIDTH - 200; x < WIDTH; x++) {
            float t     = (x - (WIDTH - 200)) / 200f;
            int   alpha = (int)(t * 55);
            g.setColor(new Color(TEAL_DARK.getRed(), TEAL_DARK.getGreen(), TEAL_DARK.getBlue(), alpha));
            g.drawLine(x, BANNER_H, x, HEIGHT);
        }
    }


    private static void drawAvatar(Graphics2D g, LevelUpData d) {
        BufferedImage av = null;
        if (d.avatarUrl != null && !d.avatarUrl.isEmpty()) {
            try {
                av = resize(ImageIO.read(URI.create(d.avatarUrl).toURL()), AV_SIZE, AV_SIZE);
            } catch (Exception ignored) {}
        }
        if (av == null) av = placeholder(d.username, AV_SIZE);

        // Drop shadow
        g.setColor(new Color(0, 0, 0, 90));
        g.fillOval(AV_X + 3, AV_Y + 4, AV_SIZE, AV_SIZE);

        // Circular avatar
        g.drawImage(toCircle(av, AV_SIZE), AV_X, AV_Y, null);

        // Caramel ring
        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(3f));
        g.drawOval(AV_X, AV_Y, AV_SIZE - 1, AV_SIZE - 1);
        g.setStroke(new BasicStroke(1f));

        // Teal level badge at bottom-right of avatar
        int bx = AV_X + AV_SIZE - 8;
        int by = AV_Y + AV_SIZE - 8;
        g.setColor(TEAL_DARK);
        g.fillOval(bx - 16, by - 16, 32, 32);
        g.setColor(TEAL);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(bx - 16, by - 16, 32, 32);
        g.setStroke(new BasicStroke(1f));
        g.setFont(serifBold(14));
        g.setColor(CREAM);
        String lvlBadge = String.valueOf(d.newLevel);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(lvlBadge, bx - fm.stringWidth(lvlBadge) / 2, by + 5);
    }

    // ── Text ──────────────────────────────────────────────────────────

    private static void drawText(Graphics2D g, LevelUpData d) {
        int ty = BANNER_H + 28;

        // Username
        g.setFont(serifBold(26));
        g.setColor(CREAM);
        g.drawString("@" + d.username, TEXT_X, ty);

        // Level transition row:  Level  17 → 18  [Rank #1]
        int ty2 = ty + 42;

        // "Level" label
        g.setFont(serif(15));
        g.setColor(LATTE);
        g.drawString("Level", TEXT_X, ty2);
        int lw = g.getFontMetrics().stringWidth("Level");

        // Old level (muted)
        g.setFont(serifBold(18));
        g.setColor(MUTED);
        String oldStr = String.valueOf(d.oldLevel);
        g.drawString(oldStr, TEXT_X + lw + 10, ty2);
        int ow = g.getFontMetrics().stringWidth(oldStr);

        // Arrow
        g.setFont(serifBold(18));
        g.setColor(CARAMEL);
        g.drawString("\u2192", TEXT_X + lw + 10 + ow + 10, ty2);
        int arw = g.getFontMetrics().stringWidth("\u2192");

        // New level (gold, larger)
        g.setFont(serifBold(24));
        g.setColor(GOLD);
        String newStr = String.valueOf(d.newLevel);
        g.drawString(newStr, TEXT_X + lw + 10 + ow + 10 + arw + 10, ty2 + 2);
        int nw = g.getFontMetrics().stringWidth(newStr);

        // Rank pill
        int pillX = TEXT_X + lw + 10 + ow + 10 + arw + 10 + nw + 20;
        String pillText = "  Rank #" + d.rank + "  ";
        g.setFont(serifBold(13));
        int pillW = g.getFontMetrics().stringWidth(pillText) + 4;
        int pillH = 22;
        g.setColor(TEAL_DARK);
        g.fillRoundRect(pillX, ty2 - 16, pillW, pillH, 12, 12);
        g.setColor(TEAL);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(pillX, ty2 - 16, pillW, pillH, 12, 12);
        g.setColor(TEAL);
        g.drawString(pillText, pillX + 2, ty2 - 1);

        // XP label
        int ty3 = ty2 + 28;
        g.setFont(serif(14));
        g.setColor(LATTE);
        g.drawString("XP:  " + formatXP(d.xpProgress) + " / " + formatXP(d.xpNeeded), TEXT_X, ty3);

        // XP % right-aligned above bar
        float pct = d.xpNeeded > 0 ? (float) d.xpProgress / d.xpNeeded * 100f : 0f;
        String pctStr = String.format("%.1f%%", pct);
        g.setFont(serifBold(18));
        g.setColor(MUTED);
        int pw = g.getFontMetrics().stringWidth(pctStr);
        int barW = WIDTH - TEXT_X - 28;
        g.drawString(pctStr, TEXT_X + barW - pw, HEIGHT - BAR_H - 25);
    }

    private static void drawBar(Graphics2D g, LevelUpData d) {
        int barX  = TEXT_X;
        int barY  = HEIGHT - BAR_H - 16;
        int barW  = WIDTH - TEXT_X - 28;
        float pct = d.xpNeeded > 0 ? Math.min(1f, (float) d.xpProgress / d.xpNeeded) : 0f;
        int filled = Math.max((int)(barW * pct), BAR_H);

        // Track
        g.setColor(BAR_TRACK);
        g.fillRoundRect(barX, barY, barW, BAR_H, BAR_H, BAR_H);

        // Gradient fill
        GradientPaint fill = new GradientPaint(barX, barY, BAR_FILL_L, barX + filled, barY, BAR_FILL_R);
        g.setPaint(fill);
        g.fillRoundRect(barX, barY, filled, BAR_H, BAR_H, BAR_H);

        // Shine
        g.setColor(new Color(255, 255, 255, 35));
        g.fillRoundRect(barX, barY, filled, BAR_H / 2, BAR_H / 2, BAR_H / 2);

        // Glowing tip
        if (filled > BAR_H) {
            RadialGradientPaint tip = new RadialGradientPaint(
                    barX + filled, barY + BAR_H / 2f, BAR_H * 1.4f,
                    new float[]{ 0f, 1f },
                    new Color[]{ new Color(0x6EDDD6, true), new Color(0x2EC4B6) }
            );
            g.setPaint(tip);
            g.fillOval(barX + filled - BAR_H, barY - BAR_H / 2, BAR_H * 2, BAR_H * 2);
        }

        // Bar outline
        g.setColor(new Color(255, 255, 255, 18));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(barX, barY, barW, BAR_H, BAR_H, BAR_H);
    }


    private static void drawCoffeeCup(Graphics2D g, int x, int y, int s) {
        // Steam
        g.setColor(new Color(LATTE.getRed(), LATTE.getGreen(), LATTE.getBlue(), 90));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(x + 2, y - 8, 4, 6, 180, 180);
        g.drawArc(x + 9, y - 8, 4, 6, 180, 180);
        g.setStroke(new BasicStroke(1f));
        // Cup body
        int[] cx = { x + 3, x + s - 3, x + s, x };
        int[] cy = { y, y, y + s, y + s };
        GradientPaint cupG = new GradientPaint(x, y, CARAMEL, x + s, y + s, new Color(0x8B4513));
        g.setPaint(cupG);
        g.fillPolygon(cx, cy, 4);
        // Rim
        g.setColor(CREAM);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x, y, x + s, y);
        g.setStroke(new BasicStroke(1f));
        // Liquid
        g.setColor(new Color(40, 18, 8));
        g.fillRect(x + 3, y + 3, s - 9, 6);
        // Handle
        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(2f));
        g.drawArc(x + s - 5, y + 3, 10, 12, -90, 180);
        g.setStroke(new BasicStroke(1f));
        // Saucer
        g.setColor(LATTE);
        g.fillRoundRect(x - 3, y + s + 1, s + 6, 4, 3, 3);
    }


    private static void drawBorder(Graphics2D g) {
        g.setColor(BORDER_COLOR);
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Float(1, 1, WIDTH - 2, HEIGHT - 2, CORNER, CORNER));
        g.setStroke(new BasicStroke(1f));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static String formatXP(int xp) {
        return xp >= 1000 ? String.format("%.1fK", xp / 1000.0) : String.valueOf(xp);
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
        g.setFont(serifBold(34));
        g.setColor(CREAM);
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
