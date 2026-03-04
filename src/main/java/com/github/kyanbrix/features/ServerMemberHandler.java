package com.github.kyanbrix.features;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdatePrimaryGuildEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

public class ServerMemberHandler extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(ServerMemberHandler.class);
    private static final long GUILD_ID = 1477920217270194298L;

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {


        
        if (event.getMember().getUser().isBot()) return;

        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (guild.getIdLong() == 1357336100514828411L) return;

        TextChannel logChannel = guild.getTextChannelById(1477920159443456194L);

        WebhookClientBuilder webhookClientBuilder = new WebhookClientBuilder("https://discord.com/api/webhooks/1472353251578876197/CUL1FXmhAPL1XrPKqE8ghULfmd1jJT1KgZprgEUjUbRyrL3AQT0CsniAG_VaY3UfQ8fQ");

        webhookClientBuilder.setThreadFactory(kian -> {
            Thread thread = new Thread(kian);

            thread.setDaemon(true);
            thread.setName("Cafe");
            return thread;

        });

        WebhookClient webhookClient = webhookClientBuilder.build();

        WebhookEmbed.EmbedAuthor author = new WebhookEmbed.EmbedAuthor("New Member",guild.getIconUrl(),null);

        WebhookEmbed webhookEmbed = new WebhookEmbedBuilder()
                .setAuthor(author)
                .setDescription(String.format("%s has joined the server",member.getAsMention()))
                .setColor(15316501)
                .setThumbnailUrl(member.getUser().getAvatarUrl())
                .build();

        WebhookMessage webhookMessage = new WebhookMessageBuilder()
                .setUsername(guild.getName())
                .setAvatarUrl(guild.getIconUrl())
                .addEmbeds(webhookEmbed)
                .build();


        webhookClient.send(webhookMessage).thenAccept(msg-> System.out.println("New member joined"));


        String timestamp = TimeFormat.RELATIVE.format(member.getUser().getTimeCreated());

        MessageEmbed embed = new EmbedBuilder()
                .setAuthor("Member Joined",null,member.getAvatarUrl())
                .addField("User",String.format("%s (%s)",member.getAsMention(),member.getIdLong()),false)
                .addField("Account Created",timestamp,false)
                .setColor(Color.ORANGE)
                .setThumbnail(member.getUser().getAvatarUrl())
                .build();

        if (logChannel != null ) logChannel.sendMessageEmbeds(embed).queue();
        else log.error("Member joined log channel is null");

    }



    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {

        if (event.getUser().isBot()) return;

        long memberIdLong = event.getUser().getIdLong();

        Guild guild = event.getGuild();
        TextChannel logChannel = guild.getTextChannelById(1477920217270194298L);
        User user = event.getUser();

        if (logChannel != null) {

            MessageEmbed embed = new EmbedBuilder()
                    .setAuthor("User Left",null,user.getAvatarUrl())
                    .setDescription(String.format("%s (%s) has left the server",user.getAsMention(),user.getName()))
                    .setColor(Color.decode("#FF0000"))
                    .setThumbnail(user.getAvatarUrl())
                    .setFooter("ID: "+user.getIdLong())
                    .build();

            logChannel.sendMessageEmbeds(embed).queue();
        }


        try (Connection connection = Caffein.getInstance().getConnection()) {

            String deleteUserMessages = "DELETE FROM user_messages WHERE userid = ?";
            String deleteFromRegular = "DELETE FROM regulars WHERE user_id = ?";

            try (PreparedStatement ps = connection.prepareStatement(deleteUserMessages)) {

                ps.setObject(1,memberIdLong);
                ps.executeUpdate();

                try (PreparedStatement ps1 = connection.prepareStatement(deleteFromRegular)) {

                    ps1.setObject(1,memberIdLong);
                    ps1.executeUpdate();
                }

            }

        }catch (SQLException e) {
            log.error(e.getMessage(),e.fillInStackTrace());
        }


    }


    @Override
    public void onUserUpdatePrimaryGuild(@NotNull UserUpdatePrimaryGuildEvent event) {

        User.PrimaryGuild primaryGuild = event.getNewPrimaryGuild();
        User.PrimaryGuild oldGuild = event.getOldPrimaryGuild();
        User user = event.getUser();

        Guild guild = event.getJDA().getGuildById(GUILD_ID);

        TextChannel logChannel = guild.getTextChannelById(1477920635748352123L);



        EmbedBuilder builder = new EmbedBuilder();
        builder.setAuthor(user.getName(),null,user.getAvatarUrl());
        builder.setTimestamp(Instant.now());
        builder.setColor(Color.decode("#FF4500"));



        //no tag before then added a tag right after
        if (oldGuild == null && primaryGuild != null) {
            ;
            if (primaryGuild.getIdLong() == GUILD_ID) {
                builder.setDescription(String.format("%s used our server tag",user.getAsMention()));
            }



        }

        if (oldGuild != null && primaryGuild != null) {

            if (oldGuild.getIdLong() == GUILD_ID && primaryGuild.getIdLong() != GUILD_ID) {

                builder.setDescription(String.format("%s removed our server tag from their profile",user.getAsMention()));
                builder.setColor(Color.decode("#FF4500"));


            }else if (oldGuild.getIdLong() != GUILD_ID && primaryGuild.getIdLong() == GUILD_ID) {

                builder.setDescription(String.format("%s used our server tag",user.getAsMention()));
            }


        }

        //User has tag before then remove the tag
        if (oldGuild != null && primaryGuild == null) {

            if (oldGuild.getIdLong() == GUILD_ID) {
                builder.setDescription(String.format("%s removed our server tag from their profile",user.getAsMention()));
                builder.setColor(Color.decode("#FF4500"));

            }

        }


        if (logChannel == null) return;

        logChannel.sendMessageEmbeds(builder.build()).queue();



    }

}
