package chat.giga.springai.autoconfigure;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.GigaChatOptions;
import chat.giga.springai.api.chat.param.FunctionCallParam;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

@ConfigurationProperties(GigaChatChatProperties.CONFIG_PREFIX)
@Getter
@Setter
public class GigaChatChatProperties {

    public static final String CONFIG_PREFIX = "spring.ai.gigachat.chat";

    @Nullable
    private String model = GigaChatModel.DEFAULT_MODEL_NAME;

    @Nullable
    private Double temperature;

    @Nullable
    private Double topP;

    @Nullable
    private Integer maxTokens;

    @Nullable
    private Double repetitionPenalty;

    @Nullable
    private Double updateInterval;

    @Nullable
    private Boolean profanityCheck;

    @Nullable
    private Boolean internalToolExecutionEnabled;

    private GigaChatOptions.FunctionCallMode functionCallMode;

    @Nullable
    private FunctionCallParam functionCallParam;

    private Map<String, String> httpHeaders;

    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private Options options = new Options();

    @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat")
    @Deprecated(since = "2.0.0", forRemoval = true)
    public Options getOptions() {
        return this.options;
    }

    @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat")
    @Deprecated(since = "2.0.0", forRemoval = true)
    public void setOptions(Options options) {
        this.options = options;
    }

    public class Options {

        @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat.model")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable String getModel() {
            return GigaChatChatProperties.this.getModel();
        }

        public void setModel(@Nullable String model) {
            GigaChatChatProperties.this.setModel(model);
        }

        @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat.temperature")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Double getTemperature() {
            return GigaChatChatProperties.this.getTemperature();
        }

        public void setTemperature(@Nullable Double temperature) {
            GigaChatChatProperties.this.setTemperature(temperature);
        }

        @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat.top-p")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Double getTopP() {
            return GigaChatChatProperties.this.getTopP();
        }

        public void setTopP(@Nullable Double topP) {
            GigaChatChatProperties.this.setTopP(topP);
        }

        @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat.max-tokens")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Integer getMaxTokens() {
            return GigaChatChatProperties.this.getMaxTokens();
        }

        public void setMaxTokens(@Nullable Integer maxTokens) {
            GigaChatChatProperties.this.setMaxTokens(maxTokens);
        }

        @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat.repetition-penalty")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Double getRepetitionPenalty() {
            return GigaChatChatProperties.this.getRepetitionPenalty();
        }

        public void setRepetitionPenalty(@Nullable Double repetitionPenalty) {
            GigaChatChatProperties.this.setRepetitionPenalty(repetitionPenalty);
        }

        @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat.update-interval")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Double getUpdateInterval() {
            return GigaChatChatProperties.this.getUpdateInterval();
        }

        public void setUpdateInterval(@Nullable Double updateInterval) {
            GigaChatChatProperties.this.setUpdateInterval(updateInterval);
        }

        @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat.profanity-check")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Boolean getProfanityCheck() {
            return GigaChatChatProperties.this.getProfanityCheck();
        }

        public void setProfanityCheck(@Nullable Boolean profanityCheck) {
            GigaChatChatProperties.this.setProfanityCheck(profanityCheck);
        }

        @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat.internal-tool-execution-enabled")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable Boolean getInternalToolExecutionEnabled() {
            return GigaChatChatProperties.this.getInternalToolExecutionEnabled();
        }

        public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
            GigaChatChatProperties.this.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
        }

        @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat.function-call-mode")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public GigaChatOptions.FunctionCallMode getFunctionCallMode() {
            return GigaChatChatProperties.this.getFunctionCallMode();
        }

        public void setFunctionCallMode(GigaChatOptions.FunctionCallMode functionCallMode) {
            GigaChatChatProperties.this.setFunctionCallMode(functionCallMode);
        }

        @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat.function-call-param")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public @Nullable FunctionCallParam getFunctionCallParam() {
            return GigaChatChatProperties.this.getFunctionCallParam();
        }

        public void setFunctionCallParam(@Nullable FunctionCallParam functionCallParam) {
            GigaChatChatProperties.this.setFunctionCallParam(functionCallParam);
        }

        @DeprecatedConfigurationProperty(replacement = "spring.ai.gigachat.chat.http-headers")
        @Deprecated(since = "2.0.0", forRemoval = true)
        public Map<String, String> getHttpHeaders() {
            return GigaChatChatProperties.this.getHttpHeaders();
        }

        public void setHttpHeaders(Map<String, String> httpHeaders) {
            GigaChatChatProperties.this.setHttpHeaders(httpHeaders);
        }
    }
}
