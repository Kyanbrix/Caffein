package com.github.kyanbrix.api.OpenAI.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import com.openai.core.http.HttpResponse;
import com.openai.models.audio.speech.SpeechCreateParams;
import com.openai.models.audio.speech.SpeechModel;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/** Text to speech with OpenAI. Used by {@code /speak}. */
public final class TextToSpeechService {

    // About $0.015 per minute of speech; the only model that takes style instructions and has every voice
    public static final SpeechModel MODEL = SpeechModel.GPT_4O_MINI_TTS;
    // OpenAI's input limit
    public static final int MAX_INPUT_LENGTH = 4096;
    // Every voice gpt-4o-mini-tts has; marin and cedar are OpenAI's best-sounding ones
    public static final List<String> VOICES = List.of(
            "marin", "cedar", "alloy", "ash", "ballad", "coral", "echo",
            "fable", "nova", "onyx", "sage", "shimmer", "verse");
    public static final String DEFAULT_VOICE = "marin";

    // No automatic retries: a retried request can be billed twice
    private static final OpenAIClient CLIENT = OpenAIOkHttpClient.builder()
            .apiKey(System.getenv("OPENAI_KEY"))
            .maxRetries(0)
            .timeout(Timeout.builder()
                    .read(Duration.ofMinutes(3))
                    .request(Duration.ofMinutes(3))
                    .build())
            .build();

    private TextToSpeechService() {}

    /**
     * @param voice        one of {@link #VOICES}
     * @param instructions how to speak (tone, accent, pace), or null for the voice's default delivery
     * @return the speech as Ogg Opus, the format Discord voice messages use
     */
    public static byte[] speak(String text, String voice, String instructions) throws IOException {

        SpeechCreateParams.Builder params = SpeechCreateParams.builder()
                .model(MODEL)
                .input(text)
                .voice(voice)
                .responseFormat(SpeechCreateParams.ResponseFormat.OPUS);
        if (instructions != null && !instructions.isBlank()) {
            params.instructions(instructions);
        }

        try (HttpResponse response = CLIENT.audio().speech().create(params.build());
             InputStream body = response.body()) {
            return body.readAllBytes();
        }
    }
}
