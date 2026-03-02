package com.github.kyanbrix.features;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceStreamEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;

public class ServerVoiceLogs extends ListenerAdapter {

    @Override
    public void onGuildVoiceStream(@NotNull GuildVoiceStreamEvent event) {
        Guild guild = event.getGuild();
        User user = event.getMember().getUser();
        TextChannel logChannel = guild.getTextChannelById(1477921214272897125L);



        if (logChannel != null) {

            if (event.isStream()) {

                MessageEmbed embed = new EmbedBuilder()
                        .setAuthor(user.getName(),null,user.getAvatarUrl())
                        .setColor(Color.CYAN)
                        .setDescription(String.format("%s is streaming on %s",user.getAsMention(),event.getVoiceState().getChannel().getAsMention()))
                        .setTimestamp(Instant.now())
                        .build();

                logChannel.sendMessageEmbeds(embed).queue();
            }
        }

    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {

        AudioChannelUnion joinedChannel = event.getChannelJoined();
        AudioChannelUnion leftChannel = event.getChannelLeft();

        Guild guild = event.getGuild();
        User user = event.getMember().getUser();
        TextChannel logChannel = guild.getTextChannelById(1477921214272897125L);


        if (logChannel != null) {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setAuthor(user.getName(),null,user.getAvatarUrl());
            builder.setColor(Color.green);
            builder.setTimestamp(Instant.now());

            if (joinedChannel != null && leftChannel != null) {
                builder.setDescription(String.format("%s moved to %s (%s)",user.getAsMention(),joinedChannel.getAsMention(),joinedChannel.getName()));
                builder.setColor(Color.blue);
            }else if (joinedChannel != null) {
                builder.setDescription(String.format("%s has joined voice channel %s (%s)",user.getAsMention(),joinedChannel.getAsMention(),joinedChannel.getName()));
                builder.setColor(Color.green);

            }else if (leftChannel != null) {
                builder.setDescription(String.format("%s left voice channel %s (%s) ",user.getAsMention(),leftChannel.getAsMention(),leftChannel.getName()));
                builder.setColor(Color.orange);

            }


            logChannel.sendMessageEmbeds(builder.build()).queue();

        }




    }
}
