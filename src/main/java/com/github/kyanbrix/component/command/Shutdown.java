package com.github.kyanbrix.component.command;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.utils.Constant;
import com.github.kyanbrix.utils.UserRoles;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class Shutdown implements ICommand {

    @Override
    public void accept(MessageReceivedEvent event) {

        if(event.getAuthor().getIdLong() != UserRoles.DEV_ID.getId()) return;

        System.exit(0);

        Caffein.getInstance().getDockerManager().stopContainer();

    }

    @Override
    public String commandName() {
        return "shutdown";
    }
}
