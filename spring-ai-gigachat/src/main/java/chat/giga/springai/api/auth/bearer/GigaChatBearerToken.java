package chat.giga.springai.api.auth.bearer;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class GigaChatBearerToken {

    private final String accessToken;
    private final Long expiresAt;
    private final Long refreshTime;

    public GigaChatBearerToken(String bearerToken, Long expiresAt) {
        long delta = BigDecimal.valueOf(expiresAt - System.currentTimeMillis())
                .multiply(BigDecimal.valueOf(0.95d))
                .longValue();
        this.refreshTime = System.currentTimeMillis() + delta;
        this.accessToken = bearerToken;
        this.expiresAt = expiresAt;
    }

    public boolean needsRefresh() {
        return System.currentTimeMillis() >= this.refreshTime;
    }
}
