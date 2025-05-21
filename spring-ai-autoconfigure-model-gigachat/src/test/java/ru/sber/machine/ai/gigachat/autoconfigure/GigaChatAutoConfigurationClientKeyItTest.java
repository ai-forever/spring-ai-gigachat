package ru.sber.machine.ai.gigachat.autoconfigure;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import ai.forever.gigachat.GigaChatEmbeddingModel;
import ai.forever.gigachat.GigaChatModel;
import ai.forever.gigachat.api.auth.GigaChatApiProperties;
import ai.forever.gigachat.api.chat.GigaChatApi;
import ai.forever.gigachat.autoconfigure.GigaChatEmbeddingProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("client-key")
@EnableAutoConfiguration
@SpringBootTest(classes = GigaChatAutoConfigurationClientKeyItTest.MyCustomApplication.class)
public class GigaChatAutoConfigurationClientKeyItTest {

    private static final Logger log = LoggerFactory.getLogger(GigaChatAutoConfigurationClientKeyItTest.class);

    @SpringBootConfiguration
    public static class MyCustomApplication {}

    @Autowired
    GigaChatModel gigaChat;

    @Autowired
    GigaChatApiProperties gigaChatApiProperties;

    @Autowired
    GigaChatModel gigaChatModel;

    @Autowired
    GigaChatApi gigaChatApi;

    @Autowired
    GigaChatEmbeddingModel gigaChatEmbeddingModel;

    @Autowired
    GigaChatEmbeddingProperties gigaChatEmbeddingProperties;

    @Test
    @DisplayName("Тест проверяет корректную сборку бина gigaChat в конфигурации с client-id")
    void gigaChatBeanTest() {
        assertThat(gigaChat, is(not(nullValue())));
    }

    @Test
    @DisplayName("Тест проверяет корректную сборку бина gigaChatApiProperties в конфигурации с client-id")
    void gigaChatApiPropertiesBeanTest() {
        assertThat(gigaChatApiProperties, is(not(nullValue())));
        assertThat(gigaChatApiProperties.getBaseUrl(), is(not(emptyOrNullString())));
        assertThat(gigaChatApiProperties.getAuthUrl(), is(not(emptyOrNullString())));
        assertThat(gigaChatApiProperties.getClientId(), is(not(emptyOrNullString())));
        assertThat(gigaChatApiProperties.getClientSecret(), is(not(emptyOrNullString())));
        assertThat(gigaChatApiProperties.getScope(), is(not(nullValue())));
    }

    @Test
    @DisplayName("Тест проверяет корректную сборку бина gigaChatModel в конфигурации с client-id")
    void gigaChatModelTest() {
        assertThat(gigaChatModel, is(not(nullValue())));
    }

    @Test
    @DisplayName("Тест проверяет корректную сборку бина gigaChatApi в конфигурации с client-id")
    void gigaChatApiTest() {
        assertThat(gigaChatApi, is(not(nullValue())));
    }

    @Test
    @DisplayName("Тест проверяет корректную сборку бина gigaChatEmbeddingProperties в конфигурации с сертификатами")
    void gigaChatEmbeddingPropertiesBeanTest() {
        assertThat(gigaChatEmbeddingProperties, is(not(nullValue())));
        assertThat(gigaChatEmbeddingProperties.getEmbeddingsPath(), is(not(emptyOrNullString())));
        assertThat(gigaChatEmbeddingProperties.getOptions(), is(not(nullValue())));
        assertThat(gigaChatEmbeddingProperties.getMetadataMode(), is(not(nullValue())));
    }

    @Test
    @DisplayName("Тест проверяет корректную сборку бина gigaChatEmbeddingModel в конфигурации с сертификатами")
    void gigaChatEmbeddingModelTest() {
        assertThat(gigaChatEmbeddingModel, is(not(nullValue())));
    }

    @Test
    @DisplayName("Тест взаимодействия с чатом модели в конфигурации с client-id")
    @EnabledIfEnvironmentVariable(named = "GIGACHAT_API_CLIENT_SECRET", matches = ".*")
    void chatInteractionTest() {
        final String call = gigaChatModel.call("Привет, как дела?");
        assertThat("Сихронный запрос в chatModel", call, is(not(emptyOrNullString())));
        log.info("Ответ модели: {}", call);
        final List<String> call2 =
                gigaChatModel.stream("Привет, как дела?").collectList().block();
        assertThat("Асихронный запрос в chatModel", call2, is(not(empty())));
        log.info("Ответ модели: {}", call2);
    }

    @Test
    @DisplayName("Тест взаимодействия с embedding моделью в конфигурации с сертификатами")
    @EnabledIfEnvironmentVariable(named = "GIGACHAT_API_CLIENT_SECRET", matches = ".*")
    void embeddingInteractionTest() {
        EmbeddingRequest embeddingRequest =
                new EmbeddingRequest(List.of("Привет, как дела?"), gigaChatEmbeddingProperties.getOptions());
        final EmbeddingResponse embeddingResponse = gigaChatEmbeddingModel.call(embeddingRequest);
        assertThat("Запрос в embeddingModel", embeddingResponse, is(not(nullValue())));
    }
}
