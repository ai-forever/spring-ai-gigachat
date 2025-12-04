package chat.giga.springai.autoconfigure;

import chat.giga.springai.GigaChatEmbeddingModel;
import chat.giga.springai.GigaChatModel;
import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.auth.bearer.GigaAuthToken;
import chat.giga.springai.api.auth.bearer.NoopGigaAuthToken;
import chat.giga.springai.api.auth.bearer.SimpleGigaAuthToken;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.image.GigaChatImageModel;
import io.micrometer.observation.ObservationRegistry;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.ssl.pem.util.PemUtils;
import nl.altindag.ssl.util.KeyManagerUtils;
import nl.altindag.ssl.util.TrustManagerUtils;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationAutoConfiguration;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration(
        after = {
            RestClientAutoConfiguration.class,
            WebClientAutoConfiguration.class,
            SpringAiRetryAutoConfiguration.class,
            ChatObservationAutoConfiguration.class,
            ToolCallingAutoConfiguration.class
        })
@EnableConfigurationProperties({
    GigaChatChatProperties.class,
    GigaChatEmbeddingProperties.class,
    GigaChatImageProperties.class
})
@ConditionalOnClass(GigaChatApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = "gigachat", matchIfMissing = true)
@Slf4j
public class GigaChatAutoConfiguration {

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
        } else if (gigaChatApiProperties.getClientKey() != null
                && gigaChatApiProperties.getClientCertificate() != null) {
            keyManagerFactory = KeyManagerUtils.createKeyManagerFactory(PemUtils.loadIdentityMaterial(
                    gigaChatApiProperties.getClientCertificate().getInputStream(),
                    gigaChatApiProperties.getClientKey().getInputStream()));
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
    @ConditionalOnMissingBean
    public GigaChatModel gigaChatChatModel(
            GigaChatApi gigaChatApi,
            GigaChatChatProperties chatProperties,
            ObjectProvider<RetryTemplate> retryTemplateProvider,
            ObjectProvider<ToolCallingManager> toolCallingManagerProvider,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatModelObservationConvention> observationConvention,
            ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate,
            GigaChatInternalProperties internalProperties) {
        final GigaChatModel gigaChatModel = GigaChatModel.builder()
                .gigaChatApi(gigaChatApi)
                .defaultOptions(chatProperties.getOptions())
                .retryTemplate(retryTemplateProvider.getIfAvailable(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
                .toolCallingManager(
                        toolCallingManagerProvider.getIfAvailable(() -> GigaChatModel.DEFAULT_TOOL_CALLING_MANAGER))
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .toolExecutionEligibilityPredicate(
                        toolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
                .internalProperties(internalProperties)
                .build();

        observationConvention.ifAvailable(gigaChatModel::setObservationConvention);
        return gigaChatModel;
    }

    @Bean
    @ConditionalOnMissingBean
    public GigaChatEmbeddingModel gigaChatEmbeddingModel(
            GigaChatApi gigaChatApi,
            GigaChatEmbeddingProperties gigaChatEmbeddingProperties,
            ObjectProvider<RetryTemplate> retryTemplateProvider,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {
        GigaChatEmbeddingModel gigaChatEmbeddingModel = new GigaChatEmbeddingModel(
                gigaChatApi,
                gigaChatEmbeddingProperties.getOptions(),
                retryTemplateProvider.getIfAvailable(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE),
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

        observationConvention.ifAvailable(gigaChatEmbeddingModel::setObservationConvention);

        return gigaChatEmbeddingModel;
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
    public GigaAuthToken simpleGigaAuthToken(GigaChatApiProperties gigaChatApiProperties) {
        if (gigaChatApiProperties.isBearer()) {
            return new SimpleGigaAuthToken(gigaChatApiProperties.getApiKey());
        }
        return new NoopGigaAuthToken();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = SpringAIModelProperties.IMAGE_MODEL, havingValue = "gigachat", matchIfMissing = true)
    public ImageModel gigaChatImageModel(
            GigaChatApi gigaChatApi,
            GigaChatImageProperties properties,
            ObjectProvider<RetryTemplate> retryTemplateProvider,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ImageModelObservationConvention> observationConvention) {

        GigaChatImageModel gigaChatImageModel = new GigaChatImageModel(
                gigaChatApi,
                properties.getOptions(),
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
                retryTemplateProvider.getIfAvailable(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE));

        observationConvention.ifAvailable(gigaChatImageModel::setObservationConvention);

        return gigaChatImageModel;
    }
}
