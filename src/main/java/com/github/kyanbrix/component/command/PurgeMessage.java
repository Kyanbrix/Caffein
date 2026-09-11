package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.List;

public class PurgeMessage implements ICommand {
    private static final Logger log = LoggerFactory.getLogger(PurgeMessage.class);

    @Override
    public void accept(MessageReceivedEvent event) {

        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) return;



        Message message = event.getMessage();

        final String command = removePrefix(message.getContentRaw().toLowerCase());
        String[] splitCommand = command.split(" ");

        MessageChannelUnion channel = event.getChannel();

        List<User> mentions = message.getMentions().getUsers();


        if (!mentions.isEmpty()) {

            if (splitCommand.length == 1) {

                message.delete().queue();

                Guild guild = event.getGuild();

                List<TextChannel> channels = guild.getTextChannels();




                for (TextChannel ch : channels) {

                    ch.getIterableHistory().takeAsync(5)
                            .thenApply(messages -> messages.stream()
                                    .filter(message1 -> message1.getAuthor() == mentions.getFirst() && message1.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(5))).toList())
                            .thenAccept(ch::purgeMessages)
                            .whenComplete((unused, throwable) -> {
                                if (throwable != null) {
                                    log.error(throwable.getMessage());
                                }

                            });

                }


                MessageEmbed embed = new EmbedBuilder()
                        .setDescription(String.format("%s messages has been purged!",mentions.getFirst().getAsMention()))
                        .setColor(Color.red)
                        .build();

                event.getChannel().sendMessageEmbeds(embed).queue();
                return;
            }

            User targetUser = mentions.getFirst();
            String messageId = splitCommand[1];

            channel.retrieveMessageById(messageId).queue(targetMessage -> {
                System.out.println("Target User: "+ targetUser.getName());

                channel.getIterableHistory().takeUntilAsync(msg -> msg.getIdLong() == targetMessage.getIdLong())
                        .thenApply(messages -> messages.stream().filter(userMessage-> userMessage.getAuthor() == targetUser).toList())
                        .thenAccept(channel::purgeMessages)
                        .whenComplete((unused, throwable) -> {

                            if (throwable != null) {
                                channel.sendMessage("Something went wrong!").queue();
                                log.error("Purge Error",throwable);
                                return;
                            }

                            log.info("Purge is success!");

                        });
            },new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE,e -> {

                try {
                    int numberOfMessageToPurge = Integer.parseInt(splitCommand[1]);

                    channel.getIterableHistory().takeAsync(numberOfMessageToPurge)
                            .thenApply(messages -> messages.stream().filter(message1 -> message1.getAuthor() == targetUser).toList())
                            .thenAccept(channel::purgeMessages)
                            .whenComplete((unused, throwable) -> {
                                if (throwable != null) {
                                    channel.sendMessage("Something went wrong!").queue();
                                    return;
                                }

                                log.info("Purged a user messages!");
                            });


                }catch (NumberFormatException exception) {

                    log.error("Probably that number is not an actual number!",exception);

                }

            }));


        }else {


            try {
                int numberOfMessageToPurge = Integer.parseInt(splitCommand[0]);

                channel.getIterableHistory().takeAsync(numberOfMessageToPurge)
                        .thenAccept(channel::purgeMessages)
                        .whenComplete((unused, throwable) -> {
                            if (throwable != null) {
                                channel.sendMessage("Something went wrong!").queue();
                                return;
                            }

                            log.info("Purged a messages in {}",channel.getName());
                        });


            }catch (NumberFormatException exception) {

                log.error("Not a number!",exception);

            }



        }




    }

    @Override
    public String commandName() {
        return "purge";
    }

    private String removePrefix(String message) {

        return message.substring(Constant.PREFIX.length() + commandName().length()).strip();

    }

}
