package com.github.kyanbrix.features.leveling.utilities;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.net.URI;

/**
 * DailyXpCardV2 — café-themed daily XP reward card with left avatar panel.
 *
 * JDA usage:
 *   byte[] bytes = DailyXpCardV2.generate(data);
 *   event.getMessage().reply(MessageCreateData.fromFiles(
 *       FileUpload.fromData(bytes, "daily.png")
 *   )).queue();
 */
public class DailyXpCard {

    // ── Layout ─────────────────────────────────────────────────────────
    private static final int WIDTH    = 520;
    private static final int HEIGHT   = 200;
    private static final int CORNER   = 16;
    private static final int PANEL_W  = 160;   // left avatar panel width
    private static final int AV_SIZE  = 80;
    private static final int RX       = PANEL_W + 20;  // right content start X

    // ── Café Palette ───────────────────────────────────────────────────
    private static final Color BG_PANEL_L  = new Color(0x1C1109);
    private static final Color BG_PANEL_R  = new Color(0x2A1A0C);
    private static final Color BG_RIGHT_L  = new Color(0x34200E);  // warm mocha left
    private static final Color BG_RIGHT_R  = new Color(0x26160A);  // warm mocha right
    private static final Color CREAM       = new Color(0xF5E6C8);
    private static final Color LATTE       = new Color(0xC8A97A);
    private static final Color CARAMEL     = new Color(0xD4843A);
    private static final Color GOLD        = new Color(0xF0C040);
    private static final Color MUTED       = new Color(0x8A7060);
    private static final Color TEAL        = new Color(0x2EC4B6);
    private static final Color TEAL_DARK   = new Color(0x124842);
    private static final Color PILL_BG     = new Color(0x3C260C);


        public record Data(String username, int xpAwarded, String rewardLabel, int streakDays, String resetsIn, int totalXP,
                           String avatarUrl) {
    }

    // ── Public API ─────────────────────────────────────────────────────

    public static byte[] generate(Data d) throws IOException {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hq(g);

        // Clip everything to rounded card
        RoundRectangle2D card = new RoundRectangle2D.Float(0, 0, WIDTH, HEIGHT, CORNER, CORNER);
        g.setClip(card);

        drawLeftPanel(g);
        drawRightPanel(g);
        drawAvatar(g, d);
        drawRightContent(g, d);
        drawDecorations(g);

        // Remove clip, draw border on top
        g.setClip(null);
        drawBorder(g);

        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    // ── Right panel ───────────────────────────────────────────────────

    private static void drawRightPanel(Graphics2D g) {
        // Warm mocha gradient for right side
        GradientPaint rPanel = new GradientPaint(PANEL_W, 0, BG_RIGHT_L, WIDTH, 0, BG_RIGHT_R);
        g.setPaint(rPanel);
        g.fillRect(PANEL_W, 0, WIDTH - PANEL_W, HEIGHT);

        // Subtle wood grain on right panel
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < WIDTH + HEIGHT; i += 22) {
            int alpha = 4 + (i % 3);
            g.setColor(new Color(160, 100, 40, alpha));
            g.drawLine(i, 0, 0, i);
            g.drawLine(WIDTH, i - WIDTH, i, HEIGHT);
        }
    }

    // ── Left panel ────────────────────────────────────────────────────

    private static void drawLeftPanel(Graphics2D g) {
        // Dark espresso gradient left panel
        GradientPaint panel = new GradientPaint(0, 0, BG_PANEL_L, PANEL_W, 0, BG_PANEL_R);
        g.setPaint(panel);
        g.fillRect(0, 0, PANEL_W, HEIGHT);

        // Wood grain on left panel only
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < PANEL_W + HEIGHT; i += 20) {
            int alpha = 6 + (i % 3) * 2;
            g.setColor(new Color(180, 120, 60, alpha));
            g.drawLine(i, 0, 0, i);
            g.drawLine(PANEL_W, i - PANEL_W, i, HEIGHT);
        }

        // Vertical separator line
        g.setColor(new Color(CARAMEL.getRed(), CARAMEL.getGreen(), CARAMEL.getBlue(), 150));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(PANEL_W, 0, PANEL_W, HEIGHT);
        g.setStroke(new BasicStroke(1f));
    }

    // ── Avatar ────────────────────────────────────────────────────────

    private static void drawAvatar(Graphics2D g, Data d) {
        BufferedImage av = null;
        if (d.avatarUrl != null && !d.avatarUrl.isEmpty()) {
            try {
                av = resize(ImageIO.read(URI.create(d.avatarUrl).toURL()), AV_SIZE, AV_SIZE);
            } catch (Exception ignored) {}
        }
        if (av == null) av = placeholder(d.username, AV_SIZE);

        int ax = (PANEL_W - AV_SIZE) / 2;
        int ay = (HEIGHT - AV_SIZE) / 2 - 10;

        // Shadow
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(ax + 2, ay + 3, AV_SIZE, AV_SIZE);

        // Circular avatar
        g.drawImage(toCircle(av, AV_SIZE), ax, ay, null);

        // Caramel ring
        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(2.5f));
        g.drawOval(ax, ay, AV_SIZE - 1, AV_SIZE - 1);
        g.setStroke(new BasicStroke(1f));

        // Username below avatar
        g.setFont(serif(12));
        g.setColor(LATTE);
        FontMetrics fm = g.getFontMetrics();
        String uname = "@" + d.username;
        g.drawString(uname, (PANEL_W - fm.stringWidth(uname)) / 2, ay + AV_SIZE + 18);
    }

    // ── Right content ─────────────────────────────────────────────────

    private static void drawRightContent(Graphics2D g, Data d) {
        // ── Tag pill: "DAILY REWARD" ──
        String tag = "DAILY REWARD";
        g.setFont(serifBold(11));
        int tagW = g.getFontMetrics().stringWidth(tag) + 16;
        g.setColor(PILL_BG);
        g.fillRoundRect(RX, 16, tagW, 20, 6, 6);
        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(RX, 16, tagW, 20, 6, 6);
        g.drawString(tag, RX + 8, 30);

        // ── Big XP number ──
        String xpText = "+" + formatNumber(d.xpAwarded) + " XP";
        g.setFont(serifBold(34));
        // Shadow
        g.setColor(new Color(0, 0, 0, 80));
        g.drawString(xpText, RX + 2, 78);
        // Gold
        g.setColor(GOLD);
        g.drawString(xpText, RX, 76);

        // ── Reward label ──
        g.setFont(serifBold(13));
        g.setColor(CARAMEL);
        g.drawString(d.rewardLabel, RX, 100);

        // ── Thin divider ──
        g.setColor(new Color(CARAMEL.getRed(), CARAMEL.getGreen(), CARAMEL.getBlue(), 50));
        g.drawLine(RX, 112, WIDTH - 20, 112);

        // ── Bottom pills ──
        int pillY = 122;
        int pillH = 20;

        // Streak pill — caramel
        String streakText = "★ Streak: " + d.streakDays + " day" + (d.streakDays != 1 ? "s" : "");
        g.setFont(serifBold(11));
        int sW = g.getFontMetrics().stringWidth(streakText) + 14;
        g.setColor(PILL_BG);
        g.fillRoundRect(RX, pillY, sW, pillH, 6, 6);
        g.setColor(CARAMEL);
        g.drawRoundRect(RX, pillY, sW, pillH, 6, 6);
        g.setColor(CREAM);
        g.drawString(streakText, RX + 7, pillY + 14);

        // Reset pill — teal
        String resetText = "⏰ Resets in " + d.resetsIn;
        g.setFont(serifBold(11));
        int rW = g.getFontMetrics().stringWidth(resetText) + 14;
        g.setColor(TEAL_DARK);
        g.fillRoundRect(RX + sW + 10, pillY, rW, pillH, 6, 6);
        g.setColor(TEAL);
        g.drawRoundRect(RX + sW + 10, pillY, rW, pillH, 6, 6);
        g.setColor(CREAM);
        g.drawString(resetText, RX + sW + 17, pillY + 14);

        // ── Total XP note ──
        g.setFont(serif(12));
        g.setColor(MUTED);
        g.drawString("Total XP: " + formatNumber(d.totalXP), RX, 162);
    }

    // ── Coffee bean decorations (top-right) ───────────────────────────

    private static void drawDecorations(Graphics2D g) {
        int[][] positions = { {WIDTH-30,20}, {WIDTH-18,36}, {WIDTH-34,50} };
        int[] alphas = { 110, 140, 80 };
        for (int i = 0; i < positions.length; i++) {
            int ox = positions[i][0], oy = positions[i][1];
            g.setColor(new Color(CARAMEL.getRed(), CARAMEL.getGreen(), CARAMEL.getBlue(), alphas[i]));
            g.fillOval(ox - 5, oy - 3, 10, 6);
            g.setColor(new Color(28, 17, 9, alphas[i]));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(ox - 3, oy, ox + 3, oy);
        }
    }

    // ── Border ────────────────────────────────────────────────────────

    private static void drawBorder(Graphics2D g) {
        g.setColor(CARAMEL);
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Float(1, 1, WIDTH - 2, HEIGHT - 2, CORNER, CORNER));
        g.setStroke(new BasicStroke(1f));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static String formatNumber(int n) {
        return String.format("%,d", n);
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
        g.setFont(serifBold(24));
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