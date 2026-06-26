package chat.giga.springai.advisor;

import chat.giga.springai.GigaChatOptions;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

/**
 * Advisor, который перекладывает sessionId из контекста в запрос.
 */
public class GigaChatCachingAdvisor implements CallAdvisor, StreamAdvisor {
    public static final String X_SESSION_ID = "X-Session-ID";

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        Assert.notNull(chatClientRequest, "the chatClientRequest cannot be null");
        var updatedChatClientRequest = fillOptions(chatClientRequest);

        return callAdvisorChain.nextCall(updatedChatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        Assert.notNull(chatClientRequest, "the chatClientRequest cannot be null");
        var updatedChatClientRequest = fillOptions(chatClientRequest);

        return streamAdvisorChain.nextStream(updatedChatClientRequest);
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private ChatClientRequest fillOptions(@NonNull ChatClientRequest chatClientRequest) {
        Prompt prompt = chatClientRequest.prompt();
        ChatOptions chatOptions = prompt.getOptions();
        if (chatOptions instanceof GigaChatOptions gigaChatOptions) {
            String sessionId = (String) chatClientRequest.context().get(X_SESSION_ID);
            if (sessionId != null) {
                Map<String, String> httpHeaders = new HashMap<>();
                if (!CollectionUtils.isEmpty(gigaChatOptions.getHttpHeaders())) {
                    httpHeaders.putAll(gigaChatOptions.getHttpHeaders());
                }
                httpHeaders.put(X_SESSION_ID, sessionId);

                return chatClientRequest
                        .mutate()
                        .prompt(prompt.mutate()
                                .chatOptions(gigaChatOptions
                                        .mutate()
                                        .httpHeaders(httpHeaders)
                                        .build())
                                .build())
                        .build();
            }
        }
        return chatClientRequest;
    }
}
