package chat.giga.springai.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import chat.giga.springai.GigaChatModel;
import java.util.Collections;
import java.util.List;
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
    public void testGetConversationHistoryWithNullChatResponse() {
        List<Message> result = GigaChatResponseUtils.getConversationHistory(null);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testGetConversationHistoryWithNullMetadata() {
        when(chatResponse.getMetadata()).thenReturn(null);
        List<Message> result = GigaChatResponseUtils.getConversationHistory(chatResponse);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testGetConversationHistoryWithEmptyMetadata() {
        when(chatResponse.getMetadata())
                .thenReturn(ChatResponseMetadata.builder().build());
        List<Message> result = GigaChatResponseUtils.getConversationHistory(chatResponse);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testGetConversationHistoryWithConversationHistoryInMetadata() {
        Message message = new UserMessage("Hello, world!");
        var metadata = ChatResponseMetadata.builder()
                .keyValue(GigaChatModel.CONVERSATION_HISTORY, List.of(message))
                .build();
        when(chatResponse.getMetadata()).thenReturn(metadata);
        List<Message> result = GigaChatResponseUtils.getConversationHistory(chatResponse);
        assertEquals(Collections.singletonList(message), result);
    }
}
