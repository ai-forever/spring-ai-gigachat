package chat.giga.springai.autoconfigure;

import chat.giga.springai.GigaChatEmbeddingModel;
import chat.giga.springai.api.chat.GigaChatApi;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.embedding.observation.autoconfigure.EmbeddingObservationAutoConfiguration;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;

@AutoConfiguration(
        after = {
            GigaChatApiAutoConfiguration.class,
            SpringAiRetryAutoConfiguration.class,
            EmbeddingObservationAutoConfiguration.class
        })
@EnableConfigurationProperties(GigaChatEmbeddingProperties.class)
@ConditionalOnClass(GigaChatEmbeddingModel.class)
@ConditionalOnBean(GigaChatApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = "gigachat", matchIfMissing = true)
public class GigaChatEmbeddingModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GigaChatEmbeddingModel gigaChatEmbeddingModel(
            final GigaChatApi gigaChatApi,
            final GigaChatEmbeddingProperties gigaChatEmbeddingProperties,
            final ObjectProvider<RetryTemplate> retryTemplateProvider,
            final ObjectProvider<ObservationRegistry> observationRegistry,
            final ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {
        final GigaChatEmbeddingModel gigaChatEmbeddingModel = new GigaChatEmbeddingModel(
                gigaChatApi,
                gigaChatEmbeddingProperties.getOptions(),
                retryTemplateProvider.getIfAvailable(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE),
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

        observationConvention.ifAvailable(gigaChatEmbeddingModel::setObservationConvention);

        return gigaChatEmbeddingModel;
    }
}
