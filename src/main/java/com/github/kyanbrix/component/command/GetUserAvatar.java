package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.ImageFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.Optional;

public class GetUserAvatar implements ICommand{
    private static final Logger log = LoggerFactory.getLogger(GetUserAvatar.class);

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

        List<User> users = message.getMentions().getUsers();

        if (!users.isEmpty()) {
            User mentionUser = message.getMentions().getUsers().getFirst();

            try {

                if (mentionUser.getIdLong() == 683613536823279794L) return;

                String userAvatarUrl = mentionUser.getEffectiveAvatar().getUrl(600);

                MessageEmbed embed = new EmbedBuilder()
                        .setAuthor(mentionUser.getName()+"'s avatar",null,userAvatarUrl)
                        .setImage(userAvatarUrl)
                        .setFooter("Requested by: "+message.getAuthor().getName())
                        .setColor(Color.decode("#FFE4C4"))
                        .build();

                channel.sendMessageEmbeds(embed).queue();



            }catch (Exception e) {

                log.error("Cannot retrieve mentioned user avatar!",e);

            }

            return;
        }

        String userId = removePrefix(message.getContentRaw());


        if (userId.isEmpty()) {
            User author = event.getAuthor();
            String authorAvatarUrl = author.getEffectiveAvatar().getUrl(600);
            MessageEmbed embed = new EmbedBuilder()
                    .setAuthor(author.getName()+"'s avatar",null,authorAvatarUrl)
                    .setImage(authorAvatarUrl)
                    .setColor(Color.decode("#FFE4C4"))
                    .build();

            channel.sendMessageEmbeds(embed).queue();


            return;
        }



        JDA jda = event.getJDA();




        jda.retrieveUserById(userId).queue(user -> {



            user.getEffectiveAvatar().download(600).whenComplete((inputStream, throwable) -> {


                if (throwable != null) {

                    log.error("Cannot retrieve user avatar!",throwable);
                    return;
                }

                channel.sendMessage(user.getName()+"'s avatar").addFiles(FileUpload.fromData(inputStream,"avatar.png")).queue();

            });



        },new ErrorHandler().handle(ErrorResponse.UNKNOWN_USER,e -> channel.sendMessageEmbeds(new EmbedBuilder().setDescription("User not found!").setColor(Color.red).build()).queue()));













    }

    @Override
    public String[] aliases() {
        return new String[]{"avatar","profile","pf","dp"};
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
