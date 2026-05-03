package chat.giga.springai.advisor;

import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;

/**
 * Configuration options for the GigaChat ChatClient.
 * Provides preset advisor parameters for GigaChat-specific features.
 */
public final class GigaChatAdvisorParams {

    private static final String STRUCTURED_OUTPUT_NATIVE_KEY = ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey();

    private GigaChatAdvisorParams() {}

    /**
     * Enables virtual function structured output for GigaChat.
     *
     * <p>Uses function calling mechanism to guarantee JSON response format.
     * Under the hood, a virtual function {@code _structured_output_function} is created
     * with JSON Schema, and the model calls it with data as arguments.
     *
     * <p>Usage:
     * <pre>{@code
     * // Per-request
     * ActorFilms result = chatClient
     *     .prompt("List 5 films by Tarantino")
     *     .advisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
     *     .call()
     *     .entity(ActorFilms.class);
     *
     * // Global configuration
     * ChatClient chatClient = builder
     *     .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
     *     .build();
     * }</pre>
     *
     * @see <a href="https://github.com/ai-forever/spring-ai-gigachat/blob/main/docs/structured-output.md">
     *     Structured Output Documentation</a>
     */
    public static final Consumer<ChatClient.AdvisorSpec> VIRTUAL_FUNCTION_STRUCTURED_OUTPUT =
            advisorSpec -> advisorSpec.param(STRUCTURED_OUTPUT_NATIVE_KEY, true);
}
