package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;

public class GetUserAvatar implements ICommand{
    @Override
    public void accept(MessageReceivedEvent event) {


        Message message = event.getMessage();

        MessageChannelUnion channel = event.getChannel();



        if (message.getType().equals(MessageType.INLINE_REPLY)) {

            Message referenceMessage = message.getReferencedMessage();

            if (referenceMessage != null) {

                User user = referenceMessage.getAuthor();

                if (user.getIdLong() == Constant.KIAN_ID) return;

                MessageEmbed embed = new EmbedBuilder()
                        .setAuthor(user.getName()+"'s avatar",null,user.getAvatarUrl())
                        .setImage(user.getAvatar().getUrl(600))
                        .setFooter("Requested by: "+message.getAuthor().getName())
                        .setColor(Color.decode("#FFE4C4"))
                        .build();

                channel.sendMessageEmbeds(embed).queue();

            }
            return;
        }

        try {
            User mentionUser = message.getMentions().getUsers().getFirst();

            if (mentionUser.getIdLong() == 683613536823279794L) return;

            String userAvatarUrl = (mentionUser.getAvatarUrl() == null ? mentionUser.getDefaultAvatarUrl() : mentionUser.getAvatar().getUrl(600));

            MessageEmbed embed = new EmbedBuilder()
                    .setAuthor(mentionUser.getName()+"'s avatar",null,userAvatarUrl)
                    .setImage(userAvatarUrl)
                    .setFooter("Requested by: "+message.getAuthor().getName())
                    .setColor(Color.decode("#FFE4C4"))
                    .build();



            channel.sendMessageEmbeds(embed).queue();




        }catch (Exception e) {

            String userId = removePrefix(message.getContentRaw());

            event.getJDA().retrieveUserById(userId).queue(user ->  {

                MessageEmbed embed = new EmbedBuilder()
                        .setAuthor(user.getName()+"'s avatar",null,user.getAvatarUrl())
                        .setImage(user.getAvatar().getUrl(600))
                        .setFooter("Requested by: "+message.getAuthor().getName())
                        .setColor(Color.decode("#FFE4C4"))
                        .build();


                channel.sendMessageEmbeds(embed).queue();
            });

        }





    }

    @Override
    public String[] aliases() {
        return new String[]{"avatar","profile"};
    }

    @Override
    public String commandName() {
        return "av";
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
