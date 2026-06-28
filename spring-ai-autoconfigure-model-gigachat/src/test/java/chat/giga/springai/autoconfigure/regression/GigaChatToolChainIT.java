package chat.giga.springai.autoconfigure.regression;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.GigaChatModel;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Регресс ПОСЛЕДОВАТЕЛЬНЫХ ТУЛ-ЦЕПОЧЕК через ChatClient на реальном API (PATH A, Spring AI 2.0.0 GA).
 *
 * <p>В Spring AI 2.0 GA исполнение инструментов выполняет ChatClient (ToolCallingAdvisor): когда задача
 * требует нескольких шагов, модель сама вызывает инструменты по очереди, advisor крутит цикл, скармливая
 * результат предыдущего вызова в следующий запрос. Тесты подтверждают, что многошаговые цепочки реально
 * проходят round-trip: инструменты вызываются, в нужном ПОРЯДКЕ, а магические значения из результатов
 * инструментов попадают в финальный ответ модели.
 */
@ActiveProfiles("it")
@EnableAutoConfiguration
@SpringBootTest(classes = GigaChatToolChainIT.MyCustomApplication.class)
public class GigaChatToolChainIT {

    private static final Logger log = LoggerFactory.getLogger(GigaChatToolChainIT.class);

    @SpringBootConfiguration
    public static class MyCustomApplication {}

    @Autowired
    GigaChatModel gigaChatModel;

    // ----------------------------------------------------------------------------------------------
    // Тест 1: цепочка из 2 шагов (город -> IATA-код -> погода по коду)
    // ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Цепочка из 2 шагов: сначала IATA-код города, затем погода по этому коду")
    void twoStepChainTest() {
        TwoStepTools tools = new TwoStepTools();
        ChatClient chatClient = ChatClient.create(gigaChatModel);

        String answer = chatClient
                .prompt()
                .user("Узнай текущую погоду в городе Сочи. Сначала с помощью инструмента получи IATA-код "
                        + "этого города, а затем по полученному коду узнай погоду. Используй только инструменты.")
                .tools(tools)
                .call()
                .content();

        log.info("Ответ модели (2 шага): {}", answer);

        assertThat(tools.iataCalls.get())
                .as("инструмент поиска IATA-кода должен быть вызван")
                .isPositive();
        assertThat(tools.weatherCalls.get())
                .as("инструмент погоды по коду должен быть вызван")
                .isPositive();
        assertThat(tools.iataOrder.get())
                .as("IATA-код должен быть получен РАНЬШЕ погоды (порядок цепочки)")
                .isLessThan(tools.weatherOrder.get());
        assertThat(answer).as("финальный ответ не должен быть пустым").isNotBlank();
        assertThat(answer)
                .as("магическое значение температуры из результата инструмента должно дойти до ответа")
                .contains("42");
    }

    public static class TwoStepTools {
        private final AtomicInteger seq = new AtomicInteger();
        private final AtomicInteger iataCalls = new AtomicInteger();
        private final AtomicInteger weatherCalls = new AtomicInteger();
        private final AtomicInteger iataOrder = new AtomicInteger();
        private final AtomicInteger weatherOrder = new AtomicInteger();

        @Tool(description = "Возвращает трёхбуквенный IATA-код аэропорта по русскому названию города")
        public String findCityIataCode(@ToolParam(description = "Название города на русском") String cityName) {
            iataCalls.incrementAndGet();
            iataOrder.set(seq.incrementAndGet());
            log.info("Инструмент findCityIataCode вызван для города: {}", cityName);
            return "AER";
        }

        @Tool(description = "Возвращает текущую погоду по трёхбуквенному IATA-коду аэропорта")
        public String getWeatherByIataCode(@ToolParam(description = "IATA-код аэропорта") String code) {
            weatherCalls.incrementAndGet();
            weatherOrder.set(seq.incrementAndGet());
            log.info("Инструмент getWeatherByIataCode вызван для кода: {}", code);
            return "в коде " + code + " сейчас ровно 42 градуса";
        }
    }

    // ----------------------------------------------------------------------------------------------
    // Тест 2: глубокая цепочка из 3 шагов (имя -> userId -> orderId -> статус заказа)
    // ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Глубокая цепочка из 3 шагов: имя -> userId -> orderId -> статус заказа")
    void threeStepChainTest() {
        ThreeStepTools tools = new ThreeStepTools();
        ChatClient chatClient = ChatClient.create(gigaChatModel);

        String answer = chatClient
                .prompt()
                .user("Узнай статус активного заказа пользователя по имени Иван. Действуй строго по шагам, "
                        + "используя только инструменты: сначала получи идентификатор пользователя по имени, "
                        + "затем по идентификатору пользователя получи идентификатор активного заказа, "
                        + "затем по идентификатору заказа получи его статус. Верни итоговый статус.")
                .tools(tools)
                .call()
                .content();

        log.info("Ответ модели (3 шага): {}", answer);

        assertThat(tools.userCalls.get())
                .as("инструмент getUserIdByName должен быть вызван")
                .isPositive();
        assertThat(tools.orderCalls.get())
                .as("инструмент getActiveOrderId должен быть вызван")
                .isPositive();
        assertThat(tools.statusCalls.get())
                .as("инструмент getOrderStatus должен быть вызван")
                .isPositive();
        assertThat(tools.userOrder.get())
                .as("userId должен быть получен раньше orderId")
                .isLessThan(tools.orderOrder.get());
        assertThat(tools.orderOrder.get())
                .as("orderId должен быть получен раньше статуса заказа")
                .isLessThan(tools.statusOrder.get());
        assertThat(answer).as("финальный ответ не должен быть пустым").isNotBlank();
        assertThat(answer)
                .as("магический код статуса из результата инструмента должен дойти до ответа")
                .contains("7777");
    }

    public static class ThreeStepTools {
        private final AtomicInteger seq = new AtomicInteger();
        private final AtomicInteger userCalls = new AtomicInteger();
        private final AtomicInteger orderCalls = new AtomicInteger();
        private final AtomicInteger statusCalls = new AtomicInteger();
        private final AtomicInteger userOrder = new AtomicInteger();
        private final AtomicInteger orderOrder = new AtomicInteger();
        private final AtomicInteger statusOrder = new AtomicInteger();

        @Tool(description = "Возвращает числовой идентификатор пользователя по его имени")
        public String getUserIdByName(@ToolParam(description = "Имя пользователя") String name) {
            userCalls.incrementAndGet();
            userOrder.set(seq.incrementAndGet());
            log.info("Инструмент getUserIdByName вызван для имени: {}", name);
            return "U-100";
        }

        @Tool(description = "Возвращает идентификатор активного заказа по идентификатору пользователя")
        public String getActiveOrderId(@ToolParam(description = "Идентификатор пользователя") String userId) {
            orderCalls.incrementAndGet();
            orderOrder.set(seq.incrementAndGet());
            log.info("Инструмент getActiveOrderId вызван для пользователя: {}", userId);
            return "O-555";
        }

        @Tool(description = "Возвращает статус заказа по его идентификатору")
        public String getOrderStatus(@ToolParam(description = "Идентификатор заказа") String orderId) {
            statusCalls.incrementAndGet();
            statusOrder.set(seq.incrementAndGet());
            log.info("Инструмент getOrderStatus вызван для заказа: {}", orderId);
            return "заказ " + orderId + ": ДОСТАВЛЕН-КОД-7777";
        }
    }

    // ----------------------------------------------------------------------------------------------
    // Тест 3: четыре независимых инструмента, задача требует ровно двух из них
    // ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Четыре независимых инструмента: вызваны только два нужных, лишние не тронуты")
    void selectiveToolUsageTest() {
        IndependentTools tools = new IndependentTools();
        ChatClient chatClient = ChatClient.create(gigaChatModel);

        String answer = chatClient
                .prompt()
                .user("Выполни две задачи, используя только подходящие инструменты: "
                        + "1) узнай столицу страны Франция; "
                        + "2) переведи слово 'кошка' на английский язык. "
                        + "Не используй инструменты, не относящиеся к этим задачам.")
                .tools(tools)
                .call()
                .content();

        log.info("Ответ модели (выборочные инструменты): {}", answer);

        assertThat(tools.capitalCalls.get())
                .as("инструмент получения столицы должен быть вызван")
                .isPositive();
        assertThat(tools.translateCalls.get())
                .as("инструмент перевода слова должен быть вызван")
                .isPositive();
        assertThat(tools.currencyCalls.get())
                .as("инструмент курса валют не относится к задаче и не должен вызываться")
                .isZero();
        assertThat(tools.distanceCalls.get())
                .as("инструмент расстояния не относится к задаче и не должен вызываться")
                .isZero();
        assertThat(answer).as("финальный ответ не должен быть пустым").isNotBlank();
    }

    public static class IndependentTools {
        private final AtomicInteger capitalCalls = new AtomicInteger();
        private final AtomicInteger translateCalls = new AtomicInteger();
        private final AtomicInteger currencyCalls = new AtomicInteger();
        private final AtomicInteger distanceCalls = new AtomicInteger();

        @Tool(description = "Возвращает столицу указанной страны")
        public String getCapitalOfCountry(@ToolParam(description = "Название страны") String country) {
            capitalCalls.incrementAndGet();
            log.info("Инструмент getCapitalOfCountry вызван для страны: {}", country);
            return "столица страны " + country + " — Париж";
        }

        @Tool(description = "Переводит русское слово на английский язык")
        public String translateWordToEnglish(@ToolParam(description = "Слово на русском") String word) {
            translateCalls.incrementAndGet();
            log.info("Инструмент translateWordToEnglish вызван для слова: {}", word);
            return "перевод слова '" + word + "' — cat";
        }

        @Tool(description = "Возвращает текущий курс обмена между двумя валютами")
        public String getCurrencyRate(
                @ToolParam(description = "Код исходной валюты") String from,
                @ToolParam(description = "Код целевой валюты") String to) {
            currencyCalls.incrementAndGet();
            log.info("Инструмент getCurrencyRate вызван: {} -> {}", from, to);
            return "курс " + from + "/" + to + " = 99.9";
        }

        @Tool(description = "Возвращает расстояние в километрах между двумя городами")
        public String getDistanceBetweenCities(
                @ToolParam(description = "Первый город") String cityA,
                @ToolParam(description = "Второй город") String cityB) {
            distanceCalls.incrementAndGet();
            log.info("Инструмент getDistanceBetweenCities вызван: {} - {}", cityA, cityB);
            return "расстояние между " + cityA + " и " + cityB + " = 1234 км";
        }
    }
}
