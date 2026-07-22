package chat.giga.springai.autoconfigure.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import chat.giga.springai.GigaChatModel;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Регрессионный интеграционный тест агентных сценариев на реальном API GigaChat (PATH A, Spring AI
 * 2.0.0 GA): многошаговые тул-цепочки через ChatClient (ToolCallingAdvisor крутит цикл, скармливая
 * результат предыдущего инструмента в следующий запрос) и многоходовая память диалога (ChatMemory).
 * Объединяет прежние ToolChain- и ReAct-тесты.
 *
 * <p><b>Принцип ассертов.</b> Жёстко проверяются только инварианты ФРЕЙМВОРКА (наш код / Spring AI):
 * вызов завершился ответом, память персистит сообщения. Всё, что зависит от качества LLM (позвала ли
 * модель нужные инструменты, в каком порядке, не дёрнула ли лишние, дословно ли перенесла результат в
 * ответ), гейтится через {@link org.junit.jupiter.api.Assumptions#assumeTrue}: на слабой модели тест
 * уходит в SKIP (сигнал), а не роняет сборку.
 *
 * <p>Базовый кейс «модель реально зовёт инструмент» жёстко покрыт в
 * GigaChatAutoConfigurationIT.toolCallingThroughChatClientTest и GigaChatModelFullIT.functionCallMode_auto.
 * Многораундовый цикл advisor'а здесь неизбежно завязан на кооперацию модели — детерминированно
 * заставить слабую модель пройти всю цепочку нельзя, поэтому он под assumeTrue.
 */
@ActiveProfiles("it")
@EnableAutoConfiguration
@SpringBootTest(classes = GigaChatAgentIT.MyCustomApplication.class)
public class GigaChatAgentIT {

    private static final Logger log = LoggerFactory.getLogger(GigaChatAgentIT.class);

    @SpringBootConfiguration
    public static class MyCustomApplication {}

    @Autowired
    GigaChatModel gigaChatModel;

    // ----------------------------------------------------------------------------------------------
    // ReAct-цепочка: год основания -> вычитание (два зависимых раунда)
    // ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("ReAct-цепочка: модель пошагово зовёт два зависимых инструмента (lookup -> subtract)")
    void reactChainTest() {
        CityAgeTools tools = new CityAgeTools();

        String answer = ChatClient.create(gigaChatModel)
                .prompt()
                .system("Рассуждай строго пошагово и перед ответом обязательно пользуйся доступными "
                        + "инструментами, а не своими знаниями: сначала используй инструмент, затем "
                        + "наблюдай его результат, затем делай следующий шаг.")
                .user("Сколько лет городу Москва в 2026 году? Сначала узнай год основания инструментом "
                        + "lookupFoundationYear, затем посчитай разницу инструментом subtract.")
                .tools(tools)
                .call()
                .content();

        log.info("Ответ ReAct-цепочки: {}", answer);

        assertThat(answer).isNotBlank();
        assumeTrue(
                tools.lookupCalls.get() > 0 && tools.subtractCalls.get() > 0,
                "SKIP: модель не прошла по цепочке инструментов (поведение LLM, не регресс фреймворка)");
        assumeTrue(answer.contains("879"), "SKIP: модель не отразила результат вычисления в ответе (поведение LLM)");
    }

    public static class CityAgeTools {
        private final AtomicInteger lookupCalls = new AtomicInteger();
        private final AtomicInteger subtractCalls = new AtomicInteger();

        @Tool(description = "Возвращает год основания указанного города по его названию")
        public int lookupFoundationYear(String cityName) {
            lookupCalls.incrementAndGet();
            log.info("Инструмент lookupFoundationYear вызван для города: {}", cityName);
            return 1147;
        }

        @Tool(description = "Возвращает разницу двух целых чисел: a минус b")
        public int subtract(int a, int b) {
            subtractCalls.incrementAndGet();
            log.info("Инструмент subtract вызван с аргументами a={}, b={}", a, b);
            return a - b;
        }
    }

    // ----------------------------------------------------------------------------------------------
    // Цепочка из 2 шагов: город -> IATA-код -> погода по коду
    // ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Цепочка из 2 шагов: сначала IATA-код города, затем погода по этому коду")
    void twoStepChainTest() {
        TwoStepTools tools = new TwoStepTools();

        String answer = ChatClient.create(gigaChatModel)
                .prompt()
                .user("Узнай текущую погоду в городе Сочи. Сначала с помощью инструмента получи IATA-код "
                        + "этого города, а затем по полученному коду узнай погоду. Используй только инструменты.")
                .tools(tools)
                .call()
                .content();

        log.info("Ответ модели (2 шага): {}", answer);

        assertThat(answer).isNotBlank();
        assumeTrue(
                tools.iataCalls.get() > 0 && tools.weatherCalls.get() > 0,
                "SKIP: модель не прошла по 2-шаговой цепочке инструментов (поведение LLM)");
        assumeTrue(
                answer.contains("42"),
                "SKIP: модель не отразила температуру из результата инструмента в ответе (поведение LLM)");
    }

    public static class TwoStepTools {
        private final AtomicInteger iataCalls = new AtomicInteger();
        private final AtomicInteger weatherCalls = new AtomicInteger();

        @Tool(description = "Возвращает трёхбуквенный IATA-код аэропорта по русскому названию города")
        public String findCityIataCode(@ToolParam(description = "Название города на русском") String cityName) {
            iataCalls.incrementAndGet();
            log.info("Инструмент findCityIataCode вызван для города: {}", cityName);
            return "AER";
        }

        @Tool(description = "Возвращает текущую погоду по трёхбуквенному IATA-коду аэропорта")
        public String getWeatherByIataCode(@ToolParam(description = "IATA-код аэропорта") String code) {
            weatherCalls.incrementAndGet();
            log.info("Инструмент getWeatherByIataCode вызван для кода: {}", code);
            return "в коде " + code + " сейчас ровно 42 градуса";
        }
    }

    // ----------------------------------------------------------------------------------------------
    // Глубокая цепочка из 3 шагов: имя -> userId -> orderId -> статус заказа
    // ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Глубокая цепочка из 3 шагов: имя -> userId -> orderId -> статус заказа")
    void threeStepChainTest() {
        ThreeStepTools tools = new ThreeStepTools();

        String answer = ChatClient.create(gigaChatModel)
                .prompt()
                .user("Узнай статус активного заказа пользователя по имени Иван. Действуй строго по шагам, "
                        + "используя только инструменты: сначала получи идентификатор пользователя по имени, "
                        + "затем по идентификатору пользователя получи идентификатор активного заказа, "
                        + "затем по идентификатору заказа получи его статус. Верни итоговый статус.")
                .tools(tools)
                .call()
                .content();

        log.info("Ответ модели (3 шага): {}", answer);

        assertThat(answer).isNotBlank();
        assumeTrue(
                tools.userCalls.get() > 0 && tools.orderCalls.get() > 0 && tools.statusCalls.get() > 0,
                "SKIP: модель не прошла по 3-шаговой цепочке инструментов (поведение LLM)");
        assumeTrue(
                answer.contains("7777"),
                "SKIP: модель не отразила код статуса из результата инструмента в ответе (поведение LLM)");
    }

    public static class ThreeStepTools {
        private final AtomicInteger userCalls = new AtomicInteger();
        private final AtomicInteger orderCalls = new AtomicInteger();
        private final AtomicInteger statusCalls = new AtomicInteger();

        @Tool(description = "Возвращает числовой идентификатор пользователя по его имени")
        public String getUserIdByName(@ToolParam(description = "Имя пользователя") String name) {
            userCalls.incrementAndGet();
            log.info("Инструмент getUserIdByName вызван для имени: {}", name);
            return "U-100";
        }

        @Tool(description = "Возвращает идентификатор активного заказа по идентификатору пользователя")
        public String getActiveOrderId(@ToolParam(description = "Идентификатор пользователя") String userId) {
            orderCalls.incrementAndGet();
            log.info("Инструмент getActiveOrderId вызван для пользователя: {}", userId);
            return "O-555";
        }

        @Tool(description = "Возвращает статус заказа по его идентификатору")
        public String getOrderStatus(@ToolParam(description = "Идентификатор заказа") String orderId) {
            statusCalls.incrementAndGet();
            log.info("Инструмент getOrderStatus вызван для заказа: {}", orderId);
            return "заказ " + orderId + ": ДОСТАВЛЕН-КОД-7777";
        }
    }

    // ----------------------------------------------------------------------------------------------
    // Четыре независимых инструмента, задача требует ровно двух из них
    // ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Четыре независимых инструмента: задача требует двух нужных, лишние не относятся к задаче")
    void selectiveToolUsageTest() {
        IndependentTools tools = new IndependentTools();

        String answer = ChatClient.create(gigaChatModel)
                .prompt()
                .user("Выполни две задачи, используя только подходящие инструменты: "
                        + "1) узнай столицу страны Франция; "
                        + "2) переведи слово 'кошка' на английский язык. "
                        + "Не используй инструменты, не относящиеся к этим задачам.")
                .tools(tools)
                .call()
                .content();

        log.info("Ответ модели (выборочные инструменты): {}", answer);

        assertThat(answer).isNotBlank();
        assumeTrue(
                tools.capitalCalls.get() > 0 && tools.translateCalls.get() > 0,
                "SKIP: модель не вызвала оба нужных инструмента (поведение LLM)");
        assumeTrue(
                tools.currencyCalls.get() == 0 && tools.distanceCalls.get() == 0,
                "SKIP: модель дёрнула нерелевантные инструменты (поведение LLM, не регресс фреймворка)");
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

    // ----------------------------------------------------------------------------------------------
    // Многоходовая память диалога
    // ----------------------------------------------------------------------------------------------

    /**
     * MessageWindowChatMemory + InMemoryChatMemoryRepository + MessageChatMemoryAdvisor с единым
     * conversationId. Жёстко проверяется WRITE-инвариант адвайзора: отправленное сообщение реально
     * записано в память под conversationId. READ/inject-половина (подмешивание истории в следующий
     * запрос) наблюдается только через ответ модели, поэтому recall гейтится через assumeTrue.
     */
    @Test
    @DisplayName("Многоходовая ChatMemory: история диалога сохраняется в память; recall модели — мягко")
    void multiTurnChatMemoryTest() {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();

        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        String conversationId = "agent-memory-conversation";

        chatClient
                .prompt()
                .user("Запомни: меня зовут Артём и мой любимый цвет — фиолетовый.")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        String secondAnswer = chatClient
                .prompt()
                .user("Как меня зовут и какой мой любимый цвет?")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        log.info("Ответ модели на ход 2: {}", secondAnswer);

        // framework: адвайзор записал отправленные сообщения в память под conversationId
        assertThat(chatMemory.get(conversationId))
                .as("MessageChatMemoryAdvisor должен персистить сообщения диалога")
                .anyMatch(m -> m.getText().contains("Артём") && m.getText().contains("фиолетовый"));
        assertThat(secondAnswer).isNotBlank();

        // поведение LLM -> SKIP: воспроизвела ли модель контекст из памяти
        assumeTrue(
                secondAnswer.toLowerCase().contains("артём")
                        && secondAnswer.toLowerCase().contains("фиолетов"),
                "SKIP: модель не воспроизвела контекст из памяти (поведение LLM, не регресс фреймворка)");
    }
}
