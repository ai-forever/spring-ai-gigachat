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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import chat.giga.springai.GigaChatModel;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;

@ExtendWith(MockitoExtension.class)
public class GigaChatResponseUtilsTest {

    @Mock
    private ChatResponse chatResponse;

    @Test
    public void testGetInternalMessagesWithNullChatResponse() {
        var result = GigaChatResponseUtils.getInternalMessages(null);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testGetInternalMessagesWithNullMetadata() {
        when(chatResponse.getMetadata()).thenReturn(null);
        var result = GigaChatResponseUtils.getInternalMessages(chatResponse);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testGetInternalMessagesWithEmptyMetadata() {
        when(chatResponse.getMetadata())
                .thenReturn(ChatResponseMetadata.builder().build());
        var result = GigaChatResponseUtils.getInternalMessages(chatResponse);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testGetInternalMessages() {
        Message message = new UserMessage("Hello, world!");
        var metadata = ChatResponseMetadata.builder()
                .keyValue(GigaChatModel.INTERNAL_CONVERSATION_HISTORY, List.of(message))
                .build();
        when(chatResponse.getMetadata()).thenReturn(metadata);
        var result = GigaChatResponseUtils.getInternalMessages(chatResponse);
        assertEquals(Collections.singletonList(message), result);
    }

    @Test
    public void testGetUploadedMediaIdsWithNullChatResponse() {
        var result = GigaChatResponseUtils.getUploadedMediaIds(null);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testGetUploadedMediaIdsWithNullMetadata() {
        when(chatResponse.getMetadata()).thenReturn(null);
        var result = GigaChatResponseUtils.getUploadedMediaIds(chatResponse);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testGetUploadedMediaIdsWithEmptyMetadata() {
        when(chatResponse.getMetadata())
                .thenReturn(ChatResponseMetadata.builder().build());
        var result = GigaChatResponseUtils.getUploadedMediaIds(chatResponse);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testGetUploadedMediaIds() {
        String mediaId = UUID.randomUUID().toString();
        var metadata = ChatResponseMetadata.builder()
                .keyValue(GigaChatModel.UPLOADED_MEDIA_IDS, List.of(mediaId))
                .build();
        when(chatResponse.getMetadata()).thenReturn(metadata);
        var result = GigaChatResponseUtils.getUploadedMediaIds(chatResponse);
        assertEquals(Collections.singletonList(mediaId), result);
    }
}
