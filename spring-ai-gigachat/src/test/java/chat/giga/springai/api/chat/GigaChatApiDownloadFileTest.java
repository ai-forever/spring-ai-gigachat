package chat.giga.springai.api.chat;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;

/**
 * Юнит на реальный HTTP-контракт GET /files/{fileId}/content (GigaChatApi.downloadFile) —
 * ранее проверялся только замоканным на уровне GigaChatModel вызовом.
 */
class GigaChatApiDownloadFileTest {

    // реальный формат наблюдался в интеграции (pr:136): JPEG, начинается с FF D8 (SOI)
    private static final byte[] JPEG_FIXTURE = new byte[] {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', (byte) 0xFF, (byte) 0xD9
    };

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
    @DisplayName("downloadFile: 200 с байтами JPEG возвращает их как есть")
    void downloadFile_success_returnsBytes() {
        String fileId = "e5f8ce06-9742-48b9-b7f4-85e92acea7aa";
        server.stubFor(get(urlEqualTo("/files/" + fileId + "/content"))
                .willReturn(aResponse().withStatus(200).withBody(JPEG_FIXTURE)));

        GigaChatApi api = new GigaChatApi(properties());

        byte[] result = api.downloadFile(fileId);

        assertArrayEquals(JPEG_FIXTURE, result);
        server.verify(getRequestedFor(urlEqualTo("/files/" + fileId + "/content"))
                .withHeader("User-Agent", equalTo(GigaChatApi.USER_AGENT_SPRING_AI_GIGACHAT)));
    }

    @Test
    @DisplayName("downloadFile: 404 пробрасывается как исключение, а не молча возвращает null "
            + "(документирует реальное поведение defaultStatusHandler, вызываемого buildGeneration)")
    void downloadFile_404_throwsInsteadOfReturningNull() {
        String fileId = "does-not-exist";
        server.stubFor(get(urlEqualTo("/files/" + fileId + "/content"))
                .willReturn(aResponse().withStatus(404).withBody("not found")));

        GigaChatApi api = new GigaChatApi(properties());

        assertThrows(NonTransientAiException.class, () -> api.downloadFile(fileId));
    }

    private GigaChatApiProperties properties() {
        return GigaChatApiProperties.builder()
                .baseUrl("http://localhost:" + server.port())
                .auth(GigaChatAuthProperties.builder().build())
                .build();
    }
}
