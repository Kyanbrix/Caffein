package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.api.OpenAI.service.VideoGenerationService;
import com.github.kyanbrix.api.OpenAI.service.VideoGenerationService.Orientation;
import com.github.kyanbrix.config.database.VideoGenerationRepository;
import com.github.kyanbrix.utils.Constant;
import com.openai.models.videos.VideoModel;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * {@code /video prompt [model] [orientation] [seconds] [reference]}: makes a short 720p video with OpenAI's Sora
 * ({@code sora-2} at $0.10 a second unless {@code sora-2-pro}, $0.30 a second, is picked). Owner-only, like
 * {@code /imagine}. Every job OpenAI accepts is logged to {@code video_generations}.
 * <p>
 * A video takes minutes, so the deferred reply shows the job's progress and the finished video comes as a
 * follow-up that pings the owner. Discord's interaction token lasts 15 minutes; after that the video is posted
 * straight to the channel when the bot can, or else sent by DM.
 */
public class GenerateVideo implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(GenerateVideo.class);

    private static final int MAX_PROMPT_LENGTH = 2000;
    private static final int DEFAULT_SECONDS = 4;
    private static final String FILE_NAME = "video.mp4";
    private static final long MAX_REFERENCE_BYTES = 25L * 1024 * 1024;
    // Discord's upload limit without boosts, used where the bot can't see the server's own limit
    private static final long DEFAULT_MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    // The interaction token lasts 15 minutes; leave room for the upload itself
    private static final Duration HOOK_LIFETIME = Duration.ofMinutes(14);

    // Each job holds a thread for minutes while it waits, so at most two run at once; more wait their turn.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "video");
        thread.setDaemon(true);
        return thread;
    });

    /** Everything one job needs once it's off the JDA thread. */
    private record Request(InteractionHook hook, Instant hookExpires, MessageChannel channel, long userId,
                           long guildId, long maxUploadBytes, String prompt, VideoModel model,
                           Orientation orientation, int seconds, Message.Attachment reference) {

        boolean hookUsable() {
            return Instant.now().isBefore(hookExpires);
        }

        String settings() {
            return model.asString() + " · " + seconds + " s · " + orientation.label()
                    + (reference != null ? " · from a reference" : "")
                    + String.format(Locale.ROOT, " · ≈ $%.2f", VideoGenerationService.estimatedCost(model, seconds));
        }
    }

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {

        // Installed in the server and as a user app, so without this anyone could spend on the owner's key
        if (event.getUser().getIdLong() != Constant.KIAN_ID) {
            event.reply("This command is private.").setEphemeral(true).queue();
            return;
        }

        String prompt = event.getOption("prompt", OptionMapping::getAsString);
        if (prompt == null || prompt.isBlank()) {
            event.reply("Please describe the video you want.").setEphemeral(true).queue();
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

        VideoModel model = VideoModel.of(event.getOption("model", VideoGenerationService.DEFAULT_MODEL.asString(),
                OptionMapping::getAsString));
        Orientation orientation = Orientation.fromLabel(event.getOption("orientation", "landscape", OptionMapping::getAsString));
        int seconds = event.getOption("seconds", DEFAULT_SECONDS, OptionMapping::getAsInt);

        boolean attachedGuild = event.isFromAttachedGuild() && event.getGuild() != null;
        long guildId = event.getGuild() == null ? 0 : event.getGuild().getIdLong();
        long maxUploadBytes = attachedGuild ? event.getGuild().getMaxFileSize() : DEFAULT_MAX_UPLOAD_BYTES;

        Request request = new Request(event.getHook(), event.getTimeCreated().toInstant().plus(HOOK_LIFETIME),
                canPostInChannel(event) ? event.getMessageChannel() : null, event.getUser().getIdLong(), guildId,
                maxUploadBytes, prompt.strip(), model, orientation, seconds, reference);

        event.deferReply().queue();
        EXECUTOR.submit(() -> handle(request));
    }

    /** Whether the bot can post in the channel itself, for when the interaction token has run out. */
    private static boolean canPostInChannel(SlashCommandInteraction event) {
        return event.isFromAttachedGuild() && event.getGuild() != null
                && event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(),
                Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_ATTACH_FILES);
    }

    /** Why this attachment can't be used as a reference, or null if it's fine. */
    private static String referenceProblem(Message.Attachment attachment) {
        String type = attachment.getContentType() == null ? "" : attachment.getContentType().toLowerCase(Locale.ROOT);
        String extension = attachment.getFileExtension() == null ? "" : attachment.getFileExtension().toLowerCase(Locale.ROOT);
        boolean supported = type.startsWith("image/png") || type.startsWith("image/jpeg")
                || extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg");
        if (!supported) {
            return "The reference has to be a PNG or JPEG image.";
        }
        if (attachment.getSize() > MAX_REFERENCE_BYTES) {
            return "The reference image is too big. Please use one under " + (MAX_REFERENCE_BYTES / 1024 / 1024) + " MB.";
        }
        return null;
    }

    private void handle(Request request) {

        InteractionHook hook = request.hook();
        String videoId = null;

        try {
            byte[] referencePng = null;
            if (request.reference() != null) {
                byte[] data;
                try (InputStream in = request.reference().getProxy().download().join()) {
                    data = in.readAllBytes();
                }
                referencePng = VideoGenerationService.fitReference(data, request.orientation());
            }

            hook.editOriginal(progressText(request, 0)).queue();

            try {
                videoId = VideoGenerationService.start(request.prompt(), request.model(), request.orientation(),
                        request.seconds(), referencePng);
            } catch (VideoGenerationService.BlockedPromptException e) {
                log.info("/video prompt was blocked: {}", e.getMessage());
                hook.editOriginal("OpenAI's safety filter blocked that prompt" + (request.reference() != null ? " or reference image" : "")
                        + ", so no video was made.").queue();
                return;
            }
            log.info("/video started {} ({})", videoId, request.settings());

            long[] lastProgress = {-1};
            VideoGenerationService.await(videoId, progress -> {
                if (progress != lastProgress[0] && request.hookUsable()) {
                    lastProgress[0] = progress;
                    hook.editOriginal(progressText(request, progress)).queue(null, error -> {});
                }
            });

            byte[] mp4 = VideoGenerationService.download(videoId);
            double cost = VideoGenerationService.estimatedCost(request.model(), request.seconds());
            save(request, videoId, "completed", cost);
            log.info("/video finished {} ({} bytes, ≈ ${})", videoId, mp4.length, String.format(Locale.ROOT, "%.2f", cost));

            deliver(request, mp4);

        } catch (VideoGenerationService.VideoFailedException e) {
            log.warn("/video job {} failed: {}", videoId, e.getMessage());
            save(request, videoId, "failed", 0);
            reportText(request, e.isModeration()
                    ? "OpenAI's safety filter stopped the video partway through, so nothing was made. Try rewording the prompt."
                    : "OpenAI couldn't make that video (" + e.code() + "). Try again in a bit.");
        } catch (TimeoutException e) {
            log.warn("/video gave up waiting: {}", e.getMessage());
            save(request, videoId, "timed_out", 0);
            reportText(request, "OpenAI is taking unusually long, so I stopped waiting. The job ID is `" + videoId + "`.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("/video failed (job {})", videoId, e);
            if (videoId != null) {
                save(request, videoId, "error", 0);
            }
            reportText(request, "Something went wrong while making the video"
                    + (videoId != null ? " (job `" + videoId + "`)" : "") + ". Try again in a bit.");
        }
    }

    private static void save(Request request, String videoId, String status, double cost) {
        VideoGenerationRepository.save(request.userId(), request.guildId(), videoId, request.prompt(),
                request.model().asString(), request.orientation().size().asString(), request.seconds(),
                request.reference() != null, status, cost);
    }

    private static String progressText(Request request, long progress) {
        return "🎬 Making your video… **" + progress + "%**\n-# " + request.settings() + " · usually takes a few minutes";
    }

    /** Sends the finished video as a new message that pings the owner, by the first route still open. */
    private static void deliver(Request request, byte[] mp4) {

        if (mp4.length > request.maxUploadBytes()) {
            reportText(request, String.format(Locale.ROOT,
                    "The video came out at %.1f MB, over the %d MB upload limit here, so it can't be posted.",
                    mp4.length / 1024.0 / 1024.0, request.maxUploadBytes() / 1024 / 1024));
            return;
        }

        String caption = "<@" + request.userId() + "> your video is ready\n"
                + Wiki.truncate("**Prompt:** " + MarkdownSanitizer.escape(request.prompt()), 1700)
                + "\n-# " + request.settings();

        if (request.hookUsable()) {
            request.hook().sendMessage(caption)
                    .setAllowedMentions(List.of())
                    .mentionUsers(request.userId())
                    .addFiles(FileUpload.fromData(mp4, FILE_NAME))
                    .complete();
            request.hook().deleteOriginal().queue(null, error -> {});
        } else if (request.channel() != null) {
            request.channel().sendMessage(caption)
                    .setAllowedMentions(List.of())
                    .mentionUsers(request.userId())
                    .addFiles(FileUpload.fromData(mp4, FILE_NAME))
                    .complete();
        } else {
            JDA jda = request.hook().getJDA();
            jda.openPrivateChannelById(request.userId())
                    .flatMap(dm -> dm.sendMessage(caption).addFiles(FileUpload.fromData(mp4, FILE_NAME)))
                    .complete();
        }
    }

    /** A text-only outcome, by the same routes as {@link #deliver}. */
    private static void reportText(Request request, String text) {
        try {
            if (request.hookUsable()) {
                request.hook().editOriginal(text).setAllowedMentions(List.of()).complete();
            } else if (request.channel() != null) {
                request.channel().sendMessage("<@" + request.userId() + "> " + text)
                        .setAllowedMentions(List.of())
                        .mentionUsers(request.userId())
                        .complete();
            } else {
                request.hook().getJDA().openPrivateChannelById(request.userId())
                        .flatMap(dm -> dm.sendMessage(text))
                        .complete();
            }
        } catch (Exception e) {
            log.warn("Couldn't tell the owner how /video went: {}", text, e);
        }
    }

    @Override
    public @NonNull CommandData getCommandData() {

        OptionData model = new OptionData(OptionType.STRING, "model", "Which Sora model (Sora 2 if left out)", false)
                .addChoice("Sora 2 · $0.10/s", VideoModel.SORA_2.asString())
                .addChoice("Sora 2 Pro · $0.30/s, higher quality", VideoModel.SORA_2_PRO.asString());

        OptionData orientation = new OptionData(OptionType.STRING, "orientation", "Landscape (1280x720) or portrait (720x1280)", false)
                .addChoice("Landscape", Orientation.LANDSCAPE.label())
                .addChoice("Portrait", Orientation.PORTRAIT.label());

        OptionData seconds = new OptionData(OptionType.INTEGER, "seconds", "How long the video is (4 s if left out)", false)
                .addChoice("4 seconds", 4)
                .addChoice("8 seconds", 8)
                .addChoice("12 seconds", 12);

        return Commands.slash("video", "Generate a short video with AI")
                .addOptions(new OptionData(OptionType.STRING, "prompt", "Describe the video you want", true)
                        .setMinLength(3)
                        .setMaxLength(MAX_PROMPT_LENGTH))
                .addOptions(model, orientation, seconds)
                .addOptions(new OptionData(OptionType.ATTACHMENT, "reference", "Optional first frame to animate (PNG or JPEG)", false))
                .setContexts(InteractionContextType.GUILD, InteractionContextType.PRIVATE_CHANNEL)
                .setIntegrationTypes(IntegrationType.USER_INSTALL, IntegrationType.GUILD_INSTALL);
    }
}
