package com.github.kyanbrix.component.command;

import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class CommandManager extends ListenerAdapter {


    private final Map<String,ICommand> commands = new HashMap<>();



    public CommandManager() {
        this.addCommands(new MessageCommandMaker());
        this.addCommands(new RoleSelectionCommand());
    }



    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {

        //Will not receive from dms or bots
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        final String command = event.getMessage().getContentRaw().toLowerCase().trim();
        final String PREFIX = "!!";

        if (command.startsWith(PREFIX)) {

            commands.forEach((s, iCommand) -> {

                if (command.startsWith(PREFIX) && command.startsWith(s,PREFIX.length())) {

                    try {
                        iCommand.accept(event);

                    }catch (Exception e) {
                        System.out.println(e.fillInStackTrace().toString());
                    }

                }





            });

        }


    }


    private void addCommands(final ICommand iCommand) {

        if (commands.containsKey(iCommand.commandName())) throw new IllegalArgumentException("Duplicate Command name");

        commands.put(iCommand.commandName(),iCommand);

    }

}
