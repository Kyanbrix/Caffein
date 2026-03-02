package com.github.kyanbrix.features;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RantMessages extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(RantMessages.class);

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {

        if (event.getChannel().getIdLong() == 1477966443600679053L) {

            Guild guild = event.getGuild();
            Member member = event.getMember();
            Message message = event.getMessage();

            //https://discord.com/api/webhooks/1477966705174249556/6bZbgbzxWFNUzpKCCS5AJefpTo1Q98_YTRy__THPH99Z-ErA5DrIuywGP5LIuPBh77UG
            TextChannel logChannel = guild.getTextChannelById(1477965995451748373L);

            if (logChannel != null && member != null) {

                WebhookClientBuilder webhookClientBuilder = new WebhookClientBuilder("https://discord.com/api/webhooks/1477966705174249556/6bZbgbzxWFNUzpKCCS5AJefpTo1Q98_YTRy__THPH99Z-ErA5DrIuywGP5LIuPBh77UG");

                WebhookMessage webhookMessage = new WebhookMessageBuilder()
                        .setUsername((member.getNickname() != null ? member.getNickname() : member.getEffectiveName()))
                        .setContent(message.getContentRaw())
                        .setAvatarUrl(member.getUser().getAvatarUrl())
                        .build();

                WebhookClient webhookClient = webhookClientBuilder.build();

                webhookClient.send(webhookMessage).thenAccept(msg -> System.out.printf("Rant log "+msg.getContent()));
            }



        }


    }
}
