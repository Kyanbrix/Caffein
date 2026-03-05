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
import java.util.Random;

public class ConfessionModal extends ListenerAdapter {

    public static final String CONFESSION_MODAL_ID = "confession";
    private static final String REPLY_MODAL_ID = "replyConfession";
    private static final Logger log = LoggerFactory.getLogger(ConfessionModal.class);
    private static final long CONFESSION_CHANNEL_LOG_ID = 1478673936534470666L;
    private static final long CONFESSION_CHANNEL_ID = 1479100844715675648L;
    private static final Random random = new Random();

    private static final String[] colors = new String[]{"#FAEBD7","#F0F8FF","#00FFFF","#7FFF00","#000000","#F5F5DC","#0000FF","#DEB887","#DC143C","#8FBC8F","#FF1493","#FFD700","#F08080","#98FB98","#DDA0DD","#EE82EE","#40E0D0","#C0C0C0","#4169E1","#00FF00","#800000",
            "#8A2BE2","#A0522D","#F8F8FF","#FF4500","#FF0000","#FFDEAD"};


    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {

        MessageChannelUnion channel = event.getChannel();
        MessageComponentTree componentTree = event.getMessage().getComponentTree();
        User user = event.getUser();
        Guild guild = event.getGuild();

        TextChannel confessionChannel = guild.getTextChannelById(CONFESSION_CHANNEL_ID);


        int confession_id = getCurrentConfessionId() + 1;

        switch (event.getCustomId()) {

            case CONFESSION_MODAL_ID -> {

                String confession = event.getValue("confess").getAsString();
                List<Message.Attachment> attachment = event.getValue("attachment-upload").getAsAttachmentList();

                if (!attachment.isEmpty()) {

                    componentReplacer(componentTree, event.getMessage(), event.deferReply());

                    channel.sendMessageComponents(container(confession_id, confession,attachment.getFirst().getProxyUrl())).useComponentsV2().queue(confessionMessage-> {
                        try (Connection con = Caffein.getInstance().getConnection()) {
                            try (PreparedStatement insert = con.prepareStatement("INSERT INTO confession (message_id, author_id) VALUES (?,?)")) {
                                insert.setLong(1,confessionMessage.getIdLong());
                                insert.setLong(2,user.getIdLong());

                                insert.executeUpdate();

                                log.info("Successfully added a new confession");

                            }

                            sendConfessionToLogs(guild,user,confession,confessionMessage.getJumpUrl(),confession_id);

                        }catch (SQLException e) {
                            log.error("Error on callback sql ",e);
                        }




                    });


                }else {

                    componentReplacer(componentTree, event.getMessage(), event.deferReply());

                    channel.sendMessageComponents(container(confession_id,confession)).useComponentsV2().queue(confessionMessage-> {
                        try (Connection con = Caffein.getInstance().getConnection()) {
                            try (PreparedStatement insert = con.prepareStatement("INSERT INTO confession (message_id, author_id) VALUES (?,?)")) {
                                insert.setLong(1,confessionMessage.getIdLong());
                                insert.setLong(2,user.getIdLong());

                                insert.executeUpdate();

                                log.info("Successfully added a new confession without attachment");

                            }

                            sendConfessionToLogs(guild,user,confession,confessionMessage.getJumpUrl(),confession_id);

                        }catch (SQLException e) {
                            log.error("Error on callback sql ",e);
                        }

                    });
                }


            }


            case REPLY_MODAL_ID -> {

                try {
                    int replyId = Integer.parseInt(event.getValue("replyId").getAsString());
                    String replyConfess = event.getValue("replyConfess").getAsString();


                    //if user is on a thread
                    if (event.getMessage().getChannelType().isThread()) {

                        try (Connection connection = Caffein.getInstance().getConnection()) {
                            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM confession WHERE confession_id = ?")) {
                                ps.setInt(1,replyId);
                                try (ResultSet set = ps.executeQuery()) {

                                    if (set.next()) {

                                        long message_id = set.getLong("message_id");


                                        ThreadChannel threadChannel = event.getMessage().getChannel().asThreadChannel();

                                        threadChannel.retrieveMessageById(message_id).queue(threadReply -> {


                                            Container oldContainer = threadReply.getComponents().getFirst().asContainer();
                                            List<ContainerChildComponentUnion> newComponent = oldContainer.getComponents().stream().filter(component -> !(component instanceof ActionRow) && !(component instanceof Separator))
                                                    .toList();


                                            Container newContainer = Container.of(newComponent).withAccentColor(oldContainer.getAccentColor());

                                            threadReply.editMessageComponents(newContainer).useComponentsV2().queue(message -> {

                                                Container container = Container.of(

                                                        TextDisplay.of(String.format("### Anonymous Reply (#%d)",getCurrentConfessionId())),
                                                        TextDisplay.of(replyConfess),

                                                        Separator.createDivider(Separator.Spacing.LARGE),
                                                        ActionRow.of(

                                                                Button.of(ButtonStyle.SECONDARY,"replyConfess","Reply a Confession")

                                                        )



                                                ).withAccentColor(getColor());

                                                message.replyComponents(container).useComponentsV2().queue(replyConfessionMessage -> {

                                                    try (Connection con = Caffein.getInstance().getConnection()){

                                                        try (PreparedStatement insertData = con.prepareStatement("INSERT INTO confession (message_id, author_id) VALUES (?,?)")) {

                                                            insertData.setLong(1,replyConfessionMessage.getIdLong());
                                                            insertData.setLong(2,user.getIdLong());
                                                            insertData.executeUpdate();
                                                        }


                                                        event.getHook().sendMessage("Successfully replied to a confession.").setEphemeral(true).queue();



                                                    }catch (SQLException e) {
                                                        log.error("Error in Reply Confess Modal",e);
                                                    }



                                                });



                                            });





                                        });

                                    }else event.reply("Cannot find that confession id!").setEphemeral(true).queue();



                                }


                            }



                        } catch (SQLException e) {
                            log.error("Error on thread reply ",e);
                            event.reply("Something went wrong!").setEphemeral(true).queue();
                        }



                    }else {

                        try (Connection conn = Caffein.getInstance().getConnection()) {

                            try (PreparedStatement preparedStatement = conn.prepareStatement("SELECT * FROM confession WHERE confession_id = ? ")) {
                                preparedStatement.setInt(1,replyId);

                                try (ResultSet set = preparedStatement.executeQuery()) {

                                    if (set.next()) {

                                        long messageId = set.getLong("message_id");

                                        confessionChannel.retrieveMessageById(messageId).queue(confessionMessage-> {

                                            ThreadChannel threadChannel = confessionMessage.getStartedThread();

                                            if (threadChannel != null) {

                                                handle(user,guild,confession_id,replyConfess,threadChannel);

                                                event.getHook().sendMessage("Successfully reply to a confession").setEphemeral(true).queue();

                                            }else {

                                                confessionMessage.createThreadChannel(String.format("Reply to Confession (#%d)",replyId)).queue(threadChannel1 -> {

                                                    handle(user,guild,confession_id,replyConfess,threadChannel1);

                                                    event.getHook().sendMessage("Successfully reply to a confession").setEphemeral(true).queue();


                                                });





                                            }




                                        });


                                    }else event.reply("Cannot find that confession id").setEphemeral(true).queue();


                                }




                            }


                        }catch (SQLException e) {
                            log.error("Error ",e);
                        }


                    }


                }catch (NumberFormatException e) {
                    event.reply("You enter a character in the Confession ID ! Try Again!").setEphemeral(true).queue();
                }


            }


        }



    }

    private void handle(User user, Guild guild, int confession_id, String replyConfess, ThreadChannel threadChannel1) {
        threadChannel1.sendMessageComponents(replyContainer(confession_id,replyConfess)).useComponentsV2().queue(replyMessage -> {

            try (Connection connection = Caffein.getInstance().getConnection()) {

                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO confession (message_id, author_id) VALUES (?,?)")) {
                    insert.setLong(1,replyMessage.getIdLong());
                    insert.setLong(2,user.getIdLong());

                    insert.executeUpdate();
                }


                sendConfessionToLogs(guild,user,replyConfess,replyMessage.getJumpUrl(),confession_id);




            }catch (SQLException e) {
                log.error("Error in reply thread confession",e);
            }



        });
    }

    private void sendConfessionToLogs(Guild guild, User user, String confession, String jumpURl,int confession_id) {

        if (guild == null) return;

        TextChannel logChannel = guild.getTextChannelById(CONFESSION_CHANNEL_LOG_ID);

        if (logChannel == null) return;


        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(user.getName()+"'s Confession #"+confession_id,null,user.getAvatarUrl())
                .setDescription(confession)
                .addField("Jump to Confession",jumpURl,false)
                .setColor(Color.LIGHT_GRAY)
                .setTimestamp(Instant.now())
                .setFooter("User ID: "+user.getIdLong())
                .build();

        logChannel.sendMessageEmbeds(embed).queue();


    }


    public void componentReplacer(MessageComponentTree componentTree, Message message, ReplyCallbackAction replyCallbackAction) {

        Container oldContainer = componentTree.getComponents().getFirst().asContainer();

        List<ContainerChildComponentUnion> newComponent = oldContainer.getComponents().stream().filter(component -> !(component instanceof ActionRow) && !(component instanceof Separator))
                .toList();

        Container newContainer = Container.of(newComponent).withAccentColor(oldContainer.getAccentColor());


        message.editMessageComponents(newContainer).useComponentsV2().queue();
        replyCallbackAction.setEphemeral(true).flatMap(interactionHook -> interactionHook.sendMessage("Your confession has been created!").setEphemeral(true)).queue();
    }

    private Container container(int id, String confession, String attachmentUrl) {

        return Container.of(

                TextDisplay.of(String.format("### Anonymous Confession (%d)",id)),
                TextDisplay.of(String.format("%s",confession)),
                MediaGallery.of(MediaGalleryItem.fromUrl(attachmentUrl)),
                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(

                        Button.of(ButtonStyle.SUCCESS,"confess","Create Confession", Emoji.fromUnicode("U+2709")),
                        Button.of(ButtonStyle.SECONDARY,"replyConfess","Reply a Confession")

                )

        ).withAccentColor(getColor());


    }




    private Container container(int id, String confession) {

        return Container.of(

                TextDisplay.of(String.format("### Anonymous Confession (#%d)",id)),
                TextDisplay.of(String.format("%s",confession)),

                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(

                        Button.of(ButtonStyle.SUCCESS,"confess","Create Confession", Emoji.fromUnicode("U+2709")),
                        Button.of(ButtonStyle.SECONDARY,"replyConfess","Reply a Confession")

                )

        ).withAccentColor(getColor());


    }

    private Container replyContainer(int id, String confession) {

        return Container.of(

                TextDisplay.of(String.format("## Anonymous Reply (#%d)",id)),
                TextDisplay.of(String.format("%s",confession)),

                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(
                        Button.of(ButtonStyle.SECONDARY,"replyConfess","Reply a Confession")
                )

        ).withAccentColor(getColor());


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

    private Color getColor() {

        return Color.decode(colors[random.nextInt(colors.length)]);

    }

}
