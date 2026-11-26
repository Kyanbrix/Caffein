package com.github.kyanbrix.component.slashcommand;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.context.MessageContextInteraction;
import org.jetbrains.annotations.NotNull;

/** A right-click → Apps command on a message. Registered with {@link SlashManager#addContextCommands}. */
public interface IMessageContext {

    void execute(@NotNull MessageContextInteraction event);

    @NotNull
    CommandData getCommandData();

}
