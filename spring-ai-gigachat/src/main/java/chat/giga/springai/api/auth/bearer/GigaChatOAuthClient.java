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
import org.springframework.web.client.RestClient;

/**
 * HTTP client for GigaChat OAuth 2.0 token endpoint.
 * Handles token request/response logic and HTTP communication.
 *
 * <p>This client is stateless and thread-safe.
 *
 * <p>Uses JDK HttpClient which properly handles virtual threads.
 * All blocking I/O operations are virtual-thread friendly.
 */
@Slf4j
public class GigaChatOAuthClient {

    /**
     * HTTP Client for OAuth interaction with proper ssl and observability.
     */
    private final RestClient restClient;

    /**
     * Scope requested scope for the access token.
     */
    private final GigaChatApiScope scope;

    /**
     * GigaAuthToken OAuth client credentials (Base64-encoded for Basic Auth).
     */
    private final GigaAuthToken authToken;

    /**
     * Creates auth client with default RestClient.Builder and SSL configuration.
     *
     * @param apiProperties API configuration including auth URL, timeouts, SSL settings
     * @param builder RestClient.Builder with custom interceptors, filters, or observers
     * @param authToken token to use for authentication
     */
    public GigaChatOAuthClient(
            final GigaChatApiProperties apiProperties,
            final RestClient.Builder builder,
            final GigaAuthToken authToken) {
        this(apiProperties, builder, null, null, authToken);
    }

    /**
     * Creates auth client with default RestClient.Builder and SSL configuration.
     *
     * @param apiProperties API configuration including auth URL, timeouts, SSL settings
     * @param builder RestClient.Builder with custom interceptors, filters, or observers
     * @param kmf custom KeyManagerFactory for client certificates, null to use defaults
     * @param tmf custom TrustManagerFactory for server validation, null to use defaults
     * @param authToken token to use for authentication
     */
    public GigaChatOAuthClient(
            final GigaChatApiProperties apiProperties,
            final RestClient.Builder builder,
            @Nullable KeyManagerFactory kmf,
            @Nullable TrustManagerFactory tmf,
            final GigaAuthToken authToken) {
        boolean isUnsafeSsl = apiProperties.isUnsafeSsl();
        SSLFactory sslFactory = HttpClientUtils.buildSslFactory(kmf, tmf, isUnsafeSsl);

        String authUrl = apiProperties.getAuthUrl();
        GigaChatInternalProperties internalProps = apiProperties.getInternal();

        var clientHttpRequestFactory = new JdkClientHttpRequestFactory(
                HttpClientUtils.buildHttpClient(sslFactory, internalProps.getConnectTimeout()));

        if (internalProps.getReadTimeout() != null) {
            clientHttpRequestFactory.setReadTimeout(internalProps.getReadTimeout());
        }

        this.restClient = builder.clone()
                .baseUrl(authUrl)
                .requestFactory(clientHttpRequestFactory)
                .build();

        this.authToken = authToken;
        this.scope = apiProperties.getScope();
    }

    /**
     * OAuth 2.0 token response from GigaChat API.
     *
     * @param accessToken bearer token string
     * @param expiresAt expiration timestamp in milliseconds since epoch
     */
    record GigaChatAccessTokenResponse(
            @JsonProperty("access_token") String accessToken, @JsonProperty("expires_at") Long expiresAt) {}

    /**
     * Requests a new access token from the GigaChat OAuth 2.0 endpoint.
     *
     * <p><b>Thread Safety:</b> This method is thread-safe and can be called concurrently.
     * Each call performs an independent HTTP request.
     *
     * <p><b>Error Handling:</b> Returns partial response even on HTTP errors (4xx/5xx)
     * if the response body contains valid token data. This handles cases where the API
     * returns tokens with non-2xx status codes. Validation is done by the caller.
     *
     * @return token response, may be null if request completely fails
     * @throws org.springframework.web.client.RestClientException if HTTP request fails without valid response body
     */
    GigaChatAccessTokenResponse requestToken() {
        return this.restClient
                .post()
                .headers(headers -> buildAuthHeaders(headers, this.authToken))
                .body("scope=" + this.scope)
                .retrieve()
                .onStatus(HttpStatusCode::isError, ((request, response) -> {
                    log.warn(
                            "Token request returned error status: {}, but attempting to parse response body",
                            response.getStatusCode());
                    // Allow parsing response body even on error status
                    // API may return valid token with 4xx/5xx status
                }))
                .body(GigaChatAccessTokenResponse.class);
    }

    /**
     * Builds HTTP headers for OAuth 2.0 token request.
     *
     * @param headers target HttpHeaders object
     * @param authToken OAuth client credentials
     */
    private void buildAuthHeaders(HttpHeaders headers, GigaAuthToken authToken) {
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(authToken.getValue());
        headers.set("RqUID", UUID.randomUUID().toString());
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT_SPRING_AI_GIGACHAT);
    }
}
