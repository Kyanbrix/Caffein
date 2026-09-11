package com.github.kyanbrix.utils;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.jspecify.annotations.Nullable;

public class LoggerSender {


    private final long CHANNEL_ID;
    private Guild guild;

    public LoggerSender(long channelId, Guild guild) {
        this.CHANNEL_ID = channelId;
        this.guild = guild;
    }


    public void sendMessageToLogsChannel(@Nullable MessageEmbed embed, String cont) {

        TextChannel textChannel = guild.getTextChannelById(CHANNEL_ID);

        if (textChannel == null) {return;}

        if (embed == null) {

            return;
        }


    }



}
