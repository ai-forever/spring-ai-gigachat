package chat.giga.springai.api.chat.embedding;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum EmbeddingsModel {
    EMBEDDINGS("Embeddings"),
    EMBEDDINGS_GIGA_R("EmbeddingsGigaR");

    public final String value;

    @JsonValue
    public String getName() {
        return this.value;
    }
}
