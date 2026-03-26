package chat.giga.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.embedding.EmbeddingsModel;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

        assertEquals(1024, embeddingModel.dimensions());
        Mockito.verify(gigaChatApi, Mockito.never()).embeddings(any());
    }
}
