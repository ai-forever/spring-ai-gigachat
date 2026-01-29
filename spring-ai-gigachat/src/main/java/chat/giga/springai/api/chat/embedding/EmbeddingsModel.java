package chat.giga.springai.api.chat.embedding;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import org.springframework.ai.model.EmbeddingModelDescription;

/**
 * <a href="https://developers.sber.ru/docs/ru/gigachat/models/main">Список доступных моделей</a>
 */
@AllArgsConstructor
public enum EmbeddingsModel implements EmbeddingModelDescription {
    EMBEDDINGS("Embeddings", 1024),
    EMBEDDINGS_2("Embeddings-2", 1024),
    EMBEDDINGS_GIGA_R("EmbeddingsGigaR", 2560);

    private final String value;
    private final int dimensions;

    @JsonValue
    public String getName() {
        return this.value;
    }

    @Override
    public int getDimensions() {
        return this.dimensions;
    }
}
