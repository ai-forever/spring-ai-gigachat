package chat.giga.springai.autoconfigure;

import chat.giga.springai.GigaChatEmbeddingModel;
import chat.giga.springai.GigaChatModel;
import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.chat.GigaChatApi;
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
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationAutoConfiguration;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
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
@EnableConfigurationProperties({GigaChatChatProperties.class, GigaChatEmbeddingProperties.class})
@ConditionalOnClass(GigaChatApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = "gigachat", matchIfMissing = true)
@ImportAutoConfiguration(
        classes = {
            SpringAiRetryAutoConfiguration.class,
            RestClientAutoConfiguration.class,
            WebClientAutoConfiguration.class,
            ToolCallingAutoConfiguration.class
        })
@Slf4j
public class GigaChatAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @SneakyThrows
    public GigaChatApi gigaChatApi(
            GigaChatApiProperties gigaChatApiProperties,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ObjectProvider<WebClient.Builder> webClientBuilderProvider,
            ResponseErrorHandler responseErrorHandler,
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
                restClientBuilderProvider.getIfAvailable(RestClient::builder),
                webClientBuilderProvider.getIfAvailable(WebClient::builder),
                responseErrorHandler,
                keyManagerFactory,
                trustManagerFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public GigaChatModel gigaChatChatModel(
            GigaChatApi gigaChatApi,
            GigaChatChatProperties chatProperties,
            RetryTemplate retryTemplate,
            ToolCallingManager toolCallingManager,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatModelObservationConvention> observationConvention,
            ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate,
            GigaChatInternalProperties internalProperties) {
        final GigaChatModel gigaChatModel = GigaChatModel.builder()
                .gigaChatApi(gigaChatApi)
                .defaultOptions(chatProperties.getOptions())
                .retryTemplate(retryTemplate)
                .toolCallingManager(toolCallingManager)
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
            RetryTemplate retryTemplate,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {
        GigaChatEmbeddingModel gigaChatEmbeddingModel = new GigaChatEmbeddingModel(
                gigaChatApi,
                gigaChatEmbeddingProperties.getOptions(),
                retryTemplate,
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
    @ConfigurationProperties(prefix = GigaChatApiProperties.CONFIG_PREFIX)
    public GigaChatApiProperties gigaChatApiProperties(GigaChatAuthProperties gigaChatAuthProperties) {
        GigaChatApiProperties gigaChatApiProperties = new GigaChatApiProperties();
        gigaChatApiProperties.setAuth(gigaChatAuthProperties);
        return gigaChatApiProperties;
    }

    @Bean
    @ConfigurationProperties(prefix = GigaChatInternalProperties.CONFIG_PREFIX)
    public GigaChatInternalProperties gigaChatInternalProperties() {
        return new GigaChatInternalProperties();
    }
}
