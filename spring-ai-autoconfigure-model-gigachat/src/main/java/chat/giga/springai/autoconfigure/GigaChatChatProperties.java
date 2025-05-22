package chat.giga.springai.autoconfigure;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.GigaChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(GigaChatChatProperties.CONFIG_PREFIX)
public class GigaChatChatProperties {

    public static final String CONFIG_PREFIX = "spring.ai.gigachat.chat";

    /**
     * Enable GigaChat chat model.
     */
    private boolean enabled = true;

    @NestedConfigurationProperty
    private GigaChatOptions options =
            GigaChatOptions.builder().model(GigaChatModel.DEFAULT_MODEL_NAME).build();

    public void setOptions(GigaChatOptions options) {
        this.options = options;
    }

    public GigaChatOptions getOptions() {
        return this.options;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return this.enabled;
    }
}
