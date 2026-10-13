/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.giga.springai.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import chat.giga.springai.image.GigaChatImageGenerationMetadata;
import chat.giga.springai.image.GigaChatImageOptions;
import java.util.Base64;
import java.util.Map;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/image", produces = APPLICATION_JSON_VALUE)
public class ImageController {

    private final ImageModel imageModel;

    public ImageController(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    @PostMapping(value = "/base64", consumes = APPLICATION_JSON_VALUE)
    public Map<String, Object> generate(@RequestBody String request) {
        ImagePrompt prompt = new ImagePrompt(request);
        ImageResponse response = imageModel.call(prompt);
        ImageGeneration result = response.getResult();
        String fileId = ((GigaChatImageGenerationMetadata) response.getResult().getMetadata()).getFileId();
        String base64 = result.getOutput().getB64Json();

        return Map.of("prompt", request, "fileId", fileId, "base64", base64);
    }

    @PostMapping(value = "/raw", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] generateRaw(@RequestBody String request) {
        ImagePrompt prompt = new ImagePrompt(
                request,
                GigaChatImageOptions.builder()
                        .model("GigaChat-2-Max")
                        .style("Ты — Василий Кандинский")
                        .build());
        ImageResponse response = imageModel.call(prompt);
        String b64 = response.getResult().getOutput().getB64Json();

        return Base64.getDecoder().decode(b64);
    }

    @PostMapping(value = "/url", produces = MediaType.TEXT_PLAIN_VALUE)
    public String generateUrl(@RequestBody String request) {
        ImagePrompt prompt = new ImagePrompt(
                request,
                GigaChatImageOptions.builder()
                        .responseFormat(GigaChatImageOptions.RESPONSE_FORMAT_URL)
                        .build());
        ImageResponse response = imageModel.call(prompt);

        return response.getResult().getOutput().getUrl();
    }
}
