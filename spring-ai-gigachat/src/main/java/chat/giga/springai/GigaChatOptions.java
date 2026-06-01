package chat.giga.springai;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.param.FunctionCallParam;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

@Getter
@ToString
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GigaChatOptions implements ToolCallingChatOptions {

    @JsonProperty("model")
    private @Nullable String model;

    @JsonProperty("temperature")
    private @Nullable Double temperature;

    @JsonProperty("top_p")
    private @Nullable Double topP;

    @JsonProperty("max_tokens")
    private @Nullable Integer maxTokens;

    @JsonProperty("repetition_penalty")
    private @Nullable Double repetitionPenalty;

    @JsonProperty("update_interval")
    private @Nullable Double updateInterval;

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
    private @Nullable Boolean internalToolExecutionEnabled;

    @JsonIgnore
    private Map<String, Object> toolContext = new HashMap<>();

    @JsonProperty("function_call_mode")
    private @Nullable FunctionCallMode functionCallMode;

    @JsonProperty("function_call_param")
    private @Nullable FunctionCallParam functionCallParam;

    /**
     * Флаг для включения/отключения цензуры
     */
    @JsonProperty("profanity_check")
    private @Nullable Boolean profanityCheck;

    @JsonProperty("http_headers")
    private Map<String, String> httpHeaders = new HashMap<>();

    @Nullable
    @Override
    @JsonIgnore
    public Double getFrequencyPenalty() {
        // Гигачат не поддерживает данный параметр
        return null;
    }

    @Nullable
    @Override
    @JsonIgnore
    public Double getPresencePenalty() {
        // Гигачат не поддерживает данный параметр
        return null;
    }

    @Nullable
    @Override
    @JsonIgnore
    public List<String> getStopSequences() {
        // Гигачат не поддерживает данный параметр
        return null;
    }

    @Override
    public GigaChatOptions copy() {
        return mutate().build();
    }

    @Nullable
    @Override
    @JsonIgnore
    public Integer getTopK() {
        // Гигачат не поддерживает данный параметр
        return null;
    }

    @Override
    @JsonIgnore
    public List<ToolCallback> getToolCallbacks() {
        return this.toolCallbacks;
    }

    @Override
    @JsonIgnore
    public Set<String> getToolNames() {
        return this.toolNames;
    }

    @Override
    @JsonIgnore
    public Map<String, Object> getToolContext() {
        return this.toolContext;
    }

    @Override
    @Nullable
    @JsonIgnore
    public Boolean getInternalToolExecutionEnabled() {
        return internalToolExecutionEnabled;
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

    @Override
    public Builder mutate() {
        return GigaChatOptions.builder()
                .model(this.getModel())
                .temperature(this.getTemperature())
                .topP(this.getTopP())
                .maxTokens(this.getMaxTokens())
                .repetitionPenalty(this.getRepetitionPenalty())
                .updateInterval(this.getUpdateInterval())
                .toolCallbacks(this.getToolCallbacks())
                .toolNames(this.getToolNames())
                .internalToolExecutionEnabled(this.getInternalToolExecutionEnabled())
                .toolContext(this.getToolContext())
                .functionCallMode(this.getFunctionCallMode())
                .functionCallParam(this.getFunctionCallParam())
                .profanityCheck(this.getProfanityCheck())
                .httpHeaders(this.getHttpHeaders());
    }

    public static GigaChatOptions fromOptions(@Nullable GigaChatOptions fromOptions) {
        return fromOptions == null ? GigaChatOptions.builder().build() : fromOptions.copy();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<Builder> {}

    protected abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
            extends DefaultToolCallingChatOptions.Builder<B>
    // todo: implements StructuredOutputChatOptions.Builder<B>
    {

        @Override
        public B clone() {
            AbstractBuilder<B> copy = super.clone();

            copy.repetitionPenalty = this.repetitionPenalty;
            copy.updateInterval = this.updateInterval;
            copy.toolContext = this.toolContext == null ? null : new HashMap<>(this.toolContext);
            copy.functionCallMode = this.functionCallMode;
            copy.functionCallParam = this.functionCallParam;
            copy.profanityCheck = this.profanityCheck;
            copy.httpHeaders = this.httpHeaders == null ? null : new HashMap<>(this.httpHeaders);

            return (B) copy;
        }

        private @Nullable Double repetitionPenalty;

        private @Nullable Double updateInterval;

        private @Nullable Map<String, Object> toolContext = new HashMap<>();

        private @Nullable FunctionCallMode functionCallMode;

        private @Nullable FunctionCallParam functionCallParam;

        private @Nullable Boolean profanityCheck;

        private @Nullable Map<String, String> httpHeaders = new HashMap<>();

        public B model(GigaChatApi.ChatModel model) {
            if (model != null) {
                this.model(model.getName());
            } else {
                this.model((String) null);
            }
            return self();
        }

        public B temperature(@Nullable Double temperature) {
            this.temperature = temperature;
            return self();
        }

        public B topP(@Nullable Double topP) {
            this.topP = topP;
            return self();
        }

        public B maxTokens(@Nullable Integer maxTokens) {
            this.maxTokens = maxTokens;
            return self();
        }

        public B repetitionPenalty(@Nullable Double repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return self();
        }

        public B updateInterval(@Nullable Double updateInterval) {
            this.updateInterval = updateInterval;
            return self();
        }

        public B internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
            this.internalToolExecutionEnabled = internalToolExecutionEnabled;
            return self();
        }

        public B functionCallMode(@Nullable FunctionCallMode functionCallMode) {
            this.functionCallMode = functionCallMode;
            return self();
        }

        public B functionCallParam(@Nullable FunctionCallParam functionCallParam) {
            this.functionCallParam = functionCallParam;
            return self();
        }

        public B profanityCheck(@Nullable Boolean profanityCheck) {
            this.profanityCheck = profanityCheck;
            return self();
        }

        public B httpHeaders(@Nullable Map<String, String> httpHeaders) {
            this.httpHeaders = httpHeaders == null ? new HashMap<>() : new HashMap<>(httpHeaders);
            return self();
        }

        @Override
        @SuppressWarnings("NullAway")
        public GigaChatOptions build() {
            GigaChatOptions options = new GigaChatOptions();

            // AbstractGigaChatOptions fields
            options.model = this.model;
            options.temperature = this.temperature;
            options.topP = this.topP;
            options.maxTokens = this.maxTokens;
            options.repetitionPenalty = this.repetitionPenalty;
            options.updateInterval = this.updateInterval;

            // ChatOptions fields

            // ToolCallingChatOptions fields
            options.toolCallbacks =
                    this.toolCallbacks == null ? new ArrayList<>() : new ArrayList<>(this.toolCallbacks);
            options.toolNames = this.toolNames == null ? new HashSet<>() : new HashSet<>(this.toolNames);
            options.internalToolExecutionEnabled = this.internalToolExecutionEnabled;
            options.toolContext = this.toolContext == null ? new HashMap<>() : new HashMap<>(this.toolContext);

            // GigaChat-specific fields
            options.functionCallMode = this.functionCallMode;
            options.functionCallParam = this.functionCallParam;
            options.profanityCheck = this.profanityCheck;
            options.httpHeaders = this.httpHeaders == null ? new HashMap<>() : new HashMap<>(this.httpHeaders);

            return options;
        }

        @Override
        public B combineWith(ChatOptions.Builder<?> other) {
            super.combineWith(other);
            if (other instanceof AbstractBuilder<?> that) {
                if (that.repetitionPenalty != null) {
                    this.repetitionPenalty = that.repetitionPenalty;
                }
                if (that.updateInterval != null) {
                    this.updateInterval = that.updateInterval;
                }
                if (that.functionCallMode != null) {
                    this.functionCallMode = that.functionCallMode;
                }
                if (that.functionCallParam != null) {
                    this.functionCallParam = that.functionCallParam;
                }
                if (that.profanityCheck != null) {
                    this.profanityCheck = that.profanityCheck;
                }
                if (that.httpHeaders != null) {
                    if (this.httpHeaders == null) {
                        this.httpHeaders = new HashMap<>();
                    }
                    this.httpHeaders.putAll(that.httpHeaders);
                }
            }
            return self();
        }
    }
}
