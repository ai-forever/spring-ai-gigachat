package chat.giga.springai.autoconfigure;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.image.GigaChatImageModel;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.image.observation.autoconfigure.ImageObservationAutoConfiguration;
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
            ImageObservationAutoConfiguration.class
        })
@EnableConfigurationProperties(GigaChatImageProperties.class)
@ConditionalOnClass(GigaChatImageModel.class)
@ConditionalOnBean(GigaChatApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.IMAGE_MODEL, havingValue = "gigachat", matchIfMissing = true)
public class GigaChatImageModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ImageModel gigaChatImageModel(
            final GigaChatApi gigaChatApi,
            final GigaChatImageProperties properties,
            final ObjectProvider<RetryTemplate> retryTemplateProvider,
            final ObjectProvider<ObservationRegistry> observationRegistry,
            final ObjectProvider<ImageModelObservationConvention> observationConvention) {

        final GigaChatImageModel gigaChatImageModel = new GigaChatImageModel(
                gigaChatApi,
                properties.getOptions(),
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
                retryTemplateProvider.getIfAvailable(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE));

        observationConvention.ifAvailable(gigaChatImageModel::setObservationConvention);

        return gigaChatImageModel;
    }
}
