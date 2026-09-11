package com.github.kyanbrix.utils;

import com.github.kyanbrix.component.slashcommand.responses.LastFmTopTracksResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class TopTracksEmbedBuilder {

    public static MessageCreateData createTopTracksEmbed(LastFmTopTracksResponse lastFmTopTracksResponse) {

        var topTracks = lastFmTopTracksResponse.getTopTracks().getTracks();
        var attributes = topTracks.
                stream().map(LastFmTopTracksResponse.Track::getAttributes).toList();






        return null;



    }

}
