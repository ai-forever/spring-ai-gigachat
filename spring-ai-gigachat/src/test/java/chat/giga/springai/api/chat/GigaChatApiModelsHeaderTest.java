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
