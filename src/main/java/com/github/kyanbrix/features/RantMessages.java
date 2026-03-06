package com.github.kyanbrix.features;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Instant;

public class RantMessages extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(RantMessages.class);
    private static final long RANT_CHANNEL_ID = 1477966443600679053L;
    private static final long RANT_LOG_CHANNEL_ID = 1477965995451748373L;
    private static final long GUILD_ID = 1469324454470353163L;

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {


        if (!event.isFromGuild() && !event.getAuthor().isBot()) {

            Message message = event.getMessage();
            JDA jda = event.getJDA();
            Guild guild = jda.getGuildById(GUILD_ID);

            if (guild != null) sendRantMessage(message,guild);

        }



    }


    private void sendRantMessage(Message message, Guild guild) {

        TextChannel channel = guild.getTextChannelById(RANT_CHANNEL_ID);

        if (channel == null) {
            log.error("Rant channel is null");
            return;
        }


        try (WebhookClient client = WebhookClient.withUrl(System.getenv("RANT_WEBHOOK"))) {

            WebhookMessage webhookMessage = new WebhookMessageBuilder()
                    .setAvatarUrl(message.getAuthor().getDefaultAvatarUrl())
                    .setUsername("Anonymous User")
                    .setContent(message.getContentRaw())
                    .build();

            client.send(webhookMessage);

            sendRantLogs(message,guild);

        }catch (Exception e) {
            log.error("Webhook Error",e);
        }

    }

    private void sendRantLogs(Message message, Guild guild) {

        TextChannel channel = guild.getTextChannelById(RANT_LOG_CHANNEL_ID);

        if (channel == null) {
            log.error("Rant log channel is null");
            return;
        }

        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(message.getAuthor().getName() + "("+message.getAuthor().getEffectiveName()+")",null,message.getAuthor().getAvatarUrl())
                .setDescription(message.getContentRaw())
                .addField("Jump to Message",message.getJumpUrl(),false)
                .setColor(Color.decode("#F5F5DC"))
                .setFooter("User ID: "+message.getAuthor().getIdLong())
                .setTimestamp(Instant.now())
                .build();

        channel.sendMessageEmbeds(embed).queue();

    }

}
