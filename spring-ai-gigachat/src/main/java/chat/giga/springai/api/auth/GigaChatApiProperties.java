package chat.giga.springai.api.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GigaChatApiProperties {
    public static final String CONFIG_PREFIX = "spring.ai.gigachat";

    @Builder.Default
    private String baseUrl = "https://gigachat.devices.sberbank.ru/api/v1/";

    @Builder.Default
    private String authUrl = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";

    private String clientId;
    private String clientSecret;
    private GigaChatApiScope scope;

    private Resource clientCertificate;
    private Resource clientKey;
    private boolean unsafeSsl = false;

    public enum GigaChatApiScope {
        GIGACHAT_API_PERS,
        GIGACHAT_API_B2B,
        GIGACHAT_API_CORP
    }

    public boolean isBearer() {
        return clientId != null && clientSecret != null;
    }
}
