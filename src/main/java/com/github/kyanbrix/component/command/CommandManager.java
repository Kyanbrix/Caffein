package com.github.kyanbrix.component.command;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.utils.Constant;
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
        this.addCommands(new GetUserAvatar());
        this.addCommands(new GetUserBanner());
    }



    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {

        //Will not receive from dms or bots
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        final String command = event.getMessage().getContentRaw().toLowerCase().trim();
        final String PREFIX = Constant.PREFIX;



        if (command.startsWith(PREFIX)) {

            commands.forEach((s, iCommand) -> {

                final String[] offset = command.split(" ");
                final String cmd = offset[0].substring(PREFIX.length());

                if (s.equalsIgnoreCase(cmd)) {

                    iCommand.accept(event);

                }else {
                    for (String alias : iCommand.aliases()) {

                        if (alias.equalsIgnoreCase(cmd)) {

                            iCommand.accept(event);

                            return;
                        }

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
