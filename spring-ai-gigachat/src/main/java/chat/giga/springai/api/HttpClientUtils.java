/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.giga.springai.api;

import java.net.http.HttpClient;
import java.time.Duration;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.ssl.SSLFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

@UtilityClass
@Slf4j
public class HttpClientUtils {

    public static HttpClient buildHttpClient(SSLFactory sslFactory, Duration connectTimeout) {
        Assert.notNull(sslFactory, "sslFactory must not be null");
        Assert.notNull(connectTimeout, "connectTimeout must not be null");
        // getSeconds() обрезает доли секунды: валидный таймаут вроде 500ms давал бы 0 и падал.
        Assert.state(!connectTimeout.isZero() && !connectTimeout.isNegative(), "connectTimeout must be positive");

        return HttpClient.newBuilder()
                .sslParameters(sslFactory.getSslParameters())
                .sslContext(sslFactory.getSslContext())
                .connectTimeout(connectTimeout)
                .build();
    }

    public static SSLFactory buildSslFactory(
            @Nullable KeyManagerFactory kmf, @Nullable TrustManagerFactory tmf, boolean unsafeSsl) {
        SSLFactory.Builder sslFactoryBuilder = SSLFactory.builder();
        if (kmf != null) {
            sslFactoryBuilder = sslFactoryBuilder.withIdentityMaterial(kmf);
        }
        if (unsafeSsl) {
            log.warn("Unsafe HTTP client is used");
            return sslFactoryBuilder
                    .withTrustingAllCertificatesWithoutValidation()
                    .build();
        }
        if (tmf != null) {
            return sslFactoryBuilder.withTrustMaterial(tmf).build();
        }
        return sslFactoryBuilder.withDefaultTrustMaterial().build();
    }
}
