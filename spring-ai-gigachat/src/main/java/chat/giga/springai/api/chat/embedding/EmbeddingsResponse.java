package chat.giga.springai.api.chat.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.metadata.Usage;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingsResponse {

    @Builder.Default
    private String object = "list";

    private List<EmbeddingData> data;
    private String model;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingData {
        @Builder.Default
        private String object = "embedding";

        private float[] embedding;
        private Integer index;
        private GigaChatEmbeddingsUsage usage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GigaChatEmbeddingsUsage implements Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @Override
        public Integer getCompletionTokens() {
            return promptTokens;
        }

        @Override
        public Object getNativeUsage() {
            return null;
        }
    }
}
