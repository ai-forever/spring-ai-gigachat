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

package chat.giga.springai.tool.definition;

import chat.giga.springai.tool.support.GigaToolUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Linar Abzaltdinov
 */
@Getter
@EqualsAndHashCode
@ToString
public class FewShotExample {
    // User request
    private final String request;
    // Function input example corresponding to user request.
    private final String params;

    private FewShotExample(String request, String params) {
        this.request = request;
        this.params = params;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String request;
        private String params;

        public Builder request(String request) {
            this.request = request;
            return this;
        }

        public Builder params(Object params) {
            this.params = GigaToolUtils.toJson(params);
            return this;
        }

        public Builder paramsSchema(String paramsSchema) {
            this.params = paramsSchema;
            return this;
        }

        public FewShotExample build() {
            return new FewShotExample(request, params);
        }
    }
}
