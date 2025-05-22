package chat.giga.springai.api.auth;

import lombok.Data;

@Data
public class GigaChatInternalProperties {
    public static final String CONFIG_PREFIX = "spring.ai.gigachat.internal";

    private boolean makeSystemPromptFirstMessageInMemory = true;
}
