package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.api.OpenAI.service.SpeechLanguage;
import com.github.kyanbrix.api.OpenAI.service.TranscriptionService;
import com.github.kyanbrix.api.OpenAI.service.TranscriptionService.Transcript;
import com.github.kyanbrix.config.database.TranscriptionRepository;
import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.filedisplay.FileDisplay;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.context.MessageContextInteraction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Speech to text, owner-only. Two ways in:
 * <ul>
 *     <li>{@code /transcribe file [language] [translate_to]} for an uploaded audio or video file.</li>
 *     <li>Right-click a message → Apps → "Transcribe" ({@link MessageMenu}) for a voice message or audio
 *     attachment; the spoken language is detected automatically.</li>
 * </ul>
 * A transcript short enough to show inline gets a "Translate to…" menu ({@link #TRANSLATE_SELECT_ID}), which
 * reads the transcript back from the message, so it keeps working after a restart.
 */
public class Transcribe implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(Transcribe.class);

    public static final String TRANSLATE_SELECT_ID = "transcribe:translate";

    // OpenAI's upload limit for transcription
    private static final long MAX_AUDIO_BYTES = 25L * 1024 * 1024;
    // Discord allows 4000 characters of text per Components V2 message; the header takes the rest
    private static final int MAX_INLINE_LENGTH = 3500;
    // Fixed so the translate menu can find the transcript in the message again
    private static final int TRANSCRIPT_UNIQUE_ID = 100;
    private static final Color ACCENT_COLOR = new Color(0xCD853F);

    // The formats OpenAI accepts, by extension, with the type to upload them as.
    // Opus and .oga files are Ogg inside, so they go up as .ogg.
    private static final Map<String, String> AUDIO_TYPES = Map.ofEntries(
            Map.entry("ogg", "audio/ogg"),
            Map.entry("oga", "audio/ogg"),
            Map.entry("opus", "audio/ogg"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("mpeg", "audio/mpeg"),
            Map.entry("mpga", "audio/mpeg"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("wav", "audio/wav"),
            Map.entry("webm", "audio/webm"),
            Map.entry("flac", "audio/flac"));

    // Two transcriptions or translations at once; more wait their turn.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "transcribe");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {

        // Installed in the server and as a user app, so without this anyone could spend on the owner's key
        if (event.getUser().getIdLong() != Constant.KIAN_ID) {
            event.reply("This command is private.").setEphemeral(true).queue();
            return;
        }

        Message.Attachment file = event.getOption("file", OptionMapping::getAsAttachment);
        String problem = file == null ? "Please attach an audio file." : audioProblem(file);
        if (problem != null) {
            event.reply(problem).setEphemeral(true).queue();
            return;
        }

        SpeechLanguage language = SpeechLanguage.fromCode(event.getOption("language", OptionMapping::getAsString));
        SpeechLanguage translateTo = SpeechLanguage.fromCode(event.getOption("translate_to", OptionMapping::getAsString));
        long guildId = event.getGuild() == null ? 0 : event.getGuild().getIdLong();

        event.deferReply().queue();
        EXECUTOR.submit(() -> handle(event.getHook(), event.getUser().getIdLong(), guildId, file, language, translateTo, null));
    }

    /** Why OpenAI wouldn't take this attachment, or null if it's fine. */
    private static String audioProblem(Message.Attachment attachment) {
        if (extensionOf(attachment) == null) {
            return "That file type isn't supported. Use a voice message or an OGG, MP3, M4A, MP4, WAV, WebM or FLAC file.";
        }
        if (attachment.getSize() > MAX_AUDIO_BYTES) {
            return "That file is too big. OpenAI takes audio up to " + (MAX_AUDIO_BYTES / 1024 / 1024) + " MB.";
        }
        return null;
    }

    /** The attachment's extension if it's a format OpenAI accepts, else null. */
    private static String extensionOf(Message.Attachment attachment) {
        String extension = attachment.getFileExtension() == null ? "" : attachment.getFileExtension().toLowerCase(Locale.ROOT);
        return AUDIO_TYPES.containsKey(extension) ? extension : null;
    }

    /**
     * Transcribes the attachment and replies with the transcript, then with a translation if one was asked for.
     *
     * @param sourceUrl the message the audio came from, linked in the header, or null for an uploaded file
     */
    private static void handle(InteractionHook hook, long userId, long guildId, Message.Attachment attachment,
                               SpeechLanguage language, SpeechLanguage translateTo, String sourceUrl) {

        try {
            byte[] audio;
            try (InputStream in = attachment.getProxy().download().join()) {
                audio = in.readAllBytes();
            }

            String extension = extensionOf(attachment);
            String uploadExtension = AUDIO_TYPES.get(extension).equals("audio/ogg") ? "ogg" : extension;
            Transcript transcript = TranscriptionService.transcribe(audio, "audio." + uploadExtension,
                    AUDIO_TYPES.get(extension), language);

            TranscriptionRepository.save(userId, guildId, TranscriptionService.MODEL.asString(),
                    language == null ? null : language.code(), transcript.seconds(),
                    transcript.inputTokens(), transcript.outputTokens());
            log.info("/transcribe transcribed {} ({} chars, {} in / {} out tokens, {}s billed)", attachment.getFileName(),
                    transcript.text().length(), transcript.inputTokens(), transcript.outputTokens(), transcript.seconds());

            if (transcript.text().isEmpty()) {
                hook.sendMessage("I couldn't hear any speech in that audio.").queue();
                return;
            }

            String header = header("🎙️ Transcript", attachment.getDuration(), language, sourceUrl);
            // complete() so the translation below always comes after the transcript
            hook.sendMessageComponents(transcriptComponents(header, transcript.text()))
                    .useComponentsV2()
                    .setAllowedMentions(List.of())
                    .complete();

            if (translateTo != null) {
                sendTranslation(hook, transcript.text(), translateTo);
            }

        } catch (Exception e) {
            log.error("/transcribe failed for {}", attachment.getFileName(), e);
            hook.sendMessage("Something went wrong while transcribing that audio. Try again in a bit.").queue();
        }
    }

    private static String header(String title, double durationSeconds, SpeechLanguage language, String sourceUrl) {

        StringBuilder details = new StringBuilder();
        // Discord only knows the length of voice messages
        if (durationSeconds > 0) {
            long total = Math.round(durationSeconds);
            details.append(String.format("%d:%02d", total / 60, total % 60)).append(" · ");
        }
        details.append(language == null ? "Language auto-detected" : "Spoken in " + language.label());
        if (sourceUrl != null) {
            details.append(" · [Original message](").append(sourceUrl).append(')');
        }
        return "### " + title + "\n-# " + details;
    }

    /**
     * A transcript that fits is shown inline with the translate menu; a longer one is attached as a text file.
     * Only the inline one can be read back, so only it gets the menu.
     */
    private static List<MessageTopLevelComponent> transcriptComponents(String header, String text) {

        if (text.length() <= MAX_INLINE_LENGTH) {
            Container container = Container.of(
                    TextDisplay.of(header),
                    Separator.createDivider(Separator.Spacing.SMALL),
                    TextDisplay.of(text).withUniqueId(TRANSCRIPT_UNIQUE_ID)
            ).withAccentColor(ACCENT_COLOR);
            return List.of(container, ActionRow.of(translateMenu()));
        }

        Container container = Container.of(
                TextDisplay.of(header + "\nThe transcript is too long to show here, so it's attached as a file. "
                        + "Use `/transcribe` with `translate_to` to translate it."),
                FileDisplay.fromFile(FileUpload.fromData(text.getBytes(StandardCharsets.UTF_8), "transcript.txt"))
        ).withAccentColor(ACCENT_COLOR);
        return List.of(container);
    }

    private static StringSelectMenu translateMenu() {
        StringSelectMenu.Builder menu = StringSelectMenu.create(TRANSLATE_SELECT_ID).setPlaceholder("Translate to…");
        for (SpeechLanguage language : SpeechLanguage.values()) {
            menu.addOption(language.label(), language.code());
        }
        return menu.build();
    }

    /** Translates the text and sends it as a new message, inline or as a file if it's long. */
    private static void sendTranslation(InteractionHook hook, String text, SpeechLanguage target) {

        try {
            String translated = TranscriptionService.translate(text, target);
            if (translated.isEmpty()) {
                hook.sendMessage("The translation came back empty. Try again in a bit.").queue();
                return;
            }

            String header = "### 🌐 Translation · " + target.label();
            Container container = translated.length() <= MAX_INLINE_LENGTH
                    ? Container.of(TextDisplay.of(header), Separator.createDivider(Separator.Spacing.SMALL), TextDisplay.of(translated))
                    : Container.of(TextDisplay.of(header + "\nThe translation is too long to show here, so it's attached as a file."),
                    FileDisplay.fromFile(FileUpload.fromData(translated.getBytes(StandardCharsets.UTF_8), "translation.txt")));

            hook.sendMessageComponents(container.withAccentColor(ACCENT_COLOR))
                    .useComponentsV2()
                    .setAllowedMentions(List.of())
                    .queue();

        } catch (Exception e) {
            log.error("/transcribe translation to {} failed", target, e);
            hook.sendMessage("Something went wrong while translating. Try again in a bit.").queue();
        }
    }

    /** The "Translate to…" menu under an inline transcript. */
    public static void handleTranslateSelect(StringSelectInteractionEvent event) {

        // The transcript message is public, so anyone in the channel can open the menu
        if (event.getUser().getIdLong() != Constant.KIAN_ID) {
            event.reply("Only the bot owner can translate transcripts.").setEphemeral(true).queue();
            return;
        }

        SpeechLanguage target = SpeechLanguage.fromCode(event.getValues().getFirst());
        MessageComponentTree tree = event.getMessage().getComponentTree();
        Optional<String> transcript = tree.find(TextDisplay.class, display -> display.getUniqueId() == TRANSCRIPT_UNIQUE_ID)
                .map(TextDisplay::getContent);

        if (target == null || transcript.isEmpty()) {
            event.reply("I couldn't find the transcript to translate.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        EXECUTOR.submit(() -> sendTranslation(event.getHook(), transcript.get(), target));
    }

    @Override
    public @NonNull CommandData getCommandData() {

        OptionData language = new OptionData(OptionType.STRING, "language", "The spoken language (detected automatically if left out)", false);
        OptionData translateTo = new OptionData(OptionType.STRING, "translate_to", "Also translate the transcript into this language", false);
        for (SpeechLanguage speechLanguage : SpeechLanguage.values()) {
            language.addChoice(speechLanguage.label(), speechLanguage.code());
            translateTo.addChoice(speechLanguage.label(), speechLanguage.code());
        }

        return Commands.slash("transcribe", "Turn speech in an audio or video file into text")
                .addOptions(new OptionData(OptionType.ATTACHMENT, "file", "The audio or video file (up to 25 MB)", true))
                .addOptions(language, translateTo)
                .setContexts(InteractionContextType.GUILD, InteractionContextType.PRIVATE_CHANNEL)
                .setIntegrationTypes(IntegrationType.USER_INSTALL, IntegrationType.GUILD_INSTALL);
    }

    /** Right-click a message → Apps → "Transcribe": transcribes its first voice message or audio attachment. */
    public static class MessageMenu implements IMessageContext {

        @Override
        public void execute(@NonNull MessageContextInteraction event) {

            if (event.getUser().getIdLong() != Constant.KIAN_ID) {
                event.reply("This command is private.").setEphemeral(true).queue();
                return;
            }

            Message target = event.getTarget();
            Optional<Message.Attachment> audio = target.getAttachments().stream()
                    .filter(attachment -> extensionOf(attachment) != null)
                    .findFirst();
            if (audio.isEmpty()) {
                event.reply("That message has no voice message or audio file.").setEphemeral(true).queue();
                return;
            }

            String problem = audioProblem(audio.get());
            if (problem != null) {
                event.reply(problem).setEphemeral(true).queue();
                return;
            }

            long guildId = event.getGuild() == null ? 0 : event.getGuild().getIdLong();
            event.deferReply().queue();
            EXECUTOR.submit(() -> handle(event.getHook(), event.getUser().getIdLong(), guildId, audio.get(),
                    null, null, target.getJumpUrl()));
        }

        @Override
        public @NonNull CommandData getCommandData() {
            return Commands.message("Transcribe")
                    .setContexts(InteractionContextType.GUILD, InteractionContextType.PRIVATE_CHANNEL)
                    .setIntegrationTypes(IntegrationType.USER_INSTALL, IntegrationType.GUILD_INSTALL);
        }
    }
}
