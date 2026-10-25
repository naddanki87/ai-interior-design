package com.aiinterior.factory.image;

import java.util.List;

/** Concept image generation. Text prompts only - room photographs are never sent. */
public interface ImageProvider {

    record ImageRequest(String title, String prompt, String negativePrompt, List<String> palette, List<String> materials,
                        int width, int height) {
    }

    record ImageResult(String provider, String model, boolean mock, String contentType, String content) {
    }

    String name();

    ImageResult generate(ImageRequest request);
}
