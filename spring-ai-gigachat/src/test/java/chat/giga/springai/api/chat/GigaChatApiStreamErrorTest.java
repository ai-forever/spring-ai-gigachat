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

package chat.giga.springai.api.chat;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

/**
 * #05: потоковый chatCompletionStream обязан пробрасывать HTTP-ошибки (4xx/5xx), а не глотать их
 * (как DeepSeek/Mistral через .retrieve()). Раньше exchangeToFlux на ошибке отдавал пустой поток.
 */
class GigaChatApiStreamErrorTest {

    private WireMockServer server;

    @BeforeEach
    void start() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    @DisplayName("#05: 500 в стриме пробрасывается как WebClientResponseException, а не пустой поток")
    void streamErrorIsPropagated() {
        server.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        GigaChatApiProperties properties = GigaChatApiProperties.builder()
                .baseUrl("http://localhost:" + server.port())
                .auth(GigaChatAuthProperties.builder().build())
                .build();
        GigaChatApi api = new GigaChatApi(properties);

        CompletionRequest req = CompletionRequest.builder().model("GigaChat-2").stream(true)
                .messages(List.of(new CompletionRequest.Message(CompletionRequest.Role.user, "hi")))
                .build();

        StepVerifier.create(api.chatCompletionStream(req))
                .expectError(WebClientResponseException.class)
                .verify();
    }
}
