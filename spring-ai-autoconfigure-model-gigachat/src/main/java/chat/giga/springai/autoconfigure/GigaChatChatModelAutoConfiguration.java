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

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.GigaChatOptions;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationAutoConfiguration;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
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
            ToolCallingAutoConfiguration.class,
            ChatObservationAutoConfiguration.class
        })
@EnableConfigurationProperties(GigaChatChatProperties.class)
@ConditionalOnClass(GigaChatModel.class)
@ConditionalOnBean(GigaChatApi.class)
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = "gigachat", matchIfMissing = true)
public class GigaChatChatModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GigaChatModel gigaChatChatModel(
            GigaChatApi gigaChatApi,
            GigaChatChatProperties chatProperties,
            ObjectProvider<RetryTemplate> retryTemplateProvider,
            ObjectProvider<ToolCallingManager> toolCallingManagerProvider,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatModelObservationConvention> observationConvention,
            GigaChatInternalProperties internalProperties) {
        GigaChatOptions options = GigaChatOptions.builder()
                .model(chatProperties.getModel())
                .temperature(chatProperties.getTemperature())
                .topP(chatProperties.getTopP())
                .maxTokens(chatProperties.getMaxTokens())
                .repetitionPenalty(chatProperties.getRepetitionPenalty())
                .updateInterval(chatProperties.getUpdateInterval())
                .responseFormat(chatProperties.getResponseFormat())
                .profanityCheck(chatProperties.getProfanityCheck())
                .functionCallMode(chatProperties.getFunctionCallMode())
                .functionCallParam(chatProperties.getFunctionCallParam())
                .httpHeaders(chatProperties.getHttpHeaders())
                .build();

        final GigaChatModel gigaChatModel = GigaChatModel.builder()
                .gigaChatApi(gigaChatApi)
                .defaultOptions(options)
                .retryTemplate(retryTemplateProvider.getIfAvailable(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
                .toolCallingManager(
                        toolCallingManagerProvider.getIfAvailable(() -> GigaChatModel.DEFAULT_TOOL_CALLING_MANAGER))
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .internalProperties(internalProperties)
                .build();

        observationConvention.ifAvailable(gigaChatModel::setObservationConvention);
        return gigaChatModel;
    }
}
