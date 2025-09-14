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
