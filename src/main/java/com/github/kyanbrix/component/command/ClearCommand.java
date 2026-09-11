package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ClearCommand implements ICommand{

    private static final Logger log = LoggerFactory.getLogger(ClearCommand.class);

    @Override
    public void accept(MessageReceivedEvent event) {

        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) return;


        final String command = removePrefix(event.getMessage().getContentRaw().toLowerCase());
        MessageChannel channel = event.getChannel();

        if (command.isEmpty()) {
            channel.sendMessage("You need to specify how many messages should I clear. ``Ex. !clear 20``").flatMap(Message::delete).delay(1, TimeUnit.MINUTES).queue();
            return;
        }




        try {
            int numberOfMessagesToDelete = Integer.parseInt(command);

            channel.getIterableHistory().takeAsync(numberOfMessagesToDelete).thenAccept(channel::purgeMessages).whenComplete((unused, throwable) -> {

                if (throwable != null) {
                    log.error("Something went wrong when purging a message");
                }
            });


        }catch (NumberFormatException e) {
            log.warn("Cannot clear message, input is not a number");
            channel.sendMessage("Im not sure about this command!").flatMap(Message::delete).delay(15,TimeUnit.SECONDS).queue();
        }





    }


    @Override
    public String commandName() {
        return "clear";
    }

    private String removePrefix(String message) {
        return message.substring(Constant.PREFIX.length() + commandName().length()).strip();
    }


}
