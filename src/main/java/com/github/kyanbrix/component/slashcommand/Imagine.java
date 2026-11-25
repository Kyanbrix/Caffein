package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.api.OpenAI.service.ImageGenerationService;
import com.github.kyanbrix.api.OpenAI.service.ImageGenerationService.GeneratedImage;
import com.github.kyanbrix.config.database.ImageGenerationRepository;
import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@code /imagine <prompt>}: generates an image with gpt-image-2 at medium quality (about $0.053 each).
 * Only the owner can use it, with no limits; anyone else gets a private refusal. Every image is
 * still logged to {@code image_generations} so the spend can be checked.
 */
public class Imagine implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(Imagine.class);

    private static final int MAX_PROMPT_LENGTH = 1000;
    private static final String FILE_NAME = "imagine.png";
    // OpenAI accepts up to 50 MB; Discord uploads are usually smaller anyway
    private static final long MAX_REFERENCE_BYTES = 25L * 1024 * 1024;

    // At most two images are generated at once; more wait their turn.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "imagine");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {

        // The command is installed in the server too, so without this anyone there could spend on the owner's key
        if (event.getUser().getIdLong() != Constant.KIAN_ID) {
            event.reply("This command is private.").setEphemeral(true).queue();
            return;
        }

        String prompt = event.getOption("prompt", OptionMapping::getAsString);
        if (prompt == null || prompt.isBlank()) {
            event.reply("Please describe the image you want.").setEphemeral(true).queue();
            return;
        }

        Message.Attachment reference = event.getOption("reference", OptionMapping::getAsAttachment);
        if (reference != null) {
            String problem = referenceProblem(reference);
            if (problem != null) {
                event.reply(problem).setEphemeral(true).queue();
                return;
            }
        }

        long userId = event.getUser().getIdLong();
        // 0 in DMs and other places the command is used as a user-installed app
        long guildId = event.getGuild() == null ? 0 : event.getGuild().getIdLong();

        event.deferReply().queue();
        EXECUTOR.submit(() -> handle(event.getHook(), userId, guildId, prompt.strip(), reference));
    }

    /** Why OpenAI wouldn't accept this attachment as a reference, or null if it's fine. */
    private static String referenceProblem(Message.Attachment attachment) {
        String contentType = contentTypeOf(attachment);
        if (contentType == null) {
            return "The reference has to be a PNG, JPEG or WebP image.";
        }
        if (attachment.getSize() > MAX_REFERENCE_BYTES) {
            return "The reference image is too big. Please use one under " + (MAX_REFERENCE_BYTES / 1024 / 1024) + " MB.";
        }
        return null;
    }

    /** The image type OpenAI accepts for this attachment, from Discord's type or the file extension; null if unsupported. */
    private static String contentTypeOf(Message.Attachment attachment) {
        String type = attachment.getContentType() == null ? "" : attachment.getContentType().toLowerCase(Locale.ROOT);
        String extension = attachment.getFileExtension() == null ? "" : attachment.getFileExtension().toLowerCase(Locale.ROOT);

        if (type.startsWith("image/png") || extension.equals("png")) return "image/png";
        if (type.startsWith("image/jpeg") || extension.equals("jpg") || extension.equals("jpeg")) return "image/jpeg";
        if (type.startsWith("image/webp") || extension.equals("webp")) return "image/webp";
        return null;
    }

    private void handle(InteractionHook hook, long userId, long guildId, String prompt, Message.Attachment reference) {

        try {
            ImageGenerationService.ReferenceImage referenceImage = null;
            if (reference != null) {
                byte[] data;
                try (InputStream in = reference.getProxy().download().join()) {
                    data = in.readAllBytes();
                }
                referenceImage = new ImageGenerationService.ReferenceImage(data, reference.getFileName(), contentTypeOf(reference));
            }

            GeneratedImage image = ImageGenerationService.generate(prompt,
                    referenceImage == null ? List.of() : List.of(referenceImage));

            ImageGenerationRepository.save(userId, guildId, prompt, ImageGenerationService.MODEL.asString(),
                    ImageGenerationService.QUALITY.asString(), image.inputTokens(), image.outputTokens());
            log.info("/imagine made an image{} ({} in / {} out tokens, ≈ ${})", reference != null ? " from a reference" : "",
                    image.inputTokens(), image.outputTokens(), String.format("%.4f", image.estimatedCost()));

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(0xCD853F)
                    .setDescription(Wiki.truncate("**Prompt:** " + MarkdownSanitizer.escape(prompt), 4000))
                    .setImage("attachment://" + FILE_NAME)
                    .setTimestamp(Instant.now());
            // The reference shown small in the corner, so the result can be compared with it
            if (reference != null) {
                embed.setThumbnail(reference.getUrl());
            }

            hook.sendMessageEmbeds(embed.build())
                    .addFiles(FileUpload.fromData(image.png(), FILE_NAME))
                    .queue();

        } catch (ImageGenerationService.BlockedPromptException e) {
            log.info("/imagine prompt was blocked: {}", e.getMessage());
            hook.sendMessage("OpenAI's safety filter blocked that prompt" + (reference != null ? " or reference image" : "")
                    + ", so no image was made.").queue();
        } catch (Exception e) {
            log.error("/imagine failed", e);
            hook.sendMessage("Something went wrong while generating the image. Try again in a bit.").queue();
        }
    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("imagine", "Generate an image with AI")
                .addOptions(new OptionData(OptionType.STRING, "prompt", "Describe the image you want", true)
                        .setMinLength(3)
                        .setMaxLength(MAX_PROMPT_LENGTH))
                .addOptions(new OptionData(OptionType.ATTACHMENT, "reference", "Optional image to base it on (PNG, JPEG or WebP)", false))
                .setContexts(InteractionContextType.GUILD,InteractionContextType.PRIVATE_CHANNEL)
                .setIntegrationTypes(IntegrationType.USER_INSTALL,IntegrationType.GUILD_INSTALL);
    }
}
