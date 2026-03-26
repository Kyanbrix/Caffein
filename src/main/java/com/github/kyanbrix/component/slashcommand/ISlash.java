package com.github.kyanbrix.component.slashcommand;

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.checkerframework.checker.units.qual.N;
import org.jetbrains.annotations.NotNull;

public interface ISlash {

    void execute(@NotNull SlashCommandInteraction event);

    @NotNull
    CommandData getCommandData();

}
