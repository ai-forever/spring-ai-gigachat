package chat.giga.springai;

import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.embedding.EmbeddingOptions;

@Data
@Builder(setterPrefix = "with")
public class GigaChatEmbeddingOptions implements EmbeddingOptions {

    // Пакет помечен @NullMarked, а контракт EmbeddingOptions объявляет эти поля @Nullable.
    private @Nullable String model;
    private @Nullable Integer dimensions;
}
