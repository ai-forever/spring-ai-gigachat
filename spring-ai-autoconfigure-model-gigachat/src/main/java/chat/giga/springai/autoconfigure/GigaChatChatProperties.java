package chat.giga.springai.autoconfigure;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.GigaChatOptions;
import chat.giga.springai.api.chat.param.FunctionCallParam;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(GigaChatChatProperties.CONFIG_PREFIX)
@Getter
@Setter
public class GigaChatChatProperties {

    public static final String CONFIG_PREFIX = "spring.ai.gigachat.chat";

    private String model = GigaChatModel.DEFAULT_MODEL_NAME;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Double repetitionPenalty;
    private Double updateInterval;
    private Boolean profanityCheck;
    private GigaChatOptions.FunctionCallMode functionCallMode;
    private FunctionCallParam functionCallParam;
    private Map<String, String> httpHeaders;
}
