package chat.giga.springai.api.auth.bearer;

import static chat.giga.springai.api.chat.GigaChatApi.USER_AGENT_SPRING_AI_GIGACHAT;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.HttpClientUtils;
import chat.giga.springai.api.auth.GigaChatApiScope;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.ssl.SSLFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

@Slf4j
public class GigaChatBearerAuthApi {
    private final String apiKey;
    private final GigaChatApiScope scope;
    private final RestClient restClient;
    private GigaChatBearerToken token;

    public GigaChatBearerAuthApi(GigaChatApiProperties apiProperties) {
        this(apiProperties, RestClient.builder());
    }

    public GigaChatBearerAuthApi(GigaChatApiProperties apiProperties, RestClient.Builder builder) {
        this(apiProperties, builder, null, null);
    }

    public GigaChatBearerAuthApi(
            GigaChatApiProperties apiProperties,
            RestClient.Builder builder,
            @Nullable KeyManagerFactory kmf,
            @Nullable TrustManagerFactory tmf) {
        this.apiKey = apiProperties.getApiKey();
        this.scope = apiProperties.getScope();
        boolean isUnsafeSsl = apiProperties.isUnsafeSsl();
        SSLFactory sslFactory = HttpClientUtils.buildSslFactory(kmf, tmf, isUnsafeSsl);
        String authUrl = apiProperties.getAuthUrl();
        GigaChatInternalProperties internalProps = apiProperties.getInternal();
        var clientHttpRequestFactory = new JdkClientHttpRequestFactory(
                HttpClientUtils.buildHttpClient(sslFactory, internalProps.getConnectTimeout()));
        if (internalProps.getReadTimeout() != null) {
            clientHttpRequestFactory.setReadTimeout(internalProps.getReadTimeout());
        }
        this.restClient = builder.baseUrl(authUrl)
                .requestFactory(clientHttpRequestFactory)
                .build();
    }

    public record GigaChatAccessTokenResponse(
            @JsonProperty("access_token") String accessToken, @JsonProperty("expires_at") Long expiresAt) {}

    private GigaChatBearerToken requestToken() {
        GigaChatAccessTokenResponse tokenResponse = this.restClient
                .post()
                .headers(this::getAuthHeaders)
                .body("scope=" + this.scope)
                .retrieve()
                .onStatus(HttpStatusCode::isError, ((request, response) -> {
                    log.debug("Auth token request failed with status code: {}", response.getStatusCode());
                    // Игнорируем 4xx/5xx статусы, т.к. access token все равно может быть в теле ответа
                }))
                .body(GigaChatAccessTokenResponse.class);
        Assert.notNull(tokenResponse, "Failed to get access token, response is null");
        final String token = tokenResponse.accessToken();
        Assert.notNull(token, "Failed to get access token, access token is null in the response");
        final Long expiresAt = tokenResponse.expiresAt();
        Assert.notNull(expiresAt, "Failed to get access token, expiresAt in is null the response");

        return new GigaChatBearerToken(token, expiresAt);
    }

    private void getAuthHeaders(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(this.apiKey);
        headers.set("RqUID", UUID.randomUUID().toString());
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT_SPRING_AI_GIGACHAT);
    }

    public String getAccessToken() {
        if (this.token == null || this.token.needsRefresh()) {
            this.token = this.requestToken();
        }
        return this.token.getAccessToken();
    }
}
