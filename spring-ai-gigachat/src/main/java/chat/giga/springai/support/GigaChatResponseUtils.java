package chat.giga.springai.support;

import chat.giga.springai.GigaChatModel;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
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
    public static List<Message> getConversationHistory(ChatResponse chatResponse) {
        if (chatResponse != null && chatResponse.getMetadata() != null) {
            List<Message> messages = chatResponse.getMetadata().get(GigaChatModel.CONVERSATION_HISTORY);
            return messages == null ? List.of() : messages;
        }
        return List.of();
    }
}
