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
 * Advisor, который перекладывает HTTP-заголовки из контекста запроса в {@link GigaChatOptions#getHttpHeaders()}.
 * Берёт записи контекста с префиксом {@code http_header_} (см. {@link #httpHeader(String)}), снимает префикс и
 * добавляет их к httpHeaders опций. Значение может быть {@link java.util.function.Supplier} (ленивое вычисление).
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
                .forEach(key -> {
                    String value = getHeaderValue(request.context(), key);
                    // value может быть null (Supplier ещё не готов / в context положили null): immutable
                    // build() (Map.copyOf) такой заголовок не примет и упал бы NPE — молча пропускаем.
                    if (value != null) {
                        httpHeaders.put(key.substring(HTTP_HEADER_PREFIX.length()), value);
                    }
                });

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
