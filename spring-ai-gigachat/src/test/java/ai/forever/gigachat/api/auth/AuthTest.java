package ai.forever.gigachat.api.auth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import ai.forever.extension.GigaChatTestPropertiesExtension;
import ai.forever.gigachat.api.chat.GigaChatApi;
import ai.forever.gigachat.api.chat.completion.CompletionRequest;
import ai.forever.gigachat.api.chat.completion.CompletionResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.ResponseEntity;

@Slf4j
@ExtendWith(GigaChatTestPropertiesExtension.class)
@EnabledIfEnvironmentVariable(named = "GIGACHAT_API_CLIENT_SECRET", matches = ".*")
public class AuthTest {

    @EnumSource(GigaChatApi.ChatModel.class)
    @DisplayName("Авторизация и проверка /chat/completions")
    @ParameterizedTest(name = "{0}")
    void authThenChatTest(GigaChatApi.ChatModel model, GigaChatApiProperties properties) {
        assertDoesNotThrow(() -> check(properties, model));
    }

    /**
     * Основное тело теста
     *
     * @param authProperties авторизационные параметры
     */
    private void check(GigaChatApiProperties authProperties, GigaChatApi.ChatModel chatModel) {
        final GigaChatApi gigaChatApi = new GigaChatApi(authProperties);

        final CompletionRequest chatRequest = CompletionRequest.builder()
                .model(chatModel.getName())
                .messages(List.of(CompletionRequest.Message.builder()
                        .role(CompletionRequest.Role.user)
                        .content("Расскажи, как дела?")
                        .build()))
                .build();

        final ResponseEntity<CompletionResponse> response = gigaChatApi.chatCompletionEntity(chatRequest);

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody(), is(not(nullValue())));
        assertThat(response.getBody().getChoices(), is(not(nullValue())));
        assertThat(response.getBody().getChoices(), is(not(empty())));
        assertThat(response.getBody().getChoices().get(0), is(not(nullValue())));
        assertThat(response.getBody().getChoices().get(0).getMessage(), is(not(nullValue())));
        assertThat(response.getBody().getChoices().get(0).getMessage().getContent(), is(not(emptyOrNullString())));

        log.info(
                "Model sync response: {}",
                response.getBody().getChoices().get(0).getMessage().getContent());

        final CompletionRequest asyncChatRequest = CompletionRequest.builder().model(chatModel.getName()).stream(true)
                .messages(List.of(CompletionRequest.Message.builder()
                        .role(CompletionRequest.Role.user)
                        .content("Расскажи, как дела?")
                        .build()))
                .build();

        final List<CompletionResponse> streamedResponse =
                gigaChatApi.chatCompletionStream(asyncChatRequest).collectList().block();

        assertThat(streamedResponse, is(not(nullValue())));
        assertThat(streamedResponse, is(not(empty())));
        assertThat(streamedResponse.get(0).getChoices(), is(not(nullValue())));
        assertThat(streamedResponse.get(0).getChoices(), is(not(empty())));

        log.info("Model async response");
        streamedResponse.forEach(x -> log.info("Chunk {}", x));
    }
}
