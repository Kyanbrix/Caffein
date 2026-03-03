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

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {


        if (!event.isFromGuild() && !event.getAuthor().isBot()) {

            Message message = event.getMessage();
            JDA jda = event.getJDA();
            Guild guild = jda.getGuildById(1469324454470353163L);

            if (guild != null) {
                TextChannel channel = guild.getTextChannelById(1477966443600679053L);

                if (channel == null) return;

                Container container = Container.of(
                        TextDisplay.of("## "+message.getContentRaw()),
                        TextDisplay.of("-# DM this account to create anonymous rant message")
                );

                channel.sendMessageComponents(container).useComponentsV2().queue(message1 -> {
                    MessageEmbed logEmbed = new EmbedBuilder()
                            .setAuthor(message.getAuthor().getName(),null,message.getAuthor().getAvatarUrl())
                            .setDescription(message.getContentRaw())
                            .addField("Jump to Message",message1.getJumpUrl(),false)
                            .setColor(Color.decode("#F5F5DC"))
                            .setFooter("User ID: "+message.getAuthor().getIdLong())
                            .setTimestamp(Instant.now())
                            .build();

                    TextChannel rantLogs = guild.getTextChannelById(1477965995451748373L);

                    if (rantLogs != null) rantLogs.sendMessageEmbeds(logEmbed).queue();

                });






            }


        }



    }
}
