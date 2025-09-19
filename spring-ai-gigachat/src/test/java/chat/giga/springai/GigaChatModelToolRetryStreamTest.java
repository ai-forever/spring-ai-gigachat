package chat.giga.springai;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SuppressWarnings("FieldCanBeLocal")
class GigaChatModelToolRetryStreamTest {
    private GigaChatApi gigaChatApi;
    private GigaChatOptions defaultOptions;
    private ToolCallingManager toolCallingManager;
    private RetryTemplate retryTemplate;
    private GigaChatInternalProperties internalProps;
    private ToolExecutionEligibilityPredicate eligibility;

    @Spy
    private GigaChatModel model;

    @BeforeEach
    void setUp() {
        gigaChatApi = mock(GigaChatApi.class);
        defaultOptions = new GigaChatOptions();
        toolCallingManager = mock(ToolCallingManager.class);

        retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3));
        retryTemplate.setBackOffPolicy(new NoBackOffPolicy());

        internalProps = mock(GigaChatInternalProperties.class);
        eligibility = mock(ToolExecutionEligibilityPredicate.class);

        model = spy(new GigaChatModel(
                gigaChatApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                ObservationRegistry.NOOP,
                internalProps,
                eligibility));
    }

    /** Минимальный валидный стрим-чанк, достаточный для сборки ChatResponse в stream-пути. */
    private CompletionResponse minimalStreamingChunk() {
        CompletionResponse cr = new CompletionResponse();
        CompletionResponse.Choice ch = new CompletionResponse.Choice();
        CompletionResponse.MessagesRes delta = new CompletionResponse.MessagesRes();
        ch.setDelta(delta);
        ch.setIndex(0);
        cr.setChoices(List.of(ch));
        cr.setId("stream-id-1");
        return cr;
    }

    @Test
    void tools_areRetried_and_returnDirect_isReturned_in_stream() {
        when(eligibility.isToolExecutionRequired(any(), any(ChatResponse.class)))
                .thenReturn(true);

        when(gigaChatApi.chatCompletionStream(any(CompletionRequest.class), any()))
                .thenReturn(Flux.just(minimalStreamingChunk()));

        ToolExecutionResult toolRes = mock(ToolExecutionResult.class);
        when(toolRes.returnDirect()).thenReturn(true);

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenThrow(new RuntimeException("t1"))
                .thenThrow(new RuntimeException("t2"))
                .thenReturn(toolRes);

        // Статический мок, чтобы избежать падения внутри buildGenerations(...)
        try (MockedStatic<ToolExecutionResult> mocked = mockStatic(ToolExecutionResult.class)) {
            mocked.when(() -> ToolExecutionResult.buildGenerations(toolRes))
                    .thenReturn(List.of(new Generation(new AssistantMessage("tool-gen"))));

            Prompt prompt = new Prompt(List.of(new UserMessage("stream me")), defaultOptions);
            Flux<ChatResponse> out = model.internalStream(prompt, null);

            StepVerifier.create(out)
                    .expectNextCount(1) // одного ответа достаточно, детализация проверяется через verify ниже
                    .verifyComplete();

            verify(toolCallingManager, times(3)).executeToolCalls(any(), any());
            mocked.verify(() -> ToolExecutionResult.buildGenerations(toolRes), times(1));
        }
    }

    @Test
    void tools_retryExhausted_propagatesLast_in_stream() {
        when(eligibility.isToolExecutionRequired(any(), any(ChatResponse.class)))
                .thenReturn(true);

        when(gigaChatApi.chatCompletionStream(any(CompletionRequest.class), any()))
                .thenReturn(Flux.just(minimalStreamingChunk()));

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenThrow(new RuntimeException("boom1"))
                .thenThrow(new RuntimeException("boom2"))
                .thenThrow(new RuntimeException("final boom"));

        final Prompt prompt = new Prompt(List.of(new UserMessage("stream me")), defaultOptions);
        final Flux<ChatResponse> out = model.internalStream(prompt, null);

        StepVerifier.create(out)
                .expectErrorMatches(
                        ex -> ex instanceof RuntimeException && ex.getMessage().equals("final boom"))
                .verify();

        verify(toolCallingManager, times(3)).executeToolCalls(any(), any());
    }
}
