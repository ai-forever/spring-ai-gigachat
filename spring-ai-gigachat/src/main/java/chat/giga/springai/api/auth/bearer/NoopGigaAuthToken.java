package chat.giga.springai.api.auth.bearer;

/**
 * Empty implementation of GigaAuthToken (for certificate-based authentication)
 */
public class NoopGigaAuthToken implements GigaAuthToken {
    /** {@inheritDoc} */
    public String getValue() {
        return "";
    }
}
