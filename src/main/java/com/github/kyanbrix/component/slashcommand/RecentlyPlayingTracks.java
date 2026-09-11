package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.Caffein;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.PagingCursorbased;
import se.michaelthelin.spotify.model_objects.specification.PlayHistory;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class RecentlyPlayingTracks implements ISlash{
    private static final Logger log = LoggerFactory.getLogger(RecentlyPlayingTracks.class);

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {


        try (Connection connection = Caffein.getInstance().getConnection()) {

            PreparedStatement ps = connection.prepareStatement("SELECT * FROM spotify WHERE userid = ?");
            ps.setLong(1, event.getUser().getIdLong());

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    String accessToken = rs.getString("access_token");
                    String refreshToken = rs.getString("refresh_token");
                    long expiration =  rs.getLong("expiration");
                    long timeNow = System.currentTimeMillis();


                    if (timeNow >= expiration) {

                        SpotifyApi refreshAccessToken = new SpotifyApi.Builder()
                                .setRefreshToken(refreshToken)
                                .setClientId("7b7854760e734015a95888891b4039fc")
                                .setClientSecret("a1b8497f755c415d8a9c3f0cccaf304b")
                                .build();


                        AuthorizationCodeCredentials newCredentials = refreshAccessToken
                                .authorizationCodeRefresh()
                                .build()
                                .execute();

                        PreparedStatement update =  connection.prepareStatement("UPDATE spotify SET access_token = ?, expiration = ? WHERE userid = ?");
                        update.setString(1, newCredentials.getAccessToken());
                        update.setLong(2, timeNow + 3600000L);
                        update.setLong(3, event.getUser().getIdLong());
                        update.executeUpdate();

                        SpotifyApi newSpotifyApi = new SpotifyApi.Builder()
                                .setAccessToken(newCredentials.getAccessToken())
                                .build();

                        CurrentlyPlaying currentlyPlaying = newSpotifyApi.getUsersCurrentlyPlayingTrack().build().execute();


                        PagingCursorbased<PlayHistory> playingTracksPaging = newSpotifyApi.getCurrentUsersRecentlyPlayedTracks()
                                .limit((currentlyPlaying == null ? 15 : 14))
                                .build()
                                .execute();

                        PlayHistory[] playHistories  = playingTracksPaging.getItems();
                        EmbedBuilder embedBuilder = new EmbedBuilder();
                        embedBuilder.setColor(Color.GREEN);
                        embedBuilder.setAuthor(event.getUser().getEffectiveName(),null,event.getUser().getEffectiveAvatarUrl());
                        embedBuilder.setTitle("Recently Played Tracks");
                        embedBuilder.setFooter("Spotify Music","https://cdn3.emoji.gg/emojis/148804-spotify.png");

                        StringBuilder sb = new StringBuilder();
                        if(playHistories.length == 0) {

                            event.deferReply().queue();
                            event.getHook().sendMessage("No recently played tracks found!").queue();
                            return;
                        }

                        int rank = 1;


                        for(PlayHistory playHistory : playHistories) {

                            if (rank == 1 && currentlyPlaying != null) {

                                if (currentlyPlaying.getItem() instanceof Track track) {

                                    sb.append(String.format("%d. [%s](%s) - Now Playing <a:VinylRecord:1545647146093781032>\n",rank,track.getName(),track.getExternalUrls().get("spotify")));

                                    rank++;
                                }

                            }else {
                                sb.append(String.format("%d. [%s](%s) - %s\n",rank,playHistory.getTrack().getName(),playHistory.getTrack().getExternalUrls().get("spotify"),
                                        TimeFormat.RELATIVE.format(playHistory.getPlayedAt().toInstant())));
                                rank++;
                            }


                        }

                        embedBuilder.setDescription(sb.toString());
                        event.deferReply().queue();

                        event.getHook().sendMessageEmbeds(embedBuilder.build()).queue();



                    }else {



                        SpotifyApi spotifyApi = new SpotifyApi.Builder()
                                .setAccessToken(accessToken)
                                .build();

                        CurrentlyPlaying currentlyPlaying = spotifyApi.getUsersCurrentlyPlayingTrack().build().execute();

                        PagingCursorbased<PlayHistory> playingTracksPaging = spotifyApi.getCurrentUsersRecentlyPlayedTracks()
                                .limit((currentlyPlaying == null ? 15 : 14))
                                .build()
                                .execute();

                        PlayHistory[] playHistories  = playingTracksPaging.getItems();
                        EmbedBuilder embedBuilder = new EmbedBuilder();
                        embedBuilder.setColor(Color.GREEN);
                        embedBuilder.setAuthor(event.getUser().getEffectiveName(),null,event.getUser().getEffectiveAvatarUrl());
                        embedBuilder.setTitle("Recently Played Tracks");
                        embedBuilder.setFooter("Spotify Music","https://cdn3.emoji.gg/emojis/148804-spotify.png");

                        StringBuilder sb = new StringBuilder();
                        if(playHistories.length == 0) {

                            event.deferReply().queue();
                            event.getHook().sendMessage("No recently played tracks found!").queue();
                            return;
                        }

                        int rank = 1;


                        for(PlayHistory playHistory : playHistories) {

                            if (rank == 1 && currentlyPlaying != null) {

                                if (currentlyPlaying.getItem() instanceof Track track) {

                                    sb.append(String.format("%d. [%s](%s) - Now Playing <a:VinylRecord:1545647146093781032>\n",rank,track.getName(),track.getExternalUrls().get("spotify")));
                                    rank++;
                                }

                            }else {
                                sb.append(String.format("%d. [%s](%s) - %s\n",rank,playHistory.getTrack().getName(),playHistory.getTrack().getExternalUrls().get("spotify"),
                                        TimeFormat.RELATIVE.format(playHistory.getPlayedAt().toInstant())));
                                rank++;
                            }


                        }

                        embedBuilder.setDescription(sb.toString());

                        event.deferReply().queue();

                        event.getHook().sendMessageEmbeds(embedBuilder.build()).queue();

                    }





                }else event.reply("You need to authenticate first!").queue();

            }


        }catch (Exception e){

            log.error(e.getMessage(),e);

        }



    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("recentlyplayedtracks","Get user recently played tracks");
    }
}
