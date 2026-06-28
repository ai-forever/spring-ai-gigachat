package chat.giga.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import chat.giga.springai.api.chat.file.UploadFileResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;

/**
 * Регресс-тесты на замечания ревью PR #127, актуализированные под Spring AI 2.0.0 GA (PATH A).
 *
 * <p>В GA слияние runtime+default опций и исполнение инструментов выполняет ChatClient
 * ({@code ToolCallingAdvisor}), а модель — одиночный запрос. Поэтому здесь проверяется только то,
 * что осталось ответственностью модели: загрузка media и подстановка default-опций при отсутствии
 * опций в промпте.
 */
@ExtendWith(MockitoExtension.class)
public class Pr127RegressionTest {

    private static final UUID UPLOADED_ID = UUID.fromString("5512e5c1-2829-4b44-ad2d-c9bce5f8b154");

    @Mock
    private GigaChatApi gigaChatApi;

    @Mock
    private GigaChatInternalProperties gigaChatInternalProperties;

    @Mock
    private CompletionResponse completionResponse;

    private GigaChatModel modelWithDefaults(GigaChatOptions defaults) {
        return GigaChatModel.builder()
                .gigaChatApi(gigaChatApi)
                .internalProperties(gigaChatInternalProperties)
                .defaultOptions(defaults)
                .build();
    }

    private GigaChatModel model() {
        return modelWithDefaults(GigaChatOptions.builder().model("GigaChat-2").build());
    }

    private void stubChatCompletion() {
        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(completionResponse, HttpStatusCode.valueOf(200)));
    }

    private CompletionRequest captureChatCompletionEntityRequest() {
        ArgumentCaptor<CompletionRequest> cap = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(gigaChatApi).chatCompletionEntity(cap.capture(), any());
        return cap.getValue();
    }

    // ----------------------------------------------------------------------------------------
    // BLOCKER (PR #127): загрузка media остаётся ответственностью модели (buildRequestPrompt).
    // ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("BLOCKER: media без id загружается через uploadFile; call() не падает на UUID.fromString(null)")
    void mediaWithoutId_isUploaded_andGetsId() {
        when(gigaChatApi.uploadFile(any()))
                .thenReturn(ResponseEntity.ok(
                        new UploadFileResponse(10, 0L, "file.txt", UPLOADED_ID, "file", "general", "private")));
        stubChatCompletion();

        Media media = Media.builder()
                .data("документ")
                .mimeType(MimeTypeUtils.TEXT_PLAIN)
                .build();
        assertNull(media.getId(), "предусловие: у свежего media нет id");

        Prompt prompt = new Prompt(
                List.of(UserMessage.builder().text("Что в файле?").media(media).build()),
                GigaChatOptions.builder().model("GigaChat-2").build());

        // Если бы id не проставился, createRequest упал бы на UUID.fromString(null) -> успешный вызов и есть
        // доказательство.
        model().call(prompt);

        verify(gigaChatApi, times(1)).uploadFile(any());
    }

    @Test
    @DisplayName("BLOCKER: media с заранее заданным id повторно не загружается")
    void mediaWithId_isNotReUploaded() {
        stubChatCompletion();

        Media media = Media.builder()
                .id(UPLOADED_ID.toString())
                .data("документ")
                .mimeType(MimeTypeUtils.TEXT_PLAIN)
                .build();

        Prompt prompt = new Prompt(
                List.of(UserMessage.builder().text("Кто ты?").media(media).build()),
                GigaChatOptions.builder().model("GigaChat-2").build());

        model().call(prompt);

        verify(gigaChatApi, never()).uploadFile(any());
    }

    // ----------------------------------------------------------------------------------------
    // toolContext остаётся в ToolCallingChatOptions и в GA — round-trip build()/mutate().
    // ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("toolContext сохраняется после build()")
    void toolContext_survivesBuild() {
        GigaChatOptions options =
                GigaChatOptions.builder().toolContext(Map.of("tenant", "acme")).build();
        assertEquals(Map.of("tenant", "acme"), options.getToolContext());
    }

    @Test
    @DisplayName("toolContext переживает mutate()/copy()")
    void toolContext_survivesMutate() {
        GigaChatOptions base =
                GigaChatOptions.builder().toolContext(Map.of("k", "v")).build();
        GigaChatOptions copy = base.mutate().build();
        assertEquals(Map.of("k", "v"), copy.getToolContext());
    }

    // ----------------------------------------------------------------------------------------
    // PATH A: при ОТСУТСТВИИ опций в промпте модель подставляет свои default-опции
    // (слияние частичных runtime+default делает ChatClient, не модель).
    // ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("При отсутствии опций в промпте до CompletionRequest доходят default-опции модели")
    void defaultsUsed_whenNoRuntimeOptions() {
        GigaChatModel model = modelWithDefaults(GigaChatOptions.builder()
                .model("default-model")
                .temperature(0.1)
                .build());
        stubChatCompletion();

        model.call(new Prompt(List.of(new UserMessage("hi"))));

        CompletionRequest req = captureChatCompletionEntityRequest();
        assertEquals("default-model", req.getModel());
        assertEquals(0.1, req.getTemperature());
    }

    @Test
    @DisplayName("Частичные runtime-опции сливаются поверх default: model из runtime, "
            + "незаданная temperature наследуется из default (как Mistral/OpenAI и ChatClient)")
    void partialRuntimeOptions_mergedOverDefaults() {
        GigaChatModel model = modelWithDefaults(GigaChatOptions.builder()
                .model("default-model")
                .temperature(0.1)
                .build());
        stubChatCompletion();

        Prompt prompt = new Prompt(
                List.of(new UserMessage("hi")),
                GigaChatOptions.builder().model("runtime-model").build());
        model.call(prompt);

        CompletionRequest req = captureChatCompletionEntityRequest();
        assertEquals("runtime-model", req.getModel(), "model переопределяется runtime-опциями");
        assertEquals(0.1, req.getTemperature(), "незаданная temperature наследуется из default-опций модели");
    }

    @Test
    @DisplayName("Переносимые ChatOptions (не GigaChatOptions) не вызывают ClassCastException и сливаются с default")
    void portableChatOptions_doNotThrow_andMerge() {
        GigaChatModel model = modelWithDefaults(GigaChatOptions.builder()
                .model("default-model")
                .temperature(0.1)
                .build());
        stubChatCompletion();

        Prompt prompt = new Prompt(
                List.of(new UserMessage("hi")),
                org.springframework.ai.chat.prompt.ChatOptions.builder()
                        .temperature(0.9)
                        .build());
        model.call(prompt);

        CompletionRequest req = captureChatCompletionEntityRequest();
        assertEquals("default-model", req.getModel(), "model из default сохраняется");
        assertEquals(0.9, req.getTemperature(), "temperature переопределяется переносимыми опциями");
    }
}
