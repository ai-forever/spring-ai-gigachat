package chat.giga.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.embedding.EmbeddingsModel;
import chat.giga.springai.api.chat.embedding.EmbeddingsResponse;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;

class GigaChatEmbeddingModelTest {
    GigaChatApi gigaChatApi = Mockito.mock(GigaChatApi.class);
    GigaChatEmbeddingOptions options = GigaChatEmbeddingOptions.builder()
            .withModel(EmbeddingsModel.EMBEDDINGS.getName())
            .build();
    RetryTemplate retryTemplate = RetryTemplate.defaultInstance();

    GigaChatEmbeddingModel embeddingModel =
            new GigaChatEmbeddingModel(gigaChatApi, options, retryTemplate, ObservationRegistry.NOOP);

    @Test
    void dimensionsLazyLoading() {
        Mockito.verify(gigaChatApi, Mockito.never()).embeddings(any());

        Mockito.when(gigaChatApi.embeddings(any()))
                .thenReturn(ResponseEntity.ok(EmbeddingsResponse.builder()
                        .model("Embeddings")
                        .data(new ArrayList<>(List.of(EmbeddingsResponse.EmbeddingData.builder()
                                .index(1)
                                .embedding(new float[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
                                .build())))
                        .build()));

        assertEquals(10, embeddingModel.dimensions());
        Mockito.verify(gigaChatApi, Mockito.only()).embeddings(any());
    }
}
