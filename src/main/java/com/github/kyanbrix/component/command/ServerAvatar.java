package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;

public class ServerAvatar implements ICommand{
    @Override
    public void accept(MessageReceivedEvent event) {


        Member member = event.getMember();

        String message = removePrefix(event.getMessage().getContentRaw().toLowerCase());
        MessageChannelUnion channel = event.getChannel();

        EmbedBuilder em = new EmbedBuilder();
        em.setColor(Color.decode("#FFE4C4"));


        if (message.isEmpty()) {

            em.setAuthor(member.getUser().getName()+"'s avatar",null,member.getEffectiveAvatarUrl());
            em.setImage(member.getEffectiveAvatar().getUrl(600));

            channel.sendMessageEmbeds(em.build()).queue();

        }




    }

    @Override
    public String[] aliases() {
        return new String[]{"server-av","serv-avatar","sv-avatar","sv-dp","server-avatar"};
    }

    @Override
    public String commandName() {
        return "sv-av";
    }

    private String removePrefix(String message) {

        for (String alias : aliases()) {

            if (message.contains(alias)) {

                return message.substring(Constant.PREFIX.length() + alias.length()).strip();

            }
        }

        return message.substring(Constant.PREFIX.length() + commandName().length()).strip();


    }
}
