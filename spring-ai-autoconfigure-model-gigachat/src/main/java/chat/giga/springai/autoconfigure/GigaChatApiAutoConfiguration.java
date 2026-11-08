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

package chat.giga.springai.autoconfigure;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.auth.bearer.GigaAuthToken;
import chat.giga.springai.api.auth.bearer.NoopGigaAuthToken;
import chat.giga.springai.api.auth.bearer.SimpleGigaAuthToken;
import chat.giga.springai.api.chat.GigaChatApi;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.ssl.pem.util.PemUtils;
import nl.altindag.ssl.util.KeyManagerUtils;
import nl.altindag.ssl.util.TrustManagerUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration(
        after = {
            RestClientAutoConfiguration.class,
            WebClientAutoConfiguration.class,
            SpringAiRetryAutoConfiguration.class
        })
@ConditionalOnClass(GigaChatApi.class)
@Slf4j
public class GigaChatApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @SneakyThrows
    public GigaChatApi gigaChatApi(
            GigaChatApiProperties gigaChatApiProperties,
            GigaAuthToken authToken,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ObjectProvider<WebClient.Builder> webClientBuilderProvider,
            ObjectProvider<ResponseErrorHandler> responseErrorHandlerProvider,
            ObjectProvider<SslBundles> sslBundlesProvider) {
        KeyManagerFactory keyManagerFactory = null;
        TrustManagerFactory trustManagerFactory = null;
        GigaChatAuthProperties auth = gigaChatApiProperties.getAuth();
        if (auth.isCertsAuth()) {
            SslBundles sslBundles = sslBundlesProvider.getIfUnique();
            String sslBundleName = auth.getCerts().getSslBundle();
            if (sslBundleName != null) {
                if (sslBundles != null) {
                    SslBundle sslBundle = sslBundles.getBundle(sslBundleName);
                    keyManagerFactory = sslBundle.getManagers().getKeyManagerFactory();
                    trustManagerFactory = sslBundle.getManagers().getTrustManagerFactory();
                } else {
                    log.warn("SslBundles bean was not found");
                }
            } else {
                keyManagerFactory = KeyManagerUtils.createKeyManagerFactory(PemUtils.loadIdentityMaterial(
                        auth.getCerts().getCertificate().getInputStream(),
                        auth.getCerts().getPrivateKey().getInputStream()));
            }
        }
        if (trustManagerFactory == null && auth.getCerts().getCaCerts() != null) {
            trustManagerFactory = TrustManagerUtils.createTrustManagerFactory(
                    PemUtils.loadTrustMaterial(auth.getCerts().getCaCerts().getInputStream()));
        }
        return new GigaChatApi(
                gigaChatApiProperties,
                authToken,
                restClientBuilderProvider.getIfAvailable(RestClient::builder),
                webClientBuilderProvider.getIfAvailable(WebClient::builder),
                responseErrorHandlerProvider.getIfAvailable(() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER),
                keyManagerFactory,
                trustManagerFactory);
    }

    @Bean
    @ConfigurationProperties(prefix = GigaChatAuthProperties.CONFIG_PREFIX)
    public GigaChatAuthProperties gigaChatAuthProperties() {
        return new GigaChatAuthProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = GigaChatInternalProperties.CONFIG_PREFIX)
    public GigaChatInternalProperties gigaChatInternalProperties() {
        return new GigaChatInternalProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = GigaChatApiProperties.CONFIG_PREFIX)
    public GigaChatApiProperties gigaChatApiProperties(
            GigaChatAuthProperties gigaChatAuthProperties, GigaChatInternalProperties gigaChatInternalProperties) {
        GigaChatApiProperties gigaChatApiProperties = new GigaChatApiProperties();
        gigaChatApiProperties.setAuth(gigaChatAuthProperties);
        gigaChatApiProperties.setInternal(gigaChatInternalProperties);
        return gigaChatApiProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    public GigaAuthToken simpleGigaAuthToken(GigaChatAuthProperties gigaChatAuthProperties) {
        if (gigaChatAuthProperties.isBearerAuth()) {
            return new SimpleGigaAuthToken(gigaChatAuthProperties.getApiKey());
        }
        return new NoopGigaAuthToken();
    }
}
