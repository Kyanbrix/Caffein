package com.github.kyanbrix.component;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponentUnion;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;


public class ConfessionModal extends ListenerAdapter {

    public static final String CONFESSION_MODAL_ID = "confession";
    private static final Logger log = LoggerFactory.getLogger(ConfessionModal.class);


    private static final int CARD_WIDTH = 720;
    private static final int CARD_HEIGHT = 720;
    private static final int HEADER_HEIGHT = 230;
    private static final int CORNER_RADIUS = 40;
    private static final int PADDING = 40;
    private static final long MAX_FILE_SIZE = 512 * 1024;

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String customId = event.getCustomId();

        if (customId.equals(CONFESSION_MODAL_ID)) {

            try {

                handleConfessionSubmission(event);

            }catch (IOException e) {
                event.reply("Something went wrong!").setEphemeral(true).queue();
            }
        }
    }

    private void handleConfessionSubmission(ModalInteractionEvent event) throws IOException {
        ModalMapping contentMapping = event.getValue("confess");
        if (contentMapping == null) return;

        String content = contentMapping.getAsString();

        String header = "send me anonymous messages!";

        BufferedImage image = generateConfession(header,content);

        byte[] confessImage = imageToBytes(image);

        Container oldContainer = event.getMessage().getComponents().getFirst().asContainer();

        List<ContainerChildComponentUnion> filtered = oldContainer.getComponents().stream()
                .filter(c -> !(c instanceof ActionRow) && !(c instanceof Separator))
                .toList();

        Container newContainer = Container.of(filtered);

        Container confessContainer = Container.of(

                MediaGallery.of(MediaGalleryItem.fromFile(FileUpload.fromData(confessImage,"confess.png"))),
                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(Button.of(ButtonStyle.SUCCESS,"confess","Create Confession", Emoji.fromUnicode("U+2709")))

        );

        event.editComponents(newContainer).useComponentsV2().flatMap(interactionHook -> interactionHook.sendMessage("Your Confession has been created!").setEphemeral(true)).queue();





        event.getChannel().sendMessageComponents(confessContainer).useComponentsV2().queue(message -> {

            logConfession(event.getJDA(),event.getUser(),content,message.getJumpUrl());
            saveConfessionRecord(message.getIdLong(),event.getUser().getIdLong());

        });

    }

    private static byte[] imageToBytes(BufferedImage bufferedImage) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage,"png",baos);

        return baos.toByteArray();

    }


    public static BufferedImage generateConfession(String headerText, String message) {
        // Create image exactly the size of the card (no extra background)
        BufferedImage image = new BufferedImage(CARD_WIDTH, CARD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Create rounded rectangle clip for the card
        RoundRectangle2D cardShape = new RoundRectangle2D.Double(
                0, 0, CARD_WIDTH, CARD_HEIGHT, CORNER_RADIUS, CORNER_RADIUS
        );

        // Draw cafe-themed gradient header (dark brown to light coffee)
        Graphics2D headerG2d = (Graphics2D) g2d.create();
        headerG2d.setClip(cardShape);

        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(101, 67, 33),  // Dark coffee brown
                CARD_WIDTH, HEADER_HEIGHT, new Color(188, 143, 107)  // Light coffee/latte
        );
        headerG2d.setPaint(gradient);
        headerG2d.fillRect(0, 0, CARD_WIDTH, HEADER_HEIGHT);

        // Draw header text with emoji support
        Font headerFont = new Font("Arial", Font.BOLD, 48);
        headerG2d.setFont(headerFont);
        headerG2d.setColor(Color.WHITE);
        FontMetrics headerFm = headerG2d.getFontMetrics();

        String[] headerLines = wrapText(headerText, headerFm, CARD_WIDTH - (PADDING * 2));
        int headerY = (HEADER_HEIGHT - (headerLines.length * headerFm.getHeight())) / 2 + headerFm.getAscent();

        for (String line : headerLines) {
            int headerX = (CARD_WIDTH - headerFm.stringWidth(line)) / 2;
            drawTextWithEmoji(headerG2d, line, headerX, headerY, headerFont);
            headerY += headerFm.getHeight();
        }

        headerG2d.dispose();

        // Draw white body section
        g2d.setClip(cardShape);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, HEADER_HEIGHT, CARD_WIDTH, CARD_HEIGHT - HEADER_HEIGHT);

        // Calculate optimal font size for message
        int bodyHeight = CARD_HEIGHT - HEADER_HEIGHT;
        int fontSize = calculateOptimalFontSize(message, CARD_WIDTH - (PADDING * 2), bodyHeight - (PADDING * 2));

        // Draw message text (centered) with emoji support
        Font messageFont = new Font("Arial", Font.BOLD, fontSize);
        g2d.setFont(messageFont);
        g2d.setColor(Color.BLACK);
        FontMetrics messageFm = g2d.getFontMetrics();

        String[] messageLines = wrapText(message, messageFm, CARD_WIDTH - (PADDING * 2));
        int totalTextHeight = messageLines.length * messageFm.getHeight();
        int messageY = HEADER_HEIGHT + (bodyHeight - totalTextHeight) / 2 + messageFm.getAscent();

        for (String line : messageLines) {
            int messageX = (CARD_WIDTH - messageFm.stringWidth(line)) / 2;
            drawTextWithEmoji(g2d, line, messageX, messageY, messageFont);
            messageY += messageFm.getHeight();
        }

        g2d.dispose();
        return image;
    }

    private static String[] wrapText(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            // Check if single word is too long
            if (fm.stringWidth(word) > maxWidth) {
                // Add current line if it has content
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }

                // Break the long word into chunks that fit
                StringBuilder chunk = new StringBuilder();
                for (int i = 0; i < word.length(); i++) {
                    char c = word.charAt(i);
                    String testChunk = chunk.toString() + c;

                    if (fm.stringWidth(testChunk) > maxWidth) {
                        // Current chunk is full, add it and start new chunk
                        if (chunk.length() > 0) {
                            lines.add(chunk.toString());
                            chunk = new StringBuilder();
                        }
                        chunk.append(c);
                    } else {
                        chunk.append(c);
                    }
                }

                // Add remaining chunk to current line
                if (chunk.length() > 0) {
                    currentLine.append(chunk);
                }
                continue;
            }

            // Normal word wrapping
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            int testWidth = fm.stringWidth(testLine);

            if (testWidth <= maxWidth) {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    // This shouldn't happen since we handle long words above
                    currentLine.append(word);
                }
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines.toArray(new String[0]);
    }

    private static int calculateOptimalFontSize(String text, int maxWidth, int maxHeight) {
        int minFontSize = 20;
        int maxFontSize = 80;
        int optimalFontSize = maxFontSize;

        // Binary search for optimal font size
        while (minFontSize <= maxFontSize) {
            int testFontSize = (minFontSize + maxFontSize) / 2;

            if (textFits(text, testFontSize, maxWidth, maxHeight)) {
                optimalFontSize = testFontSize;
                minFontSize = testFontSize + 1; // Try larger
            } else {
                maxFontSize = testFontSize - 1; // Try smaller
            }
        }

        return optimalFontSize;
    }

    private static boolean textFits(String text, int fontSize, int maxWidth, int maxHeight) {
        BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = tempImage.createGraphics();
        Font font = new Font("Arial", Font.BOLD, fontSize);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();

        String[] lines = wrapText(text, fm, maxWidth);
        int totalHeight = lines.length * fm.getHeight();

        g2d.dispose();

        return totalHeight <= maxHeight;
    }

    private static void drawTextWithEmoji(Graphics2D g2d, String text, int x, int y, Font baseFont) {
        // Create fonts with emoji support
        Font[] fonts = {
                baseFont,
                new Font("Noto Color Emoji", Font.PLAIN, baseFont.getSize()),
                new Font("Segoe UI Emoji", Font.PLAIN, baseFont.getSize()),
                new Font("Apple Color Emoji", Font.PLAIN, baseFont.getSize())
        };

        int currentX = x;
        FontMetrics baseFm = g2d.getFontMetrics(baseFont);

        // Process each character
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String charStr = String.valueOf(c);

            // Check if it's a high surrogate (emoji might be 2 chars)
            if (Character.isHighSurrogate(c) && i + 1 < text.length()) {
                char lowSurrogate = text.charAt(i + 1);
                if (Character.isLowSurrogate(lowSurrogate)) {
                    charStr = String.valueOf(c) + String.valueOf(lowSurrogate);
                    i++; // Skip the next character as we've already processed it
                }
            }

            // Try to render with different fonts
            boolean rendered = false;
            for (Font font : fonts) {
                if (font.canDisplay(c) || (charStr.length() > 1)) {
                    g2d.setFont(font);
                    FontMetrics fm = g2d.getFontMetrics();
                    g2d.drawString(charStr, currentX, y);
                    currentX += fm.stringWidth(charStr);
                    rendered = true;
                    break;
                }
            }

            // If no font can display it, use base font
            if (!rendered) {
                g2d.setFont(baseFont);
                g2d.drawString(charStr, currentX, y);
                currentX += baseFm.stringWidth(charStr);
            }
        }
    }





    private void saveConfessionRecord(long messageId, long authorId) {
        try (Connection con = Caffein.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO confession (message_id, author_id) VALUES (?,?)")) {
            ps.setLong(1, messageId);
            ps.setLong(2, authorId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save confession", e);
        }
    }



    private void logConfession(JDA jda, User user, String content, String jumpUrl) {
        TextChannel logChannel = jda.getTextChannelById(Constant.CONFESSION_LOG_ID);

        if (logChannel == null) {
            log.error("Log channel for confession is null");
            return;
        }

        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(user.getName() + "'s Confession", null, user.getAvatarUrl())
                .setDescription(content)
                .addField("Jump to Confession", jumpUrl, false)
                .setColor(Color.LIGHT_GRAY)
                .setTimestamp(Instant.now())
                .setFooter("User ID: " + user.getIdLong())
                .build();

        logChannel.sendMessageEmbeds(embed).queue();
    }


}
