package chat.giga.springai.autoconfigure.regression;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Регрессионный интеграционный тест для ReAct-агента (reason + act + observe) и многоходовой памяти
 * на реальном API GigaChat. Подтверждает работу PATH A (исполнение инструментов через ChatClient /
 * ToolCallingAdvisor) на цепочке из нескольких инструментов, а также корректность сохранения и
 * восстановления контекста диалога через ChatMemory между несколькими ходами.
 */
@ActiveProfiles("it")
@EnableAutoConfiguration
@SpringBootTest(classes = GigaChatReactAgentIT.MyCustomApplication.class)
public class GigaChatReactAgentIT {

    private static final Logger log = LoggerFactory.getLogger(GigaChatReactAgentIT.class);

    @SpringBootConfiguration
    public static class MyCustomApplication {}

    @Autowired
    GigaChatModel gigaChatModel;

    /**
     * ReAct-петля: system-промпт инструктирует модель рассуждать пошагово и пользоваться
     * инструментами перед ответом. Модель должна сначала узнать год основания города инструментом
     * lookupFoundationYear, затем посчитать разницу инструментом subtract. Проверяем, что оба
     * инструмента вызваны, причём lookupFoundationYear раньше subtract, и что правильная разница
     * (2026 - 1147 = 879) попала в финальный ответ.
     */
    @Test
    @DisplayName("ReAct-петля: модель пошагово зовёт два инструмента (lookup -> subtract) и доходит до ответа")
    void reactLoopWithSequentialToolsTest() {
        CityAgeTools tools = new CityAgeTools();
        ChatClient chatClient = ChatClient.create(gigaChatModel);

        String answer = chatClient
                .prompt()
                .system("Ты ReAct-агент. Рассуждай строго пошагово и перед ответом обязательно пользуйся "
                        + "доступными инструментами, а не своими знаниями. Сначала используй инструмент, "
                        + "затем наблюдай его результат, затем делай следующий шаг.")
                .user("Сколько лет городу Москва в 2026 году? Сначала узнай год основания инструментом "
                        + "lookupFoundationYear, затем посчитай разницу инструментом subtract.")
                .tools(tools)
                .call()
                .content();

        log.info("Ответ ReAct-агента: {}", answer);

        assertThat(tools.lookupCalls.get())
                .as("инструмент lookupFoundationYear должен быть вызван")
                .isPositive();
        assertThat(tools.subtractCalls.get())
                .as("инструмент subtract должен быть вызван")
                .isPositive();
        assertThat(tools.lookupOrder.get())
                .as("lookupFoundationYear должен быть вызван раньше subtract")
                .isLessThan(tools.subtractOrder.get());
        assertThat(answer).isNotBlank();
        assertThat(answer)
                .as("правильная разница 2026 - 1147 = 879 должна попасть в финальный ответ")
                .contains("879");
    }

    public static class CityAgeTools {
        private final AtomicInteger seq = new AtomicInteger();
        private final AtomicInteger lookupCalls = new AtomicInteger();
        private final AtomicInteger subtractCalls = new AtomicInteger();
        private final AtomicInteger lookupOrder = new AtomicInteger();
        private final AtomicInteger subtractOrder = new AtomicInteger();

        @Tool(description = "Возвращает год основания указанного города по его названию")
        public int lookupFoundationYear(String cityName) {
            lookupCalls.incrementAndGet();
            lookupOrder.set(seq.incrementAndGet());
            log.info("Инструмент lookupFoundationYear вызван для города: {}", cityName);
            return 1147;
        }

        @Tool(description = "Возвращает разницу двух целых чисел: a минус b")
        public int subtract(int a, int b) {
            subtractCalls.incrementAndGet();
            subtractOrder.set(seq.incrementAndGet());
            log.info("Инструмент subtract вызван с аргументами a={}, b={}", a, b);
            return a - b;
        }
    }

    /**
     * Многоходовая память: используем MessageWindowChatMemory + InMemoryChatMemoryRepository +
     * MessageChatMemoryAdvisor с единым conversationId. На первом ходу сообщаем модели имя и любимый
     * цвет, на втором ходу (тот же conversationId) спрашиваем их обратно. Проверяем, что ответ
     * содержит запомненные имя и цвет, то есть контекст диалога восстановлен из памяти.
     */
    @Test
    @DisplayName("Многоходовая ChatMemory: модель помнит имя и цвет между ходами одного диалога")
    void multiTurnChatMemoryTest() {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();

        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        String conversationId = "react-agent-memory-conversation";

        String firstAnswer = chatClient
                .prompt()
                .user("Запомни: меня зовут Артём и мой любимый цвет — фиолетовый.")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        log.info("Ответ модели на ход 1: {}", firstAnswer);

        String secondAnswer = chatClient
                .prompt()
                .user("Как меня зовут и какой мой любимый цвет?")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        log.info("Ответ модели на ход 2: {}", secondAnswer);

        assertThat(secondAnswer).isNotBlank();
        assertThat(secondAnswer)
                .as("модель должна вспомнить имя из памяти диалога")
                .containsIgnoringCase("Артём");
        assertThat(secondAnswer)
                .as("модель должна вспомнить любимый цвет из памяти диалога")
                .containsIgnoringCase("фиолетов");
    }
}
