package chat.giga.springai;

import lombok.Builder;
import lombok.Data;
import org.springframework.ai.embedding.EmbeddingOptions;

@Data
@Builder(setterPrefix = "with")
public class GigaChatEmbeddingOptions implements EmbeddingOptions {

    private String model;
    private Integer dimensions;
}
