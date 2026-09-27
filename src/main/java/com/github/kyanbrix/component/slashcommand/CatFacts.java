package com.github.kyanbrix.component.slashcommand;

import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CatFacts implements ISlash {
    private static final Logger log = LoggerFactory.getLogger(CatFacts.class);

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {




    }

    @Override
    public @NonNull CommandData getCommandData() {

        OptionData optionData = new OptionData(OptionType.STRING,"breeds","Select cat breed",false,true);
        SubcommandData subcommandData = new SubcommandData("facts","Get cat facts").addOptions(optionData);


        return Commands.slash("cat","Meow meow").addSubcommands(subcommandData).setContexts(InteractionContextType.PRIVATE_CHANNEL,InteractionContextType.GUILD)
                .setIntegrationTypes(IntegrationType.USER_INSTALL,IntegrationType.GUILD_INSTALL);
    }
}
