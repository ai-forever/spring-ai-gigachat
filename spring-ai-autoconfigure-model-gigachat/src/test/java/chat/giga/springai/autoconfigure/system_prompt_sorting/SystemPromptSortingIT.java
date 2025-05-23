package chat.giga.springai.autoconfigure.system_prompt_sorting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.autoconfigure.GigaChatAutoConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class SystemPromptSortingIT {

    ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GigaChatAutoConfiguration.class))
            .withPropertyValues(
                    "spring.ai.gigachat.scope=" + System.getenv("GIGACHAT_API_SCOPE"),
                    "spring.ai.gigachat.client-id=" + System.getenv("GIGACHAT_API_CLIENT_ID"),
                    "spring.ai.gigachat.client-secret=" + System.getenv("GIGACHAT_API_CLIENT_SECRET"),
                    "spring.ai.gigachat.unsafe-ssl=true",
                    "spring.ai.gigachat.chat.options.model=GigaChat-2-Max");

    @Test
    @DisplayName(
            "Тест проверяет, что при вызове чата выполняется корректная сортировка сообщений перед отправкой. Системный промпт в начале")
    void givenMessagesChatHistoryWithSystemPropmpt_whenSystemPromptSortingIsOn_thenCallIsSuccess() {
        contextRunner
                .withPropertyValues("spring.ai.gigachat.internal.make-system-prompt-first-message-in-memory=true")
                .run(context -> {
                    GigaChatModel gigaChatModel = context.getBean(GigaChatModel.class);

                    Prompt prompt = new Prompt(List.of(
                            new UserMessage("Какая версия java сейчас актуальна?"),
                            new AssistantMessage("23"),
                            new UserMessage("Кто создал Java?"),
                            new SystemMessage("Ты эксперт по работе с  java. Отвечай на вопросы одним словом")));
                    ChatResponse callResponse = gigaChatModel.call(prompt);
                    String assistantResponseString =
                            callResponse.getResult().getOutput().getText();

                    assertThat(assistantResponseString, is(not(emptyOrNullString())));
                });
    }

    // Если тест упадет, возможно GigaChat починили это на своей стороне и нужно выпилить тесты и сортировку
    @Test
    @DisplayName(
            "Тест проверяет, что при вызове чата, если в истории чата системный промпт не на первом месте и не включена сортировка то получаем 422 ошибку от GigaChat")
    void givenMessagesChatHistoryWithSystemPropmpt_whenSystemPromptSortingIsOff_thenThrowException422Status() {
        contextRunner
                .withPropertyValues("spring.ai.gigachat.internal.make-system-prompt-first-message-in-memory=false")
                .run(context -> {
                    GigaChatModel gigaChatModel = context.getBean(GigaChatModel.class);
                    Prompt prompt = new Prompt(List.of(
                            new UserMessage("Какая версия java сейчас актуальна?"),
                            new AssistantMessage("23"),
                            new UserMessage("Кто создал Java?"),
                            new SystemMessage("Ты эксперт по работе с  java. Отвечай на вопросы одним словом")));

                    // Проверяем, что метод выбрасывает ожидаемое исключение
                    NonTransientAiException exception =
                            assertThrows(NonTransientAiException.class, () -> gigaChatModel.call(prompt));

                    assertThat(
                            exception.getMessage(),
                            containsStringIgnoringCase("system message must be the first message"));
                });
    }
}
