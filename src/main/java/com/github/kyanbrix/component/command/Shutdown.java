package com.github.kyanbrix.component.command;

import com.github.kyanbrix.utils.UserRoles;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class Shutdown implements ICommand {

    @Override
    public void accept(MessageReceivedEvent event) {

        if (event.getAuthor().getIdLong() != UserRoles.DEV_ID.getId()) return;

        // Exit from a separate thread: the shutdown hook waits for JDA's threads to
        // finish, and this callback runs on one of them.
        event.getMessage().reply("Shutting down…").mentionRepliedUser(false).submit()
                .whenComplete((message, error) -> new Thread(() -> System.exit(0), "Caffeine-Shutdown").start());
    }

    @Override
    public String commandName() {
        return "shutdown";
    }
}
