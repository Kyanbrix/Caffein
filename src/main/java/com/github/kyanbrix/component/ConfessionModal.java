package com.github.kyanbrix.component;

import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
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

public class ConfessionModal extends ListenerAdapter {

    public static final String CONFESSION_MODAL_ID = "confession";
    private static final String REPLY_MODAL_ID = "replyConfession";
    private static final Logger log = LoggerFactory.getLogger(ConfessionModal.class);
    private static final long CONFESSION_CHANNEL_LOG_ID = 1470162738910203994L;
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {

        String confession = event.getValue("confess").getAsString();
        MessageChannelUnion channel = event.getChannel();
        List<Message.Attachment> attachment = event.getValue("attachment-upload").getAsAttachmentList();
        MessageComponentTree componentTree = event.getMessage().getComponentTree();
        User user = event.getUser();
        Guild guild = event.getGuild();

        TextChannel confessionChannel = guild.getTextChannelById(1474657187547713627L);

        int confession_id = getCurrentConfessionId() + 1;

        switch (event.getCustomId()) {

            case CONFESSION_MODAL_ID -> {

                componentReplacer(componentTree, event.getMessage(), event.deferReply());
                if (!attachment.isEmpty()) {

                    channel.sendMessageComponents(container(confession_id, confession,attachment.getFirst().getProxyUrl())).useComponentsV2().queue(confessionMessage-> {
                        try (Connection con = Caffein.getInstance().getConnection()) {
                            try (PreparedStatement insert = con.prepareStatement("INSERT INTO confession (confession_id, message_id, author_id)")) {
                                insert.setLong(1,confession_id);
                                insert.setLong(2,confessionMessage.getIdLong());
                                insert.setLong(3,user.getIdLong());

                                insert.executeUpdate();

                                log.info("Successfully added a new confession");

                            }

                            sendConfessionToLogs(guild,user,confession,confessionMessage.getJumpUrl());

                        }catch (SQLException e) {
                            log.error("Error on callback sql ",e);
                        }




                    });


                }else {

                    channel.sendMessageComponents(container(confession_id,confession)).useComponentsV2().queue(confessionMessage-> {
                        try (Connection con = Caffein.getInstance().getConnection()) {
                            try (PreparedStatement insert = con.prepareStatement("INSERT INTO confession (confession_id, message_id, author_id)")) {
                                insert.setLong(1,confession_id);
                                insert.setLong(2,confessionMessage.getIdLong());
                                insert.setLong(3,user.getIdLong());

                                insert.executeUpdate();

                                log.info("Successfully added a new confession without attachment");

                            }

                            sendConfessionToLogs(guild,user,confession,confessionMessage.getJumpUrl());

                        }catch (SQLException e) {
                            log.error("Error on callback sql ",e);
                        }

                    });
                }


            }


            case REPLY_MODAL_ID -> {

                String replyId = event.getValue("replyId").getAsString();
                String replyConfess = event.getValue("replyConfess").getAsString();

                confessionChannel.retrieveMessageById(replyId).queue(message -> {

                    ThreadChannel threadChannel = message.getStartedThread();

                    if (threadChannel != null) {

                        handle(user, guild, confession_id, replyConfess, threadChannel);


                    }else message.createThreadChannel("Replied to Confession "+replyId).queue(threadChannel1 -> handle(user, guild, confession_id, replyConfess, threadChannel1));



                },new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE,e -> event.reply("Confession ID not Found! Try Again.").setEphemeral(true).queue()));

            }


        }



    }

    private void handle(User user, Guild guild, int confession_id, String replyConfess, ThreadChannel threadChannel1) {
        threadChannel1.sendMessageComponents(replyContainer(confession_id,replyConfess)).useComponentsV2().queue(replyMessage -> {

            try (Connection connection = Caffein.getInstance().getConnection()) {

                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO confession (confession_id, message_id, author_id) VALUES (?,?,?)")) {
                    insert.setLong(1,confession_id);
                    insert.setLong(2,replyMessage.getIdLong());
                    insert.setLong(3,user.getIdLong());

                    insert.executeUpdate();
                }


                sendConfessionToLogs(guild,user,replyConfess,replyMessage.getJumpUrl());


            }catch (SQLException e) {
                log.error("Error in reply thread confession",e);
            }



        });
    }

    private void sendConfessionToLogs(Guild guild, User user, String confession, String jumpURl) {

        if (guild == null) return;

        TextChannel logChannel = guild.getTextChannelById(CONFESSION_CHANNEL_LOG_ID);

        if (logChannel == null) return;


        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(user.getName()+"'s Confession",null,user.getAvatarUrl())
                .setDescription(confession)
                .addField("Jump to Confession",jumpURl,false)
                .setColor(Color.LIGHT_GRAY)
                .setTimestamp(Instant.now())
                .setFooter("User ID: "+user.getIdLong())
                .build();

        logChannel.sendMessageEmbeds(embed).queue();


    }


    public static void componentReplacer(MessageComponentTree componentTree, Message message, ReplyCallbackAction replyCallbackAction) {
        ComponentReplacer replacer = ComponentReplacer.of(
                Button.class,
                button -> true,
                button -> null
        );

        MessageComponentTree updated = componentTree.replace(replacer);

        message.editMessageComponents(updated).useComponentsV2().queue();
        replyCallbackAction.flatMap(interactionHook -> interactionHook.sendMessage("Your confession has been created!").setEphemeral(true)).queue();
    }

    private Container container(int id, String confession, String attachmentUrl) {

        return Container.of(

                TextDisplay.of(String.format("### Anonymous Confession (%d)",id)),
                TextDisplay.of(String.format("%s",confession)),

                Separator.createInvisible(Separator.Spacing.SMALL),
                MediaGallery.of(MediaGalleryItem.fromUrl(attachmentUrl)),
                Separator.createInvisible(Separator.Spacing.LARGE),
                ActionRow.of(

                        Button.of(ButtonStyle.SUCCESS,"confess","Create Confession", Emoji.fromUnicode("U+2709")),
                        Button.of(ButtonStyle.SECONDARY,"replyConfess","Reply a Confession")

                )

        );


    }




    private Container container(int id, String confession) {

        return Container.of(

                TextDisplay.of(String.format("### Anonymous Confession (%d)",id)),
                TextDisplay.of(String.format("%s",confession)),

                Separator.createInvisible(Separator.Spacing.SMALL),
                Separator.createInvisible(Separator.Spacing.LARGE),
                ActionRow.of(

                        Button.of(ButtonStyle.SUCCESS,"confess","Create Confession", Emoji.fromUnicode("U+2709")),
                        Button.of(ButtonStyle.SECONDARY,"replyConfess","Reply a Confession")

                )

        );


    }

    private Container replyContainer(int id, String confession) {

        return Container.of(

                TextDisplay.of(String.format("### Anonymous Reply (%d)",id)),
                TextDisplay.of(String.format("%s",confession)),

                Separator.createInvisible(Separator.Spacing.SMALL),
                Separator.createInvisible(Separator.Spacing.LARGE),
                ActionRow.of(
                        Button.of(ButtonStyle.SECONDARY,"replyConfess","Reply a Confession")
                )

        );


    }

    private int getCurrentConfessionId() {

        try (Connection connection = Caffein.getInstance().getConnection()) {

            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM confession ORDER BY confession_id DESC LIMIT 1")){

                try (ResultSet set = ps.executeQuery()) {

                    if (set.next()) {
                        return set.getInt("confession_id");
                    }

                }

            }

        }catch (SQLException e) {
            log.error("Error on retrieving Confession Id",e);
        }

        return 0;

    }
}
