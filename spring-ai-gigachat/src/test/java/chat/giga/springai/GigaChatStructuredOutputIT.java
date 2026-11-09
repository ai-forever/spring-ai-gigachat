/*
 * Copyright 2026 the original author or authors.
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

package chat.giga.springai;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.extension.GigaChatTestPropertiesExtension;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;

/** Live verification of the native v1 response format; credentials come from the environment. */
@ExtendWith(GigaChatTestPropertiesExtension.class)
@EnabledIfEnvironmentVariable(named = "GIGACHAT_API_CLIENT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GIGACHAT_API_CLIENT_SECRET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GIGACHAT_API_SCOPE", matches = ".+")
class GigaChatStructuredOutputIT {
    private static final String PROMPT = "В заказе 3 яблока по 20 рублей. Посчитай стоимость.";

    public record Order(String item, int quantity, int total) {}

    private GigaChatModel model(GigaChatApiProperties properties) {
        return GigaChatModel.builder()
                .gigaChatApi(new GigaChatApi(properties))
                .defaultOptions(GigaChatOptions.builder()
                        .model("GigaChat-2-Max")
                        .temperature(0.1)
                        .maxTokens(256)
                        .build())
                .internalProperties(new GigaChatInternalProperties())
                .build();
    }

    @Test
    void nativeEntity(GigaChatApiProperties properties) {
        Order order = ChatClient.create(model(properties))
                .prompt()
                .user(PROMPT)
                .call()
                .entity(Order.class, spec -> spec.useProviderStructuredOutput().validateSchema());
        assertOrder(order);
    }

    @Test
    void nativeAdvisorParameter(GigaChatApiProperties properties) {
        Order order = ChatClient.create(model(properties))
                .prompt()
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .user(PROMPT)
                .call()
                .entity(Order.class);
        assertOrder(order);
    }

    @Test
    void nativeDefaultAdvisorParameter(GigaChatApiProperties properties) {
        var client = ChatClient.builder(model(properties))
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
        Order order = client.prompt().user(PROMPT).call().entity(Order.class);
        assertOrder(order);
    }

    @Test
    void nativeStreaming(GigaChatApiProperties properties) {
        var converter = new BeanOutputConverter<>(Order.class);
        String content = model(properties).stream(new Prompt(
                        PROMPT,
                        GigaChatOptions.builder()
                                .outputSchema(converter.getJsonSchema())
                                .build()))
                .map(response -> response.getResult().getOutput().getText())
                .reduce("", String::concat)
                .block(Duration.ofSeconds(60));
        assertOrder(converter.convert(content));
    }

    private void assertOrder(Order order) {
        assertThat(order).isNotNull();
        assertThat(order.item()).isNotBlank();
        assertThat(order.quantity()).isEqualTo(3);
        assertThat(order.total()).isEqualTo(60);
    }
}
