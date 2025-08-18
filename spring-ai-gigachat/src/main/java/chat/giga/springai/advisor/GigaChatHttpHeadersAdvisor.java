package chat.giga.springai.advisor;

import chat.giga.springai.GigaChatOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
        return -1;
    }

    private void fillOptions(ChatClientRequest chatClientRequest) {
        Optional.of(chatClientRequest.prompt())
                .map(Prompt::getOptions)
                .map(GigaChatOptions.class::cast)
                .ifPresent(it -> {
                    Map<String, String> httpHeaders = new HashMap<>();
                    if (!CollectionUtils.isEmpty(it.getHttpHeaders())) {
                        httpHeaders.putAll(it.getHttpHeaders());
                    }
                    chatClientRequest.context().keySet().stream()
                            .filter(key -> key.startsWith(HTTP_HEADER_PREFIX))
                            .forEach(key -> httpHeaders.put(
                                    key.substring(HTTP_HEADER_PREFIX.length()),
                                    getHeaderValue(chatClientRequest.context(), key)));
                    if (!httpHeaders.isEmpty()) {
                        it.setHttpHeaders(httpHeaders);
                    }
                });
    }

    private String getHeaderValue(Map<String, Object> context, String key) {
        Object value = context.get(key);
        if (value instanceof Supplier valueSupplier) {
            return (String) valueSupplier.get();
        }
        return (String) value;
    }
}
