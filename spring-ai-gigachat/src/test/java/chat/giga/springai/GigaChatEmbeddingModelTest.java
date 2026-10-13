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

package chat.giga.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.embedding.EmbeddingsModel;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;

class GigaChatEmbeddingModelTest {
    GigaChatApi gigaChatApi = Mockito.mock(GigaChatApi.class);
    GigaChatEmbeddingOptions options = GigaChatEmbeddingOptions.builder()
            .withModel(EmbeddingsModel.EMBEDDINGS.getName())
            .build();
    RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

    GigaChatEmbeddingModel embeddingModel =
            new GigaChatEmbeddingModel(gigaChatApi, options, retryTemplate, ObservationRegistry.NOOP);

    @Test
    void dimensionsLazyLoading() {
        Mockito.verify(gigaChatApi, Mockito.never()).embeddings(any());

        assertEquals(1024, embeddingModel.dimensions());
        Mockito.verify(gigaChatApi, Mockito.never()).embeddings(any());
    }
}
