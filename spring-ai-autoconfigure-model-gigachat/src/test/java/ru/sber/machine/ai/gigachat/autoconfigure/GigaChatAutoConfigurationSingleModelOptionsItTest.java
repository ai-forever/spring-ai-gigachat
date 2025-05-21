package ru.sber.machine.ai.gigachat.autoconfigure;

import static ai.forever.gigachat.api.chat.GigaChatApi.ChatModel.GIGA_CHAT_2;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import ai.forever.gigachat.GigaChatEmbeddingModel;
import ai.forever.gigachat.GigaChatModel;
import ai.forever.gigachat.autoconfigure.GigaChatChatProperties;
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

@ActiveProfiles("model-options")
@EnableAutoConfiguration
@SpringBootTest(classes = GigaChatAutoConfigurationSingleModelOptionsItTest.MyCustomApplication.class)
public class GigaChatAutoConfigurationSingleModelOptionsItTest {

    private static final Logger log = LoggerFactory.getLogger(GigaChatAutoConfigurationSingleModelOptionsItTest.class);

    @SpringBootConfiguration
    public static class MyCustomApplication {}

    @Autowired
    GigaChatChatProperties modelOptions;

    @Autowired
    GigaChatModel gigaChatModel;

    @Autowired
    GigaChatEmbeddingModel gigaChatEmbeddingModel;

    @Autowired
    GigaChatEmbeddingProperties gigaChatEmbeddingProperties;

    @Test
    @DisplayName("Тест проверяет корректную сборку modelOptions")
    void modelOptionsBeanTest() {
        assertThat(modelOptions, is(not(nullValue())));
        assertThat(modelOptions.getOptions(), is(not(nullValue())));
        assertThat(modelOptions.getOptions().getModel(), is(GIGA_CHAT_2.value));
        assertThat(modelOptions.getOptions().getTemperature(), is(0.2));
        assertThat(modelOptions.getOptions().getTopP(), is(0.7));
        assertThat(modelOptions.getOptions().getMaxTokens(), is(200));
        assertThat(modelOptions.getOptions().getRepetitionPenalty(), is(2.0));
    }

    @Test
    @DisplayName("Тест проверяет корректную сборку бина gigaChatEmbeddingProperties")
    void gigaChatEmbeddingPropertiesBeanTest() {
        assertThat(gigaChatEmbeddingProperties, is(not(nullValue())));
        assertThat(gigaChatEmbeddingProperties.getEmbeddingsPath(), is(not(emptyOrNullString())));
        assertThat(gigaChatEmbeddingProperties.getOptions(), is(not(nullValue())));
        assertThat(gigaChatEmbeddingProperties.getMetadataMode(), is(not(nullValue())));
    }

    @Test
    @DisplayName("Тест проверяет корректную сборку бина gigaChatEmbeddingModel")
    void gigaChatEmbeddingModelTest() {
        assertThat(gigaChatEmbeddingModel, is(not(nullValue())));
    }

    @Test
    @DisplayName("Тест взаимодействия с чатом модели в конфигурации с modelOptions")
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
