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
            GigaChatApi gigaChatApi,
            GigaChatEmbeddingProperties gigaChatEmbeddingProperties,
            ObjectProvider<RetryTemplate> retryTemplateProvider,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {
        GigaChatEmbeddingModel gigaChatEmbeddingModel = new GigaChatEmbeddingModel(
                gigaChatApi,
                gigaChatEmbeddingProperties.getOptions(),
                retryTemplateProvider.getIfAvailable(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE),
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
                gigaChatEmbeddingProperties.getMetadataMode());

        observationConvention.ifAvailable(gigaChatEmbeddingModel::setObservationConvention);

        return gigaChatEmbeddingModel;
    }
}
