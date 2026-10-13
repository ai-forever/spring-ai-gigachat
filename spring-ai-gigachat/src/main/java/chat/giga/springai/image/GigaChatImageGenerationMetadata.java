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

package chat.giga.springai.image;

import org.springframework.ai.image.ImageGenerationMetadata;

/**
 * Metadata object representing the result of an image generation request
 * performed by GigaChat.
 *
 * <p>This class implements {@link ImageGenerationMetadata} and stores
 * a single identifier of the generated file returned by the API.
 * The {@code fileId} can be used later to download or reference
 * the generated image within the system.</p>
 *
 * <p>Example usage:
 * <pre>{@code
 * GigaChatImageGenerationMetadata meta = new GigaChatImageGenerationMetadata("12345");
 * String fileId = meta.getFileId(); // returns "12345"
 * }</pre>
 * </p>
 */
public class GigaChatImageGenerationMetadata implements ImageGenerationMetadata {

    private final String fileId;

    /**
     * Creates a new metadata instance containing the identifier
     * of a generated image file.
     *
     * @param fileId unique identifier returned by GigaChat image generation API
     */
    public GigaChatImageGenerationMetadata(String fileId) {
        this.fileId = fileId;
    }

    /**
     * Returns the unique file identifier of the generated image.
     *
     * @return the file ID string
     */
    public String getFileId() {
        return fileId;
    }

    /**
     * Returns a string representation in the format {@code file_id=<value>},
     * commonly used by upstream systems for logging or embedding into markup.
     *
     * @return formatted metadata string
     */
    @Override
    public String toString() {
        return "file_id=" + fileId;
    }
}
