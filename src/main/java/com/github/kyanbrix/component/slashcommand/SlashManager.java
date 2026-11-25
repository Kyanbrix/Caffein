package com.github.kyanbrix.component.slashcommand;

import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class SlashManager extends ListenerAdapter {

    private final Map<String, ISlash> commands = new HashMap<>();
    private final Map<String, IMessageContext> contextCommands = new HashMap<>();

    private boolean updated = false;


    @Override
    public void onSlashCommandInteraction(@NonNull SlashCommandInteractionEvent event) {

        ISlash iSlash = commands.get(event.getName());

        if (iSlash == null) {
            event.reply("Command Not Found!").setEphemeral(true).queue();
            return;
        }

        iSlash.execute(event);


    }

    @Override
    public void onMessageContextInteraction(@NonNull MessageContextInteractionEvent event) {

        IMessageContext command = contextCommands.get(event.getName());

        if (command == null) {
            event.reply("Command Not Found!").setEphemeral(true).queue();
            return;
        }

        command.execute(event);
    }

    @Override
    public void onGuildReady(@NonNull GuildReadyEvent event) {

        if (updated) return;

        updated = true;
        event.getJDA().updateCommands()
                .addCommands(Stream.concat(
                        commands.values().stream().map(ISlash::getCommandData),
                        contextCommands.values().stream().map(IMessageContext::getCommandData)).toList())
                .queue();

    }

    public void addCommands(ISlash ... islashes) {

        for (ISlash iSlash : islashes) {

            final CommandData commandData = iSlash.getCommandData();

            commands.put(commandData.getName(),iSlash);
        }

    }

    public void addContextCommands(IMessageContext ... messageContexts) {

        for (IMessageContext messageContext : messageContexts) {
            contextCommands.put(messageContext.getCommandData().getName(), messageContext);
        }

    }

}
