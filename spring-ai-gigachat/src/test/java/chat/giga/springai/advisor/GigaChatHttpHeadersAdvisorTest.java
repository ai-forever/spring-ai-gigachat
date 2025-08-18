package chat.giga.springai.advisor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import chat.giga.springai.GigaChatOptions;
import java.util.Map;
import org.hamcrest.Matchers;
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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GigaChatHttpHeadersAdvisorTest {
    private final GigaChatHttpHeadersAdvisor advisor = new GigaChatHttpHeadersAdvisor();

    @Mock
    private CallAdvisorChain chain;

    @Mock
    private StreamAdvisorChain streamChain;

    @Test
    @DisplayName("Вызов Advisor с заголовками")
    void testAdviseCall_withContext() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(UserMessage.builder().text("test").build())
                        .chatOptions(GigaChatOptions.builder()
                                .httpHeaders(Map.of("key1", "value1"))
                                .build())
                        .build())
                .context(GigaChatHttpHeadersAdvisor.httpHeader("key2"), "value2")
                .build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(requestCaptor.capture());

        Map<String, Object> context = requestCaptor.getValue().context();
        GigaChatOptions options =
                (GigaChatOptions) requestCaptor.getValue().prompt().getOptions();

        assertThat(context, Matchers.allOf(Matchers.aMapWithSize(1), Matchers.hasEntry("http_header_key2", "value2")));

        assertThat(
                options.getHttpHeaders(),
                Matchers.allOf(
                        Matchers.notNullValue(),
                        Matchers.aMapWithSize(2),
                        Matchers.hasEntry("key1", "value1"),
                        Matchers.hasEntry("key2", "value2")));
    }

    @Test
    @DisplayName("Вызов Advisor без заголовков")
    void testAdviseCall_withoutContext() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(UserMessage.builder().text("test").build())
                        .chatOptions(GigaChatOptions.builder().build())
                        .build())
                .build();

        advisor.adviseCall(request, chain);

        ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(requestCaptor.capture());

        Map<String, Object> context = requestCaptor.getValue().context();
        GigaChatOptions options =
                (GigaChatOptions) requestCaptor.getValue().prompt().getOptions();

        assertThat(context, Matchers.aMapWithSize(0));

        assertThat(options.getHttpHeaders(), Matchers.allOf(Matchers.notNullValue(), Matchers.aMapWithSize(0)));
    }

    @Test
    @DisplayName("Стриминговый вызов Advisor с заголовками")
    void testAdviseStream_withContext() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(UserMessage.builder().text("test").build())
                        .chatOptions(GigaChatOptions.builder()
                                .httpHeaders(Map.of("key1", "value1"))
                                .build())
                        .build())
                .context(GigaChatHttpHeadersAdvisor.httpHeader("key2"), "value2")
                .build();

        ChatClientResponse response = ChatClientResponse.builder().build();
        when(streamChain.nextStream(any())).thenReturn(Flux.just(response));

        StepVerifier.create(advisor.adviseStream(request, streamChain))
                .expectNext(response)
                .verifyComplete();

        ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(streamChain).nextStream(requestCaptor.capture());

        Map<String, Object> context = requestCaptor.getValue().context();
        GigaChatOptions options =
                (GigaChatOptions) requestCaptor.getValue().prompt().getOptions();

        assertThat(context, Matchers.allOf(Matchers.aMapWithSize(1), Matchers.hasEntry("http_header_key2", "value2")));

        assertThat(
                options.getHttpHeaders(),
                Matchers.allOf(
                        Matchers.notNullValue(),
                        Matchers.aMapWithSize(2),
                        Matchers.hasEntry("key1", "value1"),
                        Matchers.hasEntry("key2", "value2")));
    }

    @Test
    @DisplayName("Стриминговый вызов Advisor без заголовков")
    void testAdviseStream_withoutContext() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(UserMessage.builder().text("test").build())
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

        Map<String, Object> context = requestCaptor.getValue().context();
        GigaChatOptions options =
                (GigaChatOptions) requestCaptor.getValue().prompt().getOptions();

        assertThat(context, Matchers.aMapWithSize(0));

        assertThat(options.getHttpHeaders(), Matchers.allOf(Matchers.notNullValue(), Matchers.aMapWithSize(0)));
    }
}
