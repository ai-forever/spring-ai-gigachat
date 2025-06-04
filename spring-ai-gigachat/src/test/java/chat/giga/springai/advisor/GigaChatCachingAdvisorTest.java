package chat.giga.springai.advisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import chat.giga.springai.GigaChatOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GigaChatCachingAdvisorTest {

    private static final String X_SESSION_ID_VALUE = "SESSION_ID";

    private final GigaChatCachingAdvisor advisor = new GigaChatCachingAdvisor();

    @Mock
    private CallAdvisorChain chain;

    @Mock
    private StreamAdvisorChain streamChain;

    @Mock
    private SystemMessage mockMessage;

    @Test
    @DisplayName("Вызов адвизора с X_SESSION_ID")
    void testAdviseCall_withContext() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(mockMessage)
                        .chatOptions(GigaChatOptions.builder().build())
                        .build())
                .context(GigaChatCachingAdvisor.X_SESSION_ID, X_SESSION_ID_VALUE)
                .build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(requestCaptor.capture());

        assertEquals(X_SESSION_ID_VALUE, requestCaptor.getValue().context().get(GigaChatCachingAdvisor.X_SESSION_ID));
        assertEquals(
                X_SESSION_ID_VALUE,
                ((GigaChatOptions) requestCaptor.getValue().prompt().getOptions()).getSessionId());
    }

    @Test
    @DisplayName("Вызов адвизора без X_SESSION_ID")
    void testAdviseCall_withoutContext() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(mockMessage)
                        .chatOptions(GigaChatOptions.builder().build())
                        .build())
                .build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(requestCaptor.capture());

        assertNull(requestCaptor.getValue().context().get(GigaChatCachingAdvisor.X_SESSION_ID));
        assertNull(((GigaChatOptions) requestCaptor.getValue().prompt().getOptions()).getSessionId());
    }

    @Test
    void testAdviseStream_withContext() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(mockMessage)
                        .chatOptions(GigaChatOptions.builder().build())
                        .build())
                .context(GigaChatCachingAdvisor.X_SESSION_ID, X_SESSION_ID_VALUE)
                .build();

        ChatClientResponse response = ChatClientResponse.builder().build();
        when(streamChain.nextStream(any())).thenReturn(Flux.just(response));

        StepVerifier.create(advisor.adviseStream(request, streamChain))
                .expectNext(response)
                .verifyComplete();

        ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(streamChain).nextStream(requestCaptor.capture());

        assertEquals(X_SESSION_ID_VALUE, requestCaptor.getValue().context().get(GigaChatCachingAdvisor.X_SESSION_ID));
        assertEquals(
                X_SESSION_ID_VALUE,
                ((GigaChatOptions) requestCaptor.getValue().prompt().getOptions()).getSessionId());
    }

    @Test
    void testAdviseStream_withoutContext() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(mockMessage)
                        .chatOptions(GigaChatOptions.builder().build())
                        .build())
                .build();

        ChatClientResponse response = ChatClientResponse.builder().build();
        when(streamChain.nextStream(any())).thenReturn(Flux.just(response));

        StepVerifier.create(advisor.adviseStream(request, streamChain))
                .expectNext(response)
                .verifyComplete();

        ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(streamChain).nextStream(requestCaptor.capture());

        assertNull(requestCaptor.getValue().context().get(GigaChatCachingAdvisor.X_SESSION_ID));
        assertNull(((GigaChatOptions) requestCaptor.getValue().prompt().getOptions()).getSessionId());
    }
}
