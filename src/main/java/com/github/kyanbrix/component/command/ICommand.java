package com.github.kyanbrix.component.command;

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

}
