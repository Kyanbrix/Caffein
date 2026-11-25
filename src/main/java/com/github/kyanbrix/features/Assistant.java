package com.github.kyanbrix.features;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.api.OpenAI.ChatSession;
import com.github.kyanbrix.api.OpenAI.ChatSession.ChatReply;
import com.github.kyanbrix.api.OpenAI.ChatSession.FileAttachment;
import com.github.kyanbrix.api.OpenAI.SessionManager;
import com.github.kyanbrix.api.OpenAI.service.FileRenderService;
import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Assistant extends ListenerAdapter {


    private static final Logger log = LoggerFactory.getLogger(Assistant.class);
    private final SessionManager sessionManager = new  SessionManager();

    // Discord's typing indicator lasts about 10 seconds, so it's refreshed while the AI is working.
    private static final long TYPING_REFRESH_SECONDS = 8;


    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {

        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        Message message = event.getMessage();
        long selfId = event.getJDA().getSelfUser().getIdLong();

        boolean mentioned = message.getMentions().getUsers().stream().anyMatch(user -> user.getIdLong() == selfId);
        if (!mentioned) return;

        String text = message.getContentRaw()
                .replace("<@" + selfId + ">", "")
                .replace("<@!" + selfId + ">", "")
                .strip();

        // Images go to the model as images; other files (except videos) are read as text.
        List<String> imageUrls = new ArrayList<>();
        List<Message.Attachment> files = new ArrayList<>();
        for (Message.Attachment attachment : message.getAttachments()) {
            if (attachment.isImage()) imageUrls.add(attachment.getUrl());
            else if (!attachment.isVideo()) files.add(attachment);
        }

        ChatSession chatSession = sessionManager.getOrCreate(event.getChannel().getId());
        MessageChannel channel = event.getChannel();
        User author = event.getAuthor();
        // The server nickname, which is what other members see and call each other
        String authorName = event.getMember() != null ? event.getMember().getEffectiveName() : author.getEffectiveName();

        // One task per message: the files are read, then everything goes to the AI in a single request.
        Caffein.getInstance().getExecutorService().submit(() -> {

            ScheduledFuture<?> typing = Caffein.getInstance().getService().scheduleAtFixedRate(
                    () -> channel.sendTyping().queue(null, error -> {}),
                    0, TYPING_REFRESH_SECONDS, TimeUnit.SECONDS);

            try {
                List<FileAttachment> fileTexts = new ArrayList<>(files.size());
                for (Message.Attachment file : files) {
                    fileTexts.add(new FileAttachment(file.getFileName(), FileRenderService.readFile(file.getUrl(), file.getFileName())));
                }

                // Image generation spends money, so only the owner gets the tool
                boolean imagesAllowed = author.getIdLong() == Constant.KIAN_ID;
                ChatReply chatReply = chatSession.chat(authorName, text, imageUrls, fileTexts, imagesAllowed);
                reply(message, channel, author, chatReply);

            } catch (Exception e) {
                log.error("Assistant failed to answer message {}", message.getId(), e);
            } finally {
                typing.cancel(false);
            }
        });
    }

    /**
     * Replies to the message with the first chunk and sends any remaining chunks after it.
     * Generated images go on the last message, the first one shown inside its embed.
     */
    private static void reply(Message message, MessageChannel channel, User author, ChatReply chatReply) {

        EmbedBuilder builder = new EmbedBuilder();
        builder.setAuthor("AI ChatBot",null,"https://cdn3.emoji.gg/emojis/2660_Clyde_Bot.png");
        builder.setColor(0xCD853F);
        builder.setFooter("Requested by: "+ author.getEffectiveName(),author.getEffectiveAvatarUrl());
        builder.setTimestamp(Instant.now());

        List<String> chunks = chatReply.chunks();
        for (int i = 0; i < chunks.size(); i++) {
            builder.setDescription(chunks.get(i));

            List<FileUpload> files = List.of();
            if (i == chunks.size() - 1 && !chatReply.images().isEmpty()) {
                files = imageFiles(chatReply.images());
                builder.setImage("attachment://" + files.getFirst().getName());
            }

            if (i == 0) message.replyEmbeds(builder.build()).addFiles(files).queue();
            else channel.sendMessageEmbeds(builder.build()).addFiles(files).queue();
        }
    }

    private static List<FileUpload> imageFiles(List<byte[]> images) {
        List<FileUpload> files = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            files.add(FileUpload.fromData(images.get(i), "chatbot-image-" + (i + 1) + ".png"));
        }
        return files;
    }
}
