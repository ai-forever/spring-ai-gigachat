package chat.giga.springai;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.param.FunctionCallParam;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GigaChatOptions implements ToolCallingChatOptions {

    @JsonProperty("model")
    private String model;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("repetition_penalty")
    private Double repetitionPenalty;

    @JsonProperty("update_interval")
    private Double updateInterval;

    /**
     * Collection of {@link ToolCallback}s to be used for tool calling in the chat
     * completion requests.
     */
    @JsonIgnore
    private List<ToolCallback> toolCallbacks = new ArrayList<>();

    /**
     * Collection of tool names to be resolved at runtime and used for tool calling in the
     * chat completion requests.
     */
    @JsonIgnore
    private Set<String> toolNames = new HashSet<>();

    /**
     * Whether to enable the tool execution lifecycle internally in ChatModel.
     */
    @JsonIgnore
    private Boolean internalToolExecutionEnabled;

    @JsonIgnore
    private Map<String, Object> toolContext = new HashMap<>();

    @JsonProperty("function_call_mode")
    private FunctionCallMode functionCallMode;

    @JsonProperty("function_call_param")
    private FunctionCallParam functionCallParam;

    /**
     * Флаг для включения/отключения цензуры
     */
    @JsonProperty("profanity_check")
    private Boolean profanityCheck;

    /**
     * Использование X-Session-ID для кэширования контекста разговора с GigaChat.
     */
    @JsonProperty("session_id")
    private String sessionId;

    @Override
    public String getModel() {
        return model;
    }

    @Override
    @JsonIgnore
    public Double getFrequencyPenalty() {
        // Гигачат не поддерживает данный параметр
        return null;
    }

    @Override
    @JsonIgnore
    public Double getPresencePenalty() {
        // Гигачат не поддерживает данный параметр
        return null;
    }

    @Override
    @JsonIgnore
    public List<String> getStopSequences() {
        // Гигачат не поддерживает данный параметр
        return null;
    }

    @Override
    public Double getTemperature() {
        return temperature;
    }

    @Override
    public Double getTopP() {
        return topP;
    }

    @Override
    public GigaChatOptions copy() {
        return this.toBuilder().build();
    }

    @Override
    @JsonIgnore
    public Integer getTopK() {
        // Гигачат не поддерживает данный параметр
        return null;
    }

    @Override
    public Map<String, Object> getToolContext() {
        return this.toolContext;
    }

    @Override
    public void setToolContext(Map<String, Object> toolContext) {
        this.toolContext = toolContext;
    }

    public FunctionCallMode getFunctionCallMode() {
        return functionCallMode;
    }

    public void setFunctionCallMode(FunctionCallMode functionCallMode) {
        this.functionCallMode = functionCallMode;
    }

    @Override
    @JsonIgnore
    public List<ToolCallback> getToolCallbacks() {
        return this.toolCallbacks;
    }

    @Override
    @JsonIgnore
    public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
        Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
        Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
        this.toolCallbacks = toolCallbacks;
    }

    @Override
    @JsonIgnore
    public Set<String> getToolNames() {
        return this.toolNames;
    }

    @Override
    @JsonIgnore
    public void setToolNames(Set<String> toolNames) {
        Assert.notNull(toolNames, "toolNames cannot be null");
        Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
        toolNames.forEach(tool -> Assert.hasText(tool, "toolNames cannot contain empty elements"));
        this.toolNames = toolNames;
    }

    @Override
    @Nullable
    @JsonIgnore
    public Boolean getInternalToolExecutionEnabled() {
        return internalToolExecutionEnabled;
    }

    @Override
    @JsonIgnore
    public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
        this.internalToolExecutionEnabled = internalToolExecutionEnabled;
    }

    @Getter
    @AllArgsConstructor
    public enum FunctionCallMode {
        /** GigaChat не будет вызывать функции. */
        NONE("none"),
        /** В зависимости от содержимого запроса, модель решает сгенерировать сообщение или вызвать функцию. */
        AUTO("auto"),
        /** Все запросы будут вызывать функцию, указанную в functionCallParam. */
        CUSTOM_FUNCTION(null);

        private final String value;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .model(this.model)
                .temperature(this.temperature)
                .topP(this.topP)
                .maxTokens(this.maxTokens)
                .repetitionPenalty(this.repetitionPenalty)
                .updateInterval(this.updateInterval)
                .toolCallbacks(this.toolCallbacks)
                .toolNames(this.toolNames)
                .internalToolExecutionEnabled(this.internalToolExecutionEnabled)
                .toolContext(this.toolContext)
                .functionCallMode(this.functionCallMode)
                .functionCallParam(this.functionCallParam)
                .profanityCheck(this.profanityCheck)
                .sessionId(this.sessionId);
    }

    public static class Builder {
        private final GigaChatOptions options = new GigaChatOptions();

        public Builder model(GigaChatApi.ChatModel model) {
            Assert.notNull(model, "model cannot be null");
            this.options.setModel(model.getName());
            return this;
        }

        public Builder model(String model) {
            this.options.setModel(model);
            return this;
        }

        public Builder temperature(Double temperature) {
            this.options.setTemperature(temperature);
            return this;
        }

        public Builder topP(Double topP) {
            this.options.setTopP(topP);
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.options.setMaxTokens(maxTokens);
            return this;
        }

        public Builder repetitionPenalty(Double repetitionPenalty) {
            this.options.setRepetitionPenalty(repetitionPenalty);
            return this;
        }

        public Builder updateInterval(Double updateInterval) {
            this.options.setUpdateInterval(updateInterval);
            return this;
        }

        public Builder toolCallbacks(ToolCallback... toolCallbacks) {
            Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
            this.options.toolCallbacks.addAll(Arrays.asList(toolCallbacks));
            return this;
        }

        public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
            this.options.setToolCallbacks(toolCallbacks);
            return this;
        }

        public Builder toolNames(String... toolNames) {
            Assert.notNull(toolNames, "toolNames cannot be null");
            this.options.toolNames.addAll(Arrays.asList(toolNames));
            return this;
        }

        public Builder toolNames(Set<String> toolNames) {
            this.options.setToolNames(toolNames);
            return this;
        }

        public Builder internalToolExecutionEnabled(Boolean internalToolExecutionEnabled) {
            this.options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
            return this;
        }

        public Builder toolContext(Map<String, Object> toolContext) {
            if (this.options.toolContext == null) {
                this.options.toolContext = toolContext;
            } else {
                this.options.toolContext.putAll(toolContext);
            }
            return this;
        }

        public Builder functionCallMode(FunctionCallMode functionCallMode) {
            this.options.setFunctionCallMode(functionCallMode);
            return this;
        }

        public Builder functionCallParam(FunctionCallParam functionCallParam) {
            this.options.setFunctionCallParam(functionCallParam);
            return this;
        }

        public Builder profanityCheck(Boolean profanityCheck) {
            this.options.setProfanityCheck(profanityCheck);
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.options.setSessionId(sessionId);
            return this;
        }

        public GigaChatOptions build() {
            return this.options;
        }
    }
}
