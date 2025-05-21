package ru.sber.machine.ai.gigachat.autoconfigure.system_prompt_sorting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.forever.gigachat.GigaChatModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("model-options")
@EnableAutoConfiguration
@SpringBootTest(classes = SystemPromptSortingNegativeTest.MyCustomApplication.class)
@TestPropertySource(properties = {"spring.ai.gigachat.internal.make-system-prompt-first-message-in-memory=false"})
@EnabledIfEnvironmentVariable(named = "GIGACHAT_API_CLIENT_SECRET", matches = ".*")
public class SystemPromptSortingNegativeTest {

    @SpringBootConfiguration
    public static class MyCustomApplication {}

    @Autowired
    GigaChatModel gigaChatModel;

    // Если тест упадет, возможно GigaChat починили это на своей стороне и нужно выпилить тесты и сортировку
    @Test
    @DisplayName(
            "Тест проверяет, что при вызове чата, если в истории чата системный промпт не на первом месте и не включена сортировка то получаем 422 ошибку от GigaChat")
    void givenMessagesChatHistoryWithSystemPropmpt_whenSystemPromptSortingIsOff_thenThrowException422Status() {
        Prompt prompt = new Prompt(List.of(
                new UserMessage("Какая версия java сейчас актуальна?"),
                new AssistantMessage("23"),
                new UserMessage("Кто создал Java?"),
                new SystemMessage("Ты эксперт по работе с  java. Отвечай на вопросы одним словом")));

        // Проверяем, что метод выбрасывает ожидаемое исключение
        NonTransientAiException exception =
                assertThrows(NonTransientAiException.class, () -> gigaChatModel.call(prompt));

        assertThat(exception.getMessage(), containsStringIgnoringCase("system message must be the first message"));
    }

    @Test
    @DisplayName(
            "Тест проверяет, что при вызове чата, если в истории есть два системных промпта, выбрасывается исключение")
    void givenMessagesChatHistoryWithTwoSystemPropmpt_whenSystemPromptSorting_thenThrowIllegalStateException() {
        Prompt prompt = new Prompt(List.of(
                new UserMessage("Какая версия java сейчас актуальна?"),
                new AssistantMessage("23"),
                new SystemMessage("Ты эксперт по работе с  kotlin. Отвечай на вопросы одним словом"),
                new UserMessage("Кто создал Java?"),
                new SystemMessage("Ты эксперт по работе с  java. Отвечай на вопросы одним словом")));
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> gigaChatModel.call(prompt));

        assertThat(exception.getMessage(), containsStringIgnoringCase("System prompt message must be the only one"));
    }
}
