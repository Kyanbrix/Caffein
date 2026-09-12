package com.github.kyanbrix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kyanbrix.component.button.*;
import com.github.kyanbrix.component.slashcommand.responses.LastFmTopTracksResponse;
import com.github.kyanbrix.config.database.UserRepository;
import com.github.kyanbrix.utils.Constant;
import com.github.kyanbrix.utils.ImageColorExtractor;
import com.github.kyanbrix.utils.SpotifySearchAlbumImage;
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
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ButtonManager extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(ButtonManager.class);
    private final Map<String, IButton> buttons = new HashMap<>();


    public ButtonManager() {
        this.addButtons(new MaleButton());
        this.addButtons(new GayButton());
        this.addButtons(new FemaleButton());
        this.addButtons(new Verify());
        this.addButtons(new CreateConfession());
        this.addButtons(new Ticket());
        this.addButtons(new DeleteAvatarButton());
    }


    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        final String id = event.getCustomId();
        final String topTracksButton = event.getComponentId();
        IButton iButton = buttons.get(id);

        if (iButton == null) {

            String[] split = topTracksButton.split(":");

           if (split[0].equals("toptracks")) {
               long userId = Long.parseLong(split[2]);
               int targetPage = Integer.parseInt(split[3]);
               String period =  split[4];

               if (event.getUser().getIdLong() != userId) {

                   event.reply("You cannot use that component!").setEphemeral(true).queue();
                   return;
               }

               event.deferEdit().queue();

               paginateLastFmTracks(event, userId, targetPage, period);


               return;
           }

            event.reply("Error!").setEphemeral(true).queue();
            return;
        }

        iButton.accept(event);
    }


    private void addButtons(IButton iButton) {

        if (buttons.containsKey(iButton.buttonId())) throw new IllegalArgumentException("Duplicate ID");

        buttons.put(iButton.buttonId(),iButton);
    }



    private String getPeriodSelection(String period) {
        return switch (period) {

            case "short_term" -> "<:Stats:1545648322805637132> Top tracks this month for";
            case "medium_term" -> "<:Stats:1545648322805637132> Top tracks for the past 6 months for";
            case "long_term" -> "<:Stats:1545648322805637132> All time tracks for";

            default -> "None";
        };
    }

    private void paginateLastFmTracks(ButtonInteractionEvent event, long userid, int targetPage, String period) {

        UserRepository repository = new UserRepository();

        HttpUrl.Builder urlBuilder = HttpUrl.parse(Constant.LAST_FM_API_URL).newBuilder();
        urlBuilder.addQueryParameter("method", "user.gettoptracks");
        urlBuilder.addQueryParameter("user", repository.getUsernameFromLastFm(userid));
        urlBuilder.addQueryParameter("api_key", System.getenv("LAST_FM_API_KEY"));
        urlBuilder.addQueryParameter("period", period);
        urlBuilder.addQueryParameter("limit", "5");
        urlBuilder.addQueryParameter("format", "json");
        urlBuilder.addQueryParameter("page", String.valueOf(targetPage)); // Append target page


        Request request = new Request.Builder().url(urlBuilder.build()).header("User-Agent", "MusicStats/1.0").get().build();


        try (Response response = new OkHttpClient().newCall(request).execute()) {

            if (response.isSuccessful()) {

                var lastFmResponse = new ObjectMapper().readValue(response.body().string(), LastFmTopTracksResponse.class);

                List<ContainerChildComponent> components = new ArrayList<>();

                components.add(TextDisplay.of(getPeriodSelection(period,event.getUser().getEffectiveName())));
                components.add(Separator.createDivider(Separator.Spacing.SMALL));


                int rank = ((targetPage - 1) * 5) + 1;
                for (LastFmTopTracksResponse.Track track : lastFmResponse.getTopTracks().getTracks()) {
                    var spotifyImage = new SpotifySearchAlbumImage(track.getName(),track.getArtist().getName());

                    components.add(Section.of(
                            Thumbnail.fromUrl(spotifyImage.getSongImage()), // Add your resolved thumbnail here
                            TextDisplay.of(String.format("### %d. [%s](%s)", rank++, track.getName(), track.getUrl())),
                            TextDisplay.of("**" + track.getArtist().getName() + "**"),
                            TextDisplay.of(String.format("-# **%s plays**", track.getPlaycount()))
                    ));
                    components.add(Separator.createInvisible(Separator.Spacing.SMALL));
                }

                components.add(Separator.createDivider(Separator.Spacing.LARGE));


                String baseId = "toptracks:%s:" + userid + ":%d:" + period;
                int prevPage = Math.max(1, targetPage - 1);
                int nextPage = targetPage + 1;

                components.add(ActionRow.of(
                        Button.of(ButtonStyle.SECONDARY, String.format(baseId, "fr", 1), Emoji.fromUnicode("U+23EA")).withDisabled(targetPage == 1),
                        Button.of(ButtonStyle.SECONDARY, String.format(baseId, "prev", prevPage), "Previous").withDisabled(targetPage == 1),
                        Button.of(ButtonStyle.SECONDARY, String.format(baseId, "next", nextPage), "Next"),
                        Button.of(ButtonStyle.SECONDARY, String.format(baseId, "ff", targetPage + 10), Emoji.fromUnicode("U+23E9"))
                ));


                event.getHook().editOriginalComponents(Container.of(components).withAccentColor(ImageColorExtractor.getColor(event.getUser().getEffectiveAvatarUrl()))).useComponentsV2()
                        .queue();



                /**
                 ActionRow.of(
                 Button.of(ButtonStyle.SECONDARY, String.format(baseId, "fr", 1), Emoji.fromUnicode("U+23EA")).withDisabled(targetPage == 1),
                 Button.of(ButtonStyle.SECONDARY, String.format(baseId, "prev", prevPage), "Previous").withDisabled(targetPage == 1),
                 Button.of(ButtonStyle.SECONDARY, String.format(baseId, "next", nextPage), "Next"),
                 Button.of(ButtonStyle.SECONDARY, String.format(baseId, "ff", targetPage + 10), Emoji.fromUnicode("U+23E9"))
                 )


               **/
            }


        }catch (Exception e) {

            log.error("Error in paginate last tracks!",e);

        }



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


}
