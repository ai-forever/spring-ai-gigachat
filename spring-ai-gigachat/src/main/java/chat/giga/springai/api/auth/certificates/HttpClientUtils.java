package chat.giga.springai.api.auth.certificates;

import chat.giga.springai.api.auth.GigaChatApiProperties;
import java.net.http.HttpClient;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.pem.util.PemUtils;

@UtilityClass
@Slf4j
public class HttpClientUtils {

    @SneakyThrows
    public static HttpClient build(GigaChatApiProperties properties) {
        var httpClientBuilder = HttpClient.newBuilder();
        if (properties.getClientCertificate() != null && properties.getClientKey() != null) {
            final SSLFactory sslFactory = SSLFactory.builder()
                    .withTrustingAllCertificatesWithoutValidation()
                    .withUnsafeHostnameVerifier()
                    .withLoggingTrustMaterial()
                    .withLoggingIdentityMaterial()
                    .withIdentityMaterial(PemUtils.loadIdentityMaterial(
                            properties.getClientCertificate().getInputStream(),
                            properties.getClientKey().getInputStream()))
                    .build();

            httpClientBuilder.sslParameters(sslFactory.getSslParameters()).sslContext(sslFactory.getSslContext());
        } else if (properties.isUnsafeSsl()) {
            log.warn("Unsafe HTTP client is used");
            final SSLFactory sslFactory = SSLFactory.builder()
                    .withTrustingAllCertificatesWithoutValidation()
                    .withUnsafeHostnameVerifier()
                    .build();
            httpClientBuilder.sslParameters(sslFactory.getSslParameters()).sslContext(sslFactory.getSslContext());
        }
        return httpClientBuilder.build();
    }
}
