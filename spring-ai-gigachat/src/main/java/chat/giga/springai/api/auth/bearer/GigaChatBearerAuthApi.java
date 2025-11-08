package chat.giga.springai.api.auth.bearer;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.ai.model.ApiKey;
import org.springframework.util.Assert;

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
 * @see GigaChatOAuthClient
 * @see GigaChatBearerToken
 */
public class GigaChatBearerAuthApi implements ApiKey {

    /**
     * Lock for synchronizing token refresh operations.
     * ReentrantLock is used instead of synchronized for better virtual thread support.
     */
    private final ReentrantLock reentrantTokenLock;

    /**
     * HTTP client for GigaChat OAuth 2.0 token endpoint.
     * Handles token request/response logic and HTTP communication.
     */
    private final GigaChatOAuthClient authClient;

    /**
     * Cached token. Volatile ensures visibility across threads without locks on read path.
     * Null initially, non-null after first successful token request.
     */
    private volatile GigaChatBearerToken token;

    /**
     * Creates a new authentication API instance with GigaChatOAuthClient build by custom RestClient.Builder
     * and SSL configuration.
     *
     * @param gigaChatOAuthClient implementation for OAuth
     * @throws IllegalArgumentException if apiProperties or builder is null
     */
    public GigaChatBearerAuthApi(final GigaChatOAuthClient gigaChatOAuthClient) {
        this.authClient = gigaChatOAuthClient;
        this.reentrantTokenLock = new ReentrantLock();
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
        var tokenResponse = authClient.requestToken();
        Assert.notNull(tokenResponse, "Failed to get access token, response is null");

        var token = tokenResponse.accessToken();
        Assert.notNull(token, "Failed to get access token, access token is null in the response");

        var expiresAt = tokenResponse.expiresAt();
        Assert.notNull(expiresAt, "Failed to get access token, expiresAt in is null the response");

        return new GigaChatBearerToken(token, expiresAt);
    }
}
