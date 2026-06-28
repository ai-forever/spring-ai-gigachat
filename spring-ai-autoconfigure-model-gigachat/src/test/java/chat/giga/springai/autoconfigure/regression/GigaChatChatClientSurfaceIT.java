package chat.giga.springai.autoconfigure.regression;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.GigaChatOptions;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Поверхностный регрессионный набор для high-level API {@link ChatClient} поверх {@link GigaChatModel}.
 * Проверяет комбинации параметров запроса, которых нет в low-level FullIT:
 * структурированный вывод через .entity(...), сквозной merge runtime-опций над дефолтными
 * (system + user + .options(...)), стриминг через .stream().content() и работу SimpleLoggerAdvisor.
 *
 * <p>Тест-обвязка скопирована 1:1 из GigaChatAutoConfigurationIT (профиль it, креды из application-it.yaml
 * через переменные окружения GIGACHAT_API_*). Запускается failsafe в профиле integration-tests (**\/*IT.java).
 */
@ActiveProfiles("it")
@EnableAutoConfiguration
@SpringBootTest(classes = GigaChatChatClientSurfaceIT.MyCustomApplication.class)
public class GigaChatChatClientSurfaceIT {

    private static final Logger log = LoggerFactory.getLogger(GigaChatChatClientSurfaceIT.class);

    @SpringBootConfiguration
    public static class MyCustomApplication {}

    @Autowired
    GigaChatModel gigaChatModel;

    /** Простой record для проверки структурированного вывода .entity(...). */
    public record Person(String name, int age) {}

    @Test
    @DisplayName("ChatClient: структурированный вывод в record через .entity()")
    void structuredEntityOutputTest() {
        Person person = ChatClient.create(gigaChatModel)
                .prompt()
                .user("Придумай вымышленного человека: верни имя и возраст")
                .call()
                .entity(Person.class);

        log.info("Структурированный ответ: {}", person);
        assertThat(person).as("распарсенный record не должен быть null").isNotNull();
        assertThat(person.name()).as("имя должно быть заполнено моделью").isNotBlank();
        assertThat(person.age()).as("возраст должен быть положительным").isPositive();
    }

    @Test
    @DisplayName("ChatClient: system + user + переопределение опций на уровне запроса (merge runtime-опций)")
    void systemUserWithRequestOptionsOverrideTest() {
        // В Spring AI 2.0.0 GA .options(...) принимает ChatOptions.Builder (не собранный объект),
        // поэтому передаём билдер без вызова .build(). GigaChatOptions.Builder реализует ChatOptions.Builder.
        String answer = ChatClient.create(gigaChatModel)
                .prompt()
                .system("Отвечай ровно одним словом")
                .user("Столица Франции?")
                .options(GigaChatOptions.builder().temperature(0.0).maxTokens(20))
                .call()
                .content();

        log.info("Ответ модели (с переопределением опций): {}", answer);
        assertThat(answer).as("ответ не должен быть пустым").isNotBlank();
        assertThat(answer)
                .as("runtime-опции должны слиться поверх дефолтных, ответ должен содержать столицу")
                .containsIgnoringCase("Париж");
    }

    @Test
    @DisplayName("ChatClient: стриминг ответа через .stream().content()")
    void streamingContentTest() {
        List<String> chunks = ChatClient.create(gigaChatModel).prompt().user("Посчитай от 1 до 5").stream()
                .content()
                .collectList()
                .block();

        log.info("Стриминговые чанки: {}", chunks);
        assertThat(chunks)
                .as("список стриминговых чанков не должен быть пустым")
                .isNotEmpty();
    }

    @Test
    @DisplayName("ChatClient: обычный вызов с SimpleLoggerAdvisor отрабатывает без ошибок")
    void simpleLoggerAdvisorTest() {
        String answer = ChatClient.create(gigaChatModel)
                .prompt()
                .advisors(new SimpleLoggerAdvisor())
                .user("Привет! Ответь коротко.")
                .call()
                .content();

        log.info("Ответ модели с SimpleLoggerAdvisor: {}", answer);
        assertThat(answer)
                .as("вызов с SimpleLoggerAdvisor должен вернуть непустой ответ")
                .isNotBlank();
    }
}
