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

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GigaChatApiModelsHeaderTest {

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
    @DisplayName("#05: models() отправляет заголовок User-Agent (как и остальные эндпоинты)")
    void models_sendsUserAgentHeader() {
        server.stubFor(get(urlEqualTo("/models")).willReturn(okJson("{\"data\":[]}")));

        // без креденшелов -> NoopGigaAuthToken, без обращения к токен-эндпоинту
        GigaChatApiProperties properties = GigaChatApiProperties.builder()
                .baseUrl("http://localhost:" + server.port())
                .auth(GigaChatAuthProperties.builder().build())
                .build();

        new GigaChatApi(properties).models();

        server.verify(getRequestedFor(urlEqualTo("/models"))
                .withHeader("User-Agent", equalTo(GigaChatApi.USER_AGENT_SPRING_AI_GIGACHAT)));
    }
}
