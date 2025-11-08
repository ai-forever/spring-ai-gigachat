package chat.giga.springai.api.auth.bearer;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatApiScope;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.ApiKey;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * Thread-safe manager for GigaChat OAuth 2.0 bearer token authentication.
 * Provides automatic token refresh with caching and expiration handling.
 *
 * <p><b>Thread Safety Guarantees:</b>
 * <ul>
 *   <li>Multiple threads can call {@link #getValue()} concurrently</li>
 *   <li>Only one thread performs token refresh at a time (others wait)</li>
 *   <li>Fast path (valid token) is lock-free using volatile read</li>
 *   <li>Compatible with both virtual threads (Project Loom) and platform threads</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * GigaChatApiProperties props = new GigaChatApiProperties();
 * props.setApiKey("your-api-key");
 * props.setScope(GigaChatApiScope.GIGACHAT_API_PERS);
 *
 * GigaChatBearerAuthApi authApi = new GigaChatBearerAuthApi(props);
 * String token = authApi.getValue(); // Returns cached or fresh token
 * }</pre>
 *
 * @see GigaChatOAuthClient
 * @see GigaChatBearerToken
 */
@Slf4j
public class GigaChatBearerAuthApi implements ApiKey {

    /**
     * Lock for synchronizing token refresh operations.
     * ReentrantLock is used instead of synchronized for better virtual thread support.
     */
    private final ReentrantLock reentrantTokenLock = new ReentrantLock();

    private final String apiKey;
    private final GigaChatApiScope scope;
    private final GigaChatOAuthClient authClient;

    /**
     * Cached token. Volatile ensures visibility across threads without locks on read path.
     * Null initially, non-null after first successful token request.
     */
    private volatile GigaChatBearerToken token;

    /**
     * Creates a new authentication API instance with default RestClient configuration.
     *
     * @param apiProperties configuration properties including API key, scope, and SSL settings
     * @throws IllegalArgumentException if apiProperties is null or contains invalid values
     */
    public GigaChatBearerAuthApi(final GigaChatApiProperties apiProperties) {
        this(apiProperties, RestClient.builder());
    }

    /**
     * Creates a new authentication API instance with custom RestClient.Builder.
     * Allows integration with observability (metrics, tracing, logging) by providing
     * a pre-configured builder with custom interceptors, filters, or observers.
     *
     * <p><b>Observability Integration Example:</b>
     * <pre>{@code
     * RestClient.Builder builder = RestClient.builder()
     *     .observationRegistry(observationRegistry);
     *
     * GigaChatBearerAuthApi authApi = new GigaChatBearerAuthApi(props, builder);
     * }</pre>
     *
     * @param apiProperties configuration properties including API key, scope, and SSL settings
     * @param builder RestClient.Builder with custom configuration (metrics, tracing, etc.)
     * @throws IllegalArgumentException if apiProperties or builder is null
     */
    public GigaChatBearerAuthApi(final GigaChatApiProperties apiProperties, final RestClient.Builder builder) {
        this(apiProperties, builder, null, null);
    }

    /**
     * Creates a new authentication API instance with custom RestClient.Builder and SSL configuration.
     * Combines observability integration with custom certificate handling.
     *
     * @param apiProperties configuration properties including API key, scope, and SSL settings
     * @param builder RestClient.Builder with custom configuration (metrics, tracing, etc.)
     * @param kmf custom KeyManagerFactory for client certificates, null to use defaults
     * @param tmf custom TrustManagerFactory for server certificate validation, null to use defaults
     * @throws IllegalArgumentException if apiProperties or builder is null
     */
    public GigaChatBearerAuthApi(
            final GigaChatApiProperties apiProperties,
            final RestClient.Builder builder,
            @Nullable final KeyManagerFactory kmf,
            @Nullable final TrustManagerFactory tmf) {
        this.apiKey = apiProperties.getApiKey();
        this.scope = apiProperties.getScope();
        this.authClient = new GigaChatOAuthClient(apiProperties, builder, kmf, tmf);
    }

    /**
     * Returns a valid bearer access token, refreshing it if necessary.
     *
     * <p><b>Algorithm:</b>
     * <ol>
     *   <li>Fast path: Check cached token without lock (volatile read)</li>
     *   <li>If valid (not null and not expired), return immediately</li>
     *   <li>If invalid, acquire lock and double-check (another thread might have refreshed)</li>
     *   <li>If still invalid, request new token from API</li>
     *   <li>Cache new token and return</li>
     * </ol>
     *
     * <p>This method is thread-safe and can be called concurrently from multiple threads,
     * including virtual threads. If the token needs refresh, only one thread will perform
     * the refresh while others wait and receive the new token.
     *
     * @return valid bearer token string (never null or empty)
     * @throws IllegalStateException if token request fails or returns invalid response
     */
    @Override
    @SuppressWarnings("NullableProblems")
    public String getValue() {
        // Fast path: check cached token without lock (volatile read)
        GigaChatBearerToken currentToken = this.token;
        if (currentToken != null && !currentToken.needsRefresh()) {
            return currentToken.accessToken();
        }
        // Slow path: token is missing or needs refresh
        reentrantTokenLock.lock();
        try {
            // Double-check: another thread might have refreshed while we waited for lock
            currentToken = this.token;
            if (currentToken == null || currentToken.needsRefresh()) {
                this.token = requestToken();
            }
            return this.token.accessToken();
        } finally {
            reentrantTokenLock.unlock();
        }
    }

    /**
     * Requests new token from API and validates response.
     * Must be called under lock.
     *
     * @return validated token
     * @throws IllegalStateException if response is invalid
     */
    private GigaChatBearerToken requestToken() {
        var tokenResponse = authClient.requestToken(this.apiKey, this.scope);
        Assert.notNull(tokenResponse, "Failed to get access token, response is null");

        var token = tokenResponse.accessToken();
        Assert.notNull(token, "Failed to get access token, access token is null in the response");

        var expiresAt = tokenResponse.expiresAt();
        Assert.notNull(expiresAt, "Failed to get access token, expiresAt in is null the response");

        return new GigaChatBearerToken(token, expiresAt);
    }
}
