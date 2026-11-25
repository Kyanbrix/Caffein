package com.github.kyanbrix.api.OpenAI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.api.OpenAI.service.AqwGuideService;
import com.github.kyanbrix.api.OpenAI.service.AqwWikiService;
import com.github.kyanbrix.api.OpenAI.service.ImageGenerationService;
import com.github.kyanbrix.api.OpenAI.service.ImageSearchEngineService;
import com.github.kyanbrix.config.database.ImageGenerationRepository;
import com.github.kyanbrix.config.database.ChatHistoryRepository;
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

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One conversation with the chatbot (one per Discord channel, see {@link SessionManager}).
 * <p>
 * The history is kept here rather than on OpenAI's side ({@code previous_response_id}), so it can be
 * trimmed to a fixed budget and survives restarts: every finished turn is saved to {@code chat_history}
 * and the newest messages are loaded back the first time the session is used.
 */
public class ChatSession {

    private static final Logger log = LoggerFactory.getLogger(ChatSession.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // One client for every session: each OpenAIClient owns its own connection pool and threads.
    private static final OpenAIClient CLIENT = OpenAIOkHttpClient.builder()
            .apiKey(System.getenv("OPENAI_KEY"))
            .maxRetries(2)
            .timeout(Timeout.builder()
                    .read(Duration.ofMinutes(5))
                    .write(Duration.ofMinutes(1))
                    .request(Duration.ofMinutes(6))
                    .build())
            .build();

    // Lookup budget per message. A deep AQW merge chain can otherwise keep the model
    // looking things up for minutes; once it's spent, the model must answer.
    private static final int MAX_TOOL_ROUNDS = 6;
    private static final int MAX_TOOL_CALLS = 16;
    // Images the model may generate for one message (about $0.053 each)
    private static final int MAX_IMAGES_PER_MESSAGE = 2;
    private static final String BUDGET_REACHED_NOTE =
            "Lookup limit reached for this message. Answer now using what you've found. "
                    + "If some parts weren't checked, say which ones briefly and offer to look them up next.";
    private static final int MAX_CHUNK_SIZE = 1900;
    // Includes reasoning tokens, so it leaves room for a long AQW guide after the model thinks it through
    private static final long MAX_OUTPUT_TOKENS = 16_000;

    // History budget sent with each request; older messages stay in the database but aren't sent.
    private static final int MAX_HISTORY_MESSAGES = 40;
    private static final int MAX_HISTORY_CHARS = 24_000;

    // Attached file text sent per message (roughly 15k tokens); the rest is cut off.
    private static final int MAX_FILE_CHARS = 60_000;

    // Discord display names can be 32 characters long.
    private static final int MAX_AUTHOR_NAME = 32;

    private static final ReasoningEffort REASONING_EFFORT = ReasoningEffort.MEDIUM;

    private static final FunctionTool IMAGE_SEARCH_TOOL = FunctionTool.builder()
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

    private static final FunctionTool AQW_WIKI_TOOL = FunctionTool.builder()
            .name("aqw_wiki")
            .description(
                    "Read a page of the AdventureQuest Worlds wiki (aqwwiki.wikidot.com): an item, class " +
                            "(with all skills), shop (every item and its price or merge materials), monster " +
                            "(location, HP, attacks, drops), map (monsters, NPCs, shops, quests, /join), NPC, " +
                            "or quest list (requirements and rewards). Use it for every AQW question before " +
                            "web search, and call it again for related pages (a merge material, the shop or " +
                            "monster an item comes from) to build a complete answer."
            )
            .parameters(FunctionTool.Parameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(Map.of(
                            "query", Map.of(
                                    "type", "string",
                                    "description", "Just the page name, not a question: \"Void Highlord\", \"nsod\", " +
                                            "\"Void Highlord Merge Shop\", \"Nulgath the Archfiend\", \"Tercessuinotlim\", " +
                                            "\"Nulgath's Quests\". A full aqwwiki.wikidot.com URL also works."
                            ),
                            "focus", Map.of(
                                    "type", "string",
                                    "description", "For long pages: a section heading or keyword to jump to, such as " +
                                            "a quest name or \"Items Dropped\". Use an empty string to start at the top."
                            )
                    )))
                    .putAdditionalProperty("required", JsonValue.from(List.of("query", "focus")))
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build())
            .strict(true)
            .build();

    private static final FunctionTool AQW_ITEM_GUIDE_TOOL = FunctionTool.builder()
            .name("aqw_item_guide")
            .description(
                    "Build the complete 'how to get it' tree for an AdventureQuest Worlds item in one call: " +
                            "where it's sold, merged, or dropped, every merge material with amounts, the quests that " +
                            "reward each material (with their requirements, where to farm, and items required), " +
                            "followed several levels deep. Use this whenever a member asks how to get, farm, or " +
                            "merge an item, instead of looking up each material with aqw_wiki."
            )
            .parameters(FunctionTool.Parameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(Map.of(
                            "item", Map.of(
                                    "type", "string",
                                    "description", "The item name or nickname (\"Necrotic Sword of Doom\", \"nsod\", " +
                                            "\"Void Highlord\"), or its aqwwiki.wikidot.com URL"
                            )
                    )))
                    .putAdditionalProperty("required", JsonValue.from(List.of("item")))
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build())
            .strict(true)
            .build();

    // Owner only: only added to the request when the message is from Constant.KIAN_ID
    private static final FunctionTool GENERATE_IMAGE_TOOL = FunctionTool.builder()
            .name("generate_image")
            .description(
                    "Generate a new image, or edit or combine the images attached to this message, with gpt-image-2. " +
                            "The image is attached to your reply automatically. Use it only when the member asks " +
                            "you to make, draw, create, or edit an image."
            )
            .parameters(FunctionTool.Parameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(Map.of(
                            "prompt", Map.of(
                                    "type", "string",
                                    "description", "A detailed description of the image: subject, style, composition, " +
                                            "lighting, colors. For edits, describe the change and what to keep. With several " +
                                            "attached images, refer to them in attachment order (first image, second image, ...)."
                            ),
                            "use_reference", Map.of(
                                    "type", "boolean",
                                    "description", "true to base the image on all the images attached to this message " +
                                            "(edits, style changes, combining them); false for a brand-new image"
                            )
                    )))
                    .putAdditionalProperty("required", JsonValue.from(List.of("prompt", "use_reference")))
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build())
            .strict(true)
            .build();

    private static final WebSearchTool WEB_SEARCH_TOOL = WebSearchTool.builder()
            .type(WebSearchTool.Type.WEB_SEARCH_2025_08_26)
            .searchContextSize(WebSearchTool.SearchContextSize.MEDIUM)
            .build();

    private record Turn(EasyInputMessage.Role role, String content) {}

    private final String sessionId;
    private final Deque<Turn> history = new ArrayDeque<>();
    private int historyChars;
    private boolean historyLoaded;

    // Messages in the same channel are answered one at a time so their turns don't interleave.
    private final ReentrantLock lock = new ReentrantLock();

    // The message being answered, for the generate_image tool. Only touched while holding the lock.
    private boolean turnImagesAllowed;
    private List<String> turnImageUrls = List.of();
    private final List<byte[]> turnImages = new ArrayList<>();

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
    }

    /** A file the member attached, already read into text (see {@code FileRenderService}). */
    public record FileAttachment(String fileName, String content) {}

    /** The answer as embed-sized text chunks, plus any images the model generated (PNG bytes). */
    public record ChatReply(List<String> chunks, List<byte[]> images) {
        static ChatReply text(String text) {
            return new ChatReply(List.of(text), List.of());
        }
    }

    public List<String> chat(String message) {
        return chat(null, message, null, null, false).chunks();
    }

    public List<String> chatWithMedia(String userMessage, List<String> imageUrls) {
        return chat(null, userMessage, imageUrls, null, false).chunks();
    }

    /**
     * Answers one message. Images and file contents are sent for this turn only; the saved history
     * keeps a short note instead, so a big PDF isn't re-sent and stored with every later turn.
     *
     * @param author        the member's display name, sent as a "[Name]:" tag so the model can tell
     *                      members apart in a shared channel; null sends the message untagged
     * @param imagesAllowed offer the model the generate_image tool (it spends money, so owner only)
     */
    public ChatReply chat(String author, String userMessage, List<String> imageUrls, List<FileAttachment> files,
                          boolean imagesAllowed) {

        String message = userMessage == null ? "" : userMessage.strip();
        boolean hasImages = imageUrls != null && !imageUrls.isEmpty();
        boolean hasFiles = files != null && !files.isEmpty();

        if (message.isEmpty() && !hasImages && !hasFiles) {
            return ChatReply.text("Hi! Ask me anything.");
        }

        // Tagged in both the request and the saved history, so later turns still know who said what.
        String text = authorTag(author) + message;

        lock.lock();
        try {
            loadHistoryOnce();
            turnImagesAllowed = imagesAllowed;
            turnImageUrls = hasImages ? List.copyOf(imageUrls) : List.of();
            turnImages.clear();

            List<ResponseInputItem> inputs = new ArrayList<>(history.size() + 1);
            for (Turn turn : history) {
                inputs.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                        .role(turn.role())
                        .content(turn.content())
                        .build()));
            }
            inputs.add(buildUserMessage(withFiles(text, files), imageUrls));

            String reply = runAgenticLoop(inputs);

            // Discord CDN links expire, so images are remembered only as a note, not replayed.
            StringBuilder remembered = new StringBuilder(text);
            if (hasImages) {
                remembered.append(" [sent ").append(imageUrls.size()).append(" image(s)]");
            }
            if (hasFiles) {
                remembered.append(" [attached: ")
                        .append(String.join(", ", files.stream().map(FileAttachment::fileName).toList()))
                        .append(']');
            }
            String rememberedReply = turnImages.isEmpty() ? reply
                    : reply + " [generated " + turnImages.size() + " image(s)]";
            remember(remembered.toString().strip(), rememberedReply);

            return new ChatReply(splitIntoChunks(reply), List.copyOf(turnImages));

        } catch (Exception e) {
            log.error("Chat session {} failed", sessionId, e);
            return ChatReply.text("Sorry, I couldn't answer that right now. Please try again in a moment.");
        } finally {
            turnImagesAllowed = false;
            turnImageUrls = List.of();
            turnImages.clear();
            lock.unlock();
        }
    }

    /** Forgets this session's conversation, in memory and in the database. */
    public void clearHistory() {
        lock.lock();
        try {
            history.clear();
            historyChars = 0;
            historyLoaded = true;
            ChatHistoryRepository.clear(sessionId);
        } catch (Exception e) {
            log.error("Failed to clear chat history for session {}", sessionId, e);
        } finally {
            lock.unlock();
        }
    }

    private void loadHistoryOnce() {
        if (historyLoaded) return;
        historyLoaded = true;

        try {
            for (ChatHistoryRepository.StoredMessage stored : ChatHistoryRepository.loadRecent(sessionId, MAX_HISTORY_MESSAGES)) {
                EasyInputMessage.Role role = "assistant".equals(stored.role())
                        ? EasyInputMessage.Role.ASSISTANT
                        : EasyInputMessage.Role.USER;
                append(new Turn(role, stored.content()));
            }
            log.debug("Loaded {} chat messages for session {}", history.size(), sessionId);
        } catch (Exception e) {
            log.error("Failed to load chat history for session {}; starting fresh", sessionId, e);
        }
    }

    private void remember(String userContent, String assistantContent) {
        append(new Turn(EasyInputMessage.Role.USER, userContent));
        append(new Turn(EasyInputMessage.Role.ASSISTANT, assistantContent));

        Caffein.getInstance().getExecutorService()
                .execute(() -> ChatHistoryRepository.saveTurn(sessionId, userContent, assistantContent));
    }

    private void append(Turn turn) {
        history.addLast(turn);
        historyChars += turn.content().length();

        // Drop the oldest messages until the window fits, but always keep the newest exchange.
        while (history.size() > 2 && (history.size() > MAX_HISTORY_MESSAGES || historyChars > MAX_HISTORY_CHARS)) {
            historyChars -= history.removeFirst().content().length();
        }
        // Never start the window with an assistant reply that has lost its question.
        while (history.size() > 2 && history.peekFirst().role() == EasyInputMessage.Role.ASSISTANT) {
            historyChars -= history.removeFirst().content().length();
        }
    }

    /**
     * "[Name]: " for the start of a member's message. Brackets, colons and line breaks are removed
     * from the name, so a display name can't fake a second speaker or close the tag early.
     */
    private static String authorTag(String author) {
        if (author == null) {
            return "";
        }

        String name = author.replaceAll("[\\[\\]:\\r\\n]", " ").replaceAll("\\s+", " ").strip();
        if (name.length() > MAX_AUTHOR_NAME) {
            name = name.substring(0, MAX_AUTHOR_NAME).strip();
        }
        return name.isEmpty() ? "" : "[" + name + "]: ";
    }

    /** Appends each file's text under its name, sharing one size budget across all files. */
    private static String withFiles(String text, List<FileAttachment> files) {

        if (files == null || files.isEmpty()) {
            return text;
        }

        StringBuilder out = new StringBuilder(text);
        int budget = MAX_FILE_CHARS;

        for (FileAttachment file : files) {
            String content = file.content() == null ? "" : file.content();
            out.append("\n\n--- File: ").append(file.fileName()).append(" ---\n");

            if (budget <= 0) {
                out.append("[not included: the attached files were too long]");
                continue;
            }
            if (content.length() > budget) {
                out.append(content, 0, budget).append("\n[... file truncated]");
                budget = 0;
            } else {
                out.append(content);
                budget -= content.length();
            }
        }
        return out.toString().strip();
    }

    private static ResponseInputItem buildUserMessage(String text, List<String> imageUrls) {

        if (imageUrls == null || imageUrls.isEmpty()) {
            return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                    .role(EasyInputMessage.Role.USER)
                    .content(text)
                    .build());
        }

        ResponseInputItem.Message.Builder builder = ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER);

        if (!text.isEmpty()) {
            builder.addInputTextContent(text);
        }

        for (String imageUrl : imageUrls) {
            builder.addContent(ResponseInputImage.builder()
                    .imageUrl(imageUrl)
                    .detail(ResponseInputImage.Detail.AUTO)
                    .build());
        }

        return ResponseInputItem.ofMessage(builder.build());
    }

    /**
     * Sends the conversation and keeps answering function calls until the model replies with text.
     * Follow-up requests chain on {@code previous_response_id}, so only the tool outputs are re-sent.
     */
    private String runAgenticLoop(List<ResponseInputItem> inputs) {

        List<ResponseInputItem> currentInputs = inputs;
        String previousResponseId = null;
        int toolCalls = 0;

        for (int round = 0; ; round++) {

            // Out of budget: one last request with tools off, so the model answers with what it found.
            boolean mustAnswer = round >= MAX_TOOL_ROUNDS || toolCalls >= MAX_TOOL_CALLS;

            ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                    .model(Constant.MODEL)
                    .instructions(Constant.SYSTEM_PROMPT)
                    .addTool(WEB_SEARCH_TOOL)
                    .addTool(IMAGE_SEARCH_TOOL)
                    .addTool(AQW_WIKI_TOOL)
                    .addTool(AQW_ITEM_GUIDE_TOOL)
                    .reasoning(Reasoning.builder().effort(REASONING_EFFORT).build())
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .truncation(ResponseCreateParams.Truncation.AUTO)
                    // Same key per channel, so the system prompt and older history hit OpenAI's prompt cache.
                    .promptCacheKey("caffein-chat-" + sessionId)
                    .input(ResponseCreateParams.Input.ofResponse(currentInputs));

            if (previousResponseId != null) {
                builder.previousResponseId(previousResponseId);
            }
            if (turnImagesAllowed) {
                builder.addTool(GENERATE_IMAGE_TOOL);
            }
            if (mustAnswer) {
                builder.toolChoice(ToolChoiceOptions.NONE);
            }

            Response response = CLIENT.responses().create(builder.build());
            previousResponseId = response.id();

            List<ResponseInputItem> toolOutputs = new ArrayList<>();
            for (ResponseOutputItem item : response.output()) {
                if (item.isFunctionCall()) {
                    ResponseFunctionToolCall call = item.asFunctionCall();
                    // Every call needs an output, so calls over the budget get a short refusal instead.
                    String output = toolCalls < MAX_TOOL_CALLS
                            ? executeTool(call.name(), call.arguments())
                            : "Not looked up: the lookup limit for this message was reached.";
                    toolCalls++;
                    toolOutputs.add(ResponseInputItem.ofFunctionCallOutput(ResponseInputItem.FunctionCallOutput.builder()
                            .callId(call.callId())
                            .output(output)
                            .build()));
                }
            }

            if (toolOutputs.isEmpty() || mustAnswer) {
                String text = extractText(response);
                if (text.isEmpty() && !turnImages.isEmpty()) {
                    return "Here's your image!";
                }
                if (text.isEmpty()) {
                    log.warn("Empty reply for session {} (status={}, incomplete={})",
                            sessionId, response.status().orElse(null), response.incompleteDetails().orElse(null));
                    return "(no response)";
                }
                return text;
            }

            currentInputs = new ArrayList<>(toolOutputs);
            if (round + 1 >= MAX_TOOL_ROUNDS || toolCalls >= MAX_TOOL_CALLS) {
                log.info("Session {} used its lookup budget ({} calls in {} rounds); asking for the answer",
                        sessionId, toolCalls, round + 1);
                currentInputs.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                        .role(EasyInputMessage.Role.DEVELOPER)
                        .content(BUDGET_REACHED_NOTE)
                        .build()));
            }
        }
    }

    private String executeTool(String functionName, String argumentsJson) {

        try {
            JsonNode args = MAPPER.readTree(argumentsJson);

            return switch (functionName) {
                case "image_search" -> {
                    String query = args.path("query").asText("");
                    int count = args.path("count").asInt(5);
                    log.info("Image search for session {}: {} ({})", sessionId, query, count);
                    yield ImageSearchEngineService.search(query, count);
                }
                case "aqw_wiki" -> {
                    String query = args.path("query").asText("");
                    String focus = args.path("focus").asText("");
                    log.info("AQW wiki lookup for session {}: {} (focus: {})", sessionId, query, focus);
                    yield AqwWikiService.search(query, focus);
                }
                case "aqw_item_guide" -> {
                    String item = args.path("item").asText("");
                    log.info("AQW item guide for session {}: {}", sessionId, item);
                    yield AqwGuideService.guide(item);
                }
                case "generate_image" -> generateImage(
                        args.path("prompt").asText(""),
                        args.path("use_reference").asBoolean(false));
                default -> "Unknown tool: " + functionName;
            };
        } catch (Exception e) {
            log.error("Tool {} failed with arguments {}", functionName, argumentsJson, e);
            return "Tool execution error: " + e.getMessage();
        }
    }

    /** Runs the generate_image tool; the image is attached to the reply, and the model gets a short status. */
    private String generateImage(String prompt, boolean useReference) {

        if (!turnImagesAllowed) {
            return "Image generation isn't available to this member.";
        }
        if (turnImages.size() >= MAX_IMAGES_PER_MESSAGE) {
            return "Not generated: the limit of " + MAX_IMAGES_PER_MESSAGE + " images per message was reached.";
        }
        if (prompt.isBlank()) {
            return "Not generated: the prompt was empty.";
        }

        List<ImageGenerationService.ReferenceImage> references = new ArrayList<>();
        if (useReference) {
            if (turnImageUrls.isEmpty()) {
                return "Not generated: no image is attached to this message to use as a reference. "
                        + "Ask the member to attach one.";
            }
            for (int i = 0; i < turnImageUrls.size(); i++) {
                try {
                    references.add(ImageGenerationService.downloadReference(turnImageUrls.get(i)));
                } catch (Exception e) {
                    log.warn("Couldn't use attached image {} as a reference: {}", i + 1, e.getMessage());
                    return "Not generated: attached image " + (i + 1) + " couldn't be used as a reference ("
                            + e.getMessage() + ").";
                }
            }
        }

        try {
            ImageGenerationService.GeneratedImage image = ImageGenerationService.generate(prompt, references);
            turnImages.add(image.png());

            ImageGenerationRepository.save(Constant.KIAN_ID, 0, prompt, ImageGenerationService.MODEL.asString(),
                    ImageGenerationService.QUALITY.asString(), image.inputTokens(), image.outputTokens());
            log.info("Chatbot made an image for session {}{} ({} in / {} out tokens, ≈ ${})", sessionId,
                    references.isEmpty() ? "" : " from " + references.size() + " reference(s)", image.inputTokens(), image.outputTokens(),
                    String.format("%.4f", image.estimatedCost()));

            return "Done. The image will be attached to your reply automatically, so don't add a link or "
                    + "markdown image for it. Just say briefly what you made.";
        } catch (ImageGenerationService.BlockedPromptException e) {
            log.info("Chatbot image request for session {} was blocked: {}", sessionId, e.getMessage());
            return "Blocked: OpenAI's safety system refused this image request. Tell the member it was blocked "
                    + "and why that kind of request usually is (for example real people's faces); don't try to get around it.";
        } catch (Exception e) {
            log.error("Chatbot image generation failed for session {}", sessionId, e);
            return "Failed: the image couldn't be generated right now (" + e.getMessage() + ").";
        }
    }

    private static String extractText(Response response) {

        StringBuilder text = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            if (!item.isMessage()) continue;

            for (ResponseOutputMessage.Content content : item.asMessage().content()) {
                if (content.isOutputText()) {
                    if (!text.isEmpty()) text.append("\n\n");
                    text.append(content.asOutputText().text());
                }
            }
        }
        return text.toString().strip();
    }

    /** Splits text for embed descriptions, preferring line breaks, then spaces, over cutting words. */
    private static List<String> splitIntoChunks(String text) {

        if (text.length() <= MAX_CHUNK_SIZE) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_SIZE, text.length());

            if (end < text.length()) {
                int minBreak = start + MAX_CHUNK_SIZE / 2;
                int newline = text.lastIndexOf('\n', end);
                int space = text.lastIndexOf(' ', end);

                if (newline >= minBreak) end = newline + 1;
                else if (space >= minBreak) end = space + 1;
            }

            String chunk = text.substring(start, end).strip();
            if (!chunk.isEmpty()) chunks.add(chunk);
            start = end;
        }
        return chunks;
    }
}
