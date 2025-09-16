package chat.giga.springai.api;

import java.time.Duration;
import lombok.Data;

@Data
public class GigaChatInternalProperties {
    public static final String CONFIG_PREFIX = "spring.ai.gigachat.internal";

    private boolean makeSystemPromptFirstMessageInMemory = true;
    private Duration connectTimeout = Duration.ofSeconds(15L);
    private Duration readTimeout;
}
