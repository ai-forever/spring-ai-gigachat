/*
 * Copyright 2023-2025 the original author or authors.
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

package chat.giga.springai.tool.execution;

import chat.giga.springai.tool.support.GigaToolUtils;
import java.lang.reflect.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.lang.Nullable;

/**
 * An implementation of {@link ToolCallResultConverter} for GigaChat LLM.
 * Converts the result of a tool call to JSON only if result is not a valid JSON string.
 *
 * @author Linar Abzaltdinov
 */
@Slf4j
public final class GigaToolCallResultConverter implements ToolCallResultConverter {

    @Override
    public String convert(@Nullable Object result, @Nullable Type returnType) {
        if (returnType == Void.TYPE) {
            log.debug("The tool has no return type. Converting to conventional response.");
            return "\"Done\"";
        } else {
            log.debug("Converting tool result to JSON.");
            return GigaToolUtils.toJsonIfNeeded(result);
        }
    }
}
