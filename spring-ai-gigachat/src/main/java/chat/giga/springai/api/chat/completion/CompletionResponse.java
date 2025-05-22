package chat.giga.springai.api.chat.completion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class CompletionResponse {
    @JsonIgnore
    private String id = ""; // проставляется из http-заголовка 'x-request-id' ответа модели

    private List<Choice> choices;
    private Long created;
    private String model;
    private Usage usage;
    private String object; // Название вызываемого метода.

    public CompletionResponse setId(String id) {
        this.id = Objects.toString(id, "");
        return this;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    public static class Choice {
        /**
         * Только для non-stream-сообщений
         */
        private MessagesRes message;
        /**
         * Только для stream-сообщений
         */
        private MessagesRes delta;
        /** Индекс сообщения в массиве начиная с ноля */
        private Integer index;
        /**
         * @see FinishReason
         */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    public static final class FinishReason {
        public static final String STOP = "stop";
        public static final String LENGTH = "length";
        public static final String FUNCTION_CALL = "function_call";
        public static final String BLACKLIST = "blacklist";
        public static final String ERROR = "error";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;

        @JsonProperty("precached_prompt_tokens")
        private Integer precachedPromptTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    public static class MessagesRes {
        /** Роль автора сообщения. */
        private Role role;
        /**
         * Содержимое сообщения, например, результат генерации.
         * В сообщениях с ролью function_in_progress содержит информацию о том, сколько времени осталось
         * до завершения работы встроенной функции.
         */
        private String content;
        /**
         * Передается в сообщениях с ролью function_in_progress.
         * Содержит информацию о том, когда был создан фрагмент сообщения.
         */
        private Long created;
        /** Название вызванной встроенной функции. Передается в сообщениях с ролью function_in_progress. */
        private String name;
        /**
         * Идентификатор, который объединяет массив функций, переданных в запросе.
         * Возвращается в ответе модели (сообщение с "role": "assistant") при вызове встроенных или собственных функций.
         * Позволяет сохранить контекст вызова функции и повысить качество работы модели.
         * Для этого нужно передать идентификатор в запросе на генерацию в сообщении с ролью assistant.
         */
        @JsonProperty("functions_state_id")
        private String functionsStateId;

        @JsonProperty("function_call")
        private FunctionCall functionCall;
    }

    /**
     * Определение функции.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionCall {
        /**
         * Название функции.
         */
        @JsonProperty("name")
        private String name;
        /**
         * Аргументы для вызова функции в виде пар ключ-значение.
         */
        @JsonProperty("arguments")
        @JsonRawValue
        private String arguments;

        @JsonSetter("arguments")
        public FunctionCall setArguments(JsonNode arguments) {
            this.arguments = arguments == null ? null : arguments.toString();
            return this;
        }

        public FunctionCall setArguments(String arguments) {
            this.arguments = arguments;
            return this;
        }
    }

    public enum Role {
        assistant,
        function_in_progress
    }
}
