package com.github.kyanbrix.features;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class MemberJoined extends ListenerAdapter {



    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {


        if (event.getMember().getUser().isBot()) return;


        Member member = event.getMember();
        Guild guild = event.getGuild();




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
}
