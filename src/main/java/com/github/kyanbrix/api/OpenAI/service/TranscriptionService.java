package com.github.kyanbrix.api.OpenAI.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.MultipartField;
import com.openai.core.Timeout;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.audio.AudioModel;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;

/** Speech to text with OpenAI, plus translation. Used by {@code /transcribe}, and by {@code /speak} to translate before speaking. */
public final class TranscriptionService {

    // About $0.003 per minute of audio, accurate enough for voice messages
    public static final AudioModel MODEL = AudioModel.GPT_4O_MINI_TRANSCRIBE;
    private static final ChatModel TRANSLATION_MODEL = ChatModel.GPT_5_4_MINI;
    private static final long MAX_TRANSLATION_TOKENS = 16_000;

    // No automatic retries: a retried transcription can be billed twice. Long recordings take a while.
    private static final OpenAIClient CLIENT = OpenAIOkHttpClient.builder()
            .apiKey(System.getenv("OPENAI_KEY"))
            .maxRetries(0)
            .timeout(Timeout.builder()
                    .read(Duration.ofMinutes(5))
                    .request(Duration.ofMinutes(6))
                    .build())
            .build();

    /** {@code seconds} is the audio length OpenAI billed, or 0 when it billed by tokens instead. */
    public record Transcript(String text, long inputTokens, long outputTokens, double seconds) {}

    private TranscriptionService() {}

    /**
     * @param fileName    needs the right extension (ogg, mp3, m4a, wav, ...): OpenAI tells the format from it
     * @param language    the spoken language, or null to let the model detect it
     */
    public static Transcript transcribe(byte[] audio, String fileName, String contentType, SpeechLanguage language) {

        TranscriptionCreateParams.Builder params = TranscriptionCreateParams.builder()
                .model(MODEL)
                .file(MultipartField.<InputStream>builder()
                        .value(new ByteArrayInputStream(audio))
                        .filename(fileName)
                        .contentType(contentType)
                        .build());
        if (language != null) {
            params.language(language.code());
        }

        Transcription transcription = CLIENT.audio().transcriptions().create(params.build()).asTranscription();

        long inputTokens = 0;
        long outputTokens = 0;
        double seconds = 0;
        if (transcription.usage().isPresent()) {
            Transcription.Usage usage = transcription.usage().get();
            if (usage.tokens().isPresent()) {
                inputTokens = usage.tokens().get().inputTokens();
                outputTokens = usage.tokens().get().outputTokens();
            }
            if (usage.duration().isPresent()) {
                seconds = usage.duration().get().seconds();
            }
        }

        return new Transcript(transcription.text().strip(), inputTokens, outputTokens, seconds);
    }

    /** Translates text into {@code target}, keeping its tone. */
    public static String translate(String text, SpeechLanguage target) {

        Response response = CLIENT.responses().create(ResponseCreateParams.builder()
                .model(TRANSLATION_MODEL)
                .instructions("Translate the user's text into " + target.label() + ". It's either a transcript "
                        + "of someone speaking or something to be read aloud, so keep the tone, slang and meaning "
                        + "rather than translating word for word. If part of it is already in " + target.label()
                        + ", keep that part as it is. Reply with only the translation: no notes, quotes or explanations.")
                .input(text)
                .reasoning(Reasoning.builder().effort(ReasoningEffort.LOW).build())
                .maxOutputTokens(MAX_TRANSLATION_TOKENS)
                .build());

        StringBuilder translated = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            if (!item.isMessage()) continue;

            for (ResponseOutputMessage.Content content : item.asMessage().content()) {
                if (content.isOutputText()) {
                    translated.append(content.asOutputText().text());
                }
            }
        }
        return translated.toString().strip();
    }
}
