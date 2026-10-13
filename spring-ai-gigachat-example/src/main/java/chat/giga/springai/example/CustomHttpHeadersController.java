/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
                        .httpHeaders(Map.of("options-header", "options-value")))
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
