package chat.giga.springai.api.auth.bearer;

import org.springframework.util.Assert;

/**
 * Simple implementation of {@link GigaAuthToken}
 * @param value auth-token value
 */
public record SimpleGigaAuthToken(String value) implements GigaAuthToken {
    public SimpleGigaAuthToken {
        Assert.notNull(value, "GigaChat API auth-token value must not be null");
    }

    /** {@inheritDoc} */
    public String getValue() {
        return value();
    }

    public String toString() {
        return "SimpleGigaAuthToken{value='***'}";
    }
}
