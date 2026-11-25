package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.api.OpenAI.ChatSession;
import com.github.kyanbrix.api.OpenAI.SessionManager;
import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ChatBot implements ISlash{

    // Shared across invocations so each user keeps one session (cached history and its lock).
    // Sessions are keyed by user ID, so /ask-ai is a private conversation that also works in DMs.
    private static final SessionManager SESSIONS = new SessionManager();

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {

        String userMessage = event.getOption("message", OptionMapping::getAsString);

        Message.Attachment attachment = event.getOption("attachment",OptionMapping::getAsAttachment);

        if (attachment != null && !attachment.isImage()) {
            event.reply("Only images (PNG, JPEG, JPG) can be attached.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        User user = event.getUser();
        ChatSession chatSession = SESSIONS.getOrCreate(user.getId());
        // Server nickname in a guild, global display name in DMs
        String authorName = event.getMember() != null ? event.getMember().getEffectiveName() : user.getEffectiveName();

        // The OpenAI call can take a while, so keep it off the JDA event thread.
        Caffein.getInstance().getExecutorService().submit(() -> {

            // Image generation spends money, so only the owner gets the tool
            ChatSession.ChatReply chatReply = chatSession.chat(
                    authorName,
                    userMessage,
                    attachment == null ? null : List.of(attachment.getUrl()),
                    null,
                    user.getIdLong() == Constant.KIAN_ID);

            EmbedBuilder builder = new EmbedBuilder();
            builder.setAuthor("AI ChatBot",null,"https://cdn3.emoji.gg/emojis/2660_Clyde_Bot.png");
            builder.setColor(0xCD853F);
            builder.setFooter("Requested by: "+ user.getEffectiveName(),user.getEffectiveAvatarUrl());
            builder.setTimestamp(Instant.now());

            // Chunks are sized for embed descriptions; generated images go on the last message.
            List<String> chunks = chatReply.chunks();
            for (int i = 0; i < chunks.size(); i++) {
                builder.setDescription(chunks.get(i));

                List<FileUpload> files = new ArrayList<>();
                if (i == chunks.size() - 1) {
                    for (int n = 0; n < chatReply.images().size(); n++) {
                        files.add(FileUpload.fromData(chatReply.images().get(n), "chatbot-image-" + (n + 1) + ".png"));
                    }
                    if (!files.isEmpty()) builder.setImage("attachment://" + files.getFirst().getName());
                }

                event.getHook().sendMessageEmbeds(builder.build()).addFiles(files).queue();
            }
        });
    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("ask-ai","Ask or get help from AI Chatbot")
                .addOptions(new OptionData(OptionType.STRING,"message","Enter your message",true).setMinLength(10).setMaxLength(500))
                .addOptions(new OptionData(OptionType.ATTACHMENT,"attachment","Attach an image (PNG,JPEG,JPG)"))
                .setIntegrationTypes(IntegrationType.USER_INSTALL,IntegrationType.GUILD_INSTALL)
                .setContexts(InteractionContextType.GUILD,InteractionContextType.PRIVATE_CHANNEL);
    }
}
