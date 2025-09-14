package chat.giga.springai.api.chat;

import static chat.giga.springai.GigaChatModel.DEFAULT_MODEL_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatApiScope;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import chat.giga.springai.api.chat.models.ModelsResponse;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;

@Slf4j
public class GigaChatApiIT {
    private static final GigaChatApi gigaChatApi;
    private static final GigaChatApi gigaChatApi2;

    static {
        GigaChatApiProperties oldApiProperties = GigaChatApiProperties.builder()
                .scope(GigaChatApiScope.valueOf(System.getenv("GIGACHAT_API_SCOPE")))
                .clientId(System.getenv("GIGACHAT_API_CLIENT_ID"))
                .clientSecret(System.getenv("GIGACHAT_API_CLIENT_SECRET"))
                .unsafeSsl(true)
                .build();
        gigaChatApi = new GigaChatApi(oldApiProperties);

        GigaChatApiProperties newApiProperties = GigaChatApiProperties.builder()
                .auth(GigaChatAuthProperties.builder()
                        .scope(GigaChatApiScope.valueOf(System.getenv("GIGACHAT_API_SCOPE")))
                        .unsafeSsl(true)
                        .bearer(GigaChatAuthProperties.Bearer.builder()
                                .clientId(System.getenv("GIGACHAT_API_CLIENT_ID"))
                                .clientSecret(System.getenv("GIGACHAT_API_CLIENT_SECRET"))
                                .build())
                        .build())
                .build();
        gigaChatApi2 = new GigaChatApi(newApiProperties);
    }

    public static Stream<Arguments> gigaChatApiProvider() {
        return Stream.of(Arguments.of(gigaChatApi), Arguments.of(gigaChatApi2));
    }

    @ParameterizedTest
    @MethodSource("gigaChatApiProvider")
    @DisplayName("Тест проверяет корректное получение списка моделей")
    void modelsTest(GigaChatApi gigaChatApi) {
        final ResponseEntity<ModelsResponse> response = gigaChatApi.models();

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody(), is(not(nullValue())));
        assertThat(response.getBody().getData(), is(not(nullValue())));
        assertThat(response.getBody().getData(), is(not(empty())));
        assertThat(response.getBody().getData(), everyItem(hasProperty("id")));
    }

    @ParameterizedTest
    @MethodSource("gigaChatApiProvider")
    @DisplayName("Авторизация и проверка /chat/completions")
    void authAndChatTest(GigaChatApi gigaChatApi) {
        final CompletionRequest chatRequest = CompletionRequest.builder()
                .model(DEFAULT_MODEL_NAME)
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

        final CompletionRequest asyncChatRequest = CompletionRequest.builder().model(DEFAULT_MODEL_NAME).stream(true)
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
