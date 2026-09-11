package com.github.kyanbrix.api.OpenAI.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImageModel;
import com.openai.models.images.ImagesResponse;

import java.time.Clock;
import java.time.ZoneId;

public class ImageGenerationService {

    public static final OpenAIClient client = OpenAIOkHttpClient.builder()
            .apiKey(System.getenv("OPENAI_KEY"))
            .clock(Clock.system(ZoneId.of("Asia/Manila")))
            .build();

    public static String generate(String prompt) {
        try {

            ImagesResponse imagesResponse = client.images().generate(ImageGenerateParams.builder()
                            .model(ImageModel.GPT_IMAGE_1_5)
                            .prompt(prompt)
                            .quality(ImageGenerateParams.Quality.MEDIUM)
                            .size(ImageGenerateParams.Size._1024X1024)
                    .build()
            );


            String b64 = imagesResponse.data()
                    .filter(list -> !list.isEmpty()).flatMap(list -> list.getFirst().b64Json())
                    .orElse(null);

            if (b64 == null) {
                return "IMAGE_GENERATION_FAILED: No image data returned from API";

            }

            return "data:image/png;base64," + b64;


        } catch (Exception e) {
            System.err.println("[ImageGen] EXCEPTION: " + e.getClass().getName());
            System.err.println("[ImageGen] MESSAGE: " + e.getMessage());
            e.printStackTrace();
            return "IMAGE_GENERATION_FAILED: " + e.getMessage();
        }
    }
}
