package chat.giga.springai;

import static chat.giga.springai.advisor.GigaChatCachingAdvisor.X_SESSION_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import chat.giga.springai.api.auth.GigaChatInternalProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import chat.giga.springai.api.chat.param.FunctionCallParam;
import chat.giga.springai.tool.GigaTools;
import chat.giga.springai.tool.annotation.GigaTool;
import java.util.*;
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
import org.mockito.ArgumentMatchers;
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
                        .model(GigaChatApi.ChatModel.GIGA_CHAT)
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
                        .model(GigaChatApi.ChatModel.GIGA_CHAT)
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
                        .model(GigaChatApi.ChatModel.GIGA_CHAT)
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
                GigaChatOptions.builder().model(GigaChatApi.ChatModel.GIGA_CHAT).build());

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
                        .model(GigaChatApi.ChatModel.GIGA_CHAT)
                        .sessionId(sessionId)
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
    void testStream_withToolCall() {
        var spyTestTool = Mockito.spy(new TestTool());

        var prompt = new Prompt(
                List.of(new UserMessage("Hello, test!")),
                GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT)
                        .toolCallbacks(GigaTools.from(spyTestTool))
                        .build());

        var functionCallResponse = new CompletionResponse()
                .setId(UUID.randomUUID().toString())
                .setModel(GigaChatApi.ChatModel.GIGA_CHAT.getName())
                .setChoices(List.of(new CompletionResponse.Choice()
                        .setIndex(1)
                        .setFinishReason(CompletionResponse.FinishReason.FUNCTION_CALL)
                        .setDelta(new CompletionResponse.MessagesRes()
                                .setRole(CompletionResponse.Role.assistant)
                                .setContent("")
                                .setFunctionsStateId(UUID.randomUUID().toString())
                                .setFunctionCall(new CompletionResponse.FunctionCall("testMethod", "{}")))));

        // Для первого запроса в гигачат - имитируем вызов функции
        Mockito.when(gigaChatApi.chatCompletionStream(
                        ArgumentMatchers.argThat(
                                rq -> rq != null && rq.getMessages().size() == 1),
                        any()))
                .thenReturn(Flux.just(functionCallResponse));

        var finalResponsePart1 = new CompletionResponse()
                .setId(UUID.randomUUID().toString())
                .setModel(GigaChatApi.ChatModel.GIGA_CHAT.getName())
                .setChoices(List.of(new CompletionResponse.Choice()
                        .setIndex(2)
                        .setDelta(new CompletionResponse.MessagesRes()
                                .setRole(CompletionResponse.Role.assistant)
                                .setContent("Final test response"))));

        var finalResponsePart2 = new CompletionResponse()
                .setId(UUID.randomUUID().toString())
                .setModel(GigaChatApi.ChatModel.GIGA_CHAT.getName())
                .setChoices(List.of(new CompletionResponse.Choice()
                        .setIndex(2)
                        .setDelta(new CompletionResponse.MessagesRes().setContent(""))
                        .setFinishReason(CompletionResponse.FinishReason.STOP)));

        // Для второго запроса в гигачат - имитируем обработку результата вызова функции
        Mockito.when(gigaChatApi.chatCompletionStream(
                        ArgumentMatchers.argThat(rq -> rq.getMessages().size() == 3), any()))
                .thenReturn(Flux.just(finalResponsePart1, finalResponsePart2));

        Flux<ChatResponse> chatResponseFlux = gigaChatModel.stream(prompt);

        // проверяем финальный результат
        StepVerifier.create(chatResponseFlux)
                .assertNext(chatResponse -> {
                    assertNotNull(chatResponse);
                    assertEquals(1, chatResponse.getResults().size());
                    assertEquals(
                            "Final test response",
                            chatResponse.getResults().get(0).getOutput().getText());
                })
                .assertNext(chatResponse -> {
                    assertNotNull(chatResponse);
                    assertEquals(1, chatResponse.getResults().size());
                    assertEquals(
                            "", chatResponse.getResults().get(0).getOutput().getText());
                    assertEquals(
                            "stop",
                            chatResponse.getResults().get(0).getMetadata().getFinishReason());
                })
                .verifyComplete();

        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(gigaChatApi, times(2)).chatCompletionStream(requestCaptor.capture(), any());

        // Первый запрос в гигачат - с сообщением пользователя и описанием функции testMethod
        var completionRequest1 = requestCaptor.getAllValues().get(0);
        assertEquals(1, completionRequest1.getMessages().size());
        assertEquals(
                CompletionRequest.Role.user,
                completionRequest1.getMessages().get(0).getRole());
        assertEquals("Hello, test!", completionRequest1.getMessages().get(0).getContent());
        assertEquals("auto", completionRequest1.getFunctionCall());
        assertEquals(1, completionRequest1.getFunctions().size());
        assertEquals("testMethod", completionRequest1.getFunctions().get(0).name());

        // Второй запрос в гигачат - с результатом выполнения функции
        var completionRequest2 = requestCaptor.getAllValues().get(1);
        assertEquals(3, completionRequest2.getMessages().size());
        // 1. Сообщение пользователя
        assertEquals(
                CompletionRequest.Role.user,
                completionRequest2.getMessages().get(0).getRole());
        assertEquals("Hello, test!", completionRequest2.getMessages().get(0).getContent());
        // 2. Сообщение ассистента с аргументами для вызова функции
        assertEquals(
                CompletionRequest.Role.assistant,
                completionRequest2.getMessages().get(1).getRole());
        assertEquals("", completionRequest2.getMessages().get(1).getContent());
        assertNotNull(completionRequest2.getMessages().get(1).getFunctionCall());
        // 3. Сообщение с результатом вызова функции
        assertEquals(
                CompletionRequest.Role.function,
                completionRequest2.getMessages().get(2).getRole());
        assertEquals("\"test\"", completionRequest2.getMessages().get(2).getContent());
        assertEquals("auto", completionRequest2.getFunctionCall());
        assertEquals(1, completionRequest2.getFunctions().size());
        assertEquals("testMethod", completionRequest2.getFunctions().get(0).name());

        // Проверяем, что был вызов функции
        verify(spyTestTool).testMethod();
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
                                new AssistantMessage(
                                        "",
                                        Map.of(),
                                        List.of(new AssistantMessage.ToolCall(
                                                "sendEmail",
                                                "function",
                                                "sendEmail",
                                                "{\"address\": \"support@chat.giga\"}"))),
                                new ToolResponseMessage(List.of(new ToolResponseMessage.ToolResponse(
                                        "sendEmail", "sendEmail", "{\"status\": \"sent\"}"))))),
                        new HashMap<>() {
                            {
                                put(
                                        GigaChatModel.INTERNAL_CONVERSATION_HISTORY,
                                        List.of(
                                                new AssistantMessage(
                                                        "",
                                                        Map.of(),
                                                        List.of(
                                                                new AssistantMessage.ToolCall(
                                                                        "sendEmail",
                                                                        "function",
                                                                        "sendEmail",
                                                                        "{\"address\": \"support@chat.giga\"}"))),
                                                new ToolResponseMessage(List.of(new ToolResponseMessage.ToolResponse(
                                                        "sendEmail", "sendEmail", "{\"status\": \"sent\"}")))));
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
}
