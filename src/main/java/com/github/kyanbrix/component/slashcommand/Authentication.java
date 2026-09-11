package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jspecify.annotations.NonNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Authentication implements ISlash{


    @Override
    public void execute(@NonNull SlashCommandInteraction event) {


        String userId = event.getUser().getId();

        String rawCallback = "https://kyanbrix.com/api/lastfm/callback?discord_id="+userId;

        String encodeCallback = URLEncoder.encode(rawCallback, StandardCharsets.UTF_8);

        String authUrl = String.format("https://www.last.fm/api/auth/?api_key=%s&cb=%s", System.getenv("LAST_FM_API_KEY"),encodeCallback);


        event.reply("Click the link below to link your Last.Fm acccount")
                .setEphemeral(true)
                .addComponents(ActionRow.of(Button.of(ButtonStyle.LINK,authUrl,"Authenticate")))
                .queue();


    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("authenticate","Get User Top Music Artist").setContexts(InteractionContextType.GUILD,InteractionContextType.PRIVATE_CHANNEL).setIntegrationTypes(IntegrationType.GUILD_INSTALL,IntegrationType.USER_INSTALL);
    }
}
