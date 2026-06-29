package chat.giga.springai.advisor;

import chat.giga.springai.GigaChatOptions;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Advisor, который перекладывает sessionId из контекста в запрос.
 */
public class GigaChatCachingAdvisor implements CallAdvisor, StreamAdvisor {
    public static final String X_SESSION_ID = "X-Session-ID";

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        chatClientRequest = fillOptions(chatClientRequest);

        return callAdvisorChain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        chatClientRequest = fillOptions(chatClientRequest);

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

    private ChatClientRequest fillOptions(ChatClientRequest request) {
        // защитный instanceof вместо жёсткого cast: если опции не GigaChatOptions, просто пропускаем
        if (!(request.prompt().getOptions() instanceof GigaChatOptions chatOptions)) {
            return request;
        }

        String sessionId = (String) request.context().get(X_SESSION_ID);
        if (sessionId == null) {
            return request;
        }

        // httpHeaders теперь immutable (Map.copyOf в build()) — не мутируем живую мапу, а строим новые
        // опции через mutate()/build() (как GigaChatHttpHeadersAdvisor). Сеттер мержит заголовок поверх.
        GigaChatOptions newOptions = chatOptions
                .mutate()
                .httpHeaders(Map.of(X_SESSION_ID, sessionId))
                .build();
        Prompt newPrompt = request.prompt().mutate().chatOptions(newOptions).build();

        return request.mutate().prompt(newPrompt).build();
    }
}
