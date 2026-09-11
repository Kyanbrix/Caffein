package com.github.kyanbrix.api.OpenAI.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class ImageSearchEngineService {


    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String ENDPOINT = "https://www.googleapis.com/customsearch/v1";

    public static String search(String query, int count) {
        String apiKey = System.getenv("GOOGLE_API_KEY");
        String cseId  = System.getenv("GOOGLE_CSE_ID");

        if (apiKey == null || apiKey.isBlank()) {
            return "Image search unavailable: GOOGLE_API_KEY is not set.";
        }
        if (cseId == null || cseId.isBlank()) {
            return "Image search unavailable: GOOGLE_CSE_ID is not set.";
        }

        int safeCount = Math.clamp(count, 1, 10);

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = ENDPOINT
                    + "?key=" + apiKey
                    + "&cx=" + cseId
                    + "&q=" + encodedQuery
                    + "&searchType=image"
                    + "&num=" + safeCount
                    + "&safe=active"; // safe search on

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println("[ImageSearch] HTTP " + response.statusCode());

            if (response.statusCode() != 200) {
                return "Image search failed (HTTP " + response.statusCode() + "): " + response.body();
            }

            return parseResponse(response.body(), query);

        } catch (Exception e) {
            System.err.println("[ImageSearch] Error: " + e.getMessage());
            return "Image search error: " + e.getMessage();
        }
    }

    private static String parseResponse(String json, String query) {
        StringBuilder result = new StringBuilder();
        result.append("Image search results for \"").append(query).append("\":\n\n");

        // Extract each image item — split on "link" field occurrences inside items
        int itemsStart = json.indexOf("\"items\"");
        if (itemsStart == -1) {
            return "No images found for: " + query;
        }

        String[] items = json.split("\"kind\": \"customsearch#result\"");
        int count = 0;

        for (int i = 1; i < items.length; i++) {
            String block = items[i];

            String title = extractField(block, "title");
            String link  = extractField(block, "link");

            if (link != null && link.startsWith("http")) {
                result.append(++count).append(". ");
                if (title != null) result.append(title).append("\n");
                result.append("   ").append(link).append("\n\n");
            }
        }

        if (count == 0) {
            return "No images found for: " + query;
        }

        return result.toString().trim();
    }

    private static String extractField(String json, String key) {
        int start = json.indexOf("\"" + key + "\"");
        if (start == -1) return null;
        int colon  = json.indexOf(':', start);
        int qStart = json.indexOf('"', colon + 1);
        if (qStart == -1) return null;
        int qEnd = qStart + 1;
        while (qEnd < json.length()) {
            if (json.charAt(qEnd) == '"' && json.charAt(qEnd - 1) != '\\') break;
            qEnd++;
        }
        return json.substring(qStart + 1, qEnd);
    }

}
