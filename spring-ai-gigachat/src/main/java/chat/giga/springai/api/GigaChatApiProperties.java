package chat.giga.springai.api;

import chat.giga.springai.api.auth.GigaChatApiScope;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import java.nio.charset.StandardCharsets;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GigaChatApiProperties {
    public static final String CONFIG_PREFIX = "spring.ai.gigachat";

    @Builder.Default
    private String baseUrl = "https://gigachat.devices.sberbank.ru/api/v1/";

    @Builder.Default
    private GigaChatAuthProperties auth = new GigaChatAuthProperties();

    /**
     * @deprecated since 1.0.4 for removal in 1.1.0 in favor of {@link GigaChatAuthProperties.Bearer#url}.
     */
    @Deprecated
    @Builder.Default
    private String authUrl = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    /**
     * @deprecated since 1.0.4 for removal in 1.1.0 in favor of {@link GigaChatAuthProperties.Bearer#clientId}.
     */
    @Deprecated
    private String clientId;
    /**
     * @deprecated since 1.0.4 for removal in 1.1.0 in favor of {@link GigaChatAuthProperties.Bearer#clientSecret}.
     */
    @Deprecated
    private String clientSecret;
    /**
     * @deprecated since 1.0.4 for removal in 1.1.0 in favor of {@link GigaChatAuthProperties#scope}.
     */
    @Deprecated
    private GigaChatApiScope scope;
    /**
     * @deprecated since 1.0.4 for removal in 1.1.0 in favor of {@link GigaChatAuthProperties.Certificates#clientCertificate}.
     */
    @Deprecated
    private Resource clientCertificate;
    /**
     * @deprecated since 1.0.4 for removal in 1.1.0 in favor of {@link GigaChatAuthProperties.Certificates#clientKey}.
     */
    @Deprecated
    private Resource clientKey;

    /**
     * @deprecated since 1.0.4 for removal in 1.1.0 in favor of {@link GigaChatAuthProperties#unsafeSsl}.
     */
    @Deprecated
    @Builder.Default
    private boolean unsafeSsl = false;

    public boolean isBearer() {
        return clientId != null && clientSecret != null || auth.isBearerAuth();
    }

    public String getApiKey() {
        if (auth.isBearerAuth()) {
            if (StringUtils.hasText(auth.getBearer().getApiKey())) {
                return auth.getBearer().getApiKey();
            }
            return HttpHeaders.encodeBasicAuth(
                    auth.getBearer().getClientId(), auth.getBearer().getClientSecret(), StandardCharsets.UTF_8);
        }
        if (isBearer()) {
            return HttpHeaders.encodeBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8);
        }
        return null;
    }

    public String getAuthUrl() {
        if (auth.isBearerAuth()) {
            return auth.getBearer().getUrl();
        }
        return authUrl;
    }

    public GigaChatApiScope getScope() {
        if (auth.getScope() != null) {
            return auth.getScope();
        }
        return scope;
    }

    public boolean isUnsafeSsl() {
        return unsafeSsl || auth.isUnsafeSsl();
    }
}
