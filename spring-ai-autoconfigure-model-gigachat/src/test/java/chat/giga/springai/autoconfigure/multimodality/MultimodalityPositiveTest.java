package chat.giga.springai.autoconfigure.multimodality;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import chat.giga.springai.GigaChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.MimeTypeUtils;

@ActiveProfiles("model-options")
@EnableAutoConfiguration
@SpringBootTest(classes = MultimodalityPositiveTest.MyCustomApplication.class)
@TestPropertySource(properties = {"spring.ai.gigachat.chat.options.model=GigaChat-2-Max"})
@EnabledIfEnvironmentVariable(named = "GIGACHAT_API_CLIENT_SECRET", matches = ".*")
public class MultimodalityPositiveTest {

    @SpringBootConfiguration
    public static class MyCustomApplication {}

    @Autowired
    GigaChatModel gigaChatModel;

    @Test
    @DisplayName("Тест проверяет, что доступна мультимодальность модели для вызова на примере vision")
    void givenPromptWithImage_whenCallChatModel_thenVisionCallIsSuccess() {
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
    }

    @Test
    @DisplayName(
            "Тест проверяет, что доступна мультимодальность модели для вызова на примере работы с текстовым файлом")
    void givenPromptWithTextFile_whenCallChatModel_thenVisionCallIsSuccess() {
        var txtFileResource = new ClassPathResource("/multimodality/example.txt");

        var userMessage = UserMessage.builder()
                .text("Какое первое слово в стихотворении в текстовом файле? Напиши его заглавными буквами.")
                .media(new Media(MimeTypeUtils.TEXT_PLAIN, txtFileResource))
                .build();

        ChatResponse response = gigaChatModel.call(new Prompt(userMessage));
        String assistantMessage = response.getResult().getOutput().getText();
        assertThat(assistantMessage, is(not(emptyOrNullString())));
        assertThat(assistantMessage, containsString("НОЧЬ"));
    }
}
