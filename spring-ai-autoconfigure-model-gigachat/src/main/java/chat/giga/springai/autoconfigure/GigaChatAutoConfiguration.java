package chat.giga.springai.autoconfigure;

import chat.giga.springai.GigaChatEmbeddingModel;
import chat.giga.springai.GigaChatModel;
import chat.giga.springai.api.auth.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatInternalProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import io.micrometer.observation.ObservationRegistry;
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
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration(
        after = {
            RestClientAutoConfiguration.class,
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
public class GigaChatAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GigaChatApi gigaChatApi(
            GigaChatApiProperties gigaChatApiProperties,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ObjectProvider<WebClient.Builder> webClientBuilderProvider,
            ResponseErrorHandler responseErrorHandler) {
        return new GigaChatApi(
                gigaChatApiProperties,
                restClientBuilderProvider.getIfAvailable(RestClient::builder),
                webClientBuilderProvider.getIfAvailable(WebClient::builder),
                responseErrorHandler);
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
    @ConfigurationProperties(prefix = GigaChatApiProperties.CONFIG_PREFIX)
    public GigaChatApiProperties gigaChatApiProperties() {
        return new GigaChatApiProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = GigaChatInternalProperties.CONFIG_PREFIX)
    public GigaChatInternalProperties gigaChatInternalProperties() {
        return new GigaChatInternalProperties();
    }
}
