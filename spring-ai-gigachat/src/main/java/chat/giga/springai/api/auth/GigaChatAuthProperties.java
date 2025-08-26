package chat.giga.springai.api.auth;

import static org.springframework.util.StringUtils.hasText;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GigaChatAuthProperties {
    public static final String CONFIG_PREFIX = "spring.ai.gigachat.auth";

    private GigaChatApiScope scope;

    /**
     * Note: {@link Certificates#sslBundle}'s truststore has higher priority than this field.
     */
    private Resource caCerts;

    @Builder.Default
    private Bearer bearer = new Bearer();

    @Builder.Default
    private Certificates certs = new Certificates();

    public boolean isBearerAuth() {
        return bearer != null
                && (hasText(bearer.getApiKey()) || hasText(bearer.getClientId()) && hasText(bearer.getClientSecret()));
    }

    public boolean isCertsAuth() {
        return certs != null
                && (certs.getSslBundle() != null
                        || certs.getClientCertificate() != null && certs.getClientKey() != null);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bearer {
        @Builder.Default
        private String url = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
        /**
         * Authorization Key that can be obtained in your personal account on developers.sber.ru.
         * Actually the same as base64-encoded 'clientId:clientSecret' pair.
         * Note: {@link #apiKey} has higher priority over {@link #clientId} and {@link #clientSecret} if all present.
         */
        private String apiKey;

        private String clientId;
        private String clientSecret;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Certificates {
        /**
         * SSL bundle name.
         * Note: {@link #sslBundle} has higher priority than {@link #clientKey} and {@link #clientCertificate}.
         *
         * @see {@link org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration#sslBundleRegistry(ObjectProvider)}
         */
        private String sslBundle;
        /**
         * Prefer to use {@link #sslBundle}.
         */
        private Resource clientCertificate;
        /**
         * Prefer to use {@link #sslBundle}.
         */
        private Resource clientKey;
    }
}
