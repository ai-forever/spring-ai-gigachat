package chat.giga.springai;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.param.FunctionCallParam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class GigaChatOptions implements ToolCallingChatOptions {

    private @Nullable String model;

    private @Nullable Double temperature;

    private @Nullable Double topP;

    private @Nullable Integer maxTokens;

    private @Nullable Double repetitionPenalty;

    private @Nullable Double updateInterval;

    /**
     * Collection of {@link ToolCallback}s to be used for tool calling in the chat
     * completion requests.
     */
    private List<ToolCallback> toolCallbacks = new ArrayList<>();

    private Map<String, Object> toolContext = new HashMap<>();

    private @Nullable FunctionCallMode functionCallMode;

    private @Nullable FunctionCallParam functionCallParam;

    /**
     * Флаг для включения/отключения цензуры
     */
    private @Nullable Boolean profanityCheck;

    private Map<String, String> httpHeaders = new HashMap<>();

    @Nullable
    @Override
    public Double getFrequencyPenalty() {
        // Гигачат не поддерживает данный параметр
        return null;
    }

    @Nullable
    @Override
    public Double getPresencePenalty() {
        // Гигачат не поддерживает данный параметр
        return null;
    }

    @Nullable
    @Override
    public List<String> getStopSequences() {
        // Гигачат не поддерживает данный параметр
        return null;
    }

    // ChatOptions#copy() удалён из интерфейса в Spring AI 2.0 GA, поэтому без @Override.
    // Метод сохранён: используется в fromOptions(...) и как удобный публичный API.
    public GigaChatOptions copy() {
        return mutate().build();
    }

    @Nullable
    @Override
    public Integer getTopK() {
        // Гигачат не поддерживает данный параметр
        return null;
    }

    @Override
    public List<ToolCallback> getToolCallbacks() {
        return this.toolCallbacks;
    }

    @Override
    public Map<String, Object> getToolContext() {
        return this.toolContext;
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
    // Реализация StructuredOutputChatOptions.Builder<B> вынесена в отдельную задачу
    // (native structured output, см. ветку feature/native-structured-output).
    {

        @Override
        public B clone() {
            AbstractBuilder<B> copy = super.clone();

            copy.repetitionPenalty = this.repetitionPenalty;
            copy.updateInterval = this.updateInterval;
            copy.functionCallMode = this.functionCallMode;
            copy.functionCallParam = this.functionCallParam;
            copy.profanityCheck = this.profanityCheck;
            copy.httpHeaders = this.httpHeaders == null ? null : new HashMap<>(this.httpHeaders);

            return (B) copy;
        }

        private @Nullable Double repetitionPenalty;

        private @Nullable Double updateInterval;

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

            // ChatOptions fields: stopSequences/frequencyPenalty/presencePenalty/topK
            // не поддерживаются GigaChat API и намеренно не заполняются (см. геттеры выше).

            // ToolCallingChatOptions fields (в Spring AI 2.0 GA остались только toolCallbacks + toolContext)
            options.toolCallbacks =
                    this.toolCallbacks == null ? new ArrayList<>() : new ArrayList<>(this.toolCallbacks);
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
            // Базовые поля (model/temperature/topP/maxTokens) + toolCallbacks/toolContext сливает
            // родительский combineWith. Здесь докидываем только GigaChat-специфичные поля, у которых
            // нет родительской обработки.
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
