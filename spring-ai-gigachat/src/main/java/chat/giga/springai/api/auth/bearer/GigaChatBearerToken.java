package chat.giga.springai.api.auth.bearer;

/**
 * Immutable bearer token holder with automatic refresh time calculation.
 * Thread-safe by design - records are inherently immutable.
 *
 * <p>The token refresh time is calculated as 95% of the token lifetime to ensure
 * refresh happens before expiration, accounting for network delays and processing time.
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * long expiresAt = System.currentTimeMillis() + 3600_000; // 1 hour from now
 * GigaChatBearerToken token = new GigaChatBearerToken("eyJ...", expiresAt);
 *
 * if (token.needsRefresh()) {
 *     // Request new token
 * }
 * }</pre>
 *
 * @param accessToken the bearer token string (never null or blank)
 * @param expiresAt expiration timestamp in milliseconds since epoch (must be in future)
 * @param refreshTime refresh timestamp in milliseconds since epoch (95% of token lifetime)
 */
public record GigaChatBearerToken(String accessToken, long expiresAt, long refreshTime) {

    /**
     * Refresh factor: refresh at 95% of token lifetime.
     * Using integer arithmetic: 95/100 = 0.95
     */
    private static final int REFRESH_FACTOR_NUMERATOR = 95;

    private static final int REFRESH_FACTOR_DENOMINATOR = 100;

    /**
     * Compact constructor with validation and refresh time calculation.
     *
     * <p>Validates that:
     * <ul>
     *   <li>Access token is not null or blank</li>
     *   <li>Expiration time is in the future</li>
     * </ul>
     *
     * <p>Automatically calculates refresh time as 95% of token lifetime.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public GigaChatBearerToken {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token cannot be null or blank");
        }

        final long currentTime = System.currentTimeMillis();
        if (expiresAt <= currentTime) {
            throw new IllegalArgumentException("Token expiration time must be in the future. ExpiresAt: " + expiresAt
                    + ", currentTime: " + currentTime);
        }

        refreshTime = calculateRefreshTime(currentTime, expiresAt);
    }

    /**
     * Public constructor for external use.
     * Delegates to compact constructor which calculates refreshTime automatically.
     *
     * @param accessToken the bearer token string
     * @param expiresAt expiration timestamp in milliseconds since epoch
     * @throws IllegalArgumentException if validation fails
     */
    public GigaChatBearerToken(String accessToken, long expiresAt) {
        this(accessToken, expiresAt, 0);
    }

    /**
     * Calculates the refresh time as 95% of the token lifetime.
     * Uses integer arithmetic to avoid floating point precision issues.
     *
     * <p><b>Algorithm:</b>
     * <pre>
     * lifetime = expiresAt - currentTime
     * refreshDelta = (lifetime * 95) / 100
     * refreshTime = currentTime + refreshDelta
     * </pre>
     *
     * <p><b>Example:</b> For a 1-hour token (3600 seconds):
     * <ul>
     *   <li>lifetime = 3600000 ms</li>
     *   <li>refreshDelta = 3420000 ms (57 minutes)</li>
     *   <li>Token will refresh after 57 minutes, 3 minutes before expiration</li>
     * </ul>
     *
     * @param currentTime current timestamp in milliseconds
     * @param expiresAt expiration timestamp in milliseconds
     * @return refresh timestamp in milliseconds
     */
    private static long calculateRefreshTime(final long currentTime, final long expiresAt) {
        final long lifetime = expiresAt - currentTime;
        final long refreshDelta = (lifetime * REFRESH_FACTOR_NUMERATOR) / REFRESH_FACTOR_DENOMINATOR;
        return currentTime + refreshDelta;
    }

    /**
     * Checks if the token needs to be refreshed.
     * Returns true when current time reaches or exceeds the calculated refresh time.
     *
     * <p><b>Thread Safety:</b> This method is thread-safe as it only reads final fields
     * and calls {@link System#currentTimeMillis()}.
     *
     * @return true if current time is at or past the refresh time
     */
    public boolean needsRefresh() {
        return System.currentTimeMillis() >= refreshTime;
    }

    /**
     * Checks if the token is expired.
     * Returns true when current time reaches or exceeds the expiration time.
     *
     * <p><b>Thread Safety:</b> This method is thread-safe as it only reads final fields
     * and calls {@link System#currentTimeMillis()}.
     *
     * @return true if current time is at or past the expiration time
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    /**
     * Custom toString with masked token for security.
     * Shows only the last 4 characters of the token to aid debugging while
     * preventing accidental token exposure in logs.
     *
     * @return string representation with masked token
     */
    @Override
    @SuppressWarnings("NullableProblems")
    public String toString() {
        final String maskedToken =
                accessToken.length() > 4 ? "***" + accessToken.substring(accessToken.length() - 4) : "***";
        return "GigaChatBearerToken[" + "accessToken="
                + maskedToken + ", expiresAt="
                + expiresAt + ", refreshTime="
                + refreshTime + ", needsRefresh="
                + needsRefresh() + ']';
    }
}
