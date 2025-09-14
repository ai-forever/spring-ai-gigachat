package chat.giga.springai.autoconfigure.multimodality;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.autoconfigure.GigaChatAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;

public class MultimodalityIT {

    ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GigaChatAutoConfiguration.class))
            .withPropertyValues(
                    "spring.ai.gigachat.auth.scope=" + System.getenv("GIGACHAT_API_SCOPE"),
                    "spring.ai.gigachat.auth.bearer.client-id=" + System.getenv("GIGACHAT_API_CLIENT_ID"),
                    "spring.ai.gigachat.auth.bearer.client-secret=" + System.getenv("GIGACHAT_API_CLIENT_SECRET"),
                    "spring.ai.gigachat.auth.unsafe-ssl=true",
                    "spring.ai.gigachat.chat.options.model=GigaChat-2-Max");

    @Test
    @DisplayName("Тест проверяет, что доступна мультимодальность модели для вызова на примере vision")
    void givenPromptWithImage_whenCallChatModel_thenVisionCallIsSuccess() {
        contextRunner.run(context -> {
            GigaChatModel gigaChatModel = context.getBean(GigaChatModel.class);

            var imageResource = new ClassPathResource("/multimodality/cat.png");

            var userMessage = UserMessage.builder()
                    .text(
                            "Что ты видишь на картинке? Опиши словом состоящим из 3 букв. Пиши все буквы строчные, не используй заглавные")
                    .media(new Media(MimeTypeUtils.IMAGE_PNG, imageResource))
                    .build();

            ChatResponse response = gigaChatModel.call(new Prompt(userMessage));
            String assistantMessage = response.getResult().getOutput().getText();
            assertThat(assistantMessage, is(not(emptyOrNullString())));
            assertThat(assistantMessage, containsString("кот"));
        });
    }

    @Test
    @DisplayName(
            "Тест проверяет, что доступна мультимодальность модели для вызова на примере работы с текстовым файлом")
    void givenPromptWithTextFile_whenCallChatModel_thenVisionCallIsSuccess() {
        contextRunner.run(context -> {
            GigaChatModel gigaChatModel = context.getBean(GigaChatModel.class);

            var txtFileResource = new ClassPathResource("/multimodality/example.txt");

            var userMessage = UserMessage.builder()
                    .text("Какое первое слово в стихотворении в текстовом файле? Напиши его заглавными буквами.")
                    .media(new Media(MimeTypeUtils.TEXT_PLAIN, txtFileResource))
                    .build();

            ChatResponse response = gigaChatModel.call(new Prompt(userMessage));
            String assistantMessage = response.getResult().getOutput().getText();
            assertThat(assistantMessage, is(not(emptyOrNullString())));
            assertThat(assistantMessage, containsString("НОЧЬ"));
        });
    }
}
