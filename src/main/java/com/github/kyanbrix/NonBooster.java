package com.github.kyanbrix;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class NonBooster extends ListenerAdapter {
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild() || event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            return;
        }

        Guild guild = event.getGuild();
        Member member = event.getMember();
        Message message = event.getMessage();
        MessageChannelUnion channel = event.getChannel();

        if (!message.getAttachments().isEmpty()) {
            if (channel.getIdLong() == 1472277508845211679L) return;

            if (!member.getRoles().contains(guild.getBoostRole())) {
                message.delete().queue();
                sendNotifications(channel, member.getUser());
            }

            return;
        }

        if (!message.getMentions().getCustomEmojis().isEmpty()) {
            System.out.println("Custom Emojis Found");

            guild.retrieveEmojis().queue(richCustomEmojis -> {
                richCustomEmojis.forEach(richCustomEmoji -> {
                    if (!message.getMentions().getCustomEmojis().contains(richCustomEmoji)) {
                        MessageEmbed embed = new EmbedBuilder()
                                .setAuthor(member.getEffectiveName(), null, member.getAvatarUrl())
                                .setDescription("You cannot send an emoji outside from the server, you can only use **" + guild.getName() + "** emojis.\n\nYou can bypass this by **Boosting the Server!**")
                                .setColor(Color.ORANGE)
                                .build();

                        channel.sendMessage(member.getAsMention() + " you have a notification:")
                                .addEmbeds(embed)
                                .flatMap(Message::delete)
                                .delay(1, TimeUnit.MINUTES)
                                .queue();
                    }
                });
            });
        }
    }

    private void sendNotifications(MessageChannelUnion channel, User user) {
        MessageEmbed embed = new EmbedBuilder()
                .setAuthor(user.getName(), null, user.getAvatarUrl())
                .setDescription("You are not allowed to send an attachments in this channel. You can bypass this by **Boosting the Server** <@&1474656723078746155>. You can send attachments only in this channel <#1472277508845211679>\n\nThis helps us prevent spam and NSFW contents")
                .setColor(Color.ORANGE)
                .build();

        channel.sendMessageFormat("%s you have a notification:", user.getAsMention())
                .addEmbeds(embed)
                .flatMap(Message::delete)
                .delay(1, TimeUnit.MINUTES)
                .queue();
    }
}
