package chat.giga.springai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import chat.giga.springai.GigaChatEmbeddingModel;
import chat.giga.springai.GigaChatModel;
import chat.giga.springai.image.GigaChatImageModel;
import chat.giga.springai.image.GigaChatImageOptions;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.tool.annotation.Tool;
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

    @Autowired
    GigaChatImageModel gigaChatImageModel;

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

    /**
     * Ключевая проверка миграции PATH A: в Spring AI 2.0 GA исполнение инструментов выполняет
     * ChatClient (ToolCallingAdvisor), а не in-model цикл (он удалён). Тест подтверждает на реальном
     * API, что полный round-trip с вызовом инструмента работает через ChatClient: инструмент реально
     * вызывается, а его результат попадает в финальный ответ.
     */
    @Test
    @DisplayName("PATH A: полный tool-calling round-trip через ChatClient на реальном API")
    void toolCallingThroughChatClientTest() {
        WeatherTools weatherTools = new WeatherTools();
        ChatClient chatClient = ChatClient.create(gigaChatModel);

        String answer = chatClient
                .prompt()
                .user("Какая сейчас погода в городе Сочи? Обязательно используй доступный инструмент.")
                .tools(weatherTools)
                .call()
                .content();

        log.info("Ответ модели с инструментом: {}", answer);
        assertThat(weatherTools.calls.get())
                .as("инструмент должен быть исполнен ChatClient'ом (PATH A: in-model цикл удалён)")
                .isPositive();
        assertThat(answer).isNotBlank();
        // результат инструмента (магическое значение) должен отразиться в финальном ответе модели
        assertThat(answer).containsIgnoringCase("42");
    }

    public static class WeatherTools {
        private final AtomicInteger calls = new AtomicInteger();

        @Tool(description = "Возвращает текущую температуру в указанном городе в градусах Цельсия")
        public String currentTemperature(String city) {
            calls.incrementAndGet();
            log.info("Инструмент currentTemperature вызван для города: {}", city);
            return "В городе " + city + " сейчас ровно 42 градуса Цельсия";
        }
    }

    @Test
    @DisplayName("Тест взаимодействия с chat моделью для генерации изображений")
    void imageInteractionTest() {
        ImagePrompt prompt = new ImagePrompt(
                List.of(new ImageMessage("Нарисуй розового кота в стиле акварели", 1.0f)),
                GigaChatImageOptions.builder().build());
        ImageResponse response = gigaChatImageModel.call(prompt);
        assertThat("Запрос в imageModel", response, is(not(nullValue())));
    }
}
