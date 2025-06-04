package chat.giga.springai.api.chat.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Важно помечать @JsonProperty каждое поле.
 * На это завязана логика метода {@link org.springframework.ai.model.ModelOptionsUtils#merge}
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompletionRequest {
    @JsonProperty("model")
    private String model;

    @JsonProperty("messages")
    private List<Message> messages;

    @JsonProperty("function_call")
    private Object functionCall;

    @JsonProperty("functions")
    private List<FunctionDescription> functions;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @Builder.Default
    @JsonProperty("stream")
    private Boolean stream = false;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("repetition_penalty")
    private Double repetitionPenalty;

    @JsonProperty("update_interval")
    private Double updateInterval;

    /**
     * Флаг для включения/отключения цензуры
     * */
    @JsonProperty("profanity_check")
    private Boolean profanityCheck;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("role")
        private Role role;

        @JsonProperty("content")
        private String content;

        /**
         * Id вызванной функции. Только при role = function
         */
        @JsonProperty("functions_state_id")
        private String functionsStateId;

        @JsonProperty("function_call")
        private CompletionResponse.FunctionCall functionCall;

        @JsonProperty("attachments")
        private List<?> attachments;

        /**
         * Название вызванной функции. Только при role = function
         */
        @JsonProperty("name")
        private String name;

        public Message(Role role, String content) {
            this.role = role;
            this.content = content;
        }

        public Message(Role role, String content, List<?> attachments) {
            this.role = role;
            this.content = content;
            this.attachments = attachments;
        }
    }

    /**
     * Описание функции.
     *
     * @param name Название функции.
     * @param description Текстовое описание функции.
     * @param parameters Валидный JSON-объект с набором пар ключ-значение, которые описывают аргументы функции.
     * @param fewShotExamples Валидный JSON-объект с набором пар ключ-значение, которые описывают аргументы функции.
     * @param returnParameters JSON-объект с описанием параметров, которые может вернуть ваша функция.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionDescription(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("parameters") @JsonRawValue String parameters,
            @JsonProperty("few_shot_examples") List<FewShotExample> fewShotExamples,
            @JsonProperty("return_parameters") @JsonRawValue String returnParameters) {}

    public record FewShotExample(
            @JsonProperty("request") String request, @JsonProperty("params") @JsonRawValue String params) {}

    public enum Role {
        system,
        user,
        assistant,
        function
    }
}
