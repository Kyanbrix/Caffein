package com.github.kyanbrix.component.slashcommand;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kyanbrix.component.slashcommand.data.TrackMetadata;
import com.github.kyanbrix.component.slashcommand.responses.LastFmRecentTracksResponse;
import com.github.kyanbrix.config.database.UserRepository;
import com.github.kyanbrix.utils.Constant;
import com.github.kyanbrix.utils.CreateAuthenticationUrl;
import com.github.kyanbrix.utils.ValidateUser;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
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

public class CurrentPlaying implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(CurrentPlaying.class);
    private final OkHttpClient client = new  OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    @Override
    public void execute(@NonNull SlashCommandInteraction event) {

        event.deferReply().queue();

        if (!ValidateUser.isUserAuthenticated(event.getUser().getIdLong())) {
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

                TrackMetadata metadataCache = TrackMetadataCache.getOrFetchMetadata(track.getArtist().getName(),track.getName());

                Container container = Container.of(

                        Section.of(
                                Thumbnail.fromUrl(metadataCache.getArtworkUrl()),
                                TextDisplay.of(String.format("-# <a:emojigg_cd:1548026549175853067> Now Playing for [%s](https://www.last.fm/user/%s)\n## [%s](%s)\n**%s** • %s",event.getUser().getEffectiveName(),username,track.getName(),track.getUrl(),track.getArtist().getName(),metadataCache.getDurationMs()))
                        )


                ).withAccentColor(metadataCache.getAccentColor());

                event.getHook().sendMessageComponents(container).useComponentsV2().queue();


            }

        }catch (Exception e){
            log.error("Error while executing request",e);
        }




    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("currentplaying","Get User currently playing")
                .setContexts(InteractionContextType.PRIVATE_CHANNEL,InteractionContextType.GUILD)
                .setIntegrationTypes(IntegrationType.USER_INSTALL,IntegrationType.GUILD_INSTALL);
    }







}
