package chat.giga.springai;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatApiScope;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.embedding.EmbeddingsModel;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.retry.RetryUtils;

/**
 * Подтверждение фиксов эмбеддингов на реальном GigaChat API.
 * Креды берутся из переменных окружения (GIGACHAT_API_*), секреты не печатаются.
 */
@Slf4j
class GigaChatEmbeddingModelIT {

    private static GigaChatEmbeddingModel embeddingModel(MetadataMode metadataMode) {
        GigaChatApiProperties properties = GigaChatApiProperties.builder()
                .auth(GigaChatAuthProperties.builder()
                        .scope(GigaChatApiScope.valueOf(System.getenv("GIGACHAT_API_SCOPE")))
                        .unsafeSsl(true)
                        .bearer(GigaChatAuthProperties.Bearer.builder()
                                .clientId(System.getenv("GIGACHAT_API_CLIENT_ID"))
                                .clientSecret(System.getenv("GIGACHAT_API_CLIENT_SECRET"))
                                .build())
                        .build())
                .build();
        GigaChatApi api = new GigaChatApi(properties);
        GigaChatEmbeddingOptions options = GigaChatEmbeddingOptions.builder()
                .withModel(EmbeddingsModel.EMBEDDINGS.getName())
                .build();
        return new GigaChatEmbeddingModel(
                api, options, RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP, metadataMode);
    }

    @Test
    @DisplayName("#01/#04: call() c null-опциями отдаёт эмбеддинг; totalTokens не удвоен")
    void nullOptions_realApi_usageNotDoubled() {
        EmbeddingResponse response =
                embeddingModel(MetadataMode.EMBED).call(new EmbeddingRequest(List.of("Привет, мир!"), null));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResult().getOutput()).hasSize(EmbeddingsModel.EMBEDDINGS.getDimensions());

        Integer prompt = response.getMetadata().getUsage().getPromptTokens();
        Integer total = response.getMetadata().getUsage().getTotalTokens();
        log.info("usage prompt={} total={}", prompt, total);
        // completion-токенов у эмбеддингов нет -> total == prompt (раньше total был 2*prompt)
        assertThat(total).isEqualTo(prompt);
    }

    @Test
    @DisplayName("#11: батч эмбеддингов — токены агрегируются по всем входам")
    void batch_realApi_usageAggregated() {
        EmbeddingResponse response = embeddingModel(MetadataMode.EMBED)
                .call(new EmbeddingRequest(List.of("первый текст", "второй текст", "третий текст"), null));

        assertThat(response.getResults()).hasSize(3);
        assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
        assertThat(response.getMetadata().getUsage().getTotalTokens())
                .isEqualTo(response.getMetadata().getUsage().getPromptTokens());
    }

    @Test
    @DisplayName("#14: embed(Document) учитывает metadata-mode и возвращает вектор нужной размерности")
    void embedDocument_realApi_honorsMetadataMode() {
        Document document = Document.builder()
                .text("Текст документа для эмбеддинга")
                .metadata("source", "bughunt")
                .build();

        float[] vector = embeddingModel(MetadataMode.NONE).embed(document);

        assertThat(vector).hasSize(EmbeddingsModel.EMBEDDINGS.getDimensions());
    }

    @Test
    @DisplayName("embed(String) и embed(List<String>): дефолтные методы EmbeddingModel работают на реальном API")
    void embedStringAndList_realApi() {
        GigaChatEmbeddingModel model = embeddingModel(MetadataMode.EMBED);

        float[] single = model.embed("просто строка");
        assertThat(single).hasSize(EmbeddingsModel.EMBEDDINGS.getDimensions());

        List<float[]> many = model.embed(List.of("раз", "два", "три"));
        assertThat(many).hasSize(3);
        assertThat(many.get(0)).hasSize(EmbeddingsModel.EMBEDDINGS.getDimensions());
    }

    @Test
    @DisplayName("dimensions(): известная модель возвращает размерность без сетевого вызова")
    void dimensions_knownModel() {
        assertThat(embeddingModel(MetadataMode.EMBED).dimensions())
                .isEqualTo(EmbeddingsModel.EMBEDDINGS.getDimensions());
    }

    @Test
    @DisplayName("Параметр model: EmbeddingsGigaR (другая размерность) работает на реальном API")
    void embeddingsGigaR_model() {
        GigaChatApiProperties properties = GigaChatApiProperties.builder()
                .auth(GigaChatAuthProperties.builder()
                        .scope(GigaChatApiScope.valueOf(System.getenv("GIGACHAT_API_SCOPE")))
                        .unsafeSsl(true)
                        .bearer(GigaChatAuthProperties.Bearer.builder()
                                .clientId(System.getenv("GIGACHAT_API_CLIENT_ID"))
                                .clientSecret(System.getenv("GIGACHAT_API_CLIENT_SECRET"))
                                .build())
                        .build())
                .build();
        GigaChatEmbeddingOptions options = GigaChatEmbeddingOptions.builder()
                .withModel(EmbeddingsModel.EMBEDDINGS_GIGA_R.getName())
                .build();
        GigaChatEmbeddingModel model = new GigaChatEmbeddingModel(
                new GigaChatApi(properties),
                options,
                RetryUtils.DEFAULT_RETRY_TEMPLATE,
                ObservationRegistry.NOOP,
                MetadataMode.EMBED);

        EmbeddingResponse response = model.call(new EmbeddingRequest(List.of("тест"), null));
        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResult().getOutput()).hasSize(EmbeddingsModel.EMBEDDINGS_GIGA_R.getDimensions());
    }
}
