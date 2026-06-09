package com.github.kyanbrix.component.command;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class Shutdown implements ICommand {

    @Override
    public void accept(MessageReceivedEvent event) {

        if (event.getAuthor().getIdLong() != Constant.KIAN_ID) return;



        System.exit(0);

        Caffein.getInstance().getDockerManager().stopContainer();

    }

    @Override
    public String commandName() {
        return "shutdown";
    }
}
