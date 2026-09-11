package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.User;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class TopArtists implements ISlash {
    private static final Logger log = LoggerFactory.getLogger(TopArtists.class);
    private final OkHttpClient client = new OkHttpClient();
    @Override
    public void execute(@NonNull SlashCommandInteraction event) {

        long userId = event.getUser().getIdLong();
        String period = event.getOption("period",OptionMapping::getAsString);
        Integer limit = event.getOption("limit",OptionMapping::getAsInt);




        if ( limit != null && limit > 12) {
            event.reply("Limit only 12 below").setEphemeral(true).queue();
            return;
        }

        try (Connection connection = Caffein.getInstance().getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM spotify WHERE userid = ?");
            statement.setLong(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {

                    String accessToken = resultSet.getString("access_token");

                    SpotifyApi spotifyApi = new SpotifyApi.Builder()
                            .setAccessToken(accessToken)
                            .build();

                    Paging<Artist> artistPaging = spotifyApi.getUsersTopArtists()
                            .limit((limit == null ? 9 : limit))
                            .time_range(period)
                            .build()
                            .execute();

                    User user = spotifyApi.getCurrentUsersProfile().build().execute();

                    try(InputStream inputStream = generateImageTopArtists(artistPaging.getItems(),period,(limit == null ? 9 : limit))) {

                        if (inputStream == null) {

                            event.reply("Error getting data").setEphemeral(true).queue();

                            return;
                        }


                        EmbedBuilder embedBuilder = new EmbedBuilder()
                                .setTitle(String.format("%s", parsePeriod(period)),user.getExternalUrls().get("spotify"))
                                .setColor(Color.RED)
                                .setFooter(user.getDisplayName(), user.getImages()[0].getUrl())
                                .setImage("attachment://chart.png");

                        event.replyFiles(FileUpload.fromData(inputStream,"chart.png"))
                                .addEmbeds(embedBuilder.build())
                                .queue();

                    }catch (IOException e){
                        log.error("Error generating pictures",e);
                    }

                }


            }


        }catch(Exception e) {

            log.error(e.getMessage(),e);

        }





    }

    @Override
    public @NonNull CommandData getCommandData() {
        OptionData optionData = new OptionData(OptionType.STRING,"period","Query what period you want to query",true)
                .addChoice("This month","short_term")
                .addChoice("Last 6 months","medium_term")
                .addChoice("All time","long_term");

        OptionData limitQuery = new OptionData(OptionType.INTEGER,"limit","How many artists you want to return (default 9, max 15)",false);

        return Commands.slash("topartists","Get user Top Artists").addOptions(optionData,limitQuery)
                .setIntegrationTypes(IntegrationType.USER_INSTALL,IntegrationType.GUILD_INSTALL)
                .setContexts(InteractionContextType.PRIVATE_CHANNEL,InteractionContextType.GUILD);
    }

    private InputStream generateImageTopArtists(Artist[]  artists, String period, int limitQuery) throws IOException {

        int limit = Math.min(artists.length, limitQuery);
        if (limit == 0) return null;

        int cols = Math.min(limit, 3);
        int rows = (int) Math.ceil((double) limit / 3.0);

        int cellSize = 300;
        int canvasWidth = cols * cellSize;
        int canvasHeight = rows * cellSize;
        BufferedImage imageTopArtists = new BufferedImage(canvasWidth,canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = imageTopArtists.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 24));
        FontMetrics fm = g2d.getFontMetrics();

        for (int i = 0; i < Math.min(artists.length,12); i++) {

            Artist artist = artists[i];

            int col = i % 3;
            int row = i / 3;
            int x = col * cellSize;
            int y = row * cellSize;

            if (artist.getImages().length > 0) {
                URL url = new URL(artist.getImages()[0].getUrl());
                BufferedImage bufferedImage = ImageIO.read(url);
                g2d.drawImage(bufferedImage, x, y, cellSize, cellSize, null);


            }else {

                g2d.setColor(Color.DARK_GRAY);
                g2d.fillRect(x, y, cellSize, cellSize);
            }

            String name = artist.getName();
            int textX = x + (cellSize - fm.stringWidth(name)) / 2; // Center text
            int textY = y + cellSize - 15; // 15px from bottom

            g2d.setColor(Color.BLACK);
            g2d.drawString(name, textX + 4, textY + 4);

            g2d.setColor(Color.WHITE);
            g2d.drawString(name, textX, textY);


        }

        g2d.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(imageTopArtists, "png", outputStream);
        return new ByteArrayInputStream(outputStream.toByteArray());
    }


    private String parsePeriod(String period) {

        return switch (period) {

            case "short_term" -> "Top artists this month";
            case "medium_term" -> "Top artists for last 6 months";
            case "long_term" -> "All time top artists";

            default -> "NONE";

        };

    }

}
