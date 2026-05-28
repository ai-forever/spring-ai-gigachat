package chat.giga.springai.advisor;

import chat.giga.springai.GigaChatOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

/**
 * Advisor, который перекладывает sessionId из контекста в запрос.
 */
public class GigaChatCachingAdvisor implements CallAdvisor, StreamAdvisor {
    public static final String X_SESSION_ID = "X-Session-ID";

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        fillOptions(chatClientRequest);

        return callAdvisorChain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        fillOptions(chatClientRequest);

        return streamAdvisorChain.nextStream(chatClientRequest);
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private void fillOptions(ChatClientRequest chatClientRequest) {
        Optional.of(chatClientRequest.prompt())
                .map(Prompt::getOptions)
                .map(GigaChatOptions.class::cast)
                .ifPresent(it -> {
                    String sessionId = (String) chatClientRequest.context().get(X_SESSION_ID);
                    if (sessionId != null) {
                        Map<String, String> httpHeaders = new HashMap<>();
                        if (!CollectionUtils.isEmpty(it.getHttpHeaders())) {
                            httpHeaders.putAll(it.getHttpHeaders());
                        }
                        httpHeaders.put(X_SESSION_ID, sessionId);
                        it.setHttpHeaders(httpHeaders);
                    }
                });
    }
}
