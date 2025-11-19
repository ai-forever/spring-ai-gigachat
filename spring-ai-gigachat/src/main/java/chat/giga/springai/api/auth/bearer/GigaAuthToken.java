package chat.giga.springai.api.auth.bearer;

/**
 * Provider for GigChat token
 */
public interface GigaAuthToken {
    /**
     * Get GigaChat API token
     * @return the token value
     */
    String getValue();
}
