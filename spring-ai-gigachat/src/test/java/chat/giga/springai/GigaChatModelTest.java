package chat.giga.springai;

import static chat.giga.springai.advisor.GigaChatCachingAdvisor.X_SESSION_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import chat.giga.springai.advisor.GigaChatCachingAdvisor;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import chat.giga.springai.api.chat.param.FunctionCallParam;
import chat.giga.springai.tool.GigaTools;
import chat.giga.springai.tool.annotation.GigaTool;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
public class GigaChatModelTest {
    @Mock
    private GigaChatApi gigaChatApi;

    @Mock
    private GigaChatInternalProperties gigaChatInternalProperties;

    @Mock
    private CompletionResponse response;

    private GigaChatModel gigaChatModel;

    @BeforeEach
    void setUp() {
        gigaChatModel = GigaChatModel.builder()
                .gigaChatApi(gigaChatApi)
                .internalProperties(gigaChatInternalProperties)
                .build();
    }

    @Test
    void testGigaChatOptions_withFunctionCallParam() {
        var functionCallback = GigaTools.from(new TestTool());

        var functionCallParam = FunctionCallParam.builder()
                .name("testToolName")
                .partialArguments(Map.of("arg1", "DEFAULT"))
                .build();

        var prompt = new Prompt(
                List.of(new UserMessage("Hello")),
                GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                        .functionCallMode(GigaChatOptions.FunctionCallMode.CUSTOM_FUNCTION)
                        .functionCallParam(functionCallParam)
                        .toolCallbacks(List.of(functionCallback))
                        .build());

        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatusCode.valueOf(200)));

        gigaChatModel.internalCall(prompt, null);

        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(gigaChatApi).chatCompletionEntity(requestCaptor.capture(), any());

        assertInstanceOf(FunctionCallParam.class, requestCaptor.getValue().getFunctionCall());

        var requestFunctionCallParam =
                (FunctionCallParam) requestCaptor.getValue().getFunctionCall();

        assertEquals(functionCallParam, requestFunctionCallParam);
    }

    @Test
    void testGigaChatOptions_withFunctionsStateIdAndFinishReasonStop() {
        var functionCallback = GigaTools.from(new TestTool());

        var prompt = new Prompt(
                List.of(new UserMessage("Hello")),
                GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                        .toolCallbacks(List.of(functionCallback))
                        .build());

        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatusCode.valueOf(200)));

        when(response.getChoices())
                .thenReturn(List.of(new CompletionResponse.Choice()
                        .setMessage(new CompletionResponse.MessagesRes().setFunctionsStateId("uuid"))));

        ChatResponse chatResponse = gigaChatModel.internalCall(prompt, null);

        assertTrue(chatResponse.getResult().getOutput().getMetadata().containsKey("functions_state_id"));
        assertEquals("uuid", chatResponse.getResult().getOutput().getMetadata().get("functions_state_id"));
    }

    @Test
    void testGigaChatOptions_withFunctionCallEmptyAndTool() {
        var functionCallback = GigaTools.from(new TestTool());

        var prompt = new Prompt(
                List.of(new UserMessage("Hello")),
                GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT)
                        .toolCallbacks(List.of(functionCallback))
                        .build());

        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatusCode.valueOf(200)));

        gigaChatModel.internalCall(prompt, null);

        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(gigaChatApi).chatCompletionEntity(requestCaptor.capture(), any());

        assertEquals("auto", requestCaptor.getValue().getFunctionCall());
    }

    @ParameterizedTest
    @EnumSource(GigaChatOptions.FunctionCallMode.class)
    void testGigaChatOptions_withFunctionCallMode(GigaChatOptions.FunctionCallMode callMode) {
        var prompt = new Prompt(
                List.of(new UserMessage("Hello")),
                GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                        .functionCallMode(callMode)
                        .build());

        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatusCode.valueOf(200)));

        gigaChatModel.internalCall(prompt, null);

        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(gigaChatApi).chatCompletionEntity(requestCaptor.capture(), any());

        assertEquals(callMode.getValue(), requestCaptor.getValue().getFunctionCall());
    }

    @ParameterizedTest
    @EnumSource(GigaChatOptions.FunctionCallMode.class)
    void testGigaChatOptions_withFunctionCallModeAndTool(GigaChatOptions.FunctionCallMode callMode) {
        var functionCallback = GigaTools.from(new TestTool());

        var prompt = new Prompt(
                List.of(new UserMessage("Hello")),
                GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                        .functionCallMode(callMode)
                        .toolCallbacks(List.of(functionCallback))
                        .build());

        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatusCode.valueOf(200)));

        gigaChatModel.internalCall(prompt, null);

        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(gigaChatApi).chatCompletionEntity(requestCaptor.capture(), any());

        assertEquals(callMode.getValue(), requestCaptor.getValue().getFunctionCall());
    }

    @Test
    void testGigaChatOptions_withDefault() {
        var prompt = new Prompt(
                List.of(new UserMessage("Hello")),
                GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                        .build());

        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatusCode.valueOf(200)));

        gigaChatModel.internalCall(prompt, null);

        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(gigaChatApi).chatCompletionEntity(requestCaptor.capture(), any());

        assertNull(requestCaptor.getValue().getFunctionCall());
    }

    @Test
    void testGigaChatOptions_withXSessionID() {
        final var sessionId = "SESSION_ID";
        var prompt = new Prompt(
                List.of(new UserMessage("Hello")),
                GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                        .httpHeaders(Map.of(GigaChatCachingAdvisor.X_SESSION_ID, sessionId))
                        .build());

        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatusCode.valueOf(200)));

        gigaChatModel.internalCall(prompt, null);

        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);
        ArgumentCaptor<HttpHeaders> headers = ArgumentCaptor.forClass(HttpHeaders.class);
        verify(gigaChatApi).chatCompletionEntity(requestCaptor.capture(), headers.capture());

        assertNull(requestCaptor.getValue().getFunctionCall());
        assertEquals(sessionId, headers.getValue().getFirst(X_SESSION_ID));
    }

    @Test
    @DisplayName("buildHeaders: коллизия регистра ключей -> одно значение (set/last-wins), а не склейка add")
    void testGigaChatOptions_httpHeadersCaseCollision_lastWins() {
        var prompt = new Prompt(
                List.of(new UserMessage("Hello")),
                GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                        .httpHeaders("X-Coll", "a")
                        .httpHeaders("x-coll", "b") // тот же заголовок в другом регистре
                        .build());

        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatusCode.valueOf(200)));

        gigaChatModel.internalCall(prompt, null);

        ArgumentCaptor<HttpHeaders> headers = ArgumentCaptor.forClass(HttpHeaders.class);
        verify(gigaChatApi).chatCompletionEntity(any(), headers.capture());

        // HttpHeaders регистронезависим: оба ключа схлопываются в один. set -> ровно одно значение
        // (add дал бы два: "a","b"). Порядок итерации HashMap недетерминирован, поэтому проверяем
        // размер, а конкретный победитель — любой из двух.
        List<String> values = headers.getValue().get("X-Coll");
        assertNotNull(values);
        assertEquals(1, values.size());
        assertTrue(List.of("a", "b").contains(values.get(0)));
    }

    @Test
    @DisplayName("PATH A: stream() возвращает запрос на вызов инструмента без in-model исполнения "
            + "(исполнение — ответственность ChatClient/ToolCallingAdvisor)")
    void testStream_returnsToolCallWithoutInModelExecution() {
        var spyTestTool = Mockito.spy(new TestTool());

        var prompt = new Prompt(
                List.of(new UserMessage("Hello, test!")),
                GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                        .toolCallbacks(GigaTools.from(spyTestTool))
                        .build());

        var functionCallResponse = new CompletionResponse()
                .setId(UUID.randomUUID().toString())
                .setModel(GigaChatApi.ChatModel.GIGA_CHAT_2.getName())
                .setChoices(List.of(new CompletionResponse.Choice()
                        .setIndex(1)
                        .setFinishReason(CompletionResponse.FinishReason.FUNCTION_CALL)
                        .setDelta(new CompletionResponse.MessagesRes()
                                .setRole(CompletionResponse.Role.assistant)
                                .setContent("")
                                .setFunctionsStateId(UUID.randomUUID().toString())
                                .setFunctionCall(new CompletionResponse.FunctionCall("testMethod", "{}")))));

        Mockito.when(gigaChatApi.chatCompletionStream(any(), any())).thenReturn(Flux.just(functionCallResponse));

        Flux<ChatResponse> chatResponseFlux = gigaChatModel.stream(prompt);

        StepVerifier.create(chatResponseFlux)
                .assertNext(chatResponse -> {
                    assertNotNull(chatResponse);
                    // модель возвращает запрос на вызов инструмента как есть
                    assertTrue(chatResponse.hasToolCalls(), "ответ должен содержать запрос на вызов инструмента");
                })
                .verifyComplete();

        // ровно один запрос к API — рекурсии/повторного вызова в модели больше нет
        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(gigaChatApi, times(1)).chatCompletionStream(requestCaptor.capture(), any());

        // объявление схемы инструмента остаётся ответственностью модели
        var request = requestCaptor.getValue();
        assertEquals("auto", request.getFunctionCall());
        assertEquals(1, request.getFunctions().size());
        assertEquals("testMethod", request.getFunctions().get(0).name());
        // #25: у инструмента без few-shot примеров поле должно быть null (а не пустой список),
        // иначе из-за @JsonInclude(NON_NULL) в теле всё равно появлялось бы "few_shot_examples": []
        assertNull(request.getFunctions().get(0).fewShotExamples());

        // сам инструмент моделью НЕ исполняется
        verify(spyTestTool, never()).testMethod();
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

    @Test
    @DisplayName("#06: reasoning_content из ответа пробрасывается в метаданные генерации")
    void reasoningContent_surfacedInMetadata() {
        var msg = new CompletionResponse.MessagesRes()
                .setRole(CompletionResponse.Role.assistant)
                .setContent("ответ")
                .setReasoningContent("мои рассуждения");
        var choice = new CompletionResponse.Choice()
                .setMessage(msg)
                .setIndex(0)
                .setFinishReason(CompletionResponse.FinishReason.STOP);
        var resp = new CompletionResponse().setModel("GigaChat-2").setChoices(List.of(choice));

        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(resp, HttpStatusCode.valueOf(200)));

        var prompt = new Prompt(
                List.of(new UserMessage("hi")),
                GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                        .build());
        ChatResponse cr = gigaChatModel.call(prompt);

        assertEquals("мои рассуждения", cr.getResult().getOutput().getMetadata().get("reasoningContent"));
    }

    private static class TestTool {
        @GigaTool
        public String testMethod() {
            return "test";
        }
    }

    @ParameterizedTest
    @MethodSource("promptAndMetadataProvider")
    @DisplayName("Тест проверяет наполнение ChatResponse кастомными метаданными")
    void testCustomMetadata(Prompt prompt, Map<String, Object> expectedMetadata) {
        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatusCode.valueOf(200)));

        ChatResponse chatResponse = gigaChatModel.call(prompt);
        ChatResponseMetadata metadata = chatResponse.getMetadata();

        expectedMetadata.forEach((metadataKey, metadataValue) -> {
            assertEquals(metadataValue, metadata.get(metadataKey));
        });
    }

    public static Stream<Arguments> promptAndMetadataProvider() {
        return Stream.of(
                Arguments.of(
                        new Prompt(List.of(SystemMessage.builder()
                                .text("Ты - полезный ассистент")
                                .build())),
                        new HashMap<>() {
                            {
                                put(GigaChatModel.INTERNAL_CONVERSATION_HISTORY, Collections.emptyList());
                                put(GigaChatModel.UPLOADED_MEDIA_IDS, null);
                            }
                        }),
                Arguments.of(
                        new Prompt(List.of(
                                SystemMessage.builder()
                                        .text("Ты - полезный ассистент")
                                        .build(),
                                UserMessage.builder().text("Что ты умеешь?").build())),
                        new HashMap<>() {
                            {
                                put(GigaChatModel.INTERNAL_CONVERSATION_HISTORY, Collections.emptyList());
                                put(GigaChatModel.UPLOADED_MEDIA_IDS, null);
                            }
                        }),
                Arguments.of(
                        new Prompt(List.of(UserMessage.builder().text("Кто ты?").build())), new HashMap<>() {
                            {
                                put(GigaChatModel.INTERNAL_CONVERSATION_HISTORY, Collections.emptyList());
                                put(GigaChatModel.UPLOADED_MEDIA_IDS, null);
                            }
                        }),
                Arguments.of(
                        new Prompt(List.of(
                                UserMessage.builder().text("Кто ты?").build(),
                                new AssistantMessage("Я - GigaChat!"),
                                UserMessage.builder().text("Что ты умеешь?").build())),
                        new HashMap<>() {
                            {
                                put(GigaChatModel.INTERNAL_CONVERSATION_HISTORY, Collections.emptyList());
                                put(GigaChatModel.UPLOADED_MEDIA_IDS, null);
                            }
                        }),
                Arguments.of(
                        new Prompt(List.of(
                                UserMessage.builder()
                                        .text("Отправь письмо на support@chat.giga")
                                        .build(),
                                AssistantMessage.builder()
                                        .content("")
                                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                                "sendEmail",
                                                "function",
                                                "sendEmail",
                                                "{\"address\": \"support@chat.giga\"}")))
                                        .build(),
                                ToolResponseMessage.builder()
                                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                                "sendEmail", "sendEmail", "{\"status\": \"sent\"}")))
                                        .build())),
                        new HashMap<>() {
                            {
                                put(
                                        GigaChatModel.INTERNAL_CONVERSATION_HISTORY,
                                        List.of(
                                                AssistantMessage.builder()
                                                        .content("")
                                                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                                                "sendEmail",
                                                                "function",
                                                                "sendEmail",
                                                                "{\"address\": \"support@chat.giga\"}")))
                                                        .build(),
                                                ToolResponseMessage.builder()
                                                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                                                "sendEmail", "sendEmail", "{\"status\": \"sent\"}")))
                                                        .build()));
                                put(GigaChatModel.UPLOADED_MEDIA_IDS, null);
                            }
                        }),
                Arguments.of(
                        new Prompt(List.of(UserMessage.builder()
                                .text("Кто ты?")
                                .media(Media.builder()
                                        .id("5512e5c1-2829-4b44-ad2d-c9bce5f8b154")
                                        .data("документ")
                                        .mimeType(MimeTypeUtils.TEXT_PLAIN)
                                        .build())
                                .build())),
                        new HashMap<>() {
                            {
                                put(GigaChatModel.INTERNAL_CONVERSATION_HISTORY, Collections.emptyList());
                                put(GigaChatModel.UPLOADED_MEDIA_IDS, List.of("5512e5c1-2829-4b44-ad2d-c9bce5f8b154"));
                            }
                        }));
    }

    @Test
    void testApplyOptions_direct_allOptionsApplied() {
        CompletionRequest request = new CompletionRequest();
        GigaChatOptions options = GigaChatOptions.builder()
                .model("test-model")
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(100)
                .repetitionPenalty(1.2)
                .updateInterval(2.0)
                .profanityCheck(true)
                .build();

        CompletionRequest result = gigaChatModel.applyOptions(request, options);

        assertEquals("test-model", result.getModel());
        assertEquals(0.7, result.getTemperature());
        assertEquals(0.9, result.getTopP());
        assertEquals(100, result.getMaxTokens());
        assertEquals(1.2, result.getRepetitionPenalty());
        assertEquals(2.0, result.getUpdateInterval());
        assertTrue(result.getProfanityCheck());
    }

    @Test
    void testApplyOptions_direct_nullOptions() {
        CompletionRequest request = new CompletionRequest();
        CompletionRequest result = gigaChatModel.applyOptions(request, null);
        assertEquals(request, result);
    }

    @Test
    void testApplyOptions_direct_partialOptions() {
        CompletionRequest request = new CompletionRequest();
        request.setModel("original-model");
        request.setTemperature(0.5);

        GigaChatOptions options = GigaChatOptions.builder()
                .model("new-model")
                .temperature(null)
                .topP(0.8)
                .build();

        CompletionRequest result = gigaChatModel.applyOptions(request, options);

        assertEquals("new-model", result.getModel());
        assertNull(result.getTemperature());
        assertEquals(0.8, result.getTopP());
        assertNull(result.getMaxTokens());
        assertNull(result.getRepetitionPenalty());
        assertNull(result.getUpdateInterval());
        assertNull(result.getProfanityCheck());
    }

    @Test
    void testInternalCall_withCustomOptions() {
        GigaChatOptions options = GigaChatOptions.builder()
                .model("custom-model")
                .temperature(0.8)
                .topP(0.95)
                .maxTokens(150)
                .repetitionPenalty(1.1)
                .updateInterval(1.5)
                .profanityCheck(false)
                .build();
        Prompt prompt = new Prompt(List.of(new UserMessage("Hello")), options);

        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatusCode.valueOf(200)));

        gigaChatModel.internalCall(prompt, null);

        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(gigaChatApi).chatCompletionEntity(requestCaptor.capture(), any());

        CompletionRequest capturedRequest = requestCaptor.getValue();
        assertEquals("custom-model", capturedRequest.getModel());
        assertEquals(0.8, capturedRequest.getTemperature());
        assertEquals(0.95, capturedRequest.getTopP());
        assertEquals(150, capturedRequest.getMaxTokens());
        assertEquals(1.1, capturedRequest.getRepetitionPenalty());
        assertEquals(1.5, capturedRequest.getUpdateInterval());
        assertEquals(false, capturedRequest.getProfanityCheck());
    }

    @Test
    void testStream_withCustomOptions() {
        GigaChatOptions options = GigaChatOptions.builder()
                .model("custom-stream-model")
                .temperature(0.9)
                .topP(0.85)
                .maxTokens(200)
                .repetitionPenalty(1.3)
                .updateInterval(2.5)
                .profanityCheck(true)
                .build();
        Prompt prompt = new Prompt(List.of(new UserMessage("Hello")), options);

        CompletionResponse completionResponse = new CompletionResponse()
                .setId(UUID.randomUUID().toString())
                .setModel("custom-stream-model")
                .setChoices(List.of(new CompletionResponse.Choice()
                        .setIndex(1)
                        .setDelta(new CompletionResponse.MessagesRes()
                                .setRole(CompletionResponse.Role.assistant)
                                .setContent("Response"))));

        when(gigaChatApi.chatCompletionStream(any(), any())).thenReturn(Flux.just(completionResponse));

        gigaChatModel.stream(prompt).blockLast();

        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(gigaChatApi).chatCompletionStream(requestCaptor.capture(), any());

        CompletionRequest capturedRequest = requestCaptor.getValue();
        assertEquals("custom-stream-model", capturedRequest.getModel());
        assertEquals(0.9, capturedRequest.getTemperature());
        assertEquals(0.85, capturedRequest.getTopP());
        assertEquals(200, capturedRequest.getMaxTokens());
        assertEquals(1.3, capturedRequest.getRepetitionPenalty());
        assertEquals(2.5, capturedRequest.getUpdateInterval());
        assertEquals(true, capturedRequest.getProfanityCheck());
    }
}
