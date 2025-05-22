package chat.giga.springai.api.auth.bearer;

import static chat.giga.springai.api.chat.GigaChatApi.USER_AGENT_SPRING_AI_GIGACHAT;

import chat.giga.springai.api.auth.GigaChatApiProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;
import nl.altindag.ssl.SSLFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

public class GigaChatBearerAuthApi {

    private final GigaChatApiProperties properties;
    private final RestClient restClient;
    private GigaChatBearerToken token;

    public GigaChatBearerAuthApi(GigaChatApiProperties properties) {
        this(properties, RestClient.builder());
    }

    public GigaChatBearerAuthApi(GigaChatApiProperties properties, RestClient.Builder builder) {
        final SSLFactory sslFactory = SSLFactory.builder()
                .withTrustingAllCertificatesWithoutValidation()
                .withUnsafeHostnameVerifier()
                .build();
        final HttpClient jdkHttpClient = HttpClient.newBuilder()
                .sslParameters(sslFactory.getSslParameters())
                .sslContext(sslFactory.getSslContext())
                .build();
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.getAuthUrl())
                .requestFactory(new JdkClientHttpRequestFactory(jdkHttpClient))
                .build();
    }

    private GigaChatBearerToken requestToken() {
        final ResponseEntity<GigaChatAccessTokenResponse> tokenResponseEntity = this.restClient
                .post()
                .headers(this::getAuthHeaders)
                .body("scope=" + properties.getScope())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(GigaChatAccessTokenResponse.class);

        Assert.notNull(tokenResponseEntity.getBody(), "Failed to get access token, response is null");
        final String token = tokenResponseEntity.getBody().accessToken();
        Assert.notNull(token, "Failed to get access token, access token is null in the response");
        final Long expiresAt = tokenResponseEntity.getBody().expiresAt();
        Assert.notNull(expiresAt, "Failed to get access token, expiresAt in is null the response");

        return new GigaChatBearerToken(token, expiresAt);
    }

    private void getAuthHeaders(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(properties.getClientId(), properties.getClientSecret());
        headers.set("RqUID", UUID.randomUUID().toString());
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT_SPRING_AI_GIGACHAT);
    }

    public String getAccessToken() {
        if (this.token == null || this.token.needsRefresh()) {
            this.token = this.requestToken();
        }
        return this.token.getAccessToken();
    }

    public record GigaChatAccessTokenResponse(
            @JsonProperty("access_token") String accessToken, @JsonProperty("expires_at") Long expiresAt) {}
}
