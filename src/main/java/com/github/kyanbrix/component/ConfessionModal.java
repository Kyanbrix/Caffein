package com.github.kyanbrix.component;

import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponentUnion;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Random;


public class ConfessionModal extends ListenerAdapter {

    public static final String CONFESSION_MODAL_ID = "confession";
    private static final String REPLY_MODAL_ID = "replyConfession";
    private static final Logger log = LoggerFactory.getLogger(ConfessionModal.class);

    private static final long CONFESSION_CHANNEL_LOG_ID = 1478673936534470666L;
    private static final long CONFESSION_CHANNEL_ID = 1479100844715675648L;

    private static final Random RANDOM = new Random();
    private static final String[] COLORS = {
            "#FAEBD7", "#F0F8FF", "#00FFFF", "#7FFF00", "#000000", "#F5F5DC", "#0000FF", "#DEB887",
            "#DC143C", "#8FBC8F", "#FF1493", "#FFD700", "#F08080", "#98FB98", "#DDA0DD", "#EE82EE",
            "#40E0D0", "#C0C0C0", "#4169E1", "#00FF00", "#800000", "#8A2BE2", "#A0522D", "#F8F8FF",
            "#FF4500", "#FF0000", "#FFDEAD"
    };

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String customId = event.getCustomId();

        switch (customId) {
            case CONFESSION_MODAL_ID -> handleConfessionSubmission(event);
            case REPLY_MODAL_ID -> handleReplySubmission(event);
        }
    }

    private void handleConfessionSubmission(ModalInteractionEvent event) {
        ModalMapping contentMapping = event.getValue("confess");
        if (contentMapping == null) return;

        String content = contentMapping.getAsString();
        ModalMapping attachmentMapping = event.getValue("attachment-upload");
        List<Message.Attachment> attachments = attachmentMapping != null ? attachmentMapping.getAsAttachmentList() : List.of();
        
        long confessionId = getNextConfessionId();

        TextChannel channel = event.getGuild().getTextChannelById(CONFESSION_CHANNEL_ID);
        if (channel == null) {
            event.reply("Something went wrong ").setEphemeral(true).queue();
            log.error("Confession channel not found!");
            return;
        }


        event.getMessage().editMessageComponents(newContainer(event.getMessage())).flatMap(message -> {
            Container container = buildConfessionContainer(confessionId, content, attachments);

            return message.getChannel().sendMessageComponents(container).useComponentsV2();

        }).queue(message -> {

            saveConfessionRecord(message.getIdLong(),event.getUser().getIdLong());
            logConfession(event.getGuild(),event.getUser(),content,message.getJumpUrl(),confessionId);

            event.getHook().sendMessage("Your confession has been posted!").setEphemeral(true).queue();

        });


    }

    private void handleReplySubmission(ModalInteractionEvent event) {
        ModalMapping replyIdMapping = event.getValue("replyId");
        ModalMapping replyContentMapping = event.getValue("replyConfess");
        
        if (replyIdMapping == null || replyContentMapping == null) return;

        try {

            int targetId = Integer.parseInt(replyIdMapping.getAsString());
            String replyContent = replyContentMapping.getAsString();
            long newId = getNextConfessionId();

            long originalMessageId = getMessageIdByConfessionId(targetId);
            if (originalMessageId == 0) {
                event.reply("Could not find a confession with ID #" + targetId).setEphemeral(true).queue();
                return;
            }


            if (event.getChannelType().isThread()) {
                processThreadReply(event, originalMessageId, newId, replyContent);
            } else {
                processChannelReply(event, originalMessageId, targetId, newId, replyContent);
            }

            event.getHook().sendMessage("Successfully replied to the confession.").setEphemeral(true).queue();

        } catch (NumberFormatException e) {
            event.reply("Invalid Confession ID!").setEphemeral(true).queue();
        }
    }

    private void processThreadReply(ModalInteractionEvent event, long originalMsgId, long nextId, String content) {
        ThreadChannel thread = event.getChannel().asThreadChannel();
        thread.retrieveMessageById(originalMsgId).queue(originalMsg -> {

            originalMsg.editMessageComponents(newContainer(originalMsg)).useComponentsV2().flatMap(t -> {
                Container container = buildReplyContainer(nextId, content);

                return originalMsg.replyComponents(container).useComponentsV2();
            }).queue(message -> {
                saveConfessionRecord(message.getIdLong(),event.getUser().getIdLong());
                logConfession(event.getGuild(),event.getUser(),content,message.getJumpUrl(),nextId);
            });

        }, new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE, e -> sendReplyToThread(thread, event.getUser(), event.getGuild(), nextId, content)));
    }

    private void processChannelReply(ModalInteractionEvent event, long originalMsgId, long targetId, long nextId, String content) {
        TextChannel channel = event.getGuild().getTextChannelById(CONFESSION_CHANNEL_ID);
        if (channel == null) return;

        channel.retrieveMessageById(originalMsgId).queue(message -> {
            ThreadChannel thread = message.getStartedThread();
            if (thread == null) {
                message.createThreadChannel("Reply to Confession (#" + targetId + ")").queue(newThread -> 
                        sendReplyToThread(newThread, event.getUser(), event.getGuild(), nextId, content)
                );
            } else {
                sendReplyToThread(thread, event.getUser(), event.getGuild(), nextId, content);
            }
        });
    }

    private void sendReplyToThread(ThreadChannel thread, User user, Guild guild, long id, String content) {
        thread.sendMessageComponents(buildReplyContainer(id, content)).useComponentsV2().queue(msg -> {
            saveConfessionRecord(msg.getIdLong(), user.getIdLong());
            logConfession(guild, user, content, msg.getJumpUrl(), id);
        });
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

    private long getMessageIdByConfessionId(long id) {
        try (Connection con = Caffein.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT message_id FROM confession WHERE confession_id = ?")) {
             ps.setLong(1, id);
             try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("message_id");
             }
        } catch (SQLException e) {
            log.error("Failed to fetch message ID", e);
        }
        return 0;
    }

    private long getNextConfessionId() {
        try (Connection con = Caffein.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT confession_id FROM confession ORDER BY confession_id DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong("confession_id") + 1;
        } catch (SQLException e) {
            log.error("Failed to retrieve next ID", e);
        }
        return 1;
    }


    private void logConfession(Guild guild, User user, String content, String jumpUrl, long id) {
        if (guild == null) return;
        TextChannel logChannel = guild.getTextChannelById(CONFESSION_CHANNEL_LOG_ID);
        if (logChannel == null) return;

        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(user.getName() + "'s Confession #" + id, null, user.getAvatarUrl())
                .setDescription(content)
                .addField("Jump to Confession", jumpUrl, false)
                .setColor(Color.LIGHT_GRAY)
                .setTimestamp(Instant.now())
                .setFooter("User ID: " + user.getIdLong())
                .build();

        logChannel.sendMessageEmbeds(embed).queue();
    }

    private boolean isSetupMessage(Message message) {
        if (message == null) return false;
        if (message.getContentRaw().contains("## Create a Confession")) return true;
        if (message.getComponents().isEmpty()) return false;
        
        Container container = message.getComponents().getFirst().asContainer();
        return container.getComponents().stream()
                .anyMatch(c -> c instanceof TextDisplay && ((TextDisplay) c).getContent().contains("Create a Confession"));
    }


    private Container newContainer(Message message) {
        Container oldContainer = message.getComponents().getFirst().asContainer();
        List<ContainerChildComponentUnion> filtered = oldContainer.getComponents().stream()
                .filter(c -> !(c instanceof ActionRow) && !(c instanceof Separator))
                .toList();

        return Container.of(filtered).withAccentColor(oldContainer.getAccentColor());
    }

    private Container buildConfessionContainer(long id, String content, List<Message.Attachment> attachments) {
        if (!attachments.isEmpty()) {
            return Container.of(
                    TextDisplay.of(String.format("### Anonymous Confession (#%d)", id)),
                    TextDisplay.of(content),
                    MediaGallery.of(MediaGalleryItem.fromUrl(attachments.getFirst().getProxyUrl())),
                    Separator.createDivider(Separator.Spacing.LARGE),
                    ActionRow.of(
                            Button.of(ButtonStyle.SUCCESS, "confess", "Create Confession", Emoji.fromUnicode("U+2709")),
                            Button.of(ButtonStyle.SECONDARY, "replyConfess", "Reply a Confession")
                    )
            ).withAccentColor(getRandomColor());
        } else {
            return Container.of(
                    TextDisplay.of(String.format("### Anonymous Confession (#%d)", id)),
                    TextDisplay.of(content),
                    Separator.createDivider(Separator.Spacing.LARGE),
                    ActionRow.of(
                            Button.of(ButtonStyle.SUCCESS, "confess", "Create Confession", Emoji.fromUnicode("U+2709")),
                            Button.of(ButtonStyle.SECONDARY, "replyConfess", "Reply a Confession")
                    )
            ).withAccentColor(getRandomColor());
        }
    }

    private Container buildReplyContainer(long id, String content) {
        String header = "### Anonymous Reply (#%d)";
        return Container.of(
                TextDisplay.of(String.format(header, id)),
                TextDisplay.of(content),
                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(Button.of(ButtonStyle.SECONDARY, "replyConfess", "Reply a Confession"))
        ).withAccentColor(getRandomColor());
    }

    private Color getRandomColor() {
        return Color.decode(COLORS[RANDOM.nextInt(COLORS.length)]);
    }
}
