package com.github.kyanbrix.utils;

import com.github.kyanbrix.Caffein;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

public record SpotifySearchAlbumImage(String name, String artist) {


    private static final Logger log = LoggerFactory.getLogger(SpotifySearchAlbumImage.class);

    public String getSongImage() {


        try (Connection connection = Caffein.getInstance().getConnection()) {

            PreparedStatement ps = connection.prepareStatement("SELECT * FROM spotify WHERE userid = ?");
            ps.setLong(1, Constant.KIAN_ID);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    String refreshToken = rs.getString("refresh_token");
                    String accessToken = rs.getString("access_token");
                    long expiration = rs.getLong("expiration");
                    long now = System.currentTimeMillis();

                    if (now >= expiration) {

                        String newAccessToken = getNewAccessToken(refreshToken);

                        if (newAccessToken == null) {
                            return null;
                        }

                        updateCredentials(newAccessToken);

                        SpotifyApi spotifyApi = new SpotifyApi.Builder()
                                .setAccessToken(newAccessToken).build();


                        var track = spotifyApi.searchTracks(name).build().execute();

                        for (Track song : track.getItems()) {

                          var artists =  Arrays.stream(song.getArtists()).map(ArtistSimplified::getName).toList();

                            for (String singer : artists) {

                                if (!singer.equals(artist)) {
                                    continue;
                                }

                                for (Image albumImage : song.getAlbum().getImages()) {


                                    if (albumImage.getUrl() == null || albumImage.getUrl().isEmpty()) {

                                        continue;
                                    }

                                    return  albumImage.getUrl();
                                }

                            }

                        }

                        return "https://cdn3.emoji.gg/emojis/3663-syntax.png";

                    } else {

                        SpotifyApi spotifyApi = new SpotifyApi.Builder()
                                .setAccessToken(accessToken).build();

                        var track = spotifyApi.searchTracks(name).build().execute();


                        for (Track song : track.getItems()) {

                            var artists =  Arrays.stream(song.getArtists()).map(ArtistSimplified::getName).toList();

                            for (String singer : artists) {

                                if (!singer.equals(artist)) {
                                    continue;
                                }

                                for (Image albumImage : song.getAlbum().getImages()) {


                                    if (albumImage.getUrl() == null || albumImage.getUrl().isEmpty()) {

                                        continue;
                                    }

                                    return  albumImage.getUrl();
                                }

                            }


                            return song.getAlbum().getImages()[0].getUrl();

                        }

                        return "https://cdn3.emoji.gg/emojis/3663-syntax.png";

                    }


                } else return "https://cdn3.emoji.gg/emojis/3663-syntax.png";

            }


        } catch (Exception e) {
            log.error("SpotifySearchAlbumImage.getSongImage: ", e);
        }

        return "https://cdn3.emoji.gg/emojis/3663-syntax.png";

    }


    private String getNewAccessToken(String refreshToken) {


        try {
            SpotifyApi spotifyApi = new SpotifyApi.Builder()
                    .setRefreshToken(refreshToken)
                    .setClientId("7b7854760e734015a95888891b4039fc")
                    .setClientSecret("a1b8497f755c415d8a9c3f0cccaf304b")
                    .build();

            AuthorizationCodeCredentials credentials = spotifyApi.authorizationCodeRefresh()
                    .build().execute();

            return credentials.getAccessToken();
        } catch (Exception e) {
            log.error("SpotifySearchAlbumImage.getNewAccessToken: ", e);
        }

        return "https://cdn3.emoji.gg/emojis/3663-syntax.png";


    }

    private void updateCredentials(String newAccessToken) {


        try (Connection connection = Caffein.getInstance().getConnection()) {

            PreparedStatement ps = connection.prepareStatement("UPDATE spotify SET access_token = ?, expiration = ? WHERE userid = ?");
            ps.setString(1, newAccessToken);
            ps.setLong(2, System.currentTimeMillis() + 3600000L);
            ps.setLong(3, Constant.KIAN_ID);

            ps.executeUpdate();

        } catch (Exception e) {
            log.error("SpotifySearchAlbumImage.getNewAccessToken: ", e);
        }



    }




}
