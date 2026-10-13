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
