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
    public static List<Message> getInternalMessages(ChatResponse chatResponse) {
        return getFromMetadata(chatResponse, GigaChatModel.INTERNAL_CONVERSATION_HISTORY, List.of());
    }

    public static List<String> getUploadedMediaIds(ChatResponse chatResponse) {
        return getFromMetadata(chatResponse, GigaChatModel.UPLOADED_MEDIA_IDS, List.of());
    }

    private static <T> T getFromMetadata(ChatResponse chatResponse, String key, T defaultValue) {
        if (chatResponse != null && chatResponse.getMetadata() != null) {
            T data = chatResponse.getMetadata().get(key);
            return data == null ? defaultValue : data;
        }
        return defaultValue;
    }
}
