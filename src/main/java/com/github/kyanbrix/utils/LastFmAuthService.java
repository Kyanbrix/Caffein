package com.github.kyanbrix.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Map;

public class LastFmAuthService {

    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    // Returns a string array: [0] = username, [1] = sessionKey
    public static String[] getSessionInfo(String token) throws Exception {
        String apiKey = System.getenv("LAST_FM_API_KEY");
        String apiSecret = System.getenv("LAST_FM_SECRET_KEY");

        Map<String, String> params = Map.of(
                "method", "auth.getSession",
                "api_key", apiKey,
                "token", token
        );

        String signature = LastFmSignature.sign(params, apiSecret);

        HttpUrl url = HttpUrl.parse("https://ws.audioscrobbler.com/2.0/").newBuilder()
                .addQueryParameter("method", "auth.getSession")
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("token", token)
                .addQueryParameter("api_sig", signature)
                .addQueryParameter("format", "json")
                .build();

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Last.fm rejected token: " + response.code());
            }

            JsonNode root = mapper.readTree(response.body().string());
            if (root.has("error")) {
                throw new RuntimeException(root.path("message").asText());
            }

            JsonNode session = root.path("session");
            return new String[]{
                    session.path("name").asText(),
                    session.path("key").asText()
            };
        }
    }

}
