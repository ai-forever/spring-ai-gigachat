package chat.giga.springai.advisor;

import chat.giga.springai.GigaChatOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

/**
 * Advisor, который перекладывает sessionId из контекста в запрос.
 */
public class GigaChatHttpHeadersAdvisor implements CallAdvisor, StreamAdvisor {
    private static final String HTTP_HEADER_PREFIX = "http_header_";

    public static String httpHeader(String headerName) {
        Assert.hasText(headerName, "headerName must not be empty");
        return HTTP_HEADER_PREFIX + headerName;
    }

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
        return -1;
    }

    private ChatClientRequest fillOptions(ChatClientRequest request) {

        if (!(request.prompt().getOptions() instanceof GigaChatOptions chatOptions)) {
            return request;
        }

        Map<String, String> httpHeaders = new HashMap<>();

        if (!CollectionUtils.isEmpty(chatOptions.getHttpHeaders())) {
            httpHeaders.putAll(chatOptions.getHttpHeaders());
        }

        request.context().keySet().stream()
                .filter(key -> key.startsWith(HTTP_HEADER_PREFIX))
                .forEach(key -> httpHeaders.put(
                        key.substring(HTTP_HEADER_PREFIX.length()), getHeaderValue(request.context(), key)));

        if (httpHeaders.isEmpty()) {
            return request;
        }

        GigaChatOptions newOptions =
                chatOptions.mutate().httpHeaders(httpHeaders).build();

        Prompt newPrompt = request.prompt().mutate().chatOptions(newOptions).build();

        return request.mutate().prompt(newPrompt).build();
    }

    private String getHeaderValue(Map<String, Object> context, String key) {
        Object value = context.get(key);
        if (value instanceof Supplier valueSupplier) {
            return (String) valueSupplier.get();
        }
        return (String) value;
    }
}
