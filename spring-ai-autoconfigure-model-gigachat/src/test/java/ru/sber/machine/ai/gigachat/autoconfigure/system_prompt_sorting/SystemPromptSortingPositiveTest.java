package ru.sber.machine.ai.gigachat.autoconfigure.system_prompt_sorting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import ai.forever.gigachat.GigaChatModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("model-options")
@EnableAutoConfiguration
@SpringBootTest(classes = SystemPromptSortingPositiveTest.MyCustomApplication.class)
@TestPropertySource(properties = {"spring.ai.gigachat.internal.make-system-prompt-first-message-in-memory=true"})
@EnabledIfEnvironmentVariable(named = "GIGACHAT_API_CLIENT_SECRET", matches = ".*")
public class SystemPromptSortingPositiveTest {

    @SpringBootConfiguration
    public static class MyCustomApplication {}

    @Autowired
    GigaChatModel gigaChatModel;

    @Test
    @DisplayName(
            "Тест проверяет, что при вызове чата выполняется корректная сортировка сообщений перед отправкой. Системный промпт в начале")
    void givenMessagesChatHistoryWithSystemPropmpt_whenSystemPromptSortingIsOn_thenCallIsSuccess() {
        Prompt prompt = new Prompt(List.of(
                new UserMessage("Какая версия java сейчас актуальна?"),
                new AssistantMessage("23"),
                new UserMessage("Кто создал Java?"),
                new SystemMessage("Ты эксперт по работе с  java. Отвечай на вопросы одним словом")));
        ChatResponse callResponse = gigaChatModel.call(prompt);
        String assistantResponseString = callResponse.getResult().getOutput().getText();

        assertThat(assistantResponseString, is(not(emptyOrNullString())));
    }
}
