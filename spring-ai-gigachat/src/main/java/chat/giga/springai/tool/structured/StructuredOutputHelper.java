package chat.giga.springai.tool.structured;

import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

/**
 * Helper for virtual function structured output.
 * Provides methods for creating structured output function and processing responses.
 */
@Slf4j
@UtilityClass
public class StructuredOutputHelper {

    private static final String FUNCTION_NAME = "_structured_output_function";
    private static final String FUNCTION_DESCRIPTION =
            """
            Формирует структурированный ответ на основе диалога. \
            Сначала вызови ВСЕ необходимые функции для получения данных, \
            затем вызови эту функцию с данными ответа. \
            Эта функция должна быть вызвана ПОСЛЕДНЕЙ.""";

    /**
     * Creates structured output function description.
     *
     * @param outputSchema JSON schema for the structured output
     * @return function description to add to completion request
     */
    public static CompletionRequest.FunctionDescription createFunction(final String outputSchema) {
        return new CompletionRequest.FunctionDescription(FUNCTION_NAME, FUNCTION_DESCRIPTION, outputSchema, null, null);
    }

    /**
     * Adds structured output function to the request if schema is provided.
     *
     * @param request completion request to modify
     * @param outputSchema JSON schema for structured output, may be null or blank
     */
    public static void addToRequest(final CompletionRequest request, @Nullable final String outputSchema) {
        if (outputSchema == null || outputSchema.isBlank()) {
            return;
        }

        // Defensive copy: getFunctions() may return immutable list (e.g., List.of())
        final List<CompletionRequest.FunctionDescription> functions =
                request.getFunctions() != null ? new ArrayList<>(request.getFunctions()) : new ArrayList<>();
        functions.add(createFunction(outputSchema));
        request.setFunctions(functions);

        log.debug("Structured output function added, model will decide execution order");
    }

    /**
     * Checks if the message contains a structured output function call.
     *
     * @param message response message to check
     * @return true if this is a structured output function call
     */
    public static boolean isStructuredOutputCall(@Nullable final CompletionResponse.MessagesRes message) {
        return message != null
                && message.getFunctionCall() != null
                && FUNCTION_NAME.equals(message.getFunctionCall().getName());
    }

    /**
     * Extracts structured content from the function call arguments.
     *
     * @param message response message with structured output function call
     * @return JSON content from function arguments, or "{}" if null
     */
    public static String extractContent(final CompletionResponse.MessagesRes message) {
        final String content = message.getFunctionCall().getArguments();
        if (content == null) {
            log.warn("Structured output function returned null arguments, using empty object");
            return "{}";
        }
        return content;
    }
}
