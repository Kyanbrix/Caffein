package com.github.kyanbrix.component.slashcommand;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kyanbrix.component.slashcommand.responses.LastFmRecentTracksResponse;
import com.github.kyanbrix.component.slashcommand.responses.LrcLibResponse;
import com.github.kyanbrix.config.database.UserRepository;
import com.github.kyanbrix.utils.Constant;
import com.github.kyanbrix.utils.CreateAuthenticationUrl;
import com.github.kyanbrix.utils.ValidateUser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
import java.util.ArrayList;
import java.util.List;

public class GetCurrentlyPlaySongLyrics implements ISlash {
    private static final Logger log = LoggerFactory.getLogger(GetCurrentlyPlaySongLyrics.class);
    private final OkHttpClient client = new  OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    @Override
    public void execute(@NonNull SlashCommandInteraction event) {

        event.deferReply().queue();

        if (ValidateUser.isUserAuthenticated(event.getUser().getIdLong())) {
            event.getHook().setEphemeral(true).sendMessage("You are not authenticated! Please click the button below to link your Last.Fm account")
                    .addComponents(ActionRow.of(Button.of(ButtonStyle.LINK, CreateAuthenticationUrl.createAuthenticationUrl(event.getUser().getId()),"Authenticate")))
                    .queue();
            return;
        }


        UserRepository userRepository = new  UserRepository();
        String username = userRepository.getUsernameFromLastFm(event.getUser().getIdLong());



        HttpUrl.Builder urlBuilder = HttpUrl.parse(Constant.LAST_FM_API_URL).newBuilder();
        urlBuilder.addQueryParameter("method","user.getrecenttracks");
        urlBuilder.addQueryParameter("user",username);
        urlBuilder.addQueryParameter("api_key",System.getenv("LAST_FM_API_KEY"));
        urlBuilder.addQueryParameter("limit","1");
        urlBuilder.addQueryParameter("format","json");

        Request request =  new Request.Builder().url(urlBuilder.build()).build();

        try (Response response = client.newCall(request).execute()) {

            if (response.isSuccessful()) {

                LastFmRecentTracksResponse recentTracks = mapper.readValue(response.body().string(), LastFmRecentTracksResponse.class);

                var track = recentTracks.getRecentTracks().getTracks().getFirst();

                if (!track.isNowPlaying()) {
                    event.getHook().sendMessage("You are currently not playing any song right now!").setEphemeral(true).queue();
                    return;
                }

                searchLyrics(event,track.getName(),track.getArtist().getName());


            }

        }catch (Exception e){
            log.error("Error while executing request",e);
        }





    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("lyrics","Get current playing song lyrics")
                .setIntegrationTypes(IntegrationType.USER_INSTALL,IntegrationType.GUILD_INSTALL)
                .setContexts(InteractionContextType.GUILD,InteractionContextType.PRIVATE_CHANNEL)
                ;
    }

    private void searchLyrics(SlashCommandInteraction event, String trackName, String artistName) {

        HttpUrl.Builder builder = HttpUrl.parse("https://lrclib.net/api/search").newBuilder();
        builder.addQueryParameter("track_name",trackName);
        builder.addQueryParameter("artist_name",artistName);

        Request rq  = new Request.Builder().url(builder.build())
                .addHeader("User-Agent", "Kian Bot v1.0.0 (kyanbrix@yahoo.com)")
                .get()
                .build();


        try (Response response = client.newCall(rq).execute()){

            if (response.isSuccessful()) {

                LrcLibResponse[] results =  mapper.readValue(response.body().string(), LrcLibResponse[].class);

                if (results.length > 0 && results[0].plainLyrics != null) {
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
                            embed.setTitle(trackName+" - "+artistName);
                        }

                        embeds.add(embed.build());

                    }//while loop end

                    event.getHook().sendMessageEmbeds(embeds).queue();

                }

            }else {

                String errorBody = response.body().string();
                log.error("LRCLIB request failed with code {}: {}", response.code(), errorBody);
                event.getHook().sendMessage("Failed to retrieve lyrics (HTTP " + response.code() + ").").queue();
            }

        }catch (IOException e){
            log.error("Error while executing request",e);

            event.getHook().sendMessage("Failed to retrieve lyrics").queue();
        }



    }

}
