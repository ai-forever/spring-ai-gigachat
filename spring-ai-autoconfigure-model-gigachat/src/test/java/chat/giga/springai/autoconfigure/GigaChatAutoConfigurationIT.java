package chat.giga.springai.autoconfigure;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import chat.giga.springai.GigaChatEmbeddingModel;
import chat.giga.springai.GigaChatModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("it")
@EnableAutoConfiguration
@SpringBootTest(classes = GigaChatAutoConfigurationIT.MyCustomApplication.class)
public class GigaChatAutoConfigurationIT {

    private static final Logger log = LoggerFactory.getLogger(GigaChatAutoConfigurationIT.class);

    @SpringBootConfiguration
    public static class MyCustomApplication {}

    @Autowired
    GigaChatModel gigaChatModel;

    @Autowired
    GigaChatEmbeddingModel gigaChatEmbeddingModel;

    @Autowired
    GigaChatEmbeddingProperties gigaChatEmbeddingProperties;

    @Test
    @DisplayName("Тест взаимодействия с чатом модели")
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
    @DisplayName("Тест взаимодействия с embedding моделью")
    void embeddingInteractionTest() {
        EmbeddingRequest embeddingRequest =
                new EmbeddingRequest(List.of("Привет, как дела?"), gigaChatEmbeddingProperties.getOptions());
        final EmbeddingResponse embeddingResponse = gigaChatEmbeddingModel.call(embeddingRequest);
        assertThat("Запрос в embeddingModel", embeddingResponse, is(not(nullValue())));
    }
}
