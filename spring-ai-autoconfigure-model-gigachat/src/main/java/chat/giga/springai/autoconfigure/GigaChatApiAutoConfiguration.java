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

@AutoConfiguration(after = {RestClientAutoConfiguration.class, WebClientAutoConfiguration.class})
@ConditionalOnClass(GigaChatApi.class)
@Slf4j
public class GigaChatApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @SneakyThrows
    public GigaChatApi gigaChatApi(
            final GigaChatApiProperties gigaChatApiProperties,
            final GigaAuthToken authToken,
            final ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            final ObjectProvider<WebClient.Builder> webClientBuilderProvider,
            final ObjectProvider<ResponseErrorHandler> responseErrorHandlerProvider,
            final ObjectProvider<SslBundles> sslBundlesProvider) {
        KeyManagerFactory keyManagerFactory = null;
        TrustManagerFactory trustManagerFactory = null;
        final GigaChatAuthProperties auth = gigaChatApiProperties.getAuth();
        if (auth.isCertsAuth()) {
            final SslBundles sslBundles = sslBundlesProvider.getIfUnique();
            final String sslBundleName = auth.getCerts().getSslBundle();
            if (sslBundleName != null) {
                if (sslBundles != null) {
                    final SslBundle sslBundle = sslBundles.getBundle(sslBundleName);
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
            final GigaChatAuthProperties gigaChatAuthProperties,
            final GigaChatInternalProperties gigaChatInternalProperties) {
        final GigaChatApiProperties gigaChatApiProperties = new GigaChatApiProperties();
        gigaChatApiProperties.setAuth(gigaChatAuthProperties);
        gigaChatApiProperties.setInternal(gigaChatInternalProperties);
        return gigaChatApiProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    public GigaAuthToken simpleGigaAuthToken(final GigaChatAuthProperties gigaChatAuthProperties) {
        if (gigaChatAuthProperties.isBearerAuth()) {
            return new SimpleGigaAuthToken(gigaChatAuthProperties.getApiKey());
        }
        return new NoopGigaAuthToken();
    }
}
