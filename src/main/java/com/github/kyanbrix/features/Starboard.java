package com.github.kyanbrix.features;

import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

public class Starboard extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(Starboard.class);

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {


        // CREATE TABLE starboard (original_id bigint PRIMARY KEY ,starboard_id bigint UNIQUE, total_reaction int DEFAULT 4)

        UnicodeEmoji unicodeEmoji = Emoji.fromUnicode(""); // Edit this shit
        long event_message_id = event.getMessageIdLong();
        MessageChannelUnion channel = event.getChannel();

        Member member = event.getMember();



        if (event.getReaction().getEmoji().equals(unicodeEmoji)) {


            if (channel.getIdLong() == 0L) { // Starboard channel
                try (Connection connection = Caffein.getInstance().getConnection()) {

                    try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM starboard WHERE starboard_id = ?")) {
                        ps.setObject(1,channel.getIdLong());

                        try (ResultSet set = ps.executeQuery()) {

                            if (set.next()) {
                                long original_message_id = set.getLong("original_id");
                                long textchannel_id = set.getLong("textchannel_id");
                                long starboard_id = set.getLong("starboarD_id");
                                Guild guild = event.getGuild();


                                guild.getTextChannelById(textchannel_id).retrieveMessageById(original_message_id).queue(message -> {

                                    message.getReaction(Emoji.fromUnicode("")).retrieveUsers().queue(users -> {

                                        if (!users.contains(member.getUser())) {

                                            channel.retrieveMessageById(starboard_id).queue(starboard_msg -> {

                                                try (Connection con = Caffein.getInstance().getConnection()){

                                                    try (PreparedStatement prep = con.prepareStatement("SELECT total_reaction WHERE starboard_id = ?")) {
                                                        prep.setObject(1,starboard_id);

                                                        try (ResultSet query = prep.executeQuery()) {

                                                            if (query.next()) {
                                                                int total_reaction = query.getInt(1) + 1;

                                                                starboard_msg.editMessageFormat("### :star: %d | %s",total_reaction,starboard_msg.getJumpUrl()).queue();

                                                                try (PreparedStatement updateData = con.prepareStatement("UPDATE starboard SET total_reaction = ? WHERE starboard_id = ?")) {
                                                                    updateData.setObject(1,total_reaction);
                                                                    updateData.setObject(2,starboard_id);

                                                                }

                                                            }

                                                        }

                                                    }


                                                }catch (SQLException e) {
                                                    log.error(e.getMessage(),e.fillInStackTrace());
                                                }



                                            });

                                        }


                                    });



                                },new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE,e -> log.error("Cannot retrieve this message id because it is null")));








                            }


                        }

                    }


                }catch (SQLException e) {
                    log.error(e.getMessage());
                }

                return;
            }



            channel.retrieveMessageById(event_message_id).queue(originalMessage -> {

                MessageReaction messageReaction = originalMessage.getReaction(Emoji.fromUnicode(""));

                if (messageReaction != null) {

                    messageReaction.retrieveUsers().queue(users -> {

                        int reactions = (users.contains(originalMessage.getAuthor()) ? messageReaction.getCount() - 1 : messageReaction.getCount());


                        if (reactions >= 4) {

                            Guild guild = event.getGuild();
                            TextChannel starboardChannel = guild.getTextChannelById(9L);

                            if (starboardChannel == null) return;

                            try (Connection connection = Caffein.getInstance().getConnection()) {
                                String sqlQuery = "SELECT * FROM starboard WHERE original_msg_id = ?";

                                try (PreparedStatement ps = connection.prepareStatement(sqlQuery)) {
                                    ps.setObject(1,originalMessage.getIdLong());

                                    try (ResultSet set = ps.executeQuery()) {

                                        if (set.next()) {

                                            int totalReactions = set.getInt("total_reaction");
                                            long starboard_msg_id = set.getLong("starboard_id");

                                            starboardChannel.retrieveMessageById(starboard_msg_id).queue(starboardMessage -> {

                                                starboardMessage
                                                        .editMessageFormat("### :star: %d | [Jump to Message](%s)",totalReactions+1,starboardMessage.getJumpUrl())
                                                        .queue();

                                                int incrementReaction = totalReactions + 1;

                                                try (Connection con = Caffein.getInstance().getConnection()) {
                                                    try (PreparedStatement updateData = con.prepareStatement("UPDATE FROM starboard SET total_reaction = ? WHERE starboard_id = ?")){
                                                        updateData.setObject(1,incrementReaction);
                                                        updateData.setObject(2,starboard_msg_id);
                                                        updateData.executeUpdate();

                                                        log.info("{} updated its total reactions to {}",starboard_msg_id,incrementReaction);

                                                    }

                                                }catch (SQLException e) {
                                                    log.error(e.getMessage(),e.fillInStackTrace());
                                                }

                                            });

                                        }else {

                                            EmbedBuilder builder = new EmbedBuilder();
                                            builder.setAuthor(originalMessage.getAuthor().getEffectiveName(),null,originalMessage.getAuthor().getAvatarUrl());
                                            builder.setDescription(originalMessage.getContentRaw());
                                            builder.setTimestamp(Instant.now());
                                            builder.setFooter(guild.getName(),guild.getIconUrl());

                                            if (!originalMessage.getAttachments().isEmpty()) {

                                                if (originalMessage.getAttachments().getFirst().isImage()) {

                                                    builder.setImage(originalMessage.getAttachments().getFirst().getProxyUrl());
                                                    starboardChannel.sendMessageFormat("### :star: %d | [Jump to Message](%s)",reactions,originalMessage.getJumpUrl()).setEmbeds(builder.build()).queue(starboard ->  {

                                                        try (Connection connection1 = Caffein.getInstance().getConnection()) {

                                                            String sql = "INSERT INTO starboard (original_id, starboard_id, total_reaction) VALUES (?,?,?)";

                                                            try (PreparedStatement statement = connection1.prepareStatement(sql)) {
                                                                statement.setObject(1,originalMessage.getIdLong());
                                                                statement.setObject(2,starboard.getIdLong());
                                                                statement.setObject(3,reactions);
                                                                statement.executeUpdate();

                                                            }

                                                        }catch (SQLException e) {
                                                            log.error(e.getMessage());
                                                        }

                                                    });


                                                }else {

                                                    try (FileUpload fileUpload = originalMessage.getAttachments().getFirst().getProxy().downloadAsFileUpload("cafe")) {

                                                        starboardChannel.sendMessageFormat("###:star: %d | [Jump to Message](%s)",reactions,originalMessage.getJumpUrl()).setFiles(fileUpload).addEmbeds(builder.build()).queue(starboard-> {

                                                            try (Connection connection1 = Caffein.getInstance().getConnection()) {

                                                                String sql = "INSERT INTO starboard (original_id, starboard_id, total_reaction) VALUES (?,?,?)";

                                                                try (PreparedStatement statement = connection1.prepareStatement(sql)) {
                                                                    statement.setObject(1,originalMessage.getIdLong());
                                                                    statement.setObject(2,starboard.getIdLong());
                                                                    statement.setObject(3,reactions);
                                                                    statement.executeUpdate();

                                                                    log.info("Successfully inserted a data in starboard with id {}",starboard.getIdLong());

                                                                }

                                                            }catch (SQLException e) {
                                                                log.error(e.getMessage());
                                                            }
                                                        });


                                                    }catch (IOException e) {
                                                        log.error(e.getMessage(),e.fillInStackTrace());
                                                    }

                                                }


                                            }else {



                                                starboardChannel.sendMessageFormat("###:star: %d | [Jump to Message](%s)",reactions,originalMessage.getJumpUrl())
                                                        .setEmbeds(builder.build())
                                                        .queue(starboard -> {

                                                            try (Connection con = Caffein.getInstance().getConnection()) {

                                                                try (PreparedStatement insert = con.prepareStatement("INSERT INTO starboard (original_id,textchannel_id,starboard_id,total_reaction) VALUES (?,?,?,?)")) {
                                                                    insert.setObject(1,originalMessage.getIdLong());
                                                                    insert.setObject(2,originalMessage.getChannel().getIdLong());
                                                                    insert.setObject(3,starboard.getIdLong());
                                                                    insert.setObject(3,reactions);

                                                                    insert.executeUpdate();

                                                                    log.info("Successfully inserted a data with starboard id {}",starboard.getIdLong());

                                                                }

                                                            }catch (SQLException e) {
                                                                log.error(e.getMessage(),e.fillInStackTrace());
                                                            }






                                                        });



                                            }

                                        }


                                    }


                                }


                            }catch (SQLException e) {
                                log.error(e.getMessage(),e.fillInStackTrace());
                            }



                        }


                    });

                }



                int reactions = originalMessage.getReaction(unicodeEmoji).getCount();



            });
        }




    }

    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {


        UnicodeEmoji starEmoji = Emoji.fromUnicode("");


        if (!event.getReaction().getEmoji().equals(starEmoji)) return;

        event.getChannel().retrieveMessageById(event.getMessageIdLong()).queue(originalMessage -> {
            try (Connection connection = Caffein.getInstance().getConnection()) {

                try (PreparedStatement query = connection.prepareStatement("SELECT * FROM starboard WHERE original_id = ?")) {
                    query.setObject(1,originalMessage.getIdLong());


                    try (ResultSet set = query.executeQuery()) {

                        if (set.next()) {

                            int totalReaction = set.getInt("total_reaction");
                            long starboard_id = set.getLong("starboard_id");



                        }


                    }


                }



            }catch (SQLException e) {
                log.error(e.getMessage());
            }
        });











    }
}
