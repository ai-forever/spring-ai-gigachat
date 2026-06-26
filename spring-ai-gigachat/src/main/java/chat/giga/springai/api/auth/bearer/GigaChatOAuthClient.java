package chat.giga.springai.api.auth.bearer;

import static chat.giga.springai.api.chat.GigaChatApi.USER_AGENT_SPRING_AI_GIGACHAT;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.HttpClientUtils;
import chat.giga.springai.api.auth.GigaChatApiScope;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.ssl.SSLFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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

    private static final int MAX_LOGGED_BODY_LENGTH = 500;

    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();

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
     * ObjectMapper for deserializing token response bodies.
     */
    private final ObjectMapper objectMapper;

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
        this(apiProperties, builder, null, null, authToken, DEFAULT_OBJECT_MAPPER);
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
        this(apiProperties, builder, kmf, tmf, authToken, DEFAULT_OBJECT_MAPPER);
    }

    /**
     * Creates auth client with full configuration including custom ObjectMapper.
     *
     * @param apiProperties API configuration including auth URL, timeouts, SSL settings
     * @param builder RestClient.Builder with custom interceptors, filters, or observers
     * @param kmf custom KeyManagerFactory for client certificates, null to use defaults
     * @param tmf custom TrustManagerFactory for server validation, null to use defaults
     * @param authToken token to use for authentication
     * @param objectMapper ObjectMapper for deserializing error response bodies
     */
    public GigaChatOAuthClient(
            final GigaChatApiProperties apiProperties,
            final RestClient.Builder builder,
            @Nullable KeyManagerFactory kmf,
            @Nullable TrustManagerFactory tmf,
            final GigaAuthToken authToken,
            final ObjectMapper objectMapper) {
        GigaChatAuthProperties authProps = apiProperties.getAuth();
        GigaChatInternalProperties internalProps = apiProperties.getInternal();

        SSLFactory sslFactory = HttpClientUtils.buildSslFactory(kmf, tmf, authProps.isUnsafeSsl());

        var clientHttpRequestFactory = new JdkClientHttpRequestFactory(
                HttpClientUtils.buildHttpClient(sslFactory, internalProps.getConnectTimeout()));

        if (internalProps.getReadTimeout() != null) {
            clientHttpRequestFactory.setReadTimeout(internalProps.getReadTimeout());
        }

        String authUrl = authProps.getBearer().getUrl();
        this.restClient = builder.clone()
                .baseUrl(authUrl)
                .requestFactory(clientHttpRequestFactory)
                .build();

        this.authToken = authToken;
        this.scope = authProps.getScope();
        this.objectMapper = objectMapper;
    }

    /**
     * OAuth 2.0 token response from GigaChat API.
     *
     * @param accessToken bearer token string
     * @param expiresAt expiration timestamp in milliseconds since epoch
     */
    record GigaChatAccessTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_at") Long expiresAt) {}

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
                .exchange((request, response) -> {
                    byte[] bodyBytes = response.getBody().readAllBytes();
                    String body = new String(bodyBytes, StandardCharsets.UTF_8);
                    String truncatedBody = truncate(body, MAX_LOGGED_BODY_LENGTH);
                    String contentType = response.getHeaders().getContentType() != null
                            ? response.getHeaders().getContentType().toString()
                            : "unknown";

                    if (response.getStatusCode().isError() && !contentType.contains("json")) {
                        log.warn(
                                "Token request failed: status={}, contentType={}, body={}",
                                response.getStatusCode(),
                                contentType,
                                truncatedBody);
                        throw new RestClientException("Auth endpoint returned non-JSON response (status="
                                + response.getStatusCode() + ", contentType=" + contentType
                                + "): " + truncatedBody);
                    }

                    if (response.getStatusCode().isError()) {
                        log.warn(
                                "Token request returned error: status={}, contentType={}",
                                response.getStatusCode(),
                                contentType);
                    }

                    // Try to parse JSON even for error HTTP response codes (API may return valid token with 4xx/5xx)
                    GigaChatAccessTokenResponse tokenResponse;
                    try {
                        tokenResponse = objectMapper.readValue(bodyBytes, GigaChatAccessTokenResponse.class);
                    } catch (JacksonException e) {
                        log.warn(
                                "Token request failed: status={}, contentType={}, body={}",
                                response.getStatusCode(),
                                contentType,
                                truncatedBody,
                                e);
                        throw new RestClientException(
                                "Auth endpoint returned unparseable JSON (status=" + response.getStatusCode() + "): "
                                        + truncatedBody,
                                e);
                    }

                    if (tokenResponse == null || tokenResponse.accessToken() == null) {
                        log.warn(
                                "Token request failed: status={}, contentType={}, body={}",
                                response.getStatusCode(),
                                contentType,
                                truncatedBody);
                    }
                    return tokenResponse;
                });
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "<empty>";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
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
