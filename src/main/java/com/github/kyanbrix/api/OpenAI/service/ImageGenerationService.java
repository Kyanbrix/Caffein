package com.github.kyanbrix.api.OpenAI.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.MultipartField;
import com.openai.core.Timeout;
import com.openai.errors.BadRequestException;
import com.openai.models.images.ImageEditParams;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImageModel;
import com.openai.models.images.ImagesResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** Generates one image with OpenAI. Used by {@code /imagine}. */
public final class ImageGenerationService {

    public static final ImageModel MODEL = ImageModel.GPT_IMAGE_2;
    public static final ImageGenerateParams.Quality QUALITY = ImageGenerateParams.Quality.MEDIUM;

    // gpt-image-2 rates per token, for the cost estimate in the logs (a 1024x1024 medium image is about $0.053)
    private static final double IMAGE_OUTPUT_PRICE = 30.0 / 1_000_000;
    private static final double TEXT_INPUT_PRICE = 5.0 / 1_000_000;
    private static final double IMAGE_INPUT_PRICE = 8.0 / 1_000_000;

    // No automatic retries: a retried request can be billed twice. Complex prompts can take up to 2 minutes.
    private static final OpenAIClient CLIENT = OpenAIOkHttpClient.builder()
            .apiKey(System.getenv("OPENAI_KEY"))
            .maxRetries(0)
            .timeout(Timeout.builder()
                    .read(Duration.ofMinutes(3))
                    .request(Duration.ofMinutes(3))
                    .build())
            .build();

    /** An image the new one should be based on, as uploaded (PNG, JPEG or WebP). */
    public record ReferenceImage(byte[] data, String fileName, String contentType) {}

    // OpenAI accepts up to 50 MB; Discord uploads are usually smaller anyway
    private static final long MAX_REFERENCE_BYTES = 25L * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** {@code withReference} is set when reference images were sent, which are billed as image input. */
    public record GeneratedImage(byte[] png, long inputTokens, long outputTokens, boolean withReference) {
        public double estimatedCost() {
            // Usage doesn't split text from image input, so with a reference all input is priced as image input
            double inputPrice = withReference ? IMAGE_INPUT_PRICE : TEXT_INPUT_PRICE;
            return outputTokens * IMAGE_OUTPUT_PRICE + inputTokens * inputPrice;
        }
    }

    /** OpenAI's safety system refused the prompt. */
    public static final class BlockedPromptException extends Exception {
        BlockedPromptException(String message) {
            super(message);
        }
    }

    private ImageGenerationService() {}

    /**
     * Downloads an image (a Discord attachment URL) to use as a reference. Throws if it isn't a
     * PNG, JPEG or WebP image, or is over {@link #MAX_REFERENCE_BYTES}.
     */
    public static ReferenceImage downloadReference(String url) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("download failed with HTTP " + response.statusCode());
        }
        if (response.body().length > MAX_REFERENCE_BYTES) {
            throw new IOException("the image is over " + (MAX_REFERENCE_BYTES / 1024 / 1024) + " MB");
        }

        // Discord's content type, or else the extension in the URL path (before the ?ex=... query)
        String header = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        String contentType;
        if (header.startsWith("image/png") || path.endsWith(".png")) contentType = "image/png";
        else if (header.startsWith("image/jpeg") || path.endsWith(".jpg") || path.endsWith(".jpeg")) contentType = "image/jpeg";
        else if (header.startsWith("image/webp") || path.endsWith(".webp")) contentType = "image/webp";
        else throw new IOException("only PNG, JPEG or WebP images can be used");

        String extension = contentType.substring("image/".length()).replace("jpeg", "jpg");
        return new ReferenceImage(response.body(), "reference." + extension, contentType);
    }

    /**
     * @param references images to base the result on, in the order the prompt refers to them, or an
     *                   empty list for a prompt-only image. With references the request goes to the
     *                   edit endpoint, which takes input images.
     * @throws IOException if mixed image formats couldn't be converted to PNG
     */
    public static GeneratedImage generate(String prompt, List<ReferenceImage> references)
            throws BlockedPromptException, IOException {

        ImagesResponse response;
        try {
            if (references.isEmpty()) {
                response = CLIENT.images().generate(ImageGenerateParams.builder()
                        .model(MODEL)
                        .prompt(prompt)
                        .quality(QUALITY)
                        .size(ImageGenerateParams.Size._1024X1024)
                        .n(1)
                        // OpenAI's less restrictive filtering, for fewer false positives (prohibited content
                        // is still blocked)
                        .moderation(ImageGenerateParams.Moderation.LOW)
                        .build());
            } else {
                List<ReferenceImage> images = sameFormat(references);
                List<InputStream> streams = images.stream()
                        .map(reference -> (InputStream) new ByteArrayInputStream(reference.data()))
                        .toList();

                // A filename and content type are required: without them OpenAI can't tell the upload is an image.
                // Several images go up as repeated "image[]" parts, all labelled with the first one's name and type.
                MultipartField<ImageEditParams.Image> image = MultipartField.<ImageEditParams.Image>builder()
                        .value(streams.size() == 1
                                ? ImageEditParams.Image.ofInputStream(streams.getFirst())
                                : ImageEditParams.Image.ofInputStreams(streams))
                        .filename(images.getFirst().fileName())
                        .contentType(images.getFirst().contentType())
                        .build();

                response = CLIENT.images().edit(ImageEditParams.builder()
                        .model(MODEL)
                        .prompt(prompt)
                        .image(image)
                        .quality(ImageEditParams.Quality.of(QUALITY.asString()))
                        .size(ImageEditParams.Size._1024X1024)
                        .n(1)
                        // The edit endpoint accepts "moderation" too, but this SDK version has no setter for it,
                        // so it's sent as an extra form field (plain text "low")
                        .putAdditionalBodyProperty("moderation", JsonValue.from("low"))
                        .build());
            }
        } catch (BadRequestException e) {
            boolean blocked = e.code().map("moderation_blocked"::equals).orElse(false)
                    || String.valueOf(e.getMessage()).toLowerCase().contains("safety system");
            if (blocked) {
                throw new BlockedPromptException(e.getMessage());
            }
            throw e;
        }

        String b64 = response.data()
                .filter(list -> !list.isEmpty())
                .flatMap(list -> list.getFirst().b64Json())
                .orElseThrow(() -> new IllegalStateException("No image data returned from the API"));

        long inputTokens = response.usage().map(ImagesResponse.Usage::inputTokens).orElse(0L);
        long outputTokens = response.usage().map(ImagesResponse.Usage::outputTokens).orElse(0L);

        return new GeneratedImage(Base64.getDecoder().decode(b64), inputTokens, outputTokens, !references.isEmpty());
    }

    /**
     * The SDK labels every uploaded image with one content type, so a mix of formats is converted to PNG.
     * Java can't decode WebP, so WebP images can only be mixed with other WebP images.
     */
    private static List<ReferenceImage> sameFormat(List<ReferenceImage> references) throws IOException {

        String contentType = references.getFirst().contentType();
        if (references.stream().allMatch(reference -> reference.contentType().equals(contentType))) {
            return references;
        }

        List<ReferenceImage> pngs = new ArrayList<>(references.size());
        for (ReferenceImage reference : references) {
            if (reference.contentType().equals("image/png")) {
                pngs.add(reference);
                continue;
            }
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(reference.data()));
            if (decoded == null) {
                throw new IOException(reference.fileName() + " couldn't be converted to PNG to go with the other "
                        + "images (WebP images can only be combined with other WebP images)");
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(decoded, "png", png);
            pngs.add(new ReferenceImage(png.toByteArray(), "reference.png", "image/png"));
        }
        return pngs;
    }
}
