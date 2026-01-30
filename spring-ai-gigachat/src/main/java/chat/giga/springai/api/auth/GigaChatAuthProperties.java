package chat.giga.springai.api.auth;

import static org.springframework.util.StringUtils.hasText;

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
public class GigaChatAuthProperties {
    public static final String CONFIG_PREFIX = "spring.ai.gigachat.auth";

    private GigaChatApiScope scope;

    @Builder.Default
    private boolean unsafeSsl = false;

    @Builder.Default
    private Bearer bearer = new Bearer();

    @Builder.Default
    private Certificates certs = new Certificates();

    /**
     * Checks whether properties set up for using bearer authorization or not.
     * @return {@code true} - if apiKey or both clientId/clientSecret has text
     *         <p>{@code false} - otherwise
     */
    public boolean isBearerAuth() {
        return bearer != null
                && (hasText(bearer.getApiKey()) || hasText(bearer.getClientId()) && hasText(bearer.getClientSecret()));
    }

    /**
     * Checks whether properties set up for using TLS-certificates based authorization or not.
     * @return {@code true} - if sslBundle or both public certificate and private key are not null.
     *         <p>{@code false} - otherwise
     */
    public boolean isCertsAuth() {
        return certs != null
                && (certs.getSslBundle() != null || certs.getCertificate() != null && certs.getPrivateKey() != null);
    }

    /**
     * Provides api-key for Bearer authorization.
     *
     * @return api-key used for Bearer authorization
     *         <p>{@code null} - if authorization type is not Bearer
     */
    public String getApiKey() {
        if (this.isBearerAuth()) {
            return this.getBearer().buildApiKey();
        }
        return null;
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
         * Actually the same as base64-encoded '{@link #clientId}:{@link #clientSecret}' pair.
         * Note: {@link #apiKey} has higher priority over {@link #clientId} and {@link #clientSecret} if all present.
         */
        private String apiKey;

        /**
         * Note: {@link Bearer#apiKey} has higher priority than this field.
         */
        private String clientId;

        /**
         * Note: {@link Bearer#apiKey} has higher priority than this field.
         */
        private String clientSecret;

        /**
         * Returns {@link #apiKey} if present.
         * Otherwise, build api key as base64-encoded '{@link #clientId}:{@link #clientSecret}' pair.
         * @return base64-encoded api key or {@code null} if neither apiKey nor clientId/clientSecret are present
         */
        public String buildApiKey() {
            if (StringUtils.hasText(this.apiKey)) {
                return this.apiKey;
            }
            if (StringUtils.hasText(this.clientId) && StringUtils.hasText(this.clientSecret)) {
                return HttpHeaders.encodeBasicAuth(this.clientId, this.clientSecret, StandardCharsets.UTF_8);
            }
            return null;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Certificates {
        /**
         * SSL bundle name.
         * Note: {@link #sslBundle} has higher priority than {@link #privateKey} and {@link #certificate}.
         *
         * @see {@link org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration#sslBundleRegistry(ObjectProvider)}
         */
        private String sslBundle;

        /**
         * Note: {@link Certificates#sslBundle}'s keystore has higher priority than this field.
         */
        private Resource certificate;

        /**
         * Note: {@link Certificates#sslBundle}'s keystore has higher priority than this field.
         */
        private Resource privateKey;

        /**
         * Note: {@link Certificates#sslBundle}'s truststore has higher priority than this field.
         */
        private Resource caCerts;
    }
}
