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

package chat.giga.springai.tool;

import chat.giga.springai.tool.annotation.GigaTool;
import chat.giga.springai.tool.method.GigaMethodToolCallbackProvider;
import lombok.experimental.UtilityClass;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Utility class for construction ToolCallbacks from Objects
 * with {@link Tool} and {@link GigaTool} annotated methods.
 *
 * @author Linar Abzaltdinov
 */
@UtilityClass
public final class GigaTools {

    public static ToolCallback[] from(Object... sources) {
        return GigaMethodToolCallbackProvider.builder()
                .toolObjects(sources)
                .build()
                .getToolCallbacks();
    }
}
