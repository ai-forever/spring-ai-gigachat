package chat.giga.springai.api;

import java.net.http.HttpClient;
import java.time.Duration;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.ssl.SSLFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

@UtilityClass
@Slf4j
public class HttpClientUtils {

    public static HttpClient buildHttpClient(SSLFactory sslFactory, Duration connectTimeout) {
        Assert.notNull(sslFactory, "sslFactory must not be null");
        Assert.notNull(connectTimeout, "connectTimeout must not be null");
        Assert.state(connectTimeout.getSeconds() > 0, "connectTimeout must be positive");

        return HttpClient.newBuilder()
                .sslParameters(sslFactory.getSslParameters())
                .sslContext(sslFactory.getSslContext())
                .connectTimeout(connectTimeout)
                .build();
    }

    @SneakyThrows
    public static SSLFactory buildSslFactory(
            @Nullable KeyManagerFactory kmf, @Nullable TrustManagerFactory tmf, boolean unsafeSsl) {
        SSLFactory.Builder sslFactoryBuilder = SSLFactory.builder();
        if (kmf != null) {
            sslFactoryBuilder = sslFactoryBuilder.withIdentityMaterial(kmf);
        }

        if (unsafeSsl) {
            log.warn("Unsafe HTTP client is used");
            sslFactoryBuilder = sslFactoryBuilder.withTrustingAllCertificatesWithoutValidation();
        } else if (tmf != null) {
            sslFactoryBuilder = sslFactoryBuilder.withTrustMaterial(tmf);
        } else {
            sslFactoryBuilder = sslFactoryBuilder.withDefaultTrustMaterial();
        }
        return sslFactoryBuilder.build();
    }
}
