package com.github.kyanbrix.api.OpenAI.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.MultipartField;
import com.openai.core.Timeout;
import com.openai.core.http.HttpResponse;
import com.openai.errors.BadRequestException;
import com.openai.models.videos.Video;
import com.openai.models.videos.VideoCreateError;
import com.openai.models.videos.VideoCreateParams;
import com.openai.models.videos.VideoModel;
import com.openai.models.videos.VideoSeconds;
import com.openai.models.videos.VideoSize;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.function.LongConsumer;

/**
 * Makes short videos with OpenAI's Sora. Used by {@code /video}.
 * <p>
 * A video is a job: {@link #start} submits it, {@link #await} checks on it until it's done (usually a few
 * minutes), and {@link #download} fetches the MP4. Only {@code start} is billed, so it's never retried;
 * checking and downloading are free and retry on network errors.
 */
public final class VideoGenerationService {

    public static final VideoModel DEFAULT_MODEL = VideoModel.SORA_2;

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(10);
    // OpenAI keeps working after this, but the command stops waiting and says so
    private static final Duration MAX_WAIT = Duration.ofMinutes(20);

    // No automatic retries: a retried create could start (and bill) a second video
    private static final OpenAIClient CREATE_CLIENT = OpenAIOkHttpClient.builder()
            .apiKey(System.getenv("OPENAI_KEY"))
            .maxRetries(0)
            .timeout(Timeout.builder()
                    .read(Duration.ofMinutes(2))
                    .request(Duration.ofMinutes(2))
                    .build())
            .build();

    // Checking a job and downloading the result cost nothing, so these may retry
    private static final OpenAIClient POLL_CLIENT = OpenAIOkHttpClient.builder()
            .apiKey(System.getenv("OPENAI_KEY"))
            .maxRetries(3)
            .timeout(Timeout.builder()
                    .read(Duration.ofMinutes(2))
                    .request(Duration.ofMinutes(5))
                    .build())
            .build();

    /** The two 720p sizes {@code sora-2} supports. A reference image must match the size exactly. */
    public enum Orientation {
        LANDSCAPE("landscape", VideoSize._1280X720, 1280, 720),
        PORTRAIT("portrait", VideoSize._720X1280, 720, 1280);

        private final String label;
        private final VideoSize size;
        private final int width;
        private final int height;

        Orientation(String label, VideoSize size, int width, int height) {
            this.label = label;
            this.size = size;
            this.width = width;
            this.height = height;
        }

        public String label() {
            return label;
        }

        public VideoSize size() {
            return size;
        }

        public static Orientation fromLabel(String label) {
            for (Orientation orientation : values()) {
                if (orientation.label.equalsIgnoreCase(label)) return orientation;
            }
            return LANDSCAPE;
        }
    }

    /** OpenAI's safety system refused the prompt or reference image before the job started. */
    public static final class BlockedPromptException extends Exception {
        BlockedPromptException(String message) {
            super(message);
        }
    }

    /** The job started but ended in {@code failed}. */
    public static final class VideoFailedException extends Exception {
        private final String code;

        VideoFailedException(String code, String message) {
            super(code + ": " + message);
            this.code = code;
        }

        public String code() {
            return code;
        }

        /** Whether it failed because of moderation (the output, not just the prompt, can be blocked). */
        public boolean isModeration() {
            return code.toLowerCase(Locale.ROOT).contains("moderation");
        }
    }

    private VideoGenerationService() {}

    /** Dollars per second at 720p, for the cost estimate. */
    public static double pricePerSecond(VideoModel model) {
        return model.asString().startsWith("sora-2-pro") ? 0.30 : 0.10;
    }

    public static double estimatedCost(VideoModel model, int seconds) {
        return pricePerSecond(model) * seconds;
    }

    /**
     * Scales a PNG or JPEG to cover the video frame and crops the middle, because Sora rejects a reference
     * that isn't exactly the output size. Returns a PNG.
     *
     * @throws IOException if Java can't decode the image (WebP, for example)
     */
    public static byte[] fitReference(byte[] image, Orientation orientation) throws IOException {

        BufferedImage source = ImageIO.read(new ByteArrayInputStream(image));
        if (source == null) {
            throw new IOException("the reference has to be a PNG or JPEG image");
        }

        double scale = Math.max((double) orientation.width / source.getWidth(),
                (double) orientation.height / source.getHeight());
        int scaledWidth = (int) Math.ceil(source.getWidth() * scale);
        int scaledHeight = (int) Math.ceil(source.getHeight() * scale);

        BufferedImage frame = new BufferedImage(orientation.width, orientation.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = frame.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, (orientation.width - scaledWidth) / 2, (orientation.height - scaledHeight) / 2,
                    scaledWidth, scaledHeight, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(frame, "png", png);
        return png.toByteArray();
    }

    /**
     * Submits the job. This is the billed call.
     *
     * @param referencePng the first frame to animate, already sized with {@link #fitReference}, or null
     * @return the job's ID
     */
    public static String start(String prompt, VideoModel model, Orientation orientation, int seconds,
                               byte[] referencePng) throws BlockedPromptException {

        VideoCreateParams.Builder params = VideoCreateParams.builder()
                .prompt(prompt)
                .model(model)
                .size(orientation.size)
                .seconds(VideoSeconds.of(String.valueOf(seconds)));

        if (referencePng != null) {
            // Without a filename and content type OpenAI can't tell the upload is an image
            params.inputReference(MultipartField.<VideoCreateParams.InputReference>builder()
                    .value(VideoCreateParams.InputReference.ofStream(new ByteArrayInputStream(referencePng)))
                    .filename("reference.png")
                    .contentType("image/png")
                    .build());
        }

        try {
            return CREATE_CLIENT.videos().create(params.build()).id();
        } catch (BadRequestException e) {
            String message = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            boolean blocked = e.code().map(code -> code.contains("moderation")).orElse(false)
                    || message.contains("moderation") || message.contains("safety system");
            if (blocked) {
                throw new BlockedPromptException(e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Checks on the job every {@link #POLL_INTERVAL} until it completes.
     *
     * @param onProgress called with the job's progress (0 to 100) after each check that isn't the last
     * @throws VideoFailedException if the job failed
     * @throws TimeoutException     if it isn't done after {@link #MAX_WAIT}
     */
    public static void await(String videoId, LongConsumer onProgress)
            throws InterruptedException, VideoFailedException, TimeoutException {

        Instant deadline = Instant.now().plus(MAX_WAIT);
        while (true) {
            Video video = POLL_CLIENT.videos().retrieve(videoId);

            if (video.status().equals(Video.Status.COMPLETED)) {
                return;
            }
            if (video.status().equals(Video.Status.FAILED)) {
                throw new VideoFailedException(
                        video.error().map(VideoCreateError::code).orElse("unknown"),
                        video.error().map(VideoCreateError::message).orElse("no details"));
            }

            onProgress.accept(video.progress());

            if (Instant.now().isAfter(deadline)) {
                throw new TimeoutException("video " + videoId + " still isn't done after " + MAX_WAIT.toMinutes() + " minutes");
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
    }

    /** Downloads a completed video as MP4. OpenAI keeps it for about an hour. */
    public static byte[] download(String videoId) throws IOException {
        try (HttpResponse response = POLL_CLIENT.videos().downloadContent(videoId);
             InputStream body = response.body()) {
            return body.readAllBytes();
        }
    }
}
