package com.github.kyanbrix.component.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public interface ICommand {

    void accept(MessageReceivedEvent event);

    String commandName();

    default void deleteMessage(MessageReceivedEvent event) {
        event.getMessage().delete().queue();
    }

}
