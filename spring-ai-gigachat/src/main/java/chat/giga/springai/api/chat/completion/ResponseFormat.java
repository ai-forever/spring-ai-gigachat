/*
 * Copyright 2026 the original author or authors.
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

package chat.giga.springai.api.chat.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/**
 * Native response format for {@code /v1/chat/completions}. The schema is a JSON object,
 * serialized directly into {@code response_format.schema}, without an additional wrapper.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseFormat(
        @JsonProperty("type") Type type,
        @JsonProperty("schema") @JsonRawValue @Nullable String schema,
        @JsonProperty("strict") @Nullable Boolean strict) {

    public ResponseFormat {
        Assert.notNull(type, "Response format type must not be null");
        if (type == Type.JSON_SCHEMA) {
            Assert.hasText(schema, "JSON schema must not be empty");
        } else {
            Assert.isTrue(schema == null && strict == null, "Text format must not contain schema or strict");
        }
    }

    public enum Type {
        TEXT("text"),
        JSON_SCHEMA("json_schema");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return this.value;
        }
    }
}
