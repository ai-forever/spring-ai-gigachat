package chat.giga.springai.example;

import static chat.giga.springai.advisor.GigaChatHttpHeadersAdvisor.httpHeader;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import chat.giga.springai.GigaChatOptions;
import chat.giga.springai.advisor.GigaChatHttpHeadersAdvisor;
import chat.giga.springai.api.chat.GigaChatApi;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestController
@RequestMapping(value = "", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class CustomHttpHeadersController {
    private final ChatClient chatClient;

    public CustomHttpHeadersController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor(), new GigaChatHttpHeadersAdvisor())
                .defaultOptions(GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                        // можно задать статические заголовки через Options
                        .httpHeaders(Map.of("options-header", "options-value"))
                        .build())
                .build();
    }

    @PostMapping("/sendWithHeaders")
    public String sendWithHeaders(@RequestBody String userMessage) {
        return chatClient
                .prompt(userMessage)
                .advisors(a ->
                        // можно отправлять статические заголовки
                        a.param(httpHeader("x-client-id"), "spring-ai-gigachat-example")
                                // или динамически вычисляемые
                                .param(httpHeader("x-request-id"), requestIdSupplier()))
                .call()
                .content();
    }

    private Supplier<String> requestIdSupplier() {
        return () -> {
            ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String reqeustId = null;
            if (sra != null) {
                reqeustId = sra.getRequest().getHeader("x-request-id");
            }
            if (reqeustId == null) {
                reqeustId = UUID.randomUUID().toString();
            }
            return reqeustId;
        };
    }
}
