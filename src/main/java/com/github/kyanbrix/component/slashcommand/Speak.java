package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.api.OpenAI.service.SpeechLanguage;
import com.github.kyanbrix.api.OpenAI.service.TextToSpeechService;
import com.github.kyanbrix.api.OpenAI.service.TranscriptionService;
import com.github.kyanbrix.config.database.SpeechGenerationRepository;
import com.github.kyanbrix.utils.Constant;
import com.github.kyanbrix.utils.OggOpus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
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
import okhttp3.MediaType;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@code /speak text [voice] [language] [style]}: reads text aloud with OpenAI's gpt-4o-mini-tts and sends it as a
 * Discord voice message. With {@code language} the text is translated first, then spoken in that language.
 * Owner-only, like {@code /imagine}.
 * <p>
 * A voice message can't carry text, so the deferred reply becomes the caption and the voice message follows it:
 * posted straight to the channel when the bot is in the server and allowed to (Discord documents voice messages
 * for normal sends), otherwise as an interaction follow-up. If Discord refuses it, the audio goes out as a plain
 * file, which still plays inline.
 */
public class Speak implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(Speak.class);

    private static final String VOICE_FILE_NAME = "voice-message.ogg";
    private static final String FALLBACK_FILE_NAME = "speech.ogg";
    private static final MediaType OGG = MediaType.get("audio/ogg");
    private static final int MAX_STYLE_LENGTH = 300;
    // The spoken text is quoted in the caption when it fits in Discord's 2000-character message limit
    private static final int MAX_QUOTED_LENGTH = 1500;

    // Two clips at once; more wait their turn.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "speak");
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

        String text = event.getOption("text", OptionMapping::getAsString);
        if (text == null || text.isBlank()) {
            event.reply("Please type something to say.").setEphemeral(true).queue();
            return;
        }

        String voice = event.getOption("voice", TextToSpeechService.DEFAULT_VOICE, OptionMapping::getAsString);
        SpeechLanguage language = SpeechLanguage.fromCode(event.getOption("language", OptionMapping::getAsString));
        String style = event.getOption("style", OptionMapping::getAsString);
        long guildId = event.getGuild() == null ? 0 : event.getGuild().getIdLong();
        MessageChannel channel = canPostVoiceMessage(event) ? event.getMessageChannel() : null;

        event.deferReply().queue();
        EXECUTOR.submit(() -> handle(event.getHook(), channel, event.getUser().getIdLong(), guildId,
                text.strip(), voice, language, style));
    }

    /**
     * Whether the bot can post the voice message in the channel itself. In DMs and servers the bot isn't in
     * (the user-installed app), it can only answer through the interaction.
     */
    private static boolean canPostVoiceMessage(SlashCommandInteraction event) {
        return event.isFromAttachedGuild() && event.getGuild() != null
                && event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(),
                Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_ATTACH_FILES,
                Permission.MESSAGE_ATTACH_VOICE_MESSAGE);
    }

    /** @param channel where to post the voice message, or null to send it as an interaction follow-up */
    private void handle(InteractionHook hook, MessageChannel channel, long userId, long guildId, String text,
                        String voice, SpeechLanguage language, String style) {

        try {
            String spoken = language == null ? text : TranscriptionService.translate(text, language);
            if (spoken.isEmpty() || spoken.length() > TextToSpeechService.MAX_INPUT_LENGTH) {
                hook.editOriginal("The translation came out " + (spoken.isEmpty() ? "empty" : "too long to read aloud")
                        + ". Try a shorter text.").queue();
                return;
            }

            byte[] ogg = TextToSpeechService.speak(spoken, voice, instructions(language, style));

            SpeechGenerationRepository.save(userId, guildId, TextToSpeechService.MODEL.asString(), voice,
                    language == null ? null : language.code(), spoken.length(), ogg.length);
            log.info("/speak made {} bytes of speech from {} characters with {}", ogg.length, spoken.length(), voice);

            // complete() so the caption is in place before the voice message appears under it
            hook.editOriginal(caption(voice, language, style, spoken)).setAllowedMentions(List.of()).complete();
            sendVoiceMessage(hook, channel, ogg);

        } catch (Exception e) {
            log.error("/speak failed", e);
            hook.editOriginal("Something went wrong while making the audio. Try again in a bit.").queue();
        }
    }

    private static void sendVoiceMessage(InteractionHook hook, MessageChannel channel, byte[] ogg) {

        FileUpload voiceMessage;
        try {
            OggOpus.VoiceClip clip = OggOpus.analyze(ogg);
            voiceMessage = FileUpload.fromData(ogg, VOICE_FILE_NAME)
                    .asVoiceMessage(OGG, clip.waveform(), clip.durationSeconds());
        } catch (IllegalArgumentException e) {
            log.warn("/speak audio couldn't be read as Ogg Opus ({}), sending it as a file", e.getMessage());
            hook.sendFiles(FileUpload.fromData(ogg, FALLBACK_FILE_NAME)).queue();
            return;
        }

        try {
            if (channel != null) {
                channel.sendFiles(voiceMessage).complete();
            } else {
                hook.sendFiles(voiceMessage).complete();
            }
        } catch (ErrorResponseException e) {
            log.warn("Discord refused the /speak voice message ({}), sending it as a file", e.getMeaning());
            hook.sendFiles(FileUpload.fromData(ogg, FALLBACK_FILE_NAME)).queue();
        }
    }

    /** How the voice should speak: the style asked for, plus a native accent when the text was translated. */
    private static String instructions(SpeechLanguage language, String style) {
        StringBuilder instructions = new StringBuilder();
        if (language != null) {
            instructions.append("Speak in ").append(language.label()).append(" with a natural, native accent. ");
        }
        if (style != null && !style.isBlank()) {
            instructions.append(style.strip());
        }
        return instructions.toString().strip();
    }

    private static String caption(String voice, SpeechLanguage language, String style, String spoken) {

        StringBuilder caption = new StringBuilder("🔊 **")
                .append(voice.substring(0, 1).toUpperCase(Locale.ROOT)).append(voice.substring(1)).append("**");
        if (language != null) {
            caption.append(" · ").append(language.label());
        }
        if (style != null && !style.isBlank()) {
            caption.append(" · *").append(MarkdownSanitizer.escape(style.strip())).append('*');
        }
        // Quoted so a translation can be read along with the audio
        if (spoken.length() <= MAX_QUOTED_LENGTH) {
            caption.append("\n>>> ").append(spoken);
        }
        return caption.toString();
    }

    @Override
    public @NonNull CommandData getCommandData() {

        OptionData voice = new OptionData(OptionType.STRING, "voice", "Which voice reads it (Marin if left out)", false);
        for (String name : TextToSpeechService.VOICES) {
            voice.addChoice(name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1), name);
        }

        OptionData language = new OptionData(OptionType.STRING, "language", "Translate the text into this language first, then speak it", false);
        for (SpeechLanguage speechLanguage : SpeechLanguage.values()) {
            language.addChoice(speechLanguage.label(), speechLanguage.code());
        }

        return Commands.slash("speak", "Read text aloud with an AI voice")
                .addOptions(new OptionData(OptionType.STRING, "text", "What to say", true)
                        .setMaxLength(TextToSpeechService.MAX_INPUT_LENGTH))
                .addOptions(voice, language)
                .addOptions(new OptionData(OptionType.STRING, "style", "How to say it, e.g. \"cheerful and fast\" or \"whisper\"", false)
                        .setMaxLength(MAX_STYLE_LENGTH))
                .setContexts(InteractionContextType.GUILD, InteractionContextType.PRIVATE_CHANNEL)
                .setIntegrationTypes(IntegrationType.USER_INSTALL, IntegrationType.GUILD_INSTALL);
    }
}
