package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.*;

public class GetUserBanner implements ICommand{
    @Override
    public void accept(MessageReceivedEvent event) {


        Message originalMessage = event.getMessage();
        MessageChannelUnion channel = event.getChannel();


        EmbedBuilder builder = new EmbedBuilder();

        if (originalMessage.getType().equals(MessageType.INLINE_REPLY)) {

            Message referenceMessage = originalMessage.getReferencedMessage();

            if (referenceMessage != null) {

                User user = referenceMessage.getAuthor();

                if (user.getIdLong() == Constant.KIAN_ID) return;

                user.retrieveProfile().queue(profile -> {

                    builder.setAuthor(user.getName()+"'s banner",null,user.getAvatarUrl());
                    builder.setImage(profile.getBanner().getUrl(600));
                    builder.setColor(Color.decode("#FFE4C4"));
                    builder.setFooter("Requested by: "+originalMessage.getAuthor().getName());

                    channel.sendMessageEmbeds(builder.build()).queue();
                });

            }

            return;
        }



        try {
            User mentionUser = originalMessage.getMentions().getUsers().getFirst();


            if (mentionUser.getIdLong() == Constant.KIAN_ID) return;


            mentionUser.retrieveProfile().queue(profile -> {


                if (profile.getBanner() == null) {
                    originalMessage.reply("That user you've mentioned has no banner!").queue();
                    return;
                }

                builder.setAuthor(mentionUser.getName()+"'s banner",null,mentionUser.getAvatarUrl());
                builder.setImage(profile.getBanner().getUrl(600));
                builder.setColor(Color.decode("#FFE4C4"));
                builder.setFooter("Requested by: "+originalMessage.getAuthor().getName());

                channel.sendMessageEmbeds(builder.build()).queue();

            },new ErrorHandler().handle(ErrorResponse.UNKNOWN_USER,e -> originalMessage.reply("Cannot find this user you mentioned!").queue()));


        }catch (Exception e) {

            String id = extractText(originalMessage.getContentRaw());

            event.getJDA().retrieveUserById(id).queue(user -> {

                user.retrieveProfile().queue(profile -> {

                    if (profile.getBanner() == null) {
                        originalMessage.reply("That user you've mentioned no profile banner!").queue();

                    }else {
                        profile.getBanner().download(600).whenComplete((inputStream, throwable) -> {

                            channel.sendMessage(user.getName()+"'s banner").addFiles(FileUpload.fromData(inputStream,(profile.getBannerUrl().contains("gif") ? "banner.gif" : "banner.png"))).queue();

                        });
                    }


                });

            },new ErrorHandler().handle(ErrorResponse.UNKNOWN_USER,e1 ->  originalMessage.reply("User not found!").queue()));


        }









    }

    @Override
    public String commandName() {
        return "banner";
    }


    private String extractText(String message) {

        return message.substring(Constant.PREFIX.length() + commandName().length()).strip();


    }
}
