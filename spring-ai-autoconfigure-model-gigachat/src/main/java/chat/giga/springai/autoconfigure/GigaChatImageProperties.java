package chat.giga.springai.autoconfigure;

import chat.giga.springai.image.GigaChatImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(GigaChatImageProperties.CONFIG_PREFIX)
public class GigaChatImageProperties {

    public static final String CONFIG_PREFIX = "spring.ai.gigachat.image";

    @NestedConfigurationProperty
    private GigaChatImageOptions options = GigaChatImageOptions.builder().build();

    public GigaChatImageOptions getOptions() {
        return options;
    }

    public void setOptions(GigaChatImageOptions options) {
        this.options = options;
    }
}
