package chat.giga.springai.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(GigaChatImageProperties.CONFIG_PREFIX)
public class GigaChatImageProperties {

    public static final String CONFIG_PREFIX = "spring.ai.gigachat.image";

    /**
     * Enable GigaChat image model.
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
