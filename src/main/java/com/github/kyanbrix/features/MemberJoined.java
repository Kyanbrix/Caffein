package com.github.kyanbrix.features;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MemberJoined extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(MemberJoined.class);

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {


        if (event.getMember().getUser().isBot()) return;


        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (guild.getIdLong() == 1357336100514828411L) return;


        WebhookClientBuilder webhookClientBuilder = new WebhookClientBuilder("https://discord.com/api/webhooks/1472353251578876197/CUL1FXmhAPL1XrPKqE8ghULfmd1jJT1KgZprgEUjUbRyrL3AQT0CsniAG_VaY3UfQ8fQ");

        webhookClientBuilder.setThreadFactory(kian -> {
            Thread thread = new Thread(kian);

            thread.setDaemon(true);
            thread.setName("Cafe");
            return thread;

        });

        WebhookClient webhookClient = webhookClientBuilder.build();

        WebhookEmbed.EmbedAuthor author = new WebhookEmbed.EmbedAuthor(member.getUser().getName(),(member.getUser().getAvatarUrl() == null ? member.getUser().getDefaultAvatarUrl() : member.getUser().getAvatarUrl()),null);


        WebhookEmbed webhookEmbed = new WebhookEmbedBuilder()
                .setAuthor(author)
                .setDescription(String.format("%s has joined the server",member.getAsMention()))
                .setColor(15316501)
                .build();

        WebhookMessage webhookMessage = new WebhookMessageBuilder()
                .setUsername(guild.getName())
                .setAvatarUrl(guild.getIconUrl())
                .addEmbeds(webhookEmbed)
                .build();


        webhookClient.send(webhookMessage).thenAccept(msg-> System.out.println("New member joined"));

    }


    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {

        if (event.getUser().isBot()) return;

        long memberIdLong = event.getUser().getIdLong();

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
}
