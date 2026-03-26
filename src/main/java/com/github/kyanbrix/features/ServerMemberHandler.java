package com.github.kyanbrix.features;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdatePrimaryGuildEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ServerMemberHandler extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(ServerMemberHandler.class);
    private static final long GUILD_ID = 1469324454470353163L;
    private static final long MEMBER_JOIN_LOG = 1417919579421806702L;
    private static final long MEMBER_LEFT_LOG_ID = 1480919286129229945L;
    private static final long SERVER_TAG_LOG_ID = 1480919662521749524L;
    private static final long MEMBER_ROLE_LOG_ID = 1417919996893597787L;
    private static final long MESSAGE_LOG_ID = 1417919677979562084L;

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        if (event.getGuild().getIdLong() != Constant.SERVER_CAFE_ID) return;

        if (event.getMember().getUser().isBot()) return;

        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (guild.getIdLong() != Constant.SERVER_CAFE_ID) return;

        TextChannel logChannel = event.getJDA().getTextChannelById(MEMBER_JOIN_LOG);

        String accountAge = TimeFormat.RELATIVE.format(member.getUser().getTimeCreated());

        MessageEmbed embed = new EmbedBuilder()
                .setAuthor("Member Joined", null, member.getAvatarUrl())
                .addField("User", String.format("%s (%s)", member.getAsMention(), member.getUser().getName()), false)
                .addField("Account Created", accountAge, false)
                .setColor(Color.ORANGE)
                .setThumbnail(member.getUser().getAvatarUrl())
                .setFooter("User ID: " + member.getId())
                .setTimestamp(Instant.now())
                .build();

        if (logChannel != null) logChannel.sendMessageEmbeds(embed).queue();
        else log.error("Member joined log channel is null");


    }

    @Override
    public void onGuildMemberRoleAdd(@NonNull GuildMemberRoleAddEvent event) {
        if (event.getGuild().getIdLong() != Constant.SERVER_CAFE_ID) return;

        Member member = event.getMember();


        JDA jda = event.getJDA();

        List<Role> roles = event.getRoles();
        TextChannel logChannel = jda.getTextChannelById(1417919996893597787L);

        if (logChannel == null) {
            log.error("Log channel for member role add is null");
            return;
        }

        for (Role role : roles) {
            EmbedBuilder builder = new EmbedBuilder()
                    .setAuthor(member.getUser().getName(),null,member.getUser().getAvatarUrl())
                    .setDescription(String.format("**%s** was added from the ``%s`` role",member.getUser().getName(),role.getName()))
                    .setColor(Color.green)
                    .setTimestamp(Instant.now())
                    .setFooter("User ID: "+member.getId());

            logChannel.sendMessageEmbeds(builder.build()).queue();
        }

    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {

        if (event.getGuild().getIdLong() != Constant.SERVER_CAFE_ID) return;

        if (event.getUser().isBot()) return;

        TextChannel logChannel = event.getJDA().getTextChannelById(MEMBER_LEFT_LOG_ID);
        User user = event.getUser();

        if (logChannel != null) {

            MessageEmbed embed = new EmbedBuilder()
                    .setAuthor("User Left", null, user.getAvatarUrl())
                    .setDescription(String.format("%s (%s) has left the server", user.getAsMention(), user.getName()))
                    .setColor(Color.decode("#FF0000"))
                    .setThumbnail(user.getAvatarUrl())
                    .setFooter("ID: " + user.getIdLong())
                    .build();

            logChannel.sendMessageEmbeds(embed).queue();

        }



    }


    @Override
    public void onMessageUpdate(@NonNull MessageUpdateEvent event) {

        if (event.getGuild().getIdLong() != Constant.SERVER_CAFE_ID) return;


        if (!event.isFromGuild()) return;
        if (event.getAuthor().isBot()) return;


        String updatedMessage = event.getMessage().getContentRaw();

        try (Connection connection = Caffein.getInstance().getConnection()) {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM user_messages WHERE message_id = ?");
            ps.setLong(1,event.getMessageIdLong());

            try (ResultSet set = ps.executeQuery()) {

                if (set.next()) {
                    JDA jda = event.getJDA();
                    String content = set.getString("user_message");
                    long userId = set.getLong("user_id");
                    User user = jda.getUserById(userId);

                    if (user == null) {
                        log.error("This user is not found!");
                        return;
                    }

                    if (updatedMessage.equals(content)) return;

                    TextChannel messageLogChannel = jda.getTextChannelById(Constant.MESSAGE_LOG_ID);

                    if (messageLogChannel == null) return;

                    MessageEmbed embed = new EmbedBuilder()
                            .setAuthor(user.getName(),null,user.getAvatarUrl())
                            .setDescription(String.format("### Message Edited in %s | %s",event.getChannel().getAsMention(),event.getMessage().getJumpUrl()))
                            .addField("Before",content,false)
                            .addField("After",updatedMessage,false)
                            .setColor(Color.decode("#90EE90"))
                            .setTimestamp(Instant.now())
                            .setFooter("User ID: "+userId)
                            .build();



                    messageLogChannel.sendMessageEmbeds(embed).queue();

                }

            }


        }catch (SQLException e) {
            log.error("Error on update message",e);
        }



    }

    @Override
    public void onMessageDelete(@NonNull MessageDeleteEvent event) {

        if (event.getGuild().getIdLong() != Constant.SERVER_CAFE_ID) return;

        long messageId = event.getMessageIdLong();
        JDA jda = event.getJDA();

        String channel = event.getChannel().getAsMention();

        String query = "SELECT m.user_id, m.user_message, a.attachment_url " +
                "FROM user_messages m " +
                "LEFT JOIN message_attachments a ON m.message_id = a.message_id " +
                "WHERE m.message_id = ? ";
        try (Connection connection = Caffein.getInstance().getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setLong(1,messageId);


            try (ResultSet set = preparedStatement.executeQuery()) {
                long userId = 0;
                List<String> attachments = new ArrayList<>();
                String content = null;

                while (set.next()) {

                    userId = set.getLong("user_id");
                    content = set.getString("user_message");

                    String attachment = set.getString("attachment_url");

                    if (attachment != null) {
                        attachments.add(attachment);
                    }

                }


                User user = jda.getUserById(userId);

                if (user == null) {
                    log.warn("User is null");
                    return;
                }

                EmbedBuilder builder = new EmbedBuilder()
                        .setColor(Color.RED)
                        .setAuthor(user.getName(),null,user.getAvatarUrl())
                        .setDescription(String.format("### Message sent by %s Deleted in %s\n%s",user.getAsMention(),channel,content != null && !content.isEmpty() ? content : ""))
                        .setTimestamp(Instant.now())
                        .setFooter("User ID: "+user.getIdLong());

                TextChannel logChannel = jda.getTextChannelById(MESSAGE_LOG_ID);

                if (logChannel == null) {
                    log.warn("Message Log channel is null");
                    return;
                }

                if (!attachments.isEmpty()) {

                    if (attachments.size() > 1) {

                        for (String attachment : attachments) {

                            logChannel.sendMessageEmbeds(builder.build()).flatMap(message -> message.getChannel().sendMessage(attachment)).queue();

                        }

                    }else logChannel.sendMessageEmbeds(builder.build()).flatMap(message -> message.getChannel().sendMessage(attachments.getFirst())).queue();


                }else logChannel.sendMessageEmbeds(builder.build()).queue();

                try (PreparedStatement deletePs = connection.prepareStatement("DELETE FROM user_messages WHERE message_id = ? ")) {
                    deletePs.setLong(1,messageId);
                    deletePs.executeUpdate();
                }


            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }


    }

    @Override
    public void onUserUpdatePrimaryGuild(@NotNull UserUpdatePrimaryGuildEvent event) {
        Guild guild = event.getJDA().getGuildById(GUILD_ID);
        if (guild == null) return;

        TextChannel logChannel = event.getJDA().getTextChannelById(SERVER_TAG_LOG_ID);

        if (logChannel == null) {
            log.error("Primary guild log channel is null for guild {}", GUILD_ID);
            return;
        }

        User user = event.getUser();
        User.PrimaryGuild newGuild = event.getNewPrimaryGuild();
        User.PrimaryGuild oldGuild = event.getOldPrimaryGuild();

        Role role = guild.getRoleById(1485312660193935661L);

        Member member = guild.getMemberById(user.getIdLong());

        EmbedBuilder builder = new EmbedBuilder()
                .setAuthor(user.getName(), null, user.getAvatarUrl())
                .setTimestamp(Instant.now());

        boolean hadTag = oldGuild != null && oldGuild.getIdLong() == GUILD_ID;
        boolean hasTag = newGuild != null && newGuild.getIdLong() == GUILD_ID;

        if (hasTag && !hadTag) {
            builder.setDescription(String.format("%s used our server tag", user.getAsMention()))
                    .setColor(Color.green);

            if (role != null && member != null) guild.addRoleToMember(member,role).queue();

        } else if (!hasTag && hadTag) {
            builder.setDescription(String.format("%s removed our server tag from their profile", user.getAsMention()))
                    .setColor(Color.decode("#FF4500"));

            if (member == null) return;

            if (member.getRoles().contains(role) && role != null) guild.removeRoleFromMember(member,role).queue();

        } else {
            return;
        }

        logChannel.sendMessageEmbeds(builder.build()).queue();
    }


    //Message Logs
    @Override
    public void onMessageReceived(@NonNull MessageReceivedEvent event) {
        if (event.getGuild().getIdLong() != Constant.SERVER_CAFE_ID) return;

        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        if (event.getGuild().getIdLong() != Constant.SERVER_CAFE_ID) return;


        long messageId = event.getMessageIdLong();

        long userId = event.getAuthor().getIdLong();
        String content = event.getMessage().getContentRaw();
        List<Message.Attachment> attachments = event.getMessage().getAttachments();
        JDA jda = event.getJDA();

        TextChannel channel = jda.getTextChannelById(1480958059755864235L);

        if (channel == null) return;

        try (Connection connection = Caffein.getInstance().getConnection();
             PreparedStatement ps1 = connection.prepareStatement("INSERT INTO user_messages (message_id, user_id , user_message) VALUES (?,?,?)")){
             ps1.setLong(1,messageId);
             ps1.setLong(2,userId);
             ps1.setString(3,content);
             ps1.executeUpdate();
        }catch (SQLException e) {
            log.error(e.getMessage());
        }


        if (!attachments.isEmpty()) {

            attachments.forEach(attachment -> attachment.getProxy().download().thenAccept(inputStream -> channel.sendFiles(FileUpload.fromData(inputStream,attachment.getFileName())).queue(message -> {

                try (Connection connection = Caffein.getInstance().getConnection();
                     PreparedStatement ps = connection.prepareStatement("INSERT INTO message_attachments (message_id,attachment_url) VALUES (?,?)")) {
                    ps.setLong(1,messageId);
                    ps.setString(2,message.getAttachments().getFirst().getUrl());

                    ps.executeUpdate();

                }catch (SQLException e) {
                    log.error("Error file upload",e);
                }

            })));


        }




    }

    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
        if (event.getGuild().getIdLong() != Constant.SERVER_CAFE_ID) return;


        Member member = event.getMember();

        Guild guild = event.getGuild();

        JDA jda = event.getJDA();

        if (event.getRoles().contains(guild.getBoostRole())) {

            try (Connection connection = Caffein.getInstance().getConnection()) {

                try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM premium_role WHERE user_id = ?")) {
                    ps.setLong(1, member.getIdLong());

                    try (ResultSet set = ps.executeQuery()) {

                        if (set.next()) {

                            long roleId = set.getLong("role_id");

                            Role role = guild.getRoleById(roleId);

                            if (role != null) guild.removeRoleFromMember(member, role).queue();

                            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM premium_role WHERE user_id = ?")) {
                                delete.setLong(1, member.getIdLong());
                                delete.executeUpdate();

                                log.info("Successfully remove a user from premium_role");
                            }


                        }


                    }

                }

            } catch (SQLException e) {
                log.error("Error on retrieving data from premium_role", e);
            }


        }

        List<Role> roles = event.getRoles();
        TextChannel logChannel = jda.getTextChannelById(MEMBER_ROLE_LOG_ID);

        if (logChannel == null) {
            log.error("Log channel for member role removed is null");
            return;
        }

        roles.forEach(role -> {

            EmbedBuilder builder = new EmbedBuilder()
                    .setAuthor(member.getUser().getName(),null,member.getUser().getAvatarUrl())
                    .setDescription(String.format("**%s** was removed from the ``%s`` role",member.getUser().getName(),role.getName()))
                    .setColor(Color.orange)
                    .setTimestamp(Instant.now())
                    .setFooter("User ID: "+member.getId());

            logChannel.sendMessageEmbeds(builder.build()).queue();
        });

    }



}
