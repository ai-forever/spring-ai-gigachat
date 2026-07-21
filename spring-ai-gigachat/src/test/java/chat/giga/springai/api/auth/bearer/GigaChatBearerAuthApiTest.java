package chat.giga.springai.api.auth.bearer;

import static chat.giga.springai.api.chat.GigaChatApi.USER_AGENT_SPRING_AI_GIGACHAT;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatApiScope;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureRestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import tools.jackson.databind.ObjectMapper;

@EnableWireMock({@ConfigureWireMock(name = "auth-api")})
@ExtendWith(SpringExtension.class)
@AutoConfigureRestClient
public abstract class GigaChatBearerAuthApiTest {

    @Autowired
    GigaChatBearerAuthApi authApi;

    @Autowired
    ObjectMapper objectMapper;

    @InjectWireMock("auth-api")
    WireMockServer mockServer;

    @BeforeEach
    void setUp() {
        setToken(null); // чистим токен, чтобы не аффектило другие тесты
    }

    @Test
    @SneakyThrows
    void testGetAccessToken_InitialCall() {
        // Arrange
        long expiresAt = System.currentTimeMillis() + 3600_000;
        GigaChatOAuthClient.GigaChatAccessTokenResponse tokenResponse =
                new GigaChatOAuthClient.GigaChatAccessTokenResponse("test-token", expiresAt);

        createStubForTokenRequest(tokenResponse);

        // Act
        String accessToken = authApi.getValue();

        // Assert
        assertEquals("test-token", accessToken);
        verify(postRequestedFor(urlEqualTo("/api/v2/oauth")));
    }

    @Test
    @SneakyThrows
    void testGetAccessToken_TokenExpired_Refresh() {
        // Arrange
        long expiresAt = System.currentTimeMillis() + 100;
        GigaChatBearerToken oldToken = new GigaChatBearerToken("old-token", expiresAt);
        setToken(oldToken);
        Thread.sleep(100);

        GigaChatOAuthClient.GigaChatAccessTokenResponse tokenResponse =
                new GigaChatOAuthClient.GigaChatAccessTokenResponse("new-token", System.currentTimeMillis() + 3600_000);

        createStubForTokenRequest(tokenResponse);

        // Act
        String accessToken = authApi.getValue();

        // Assert
        assertEquals("new-token", accessToken);
        verify(postRequestedFor(urlEqualTo("/api/v2/oauth")));
    }

    @Test
    void testGetAccessToken_CachedToken() {
        // Arrange
        long expiresAt = System.currentTimeMillis() + 3600_000;
        setToken(new GigaChatBearerToken("cached-token", expiresAt));

        // Act
        String accessToken = authApi.getValue();

        // Assert
        assertEquals("cached-token", accessToken);
        verify(exactly(0), postRequestedFor(urlEqualTo("/api/v2/oauth")));
    }

    @Test
    @DisplayName("Тест на успешное получение токена c 500 http-кодом")
    void testGetAccessToken_5xxErrorWithToken_expectSuccessfullyProceed() {
        // Arrange
        long expiresAt = System.currentTimeMillis() + 3600_000;
        GigaChatOAuthClient.GigaChatAccessTokenResponse tokenResponse =
                new GigaChatOAuthClient.GigaChatAccessTokenResponse("test-token", expiresAt);

        createStubForTokenRequest(tokenResponse, 500);

        // Act
        String accessToken = authApi.getValue();

        // Assert
        assertEquals("test-token", accessToken);
        verify(postRequestedFor(urlEqualTo("/api/v2/oauth")));
    }

    @Test
    @DisplayName("Тест на ошибку при получении HTML-ответа вместо JSON (502 Bad Gateway)")
    void testGetAccessToken_HtmlErrorResponse_throwsWithBody() {
        // Arrange
        String htmlBody = "<html><body><h1>502 Bad Gateway</h1></body></html>";
        mockServer.stubFor(post("/api/v2/oauth")
                .willReturn(aResponse()
                        .withStatus(502)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                        .withBody(htmlBody)));

        // Act & Assert
        var ex = assertThrows(Exception.class, () -> authApi.getValue());
        assertTrue(
                ex.getMessage().contains("non-JSON response"),
                "Exception message should mention non-JSON response, but was: " + ex.getMessage());
        assertTrue(
                ex.getMessage().contains("502"),
                "Exception message should contain status code, but was: " + ex.getMessage());
        assertTrue(
                ex.getMessage().contains("502 Bad Gateway"),
                "Exception message should contain response body, but was: " + ex.getMessage());
    }

    @Test
    @DisplayName("Тест на ошибку при получении JSON-ответа с невалидными credentials (401)")
    void testGetAccessToken_JsonErrorResponse_throwsWithNullToken() {
        // Arrange
        String jsonBody = "{\"code\":6,\"message\":\"credentials doesn't match db data\"}";
        mockServer.stubFor(post("/api/v2/oauth")
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonBody)));

        // Act & Assert — JSON has no access_token, should throw RestClientException
        var ex = assertThrows(RestClientException.class, () -> authApi.getValue());
        assertTrue(
                ex.getMessage().contains("unparseable JSON"),
                "Exception message should mention unparseable JSON, but was: " + ex.getMessage());
        assertTrue(
                ex.getMessage().contains("credentials doesn't match db data"),
                "Exception message should contain response body, but was: " + ex.getMessage());
    }

    public static Stream<Arguments> invalidTokenProvider() {
        return Stream.of(
                Arguments.of(new GigaChatOAuthClient.GigaChatAccessTokenResponse(null, System.currentTimeMillis())),
                Arguments.of(new GigaChatOAuthClient.GigaChatAccessTokenResponse("new-token", null)));
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("invalidTokenProvider")
    void testInvalidRequestToken_NullResponse(GigaChatOAuthClient.GigaChatAccessTokenResponse tokenResponse) {
        // Arrange
        createStubForTokenRequest(tokenResponse);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> authApi.getValue());
    }

    @SneakyThrows
    private void createStubForTokenRequest(GigaChatOAuthClient.GigaChatAccessTokenResponse response) {
        createStubForTokenRequest(response, 200);
    }

    @SneakyThrows
    private void createStubForTokenRequest(GigaChatOAuthClient.GigaChatAccessTokenResponse response, int httpStatus) {
        mockServer.stubFor(post("/api/v2/oauth")
                .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.AUTHORIZATION, WireMock.matching("Basic .+"))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .withHeader("RqUID", WireMock.matching(".+"))
                .withHeader(HttpHeaders.USER_AGENT, equalTo(USER_AGENT_SPRING_AI_GIGACHAT))
                .withFormParam("scope", equalToIgnoreCase("GIGACHAT_API_CORP"))
                .willReturn(jsonResponse(objectMapper.writeValueAsString(response), httpStatus)));
    }

    private void setToken(GigaChatBearerToken token) {
        ReflectionTestUtils.setField(authApi, "token", token);
    }

    @ContextConfiguration(classes = NewAuthPropertiesWithApiKeyTest.Config.class)
    public static class NewAuthPropertiesWithApiKeyTest extends GigaChatBearerAuthApiTest {
        @Configuration
        public static class Config {
            @Bean
            public GigaChatApiProperties gigaChatApiProperties(@Value("${wiremock.server.baseUrl}") String baseUrl) {
                return GigaChatApiProperties.builder()
                        .auth(GigaChatAuthProperties.builder()
                                .scope(GigaChatApiScope.GIGACHAT_API_CORP)
                                .bearer(GigaChatAuthProperties.Bearer.builder()
                                        .url(baseUrl + "/api/v2/oauth")
                                        .apiKey("apiKey")
                                        .build())
                                .build())
                        .build();
            }

            @Bean
            public SimpleGigaAuthToken simpleGigaAuthToken(GigaChatApiProperties apiProperties) {
                return new SimpleGigaAuthToken(apiProperties.getAuth().getApiKey());
            }

            @Bean
            public GigaChatBearerAuthApi gigaChatBearerAuthApi(
                    GigaChatApiProperties apiProperties,
                    RestClient.Builder restClientBuilder,
                    GigaAuthToken authToken) {
                return new GigaChatBearerAuthApi(new GigaChatOAuthClient(apiProperties, restClientBuilder, authToken));
            }
        }
    }

    @ContextConfiguration(classes = NewAuthPropertiesWithClientIdAndSecretTest.Config.class)
    public static class NewAuthPropertiesWithClientIdAndSecretTest extends GigaChatBearerAuthApiTest {
        @Configuration
        public static class Config {
            @Bean
            public GigaChatApiProperties gigaChatApiProperties(@Value("${wiremock.server.baseUrl}") String baseUrl) {
                return GigaChatApiProperties.builder()
                        .auth(GigaChatAuthProperties.builder()
                                .scope(GigaChatApiScope.GIGACHAT_API_CORP)
                                .bearer(GigaChatAuthProperties.Bearer.builder()
                                        .url(baseUrl + "/api/v2/oauth")
                                        .clientId("id")
                                        .clientSecret("secret")
                                        .build())
                                .build())
                        .build();
            }

            @Bean
            public SimpleGigaAuthToken simpleGigaAuthToken(GigaChatApiProperties apiProperties) {
                return new SimpleGigaAuthToken(apiProperties.getAuth().getApiKey());
            }

            @Bean
            public GigaChatBearerAuthApi gigaChatBearerAuthApi(
                    GigaChatApiProperties apiProperties,
                    RestClient.Builder restClientBuilder,
                    GigaAuthToken authToken) {
                return new GigaChatBearerAuthApi(new GigaChatOAuthClient(apiProperties, restClientBuilder, authToken));
            }
        }
    }
}
