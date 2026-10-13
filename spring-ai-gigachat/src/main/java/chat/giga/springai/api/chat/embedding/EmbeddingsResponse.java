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
            // У эмбеддингов нет генерации, completion-токенов не бывает.
            // Возврат promptTokens приводил к удвоению totalTokens (см. Usage#getTotalTokens).
            return 0;
        }

        @Override
        public Object getNativeUsage() {
            return null;
        }
    }
}
