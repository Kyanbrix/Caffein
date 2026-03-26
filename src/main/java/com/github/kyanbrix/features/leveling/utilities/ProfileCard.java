package com.github.kyanbrix.features.leveling.utilities;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

public class ProfileCard {

    private static final int WIDTH   = 700;
    private static final int HEIGHT  = 140;
    private static final int AV_SIZE = 90;
    private static final int AV_X    = 18;
    private static final int AV_Y    = (HEIGHT - AV_SIZE) / 2;
    private static final int TEXT_X  = AV_X + AV_SIZE + 20;
    private static final int BAR_H   = 14;

    // ── Café Palette ───────────────────────────────────────────────────
    private static final Color BG_LEFT    = new Color(0x1C1109);
    private static final Color BG_RIGHT   = new Color(0x2C1A0E);
    private static final Color CREAM      = new Color(0xF5E6C8);
    private static final Color CARAMEL    = new Color(0xD4843A);
    private static final Color GOLD       = new Color(0xF0C040);
    private static final Color BAR_FILL_L = new Color(0xD4843A);
    private static final Color BAR_FILL_R = new Color(0xF0C040);



    public static byte[] generate(Data d) throws IOException {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hq(g);

        drawBackground(g);
        drawAvatar(g, d);
        drawText(g, d);
        drawBar(g, d);
        drawBorder(g);

        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }


    private static void drawBackground(Graphics2D g) {
        // Solid espresso background — no accent panel
        g.setColor(BG_LEFT);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Wood grain texture
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < WIDTH + HEIGHT; i += 22) {
            int alpha = 5 + (i % 3) * 2;
            g.setColor(new Color(180, 120, 60, alpha));
            g.drawLine(i, 0, 0, i);
            g.drawLine(WIDTH, i - WIDTH, i, HEIGHT);
        }
    }

    private static void drawAvatar(Graphics2D g, Data d) {
        BufferedImage av = null;
        if (d.avatarUrl != null && !d.avatarUrl.isEmpty()) {
            try {
                av = resize(ImageIO.read(URI.create(d.avatarUrl).toURL()), AV_SIZE, AV_SIZE);
            } catch (Exception ignored) {}
        }
        if (av == null) av = placeholder(d.username, AV_SIZE);

        // Drop shadow
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(AV_X + 2, AV_Y + 3, AV_SIZE, AV_SIZE);

        // Circular avatar
        g.drawImage(toCircle(av, AV_SIZE), AV_X, AV_Y, null);

        // Caramel ring
        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(3f));
        g.drawOval(AV_X, AV_Y, AV_SIZE - 1, AV_SIZE - 1);
        g.setStroke(new BasicStroke(1f));
    }

    private static void drawText(Graphics2D g, Data d) {
        // Username
        g.setFont(serifBold(28));
        g.setColor(CREAM);
        g.drawString("@" + d.username, TEXT_X, 34);

        // Stats row — fixed Y anchor
        int statY  = 72;
        int cursor = TEXT_X;

        cursor = drawStat(g, "Level:", String.valueOf(d.level), cursor, statY);
        cursor = drawStat(g, "XP:", formatXP(d.xpProgress) + " / " + formatXP(d.xpNeeded), cursor, statY);
        drawStat(g, "Rank:", "#" + d.rank, cursor, statY);
    }

    private static int drawStat(Graphics2D g, String label, String value, int x, int y) {
        g.setFont(serifBold(17));
        g.setColor(CARAMEL);
        g.drawString(label, x, y);
        int lw = g.getFontMetrics().stringWidth(label);

        g.setColor(CREAM);
        g.drawString(value, x + lw + 5, y);
        int vw = g.getFontMetrics().stringWidth(value);

        return x + lw + 5 + vw + 28;
    }

    private static void drawBar(Graphics2D g, Data d) {
        int   barX  = TEXT_X;
        int   barY  = HEIGHT - BAR_H - 16;
        int   barW  = WIDTH - TEXT_X - 20;
        float pct   = d.xpNeeded > 0 ? Math.min(1f, (float) d.xpProgress / d.xpNeeded) : 0f;
        int   filled = Math.max((int)(barW * pct), BAR_H);

        // Track
        g.setColor(new Color(55, 35, 18));
        g.fillRoundRect(barX, barY, barW, BAR_H, BAR_H, BAR_H);

        // Caramel → gold gradient fill
        GradientPaint fill = new GradientPaint(barX, barY, BAR_FILL_L, barX + filled, barY, BAR_FILL_R);
        g.setPaint(fill);
        g.fillRoundRect(barX, barY, filled, BAR_H, BAR_H, BAR_H);

        // Shine
        g.setColor(new Color(255, 255, 255, 30));
        g.fillRoundRect(barX, barY, filled, BAR_H / 2 + 1, BAR_H / 2, BAR_H / 2);

        // Subtle outline
        g.setColor(new Color(255, 255, 255, 15));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(barX, barY, barW, BAR_H, BAR_H, BAR_H);
    }

    private static void drawBorder(Graphics2D g) {
        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(1, 1, WIDTH - 2, HEIGHT - 2);
        g.setStroke(new BasicStroke(1f));
    }

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
        g.setFont(serifBold(30));
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

    private static Font serifBold(int size) { return new Font("Serif", Font.BOLD, size); }

    private static void hq(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
}
