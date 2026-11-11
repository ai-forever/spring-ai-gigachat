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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.embedding.EmbeddingsModel;
import chat.giga.springai.api.chat.embedding.EmbeddingsResponse;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;

class GigaChatEmbeddingModelTest {
    GigaChatApi gigaChatApi = mock(GigaChatApi.class);
    GigaChatEmbeddingOptions options = GigaChatEmbeddingOptions.builder()
            .withModel(EmbeddingsModel.EMBEDDINGS.getName())
            .build();
    RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

    GigaChatEmbeddingModel embeddingModel =
            new GigaChatEmbeddingModel(gigaChatApi, options, retryTemplate, ObservationRegistry.NOOP);

    @Test
    void dimensionsLazyLoading() {
        verify(gigaChatApi, never()).embeddings(any());

        assertEquals(1024, embeddingModel.dimensions());
        verify(gigaChatApi, never()).embeddings(any());
    }

    @Test
    void embed_shouldReturnOutput_whenApiReturnsValidEmbedding() {
        float[] embedding = new float[] {0.1f, 0.2f, 0.3f};
        EmbeddingsResponse.EmbeddingData data = EmbeddingsResponse.EmbeddingData.builder()
                .embedding(embedding)
                .index(0)
                .build();
        EmbeddingsResponse apiResponse = EmbeddingsResponse.builder()
                .model(EmbeddingsModel.EMBEDDINGS.getName())
                .data(List.of(data))
                .build();
        when(gigaChatApi.embeddings(ArgumentMatchers.any())).thenReturn(ResponseEntity.ok(apiResponse));

        float[] result = embeddingModel.embed(new Document("hello"));

        assertArrayEquals(embedding, result);
    }

    @Test
    void embed_shouldThrowIllegalStateException_whenResultIsNull() {
        GigaChatEmbeddingModel spy = spy(embeddingModel);
        EmbeddingResponse mockResponse = mock(EmbeddingResponse.class);
        when(mockResponse.getResult()).thenReturn(null);
        doReturn(mockResponse).when(spy).call(ArgumentMatchers.any());

        Document document = new Document("hello");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> spy.embed(document));

        assertEquals("GigaChat returned empty embedding result", ex.getMessage());
    }

    @Test
    void embed_shouldThrowIllegalStateException_whenOutputIsNull() {
        GigaChatEmbeddingModel spy = spy(embeddingModel);
        EmbeddingResponse mockResponse = mock(EmbeddingResponse.class);
        Embedding mockEmbedding = mock(Embedding.class);
        when(mockEmbedding.getOutput()).thenReturn(null);
        when(mockResponse.getResult()).thenReturn(mockEmbedding);
        doReturn(mockResponse).when(spy).call(ArgumentMatchers.any());

        Document document = new Document("hello");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> spy.embed(document));

        assertEquals("GigaChat returned null embedding output", ex.getMessage());
    }
}
