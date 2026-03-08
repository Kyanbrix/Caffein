package com.github.kyanbrix.features;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.google.genai.Chat;
import com.google.genai.Client;
import com.google.genai.types.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Assistant extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(Assistant.class);
    private static final long RANT_CHANNEL_ID = 1477966443600679053L;
    private static final long RANT_LOG_CHANNEL_ID = 1477965995451748373L;
    private static final long GUILD_ID = 1469324454470353163L;

    private final Client geminiClient = new Client();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Map<Long, Chat> chatSessions = new ConcurrentHashMap<>();


    private final Map<Long, Integer> credits = new ConcurrentHashMap<>();

    private final Map<Long, Long> notificationCooldown = new ConcurrentHashMap<>();


    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {

        if (!event.isFromGuild() && !event.getAuthor().isBot()) return;

        Message message = event.getMessage();

        String contentRaw = message.getContentRaw().trim();

        if (!contentRaw.contains("<@&1477694522619068466>")) return;

        long userId = event.getAuthor().getIdLong();
        long currentMillis = System.currentTimeMillis();


        if (credits.containsKey(userId)) {

            int creditForPrompt = credits.get(userId);

            if (creditForPrompt == 0) {

                if (notificationCooldown.containsKey(userId)) {
                    if (notificationCooldown.get(event.getAuthor().getIdLong()) < System.currentTimeMillis()) {
                        event.getChannel().sendMessage("You dont have enough credits to prompt an AI").flatMap(Message::delete).delay(1, TimeUnit.MINUTES).queue();

                        notificationCooldown.replace(userId, currentMillis + 60000);

                        return;
                    }

                }else notificationCooldown.put(userId,currentMillis + 60000);

            } else credits.replace(userId, creditForPrompt - 1);


        }else credits.put(userId,4);


        //removing AI role mention
        String newContent = contentRaw.replace("<@&1477694522619068466>","").trim();

        String userName = message.getAuthor().getName();

        List<Message.Attachment> attachments = message.getAttachments();
        boolean hasImage =  !attachments.isEmpty() && attachments.getFirst().isImage();

        ZoneId zoneId = ZoneId.of("Asia/Manila");
        String currentDateTime = ZonedDateTime.now(zoneId)
                .format(DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a"));



        //check if the message is a reply
        if (message.getType().equals(MessageType.INLINE_REPLY)) {
            Message referenceMessage = message.getReferencedMessage();


            List<Message.Attachment> referenceMessageAttachment = referenceMessage.getAttachments();

            if (!referenceMessageAttachment.isEmpty()) {
                boolean imageAvailable = referenceMessageAttachment.getFirst().isImage();

                String prompt = String.format(
                        "[System Info: Current local time is %s]\n" +
                                "Context: The user '%s' is talking to YOU (the AI bot), and they attached a quoted message from '%s' for context.\n" +
                                "Quoted message from %s: \"%s\"\n" +
                                "User %s says to YOU: \"%s\"",
                        currentDateTime,
                        userName,
                        referenceMessage.getAuthor().getName(),
                        referenceMessage.getAuthor().getName(),
                        referenceMessage.getContentRaw(),
                        userName,
                        newContent
                );

                execution(event.getChannel(),referenceMessageAttachment,imageAvailable,prompt);


            }else {

                String prompt = String.format(
                        "[System Info: Current local time is %s]\n" +
                                "Context: The user '%s' is talking to YOU (the AI bot), and they attached a quoted message from '%s' for context.\n" +
                                "Quoted message from %s: \"%s\"\n" +
                                "User %s says to YOU: \"%s\"",
                        currentDateTime,
                        userName,
                        referenceMessage.getAuthor().getName(),
                        referenceMessage.getAuthor().getName(),
                        referenceMessage.getContentRaw(),
                        userName,
                        newContent
                );

                execution(event.getChannel(), referenceMessageAttachment, false, prompt);


            }





        }else {
            String prompt = String.format("[System Info: Current Local time is %s]\n%s says: %s",currentDateTime,userName,newContent);


            execution(event.getChannel(), attachments, hasImage, prompt);
        }




    }

    private void execution(MessageChannel channel, List<Message.Attachment> attachments, boolean hasImage, String prompt) {
        executor.submit(() -> {
            channel.sendTyping().queue();

            try {
                Content systemInstruction = Content.fromParts(Part.fromText(
                        "You are a helpful, conversational AI bot in a Discord server. " +
                                "Your ONLY job is to talk directly to the users. " +
                                "Do NOT act like a narrator, do NOT analyze the conversation from the outside, and do NOT simulate other users. " +
                                "Just read the context provided and reply naturally as the AI."
                ));

                Tool searchTool = Tool.builder()
                        .googleSearch(GoogleSearch.builder().build())
                        .build();

                Tool mapsTool = Tool.builder()
                        .googleMaps(GoogleMaps.builder().build())
                        .build();

                GenerateContentConfig contentConfig = GenerateContentConfig.builder()
                        .systemInstruction(systemInstruction)
                        .tools(List.of(searchTool,mapsTool))
                        .build();

                Chat chat = chatSessions.computeIfAbsent(channel.getIdLong(), aLong -> geminiClient.chats.create("gemini-2.5-flash",contentConfig));

                GenerateContentResponse response;

                if (hasImage) {

                    Message.Attachment attachment = attachments.getFirst();

                    try (InputStream inputStream = attachment.getProxy().download().join()) {
                        byte[] imageBytes = inputStream.readAllBytes();
                        String mimeType = attachment.getContentType();


                        Part textPart = Part.builder().text(prompt).build();
                        Part imagePart = Part.builder()
                                .inlineData(Blob.builder()
                                        .data(imageBytes)
                                        .mimeType(mimeType)
                                        .build())
                                .build();

                        Content multi = Content.builder()
                                .parts(List.of(textPart,imagePart))
                                .role("user")
                                .build();

                        response = chat.sendMessage(multi);

                    }

                }else {
                    response = chat.sendMessage(prompt);
                }

                sendLongMessage(channel,response.text());



            } catch (Exception e) {
                log.error(e.getMessage(),e.fillInStackTrace());
                channel.sendMessage(e.getMessage()).queue();
            }
        });
    }


    private void sendLongMessage(MessageChannel channel, String text) {
        if (text == null || text.isEmpty()) return;

        int i = 0;
        while (i < text.length()) {
            int end = Math.min(i + 1900, text.length());

            if (end < text.length()) {
                int lastNewline = text.lastIndexOf("\n", end);
                if (lastNewline > i + 500) {
                    end = lastNewline + 1;
                }
            }

            String part = text.substring(i, end).trim();
            if (!part.isEmpty()) {
                channel.sendMessage(part).queue();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
            i = end;
        }
    }




}
