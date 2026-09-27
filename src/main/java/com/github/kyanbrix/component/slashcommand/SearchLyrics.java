package com.github.kyanbrix.component.slashcommand;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kyanbrix.component.slashcommand.responses.LrcLibResponse;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SearchLyrics implements ISlash{

    private static final Logger log = LoggerFactory.getLogger(SearchLyrics.class);
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    @Override
    public void execute(@NonNull SlashCommandInteraction event) {

        event.deferReply().queue();

        String trackName = event.getOption("song", OptionMapping::getAsString);
        String artist = event.getOption("artist", OptionMapping::getAsString);
        String album = event.getOption("album", OptionMapping::getAsString);

        HttpUrl.Builder urlBuilder = HttpUrl.parse("https://lrclib.net/api/search").newBuilder();
        urlBuilder.addQueryParameter("track_name",trackName);
        urlBuilder.addQueryParameter("artist_name",artist);

        if (album != null) {
            urlBuilder.addQueryParameter("album_name",album);
        }

        Request request = new Request.Builder().url(urlBuilder.build())
                .addHeader("User-Agent", "Kian Bot v1.0.0 (kyanbrix@yahoo.com)")
                .get()
                .build();

        try(Response response = client.newCall(request).execute()) {

            if (response.isSuccessful()) {
                LrcLibResponse[] results = mapper.readValue(response.body().string(), LrcLibResponse[].class);

                if (results.length > 0 && results[0].plainLyrics != null) {
                    LrcLibResponse track = results[0];
                    var lyrics = results[0].plainLyrics;
                    List<MessageEmbed> embeds = new ArrayList<>();

                    while (!lyrics.isEmpty() && embeds.size() < 10) {

                        int chuckSize = 3000;
                        String chunk;

                        if (lyrics.length() <= chuckSize) {

                            chunk = lyrics;
                            lyrics = "";
                        }else {
                            int splitIndex = lyrics.lastIndexOf("\n",chuckSize);

                            if (splitIndex <= 0) {
                                splitIndex = chuckSize;
                            }

                            chunk = lyrics.substring(0,splitIndex);
                            lyrics = lyrics.substring(splitIndex).stripLeading();
                        }


                        EmbedBuilder embed = new EmbedBuilder()
                                .setDescription(chunk);

                        if (embeds.isEmpty()) {
                            embed.setTitle(track.trackName+" - "+track.artistName);
                        }

                        embeds.add(embed.build());

                    }//while loop end


                    event.getHook().sendMessageEmbeds(embeds).queue();
                }else event.getHook().sendMessage("No lyrics found!").queue();


            }else event.reply(response.code()+" Code\nResponse Error: "+response.body().string()).setEphemeral(true).queue();

        }catch (Exception e){
            log.error(e.getMessage(),e);
        }

    }

    @Override
    public @NonNull CommandData getCommandData() {
        SubcommandData subcommandData = new  SubcommandData("lyrics", "Get current lyrics")
                .addOption(OptionType.STRING,"song","Song name",true)
                .addOption(OptionType.STRING,"artist","Artist name",true)
                .addOption(OptionType.STRING,"album","Album name",false);;

        return Commands.slash("search","Search for lyrics").addSubcommands(subcommandData)
                .setIntegrationTypes(IntegrationType.GUILD_INSTALL,IntegrationType.USER_INSTALL)
                .setContexts(InteractionContextType.PRIVATE_CHANNEL,InteractionContextType.GUILD);
    }



}
