package chat.giga.springai.api.auth.bearer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GigaChatOAuthClientTest {

    @Test
    @DisplayName("#06: requestToken() при scope==null бросает понятное исключение, "
            + "а не отправляет строку \"scope=null\"")
    void requestToken_nullScope_throwsClearError() {
        // scope не задан (null) — но заданы client-id/secret, так что путь bearer активен
        GigaChatApiProperties properties = GigaChatApiProperties.builder()
                .auth(GigaChatAuthProperties.builder()
                        .unsafeSsl(true)
                        .bearer(GigaChatAuthProperties.Bearer.builder()
                                .clientId("id")
                                .clientSecret("secret")
                                .build())
                        .build())
                .build();

        GigaChatOAuthClient client = new GigaChatOAuthClient(
                properties,
                RestClient.builder(),
                new SimpleGigaAuthToken(properties.getAuth().getApiKey()));

        assertThatThrownBy(client::requestToken)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope");
    }
}
