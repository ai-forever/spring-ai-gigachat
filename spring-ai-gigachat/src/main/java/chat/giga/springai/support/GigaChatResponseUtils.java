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

package chat.giga.springai.support;

import chat.giga.springai.GigaChatModel;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Utils for providing type-safe access to ChatResponse metadata properties from GigaChat model response.
 *
 * @author Linar Abzaltdinov
 */
@UtilityClass
@Slf4j
public class GigaChatResponseUtils {
    public static List<Message> getInternalMessages(@Nullable ChatResponse chatResponse) {
        return getFromMetadata(chatResponse, GigaChatModel.INTERNAL_CONVERSATION_HISTORY, List.of());
    }

    public static List<String> getUploadedMediaIds(@Nullable ChatResponse chatResponse) {
        return getFromMetadata(chatResponse, GigaChatModel.UPLOADED_MEDIA_IDS, List.of());
    }

    private static <T> T getFromMetadata(@Nullable ChatResponse chatResponse, String key, T defaultValue) {
        if (chatResponse != null && chatResponse.getMetadata() != null) {
            T data = chatResponse.getMetadata().get(key);
            return data == null ? defaultValue : data;
        }
        return defaultValue;
    }
}
