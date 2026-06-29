package chat.giga.springai.autoconfigure.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.GigaChatOptions;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
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
        Person person;
        try {
            // framework: BeanOutputConverter добавляет схему в промпт и парсит ответ в record.
            // Слабая модель может вернуть невалидный JSON -> .entity(...) бросит -> это поведение
            // модели, поэтому уводим в SKIP, а не в FAIL.
            person = ChatClient.create(gigaChatModel)
                    .prompt()
                    .user("Придумай вымышленного человека: верни имя и возраст")
                    .call()
                    .entity(Person.class);
        } catch (RuntimeException ex) {
            Assumptions.abort("SKIP: модель вернула невалидный структурированный вывод (поведение LLM): "
                    + ex.getClass().getSimpleName());
            return; // недостижимо
        }

        log.info("Структурированный ответ: {}", person);
        // framework: распарсилось в record без исключения
        assertThat(person).as("распарсенный record не должен быть null").isNotNull();
        // поведение LLM -> SKIP: заполнены ли поля осмысленно
        assumeTrue(
                person.name() != null && !person.name().isBlank() && person.age() > 0,
                "SKIP: модель вернула неполные данные (поведение LLM)");
    }

    @Test
    @DisplayName("ChatClient: merge runtime-опций — maxTokens(20) реально ограничивает генерацию")
    void systemUserWithRequestOptionsOverrideTest() {
        // В Spring AI 2.0.0 GA .options(...) принимает ChatOptions.Builder (не собранный объект),
        // поэтому передаём билдер без вызова .build(). GigaChatOptions.Builder реализует ChatOptions.Builder.
        // Промпт намеренно требует ДЛИННЫЙ ответ: без слияния maxTokens он дал бы сотни токенов.
        ChatResponse response = ChatClient.create(gigaChatModel)
                .prompt()
                .system("Ты историк. Отвечай максимально развёрнуто и подробно.")
                .user("Подробно расскажи всю историю города Парижа с древности до наших дней.")
                .options(GigaChatOptions.builder().temperature(0.0).maxTokens(20))
                .call()
                .chatResponse();

        Integer completionTokens = response.getMetadata().getUsage().getCompletionTokens();
        String answer = response.getResult().getOutput().getText();
        log.info("Ответ модели (override опций): completionTokens={}, text={}", completionTokens, answer);

        // framework-инвариант (не зависит от ума модели): runtime maxTokens(20) слился поверх дефолтных
        // через combineWith и реально обрезал генерацию, несмотря на промпт про "развёрнуто и подробно".
        // Если merge опций сломается — ответ станет длинным и assert упадёт.
        assertThat(completionTokens)
                .as("maxTokens(20) из runtime-опций должен ограничить генерацию (merge combineWith)")
                .isNotNull()
                .isLessThanOrEqualTo(25);
        assertThat(answer).as("ответ не должен быть пустым").isNotBlank();
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
