package com.github.kyanbrix.component.slashcommand;

import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Authentication implements ISlash{


    private static final Logger log = LoggerFactory.getLogger(Authentication.class);

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS) // Set explicit connection timeout
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(1, TimeUnit.MINUTES)
            .build();


    @Override
    public void execute(@NonNull SlashCommandInteraction event) {


        event.deferReply().queue();

        HttpUrl urlBuilder = HttpUrl.parse("https://geo.ipify.org/api/v2/country").newBuilder()
                .addQueryParameter("apiKey",System.getenv("GEO_API")).build();

        Request request = new Request.Builder()
                .url(urlBuilder)
                .addHeader("User-Agent", "CaffeinBot/1.0 (kyanbrix@yahoo.com)") // Required by IP APIs
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (response.isSuccessful()) {

                event.getHook().sendMessage(
                        responseBody
                ).queue();

            }else event.getHook().sendMessage(responseBody).queue();

        }catch (IOException e) {
            event.getHook().sendMessage(e.getMessage()).setEphemeral(true).queue();
            e.printStackTrace();
        }



    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("authenticate","Get User Top Music Artist").setContexts(InteractionContextType.GUILD,InteractionContextType.PRIVATE_CHANNEL).setIntegrationTypes(IntegrationType.GUILD_INSTALL,IntegrationType.USER_INSTALL);
    }
}
