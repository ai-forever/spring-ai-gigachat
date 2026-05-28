package chat.giga.springai.advisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import chat.giga.springai.GigaChatOptions;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
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
import org.springframework.ai.chat.prompt.ChatOptions;
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
                getSessionId(requestCaptor.getValue().prompt().getOptions()));
    }

    @Test
    @DisplayName("Вызов адвизора 'call' без X_SESSION_ID")
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
        assertNull(getSessionId(requestCaptor.getValue().prompt().getOptions()));
    }

    @Test
    @DisplayName("Вызов адвизора в стриме с контекстом")
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
                getSessionId(requestCaptor.getValue().prompt().getOptions()));
    }

    @Test
    @DisplayName("Вызов адвизора в стриме без контекста")
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
        assertNull(getSessionId(requestCaptor.getValue().prompt().getOptions()));
    }

    @Test
    @DisplayName(
            "Вызов адвизора с X_SESSION_ID и immutable httpHeaders (Map.of) не падает и сохраняет прежние заголовки")
    void testAdviseCall_withImmutableHttpHeaders() {
        Map<String, String> immutableHeaders = Map.of("X-Custom", "custom-value");
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(mockMessage)
                        .chatOptions(GigaChatOptions.builder()
                                .httpHeaders(immutableHeaders)
                                .build())
                        .build())
                .context(GigaChatCachingAdvisor.X_SESSION_ID, X_SESSION_ID_VALUE)
                .build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(requestCaptor.capture());

        GigaChatOptions actualOptions =
                (GigaChatOptions) requestCaptor.getValue().prompt().getOptions();
        Assertions.assertNotNull(actualOptions);
        assertEquals(X_SESSION_ID_VALUE, actualOptions.getHttpHeaders().get(GigaChatCachingAdvisor.X_SESSION_ID));
        assertEquals("custom-value", actualOptions.getHttpHeaders().get("X-Custom"));
        // исходная immutable map не была мутирована
        assertEquals(1, immutableHeaders.size());
        assertNull(immutableHeaders.get(GigaChatCachingAdvisor.X_SESSION_ID));
    }

    @Test
    @DisplayName("Вызов адвизора с X_SESSION_ID и пустой immutable httpHeaders (Map.of()) не падает")
    void testAdviseCall_withEmptyImmutableHttpHeaders() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(mockMessage)
                        .chatOptions(
                                GigaChatOptions.builder().httpHeaders(Map.of()).build())
                        .build())
                .context(GigaChatCachingAdvisor.X_SESSION_ID, X_SESSION_ID_VALUE)
                .build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(requestCaptor.capture());

        assertEquals(
                X_SESSION_ID_VALUE,
                getSessionId(requestCaptor.getValue().prompt().getOptions()));
    }

    @Test
    @DisplayName("Вызов адвизора с X_SESSION_ID и unmodifiableMap httpHeaders не падает")
    void testAdviseCall_withUnmodifiableMapHttpHeaders() {
        Map<String, String> source = new HashMap<>();
        source.put("X-Custom", "custom-value");
        Map<String, String> immutableHeaders = Collections.unmodifiableMap(source);
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(mockMessage)
                        .chatOptions(GigaChatOptions.builder()
                                .httpHeaders(immutableHeaders)
                                .build())
                        .build())
                .context(GigaChatCachingAdvisor.X_SESSION_ID, X_SESSION_ID_VALUE)
                .build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(requestCaptor.capture());

        GigaChatOptions actualOptions =
                (GigaChatOptions) requestCaptor.getValue().prompt().getOptions();
        Assertions.assertNotNull(actualOptions);
        assertEquals(X_SESSION_ID_VALUE, actualOptions.getHttpHeaders().get(GigaChatCachingAdvisor.X_SESSION_ID));
        assertEquals("custom-value", actualOptions.getHttpHeaders().get("X-Custom"));
        // исходная map-обёртка не была мутирована
        assertEquals(1, source.size());
    }

    @Test
    @DisplayName("Вызов адвизора с X_SESSION_ID и httpHeaders=null не падает")
    void testAdviseCall_withNullHttpHeaders() {
        GigaChatOptions options = GigaChatOptions.builder().build();
        options.setHttpHeaders(null);
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(mockMessage)
                        .chatOptions(options)
                        .build())
                .context(GigaChatCachingAdvisor.X_SESSION_ID, X_SESSION_ID_VALUE)
                .build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(requestCaptor.capture());

        assertEquals(
                X_SESSION_ID_VALUE,
                getSessionId(requestCaptor.getValue().prompt().getOptions()));
    }

    @Test
    @DisplayName("Стрим: immutable httpHeaders с X_SESSION_ID не падает и сохраняет прежние заголовки")
    void testAdviseStream_withImmutableHttpHeaders() {
        Map<String, String> immutableHeaders = Map.of("X-Custom", "custom-value");
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(mockMessage)
                        .chatOptions(GigaChatOptions.builder()
                                .httpHeaders(immutableHeaders)
                                .build())
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

        GigaChatOptions actualOptions =
                (GigaChatOptions) requestCaptor.getValue().prompt().getOptions();
        Assertions.assertNotNull(actualOptions);
        assertEquals(X_SESSION_ID_VALUE, actualOptions.getHttpHeaders().get(GigaChatCachingAdvisor.X_SESSION_ID));
        assertEquals("custom-value", actualOptions.getHttpHeaders().get("X-Custom"));
    }

    private String getSessionId(ChatOptions options) {
        if (options == null) {
            return null;
        }
        if (options instanceof GigaChatOptions gigaChatOptions) {
            return gigaChatOptions.getHttpHeaders().get(GigaChatCachingAdvisor.X_SESSION_ID);
        }
        return null;
    }
}
