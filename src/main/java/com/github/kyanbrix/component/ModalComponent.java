package com.github.kyanbrix.component;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ModalComponent extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ModalComponent.class);
    private static final long FEEDBACK_LOG_CHANNEL_ID = 1470147677546090731L;
    private static final String FEEDBACK_FIELD_ID = "feed";
    private static final String ATTACHMENT_FIELD_ID = "attachment";
    private static final String FEEDBACK_REPLY =
            "Thank you for your feedback! We appreciate your ideas and suggestions. "
                    + "They help us improve the server for everyone.";

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {

        String feedback = event.getValue(FEEDBACK_FIELD_ID).getAsString();
        List<Message.Attachment> attachments = event.getValue(ATTACHMENT_FIELD_ID).getAsAttachmentList();

        Guild guild = event.getGuild();
        if (guild == null) {
            return;
        }

        TextChannel logChannel = guild.getTextChannelById(FEEDBACK_LOG_CHANNEL_ID);
        if (logChannel == null) {
            log.error("Feedback log channel is null");
            return;
        }

        Container container = buildFeedbackContainer(event.getUser(), feedback, attachments);

        event.reply(FEEDBACK_REPLY).setEphemeral(true).queue();
        logChannel.sendMessageComponents(container).useComponentsV2().queue();
    }

    private Container buildFeedbackContainer(User user, String feedback, List<Message.Attachment> attachments) {
        Section headerSection = createHeaderSection(user, feedback);

        if (attachments.isEmpty()) {
            return Container.of(headerSection);
        }

        return Container.of(
                headerSection,
                Separator.createInvisible(Separator.Spacing.SMALL),
                MediaGallery.of(toMediaGalleryItems(attachments))
        );
    }

    private Section createHeaderSection(User user, String feedback) {
        return Section.of(
                Thumbnail.fromUrl(user.getEffectiveAvatarUrl()),
                TextDisplay.of("### " + user.getEffectiveName() + "'s Feedback"),
                TextDisplay.of(feedback)
        );
    }

    private List<MediaGalleryItem> toMediaGalleryItems(List<Message.Attachment> attachments) {
        List<MediaGalleryItem> images = new ArrayList<>(attachments.size());
        for (Message.Attachment attachment : attachments) {
            images.add(MediaGalleryItem.fromUrl(attachment.getProxyUrl()));
        }
        return images;
    }
}
