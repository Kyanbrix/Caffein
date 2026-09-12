package com.github.kyanbrix.component.slashcommand;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kyanbrix.component.slashcommand.responses.LastFmTopTracksResponse;
import com.github.kyanbrix.config.database.UserRepository;
import com.github.kyanbrix.utils.*;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.enums.ReleaseDatePrecision;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class TopTracks implements ISlash {
    private static final Logger log = LoggerFactory.getLogger(TopTracks.class);
    public static final int pageSize = 5;
    private final OkHttpClient  client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();


    @Override
    public void execute(@NonNull SlashCommandInteraction event) {


        long userid = event.getUser().getIdLong();
        final String periodSelection = event.getOption("period", OptionMapping::getAsString);

        event.deferReply().queue();

        if (!ValidateUser.isUserAuthenticated(userid)) {

            event.getHook().setEphemeral(true).sendMessage("You are not authenticated! Please click the button below to link your Last.Fm account")
                    .addComponents(ActionRow.of(Button.of(ButtonStyle.LINK,CreateAuthenticationUrl.createAuthenticationUrl(event.getUser().getId()),"Authenticate")))
                    .queue();
            return;
        }

        UserRepository repository = new  UserRepository();

        HttpUrl.Builder urlBuilder = HttpUrl.parse(Constant.LAST_FM_API_URL).newBuilder();
        urlBuilder.addQueryParameter("method","user.gettoptracks");
        urlBuilder.addQueryParameter("user",repository.getUsernameFromLastFm(userid));
        urlBuilder.addQueryParameter("api_key",System.getenv("LAST_FM_API_KEY"));
        urlBuilder.addQueryParameter("period",periodSelection);
        urlBuilder.addQueryParameter("limit","5");
        urlBuilder.addQueryParameter("format","json");

        String baseId = "toptracks:%s:" + userid + ":%d:" + periodSelection;



        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent","MusicStats/1.0")
                .get().build();

        try (Response response = client.newCall(request).execute()) {

            if (response.isSuccessful()) {


                LastFmTopTracksResponse lastFmTopTracksResponse = mapper.readValue(response.body().string(), LastFmTopTracksResponse.class);

                int rank = 1;

                List<ContainerChildComponent> components = new ArrayList<>();
                components.add(TextDisplay.of(getPeriodSelection(periodSelection,event.getUser().getEffectiveName())));
                components.add(Separator.createDivider(Separator.Spacing.SMALL));

                for (LastFmTopTracksResponse.Track track : lastFmTopTracksResponse.getTopTracks().getTracks()) {

                    var spotifyData = new SpotifySearchAlbumImage(track.getName(),track.getArtist().getName());

                    if (spotifyData.getSongImage() == null) {
                        event.getHook().sendMessage("Something went wrong! Please try again later.").setEphemeral(true).queue();
                        return;
                    }

                    components.add(Section.of(
                            Thumbnail.fromUrl(spotifyData.getSongImage()),
                            TextDisplay.of(String.format("### %d. [%s](%s)",rank,track.getName(),track.getUrl())),
                            TextDisplay.of("**"+track.getArtist().getName()+"**"),
                            TextDisplay.of(String.format("-# **%s %s**",track.getPlaycount(),track.getPlaycount().equals("1") ? "play" : "plays"))
                    ));



                    components.add(Separator.createInvisible(Separator.Spacing.SMALL));

                    rank++;

                }

                components.add(Separator.createDivider(Separator.Spacing.LARGE));

                components.add(
                        ActionRow.of(

                                Button.of(ButtonStyle.SECONDARY, String.format(baseId, "fr", 1), Emoji.fromUnicode("U+23EA")).asDisabled(),
                                // "prev" ensures uniqueness for Previous
                                Button.of(ButtonStyle.SECONDARY, String.format(baseId, "prev", 1), "Previous").asDisabled(),
                                // "next" for Next
                                Button.of(ButtonStyle.SECONDARY, String.format(baseId, "next", 2), "Next"),
                                // "ff" for Fast Forward
                                Button.of(ButtonStyle.SECONDARY, String.format(baseId, "ff", 11), Emoji.fromUnicode("U+23E9"))
                        )
                );

                event.getHook().sendMessageComponents(Container.of(components)
                                .withAccentColor(ImageColorExtractor.getColor(event.getUser().getEffectiveAvatarUrl())))
                        .useComponentsV2()
                        .queue();

            }else  {
                log.error("Error code: {} - message: {}", response.code(), response.message());
                event.getHook().sendMessage("Error code: {} - message: " + response.message()).queue();
            }

        }catch (Exception e){
            log.error("Error during user top tracks",e);
        }


    }


    @Override
    public @NonNull CommandData getCommandData() {

        OptionData optionData = new OptionData(OptionType.STRING,"period","Select what period of your top tracks will be queried.",true)
                .addChoice("Weekly","7day")
                .addChoice("Monthly","1month")
                .addChoice("Yearly","12month")
                .addChoice("Half-Yearly","6month")
                .addChoice("Quarterly","3month")
                .addChoice("Overall","overall");

        return Commands.slash("toptracks","Get Spotify Top Tracks")
                .addOptions(optionData)
                .setIntegrationTypes(IntegrationType.USER_INSTALL,IntegrationType.GUILD_INSTALL)
                .setContexts(InteractionContextType.PRIVATE_CHANNEL,InteractionContextType.GUILD);
    }







    private String getPeriodSelection(String period, String effectiveName) {
        return switch (period) {

            case "7day" -> "## <:Stats:1545648322805637132> Top weekly tracks for "+ effectiveName;
            case "1month" -> "## <:Stats:1545648322805637132> Top monthly tracks for "+effectiveName ;
            case "12month" -> "## <:Stats:1545648322805637132> Top yearly tracks for "+effectiveName;
            case "6month" -> "## <:Stats:1545648322805637132> Top half-yearly tracks for "+effectiveName;
            case "3month" -> "## <:Stats:1545648322805637132> Top quarterly tracks for "+effectiveName;
            case "overall" -> "## <:Stats:1545648322805637132> Top overall tracks for "+ effectiveName;
            default -> "None";
        };
    }

    public String getRelativeReleaseDate(Track track) {
        String releaseDateStr = track.getAlbum().getReleaseDate();
        ReleaseDatePrecision precision = track.getAlbum().getReleaseDatePrecision();

        try {
            LocalDate date = switch (precision) {
                case YEAR -> LocalDate.parse(releaseDateStr + "-01-01");
                case MONTH -> LocalDate.parse(releaseDateStr + "-01");
                default -> LocalDate.parse(releaseDateStr);
            };


            Instant instant = date.atStartOfDay(ZoneOffset.UTC).toInstant();

            return TimeFormat.RELATIVE.format(instant);

        } catch (Exception e) {
            e.printStackTrace();
            return releaseDateStr;
        }
    }

}
