package chat.giga.springai;

import static chat.giga.springai.api.chat.GigaChatApi.X_REQUEST_ID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Slf4j
@SuppressWarnings("FieldCanBeLocal")
class GigaChatModelToolRetryInternalCallTest {
    private GigaChatApi gigaChatApi;
    private GigaChatOptions defaultOptions;
    private ToolCallingManager toolCallingManager;
    private RetryTemplate retryTemplate;
    private ObservationRegistry observationRegistry;
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

        observationRegistry = ObservationRegistry.NOOP;
        internalProps = mock(GigaChatInternalProperties.class);
        eligibility = mock(ToolExecutionEligibilityPredicate.class);

        model = spy(new GigaChatModel(
                gigaChatApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                observationRegistry,
                internalProps,
                eligibility));
    }

    private void stubNullBodyOnce() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(X_REQUEST_ID, "req-123");
        when(gigaChatApi.chatCompletionEntity(any(CompletionRequest.class), any()))
                .thenReturn(new ResponseEntity<CompletionResponse>(null, headers, HttpStatus.OK));
    }

    @Test
    void tools_areRetried_and_returnDirect_isReturned() {
        stubNullBodyOnce();
        when(eligibility.isToolExecutionRequired(any(), any(ChatResponse.class)))
                .thenReturn(true);

        ToolExecutionResult toolRes = mock(ToolExecutionResult.class);
        when(toolRes.returnDirect()).thenReturn(true);

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenThrow(new RuntimeException("t1"))
                .thenThrow(new RuntimeException("t2"))
                .thenReturn(toolRes);

        final Prompt prompt = new Prompt(List.of(new UserMessage("hello")), defaultOptions);

        try (MockedStatic<ToolExecutionResult> mocked = mockStatic(ToolExecutionResult.class)) {
            mocked.when(() -> ToolExecutionResult.buildGenerations(toolRes))
                    .thenReturn(List.of(new Generation(new AssistantMessage("tool-gen"))));

            final ChatResponse out = model.internalCall(prompt, null);
            log.info("Result: {}", out.toString());

            verify(toolCallingManager, times(3)).executeToolCalls(any(), any());
            verify(toolRes, times(1)).returnDirect();
            verify(gigaChatApi, times(1)).chatCompletionEntity(any(), any());
        }
    }

    @Test
    void tools_retryExhausted_propagatesLastException() {
        stubNullBodyOnce();
        when(eligibility.isToolExecutionRequired(any(), any(ChatResponse.class)))
                .thenReturn(true);

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenThrow(new RuntimeException("boom1"))
                .thenThrow(new RuntimeException("boom2"))
                .thenThrow(new RuntimeException("final boom"));

        Prompt prompt = new Prompt(List.of(new UserMessage("hello")), defaultOptions);

        assertThatThrownBy(() -> model.internalCall(prompt, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("final boom");

        verify(toolCallingManager, times(3)).executeToolCalls(any(), any());
    }
}
