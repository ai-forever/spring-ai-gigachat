package chat.giga.springai.autoconfigure;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.image.GigaChatImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(GigaChatImageProperties.CONFIG_PREFIX)
public class GigaChatImageProperties {

    public static final String SYSTEM_PROMPT = "You are an artist. If the user asks you to draw something," +
            "generate an image using the built-in text2image function" +
            "and return a tag in the form <img src=\"FILE_ID\"/>.";

    public static final String CONFIG_PREFIX = "spring.ai.gigachat.image";

    @NestedConfigurationProperty
    private GigaChatImageOptions options = new GigaChatImageOptions(SYSTEM_PROMPT, GigaChatModel.DEFAULT_MODEL_NAME);

    public GigaChatImageOptions getOptions() {
        return options;
    }

    public void setOptions(GigaChatImageOptions options) {
        this.options = options;
    }

}
