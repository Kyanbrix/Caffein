package com.github.kyanbrix.api.OpenAI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kyanbrix.api.OpenAI.service.ImageSearchEngineService;
import com.github.kyanbrix.utils.Constant;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.Timeout;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ChatSession {

    private static final Logger log = LoggerFactory.getLogger(ChatSession.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenAIClient client;
    private static final int MAX_TOOL_ITERATIONS = 8;
    private static final int MAX_CHUNK_SIZE = 3900;
    private String previousResponseId;


    private static final FunctionTool IMAGE_SEARCH_TOOL = FunctionTool.builder()
            .type(JsonValue.from("function"))
            .name("image_search")
            .description(
                    "Search Google Images for photos, pictures, or visual content. " +
                            "Use this when the user asks to see images or photos of something. " +
                            "Returns a list of direct image URLs."
            )
            .parameters(FunctionTool.Parameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(Map.of(
                            "query", Map.of(
                                    "type", "string",
                                    "description", "The image search query"
                            ),
                            "count", Map.of(
                                    "type", "integer",
                                    "description", "Number of images to return, between 1 and 10"
                            )
                    )))
                    .putAdditionalProperty("required", JsonValue.from(List.of("query", "count")))
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build())
            .strict(true)
            .build();

    private static final WebSearchTool WEB_SEARCH_TOOL = WebSearchTool.builder()
            .type(WebSearchTool.Type.WEB_SEARCH_2025_08_26)
            .searchContextSize(WebSearchTool.SearchContextSize.HIGH)
            .userLocation(WebSearchTool.UserLocation.builder()
                    .type(WebSearchTool.UserLocation.Type.APPROXIMATE)
                    .city("Cebu")
                    .country("PH")
                    .region("Central Visayas")
                    .build())
            .build();


    public ChatSession() {

        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .clock(Clock.system(ZoneId.of("Asia/Manila")))
                .apiKey(System.getenv("OPENAI_KEY"))
                .timeout(Timeout.builder()
                        .read(Duration.ofMinutes(5))
                        .write(Duration.ofMinutes(1))
                        .request(Duration.ofMinutes(6))
                        .build());

        this.client = builder.build();

    }

    public List<String> chat(String message) {

        List<ResponseInputItem> inputs = new ArrayList<>();

        inputs.add(ResponseInputItem.ofMessage(
                ResponseInputItem.Message.builder()
                        .role(ResponseInputItem.Message.Role.USER)
                        .addInputTextContent(message)
                        .build()
        ));

        return runAgenticLoop(inputs);
    }

    public List<String> chatWithMedia(String userMessage, List<String> imageUrls) {

        if (imageUrls == null || imageUrls.isEmpty()) {
            return chat(userMessage);
        }

        List<ResponseInputItem> inputs = new ArrayList<>();

        ResponseInputItem.Message.Builder builder = ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER)
                .addInputTextContent(userMessage.isEmpty() ? "" : userMessage);

        for (String imageUrl : imageUrls) {

            builder.addContent(ResponseInputImage.builder()
                            .imageUrl(imageUrl)
                            .detail(ResponseInputImage.Detail.AUTO)
                    .build());
        }

        inputs.add(ResponseInputItem.ofMessage(builder.build()));
        // Run the same agentic loop as normal chat()
        return runAgenticLoop(inputs);
    }

    private List<String> runAgenticLoop(List<ResponseInputItem> initialInputs) {
        List<ResponseInputItem> currentInputs = new ArrayList<>(initialInputs);


        try {
            ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                        .instructions(Constant.SYSTEM_PROMPT)
                        .addTool(WEB_SEARCH_TOOL)
                        .model(Constant.MODEL)
                        .reasoning(Reasoning.builder()
                                .effort(ReasoningEffort.HIGH)
                                .build())
                        .input(ResponseCreateParams.Input.ofResponse(currentInputs));

            if (previousResponseId != null) {
                    builder.previousResponseId(previousResponseId);
            }

            Response response = client.responses().create(builder.build());
            previousResponseId = response.id();

            String finalText = extractText(response);

            return splitIntoChunks(finalText);

        } catch (Exception e) {
            log.error("Chat session failed", e);
            previousResponseId = null;

            String message = e.getMessage() == null ? "Unknown error" : e.getMessage();
            if (message.contains("exceeds the context window")) {
                log.warn("Context window exceeded; resetting conversation state.");
                previousResponseId = null;
            }

            return splitIntoChunks("Error: " + message);
        }
    }

    private String executeTool(String functionName, String argumentsJson) {

        try {
            JsonNode args = MAPPER.readTree(argumentsJson);

            return switch (functionName) {

                case "web_search"-> {
                    String query = args.path("query").asText("");
                    log.info("Web search query: {}", query);
                    yield "Tool not yet implemented: web_search. Query=" + query;
                }

                case "image_search"-> ImageSearchEngineService.search(
                        extractField(argumentsJson,"query"),
                        Integer.parseInt(extractField(argumentsJson, "count").isBlank() ? "5" : extractField(argumentsJson, "count"))

                );

                default -> "Unknown tool: " + functionName;
            };
        } catch (Exception e) {
            log.error("Failed to parse tool arguments for {}", functionName, e);
            return "Tool execution error: " + e.getMessage();

        }

    }


    private List<String> splitIntoChunks(String text) {
        if (text.length() <= MAX_CHUNK_SIZE) {
            return Collections.singletonList(text);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private String extractText(Response response) {

        for (ResponseOutputItem item : response.output()) {
            if (item.isMessage()) {
                for (ResponseOutputMessage.Content content : item.asMessage().content()) {
                    if (content.isOutputText()) {
                        return content.asOutputText().text();
                    }
                }
            }
        }
        return "(no response)";
    }


    private String extractField(String json, String key) {
        int start  = json.indexOf("\"" + key + "\"");
        if (start == -1) return "";
        int colon  = json.indexOf(':', start);
        int qStart = json.indexOf('"', colon + 1);
        if (qStart == -1) return "";
        int qEnd = qStart + 1;
        while (qEnd < json.length()) {
            if (json.charAt(qEnd) == '"' && json.charAt(qEnd - 1) != '\\') break;
            qEnd++;
        }
        return json.substring(qStart + 1, qEnd);
    }

}
