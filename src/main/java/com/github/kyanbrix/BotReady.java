package com.github.kyanbrix;

import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class BotReady extends ListenerAdapter {
    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {


        event.getJDA().updateCommands().queue();

        System.out.println("Bot is ready! " + event.getGuild().getId());


    }
}
