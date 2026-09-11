package com.github.kyanbrix.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class CreateAuthenticationUrl {

    public static String createAuthenticationUrl(String userid) {


        String rawCallback = "https://api.kyanbrix.com/api/lastfm/callback?discord_id=" + userid;

        String encodeCallback = URLEncoder.encode(rawCallback, StandardCharsets.UTF_8);


        return String.format("https://www.last.fm/api/auth/?api_key=%s&cb=%s", System.getenv("LAST_FM_API_KEY"),encodeCallback);

    }

}
