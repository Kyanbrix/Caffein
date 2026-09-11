package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;

public interface ICommand {

    void accept(MessageReceivedEvent event);

    String commandName();

    default String[] aliases() {
        return new String[0];
    }

    default void deleteMessage(MessageReceivedEvent event) {
        event.getMessage().delete().queue();
    }

    default String removePrefixCommand(String message) {
        return message.substring(Constant.PREFIX.length() + commandName().length()).strip();
    }
}
