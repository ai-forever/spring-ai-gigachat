package chat.giga.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.GigachatLoggingInterceptor;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.embedding.EmbeddingsModel;
import chat.giga.springai.api.chat.embedding.EmbeddingsRequest;
import chat.giga.springai.api.chat.embedding.EmbeddingsResponse;
import chat.giga.springai.tool.definition.GigaToolDefinition;
import io.micrometer.observation.ObservationRegistry;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import nl.altindag.ssl.SSLFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Регресс-тесты bughunt: подтверждённые баги, найденные при аудите подсистем
 * (каждый параметр/функция/класс/API должен работать со всеми заявленными значениями).
 * Номера в комментариях ссылаются на находки аудита.
 */
class BughuntRegressionTest {

    // ----------------------------------------------------------------------------------------
    // #04 — usage эмбеддингов: completionTokens=0, totalTokens НЕ удваивается
    // ----------------------------------------------------------------------------------------
    @Nested
    class EmbeddingsUsageTokens {

        @Test
        @DisplayName("#04: getCompletionTokens()==0, totalTokens==promptTokens (раньше токены удваивались)")
        void completionTokensIsZero_totalNotDoubled() {
            var usage = EmbeddingsResponse.GigaChatEmbeddingsUsage.builder()
                    .promptTokens(42)
                    .build();

            assertThat(usage.getPromptTokens()).isEqualTo(42);
            assertThat(usage.getCompletionTokens()).isZero();
            assertThat(usage.getTotalTokens()).isEqualTo(42);
        }
    }

    // ----------------------------------------------------------------------------------------
    // Эмбеддинг-модель: null-опции (#01) и агрегирование usage по батчу (#11)
    // ----------------------------------------------------------------------------------------
    @Nested
    class EmbeddingModel {

        private final GigaChatApi gigaChatApi = Mockito.mock(GigaChatApi.class);
        private final GigaChatEmbeddingOptions defaults = GigaChatEmbeddingOptions.builder()
                .withModel(EmbeddingsModel.EMBEDDINGS.getName())
                .build();
        private final GigaChatEmbeddingModel model = new GigaChatEmbeddingModel(
                gigaChatApi, defaults, RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP);

        private EmbeddingsResponse.EmbeddingData data(int index, int promptTokens) {
            return EmbeddingsResponse.EmbeddingData.builder()
                    .index(index)
                    .embedding(new float[] {0.1f, 0.2f})
                    .usage(EmbeddingsResponse.GigaChatEmbeddingsUsage.builder()
                            .promptTokens(promptTokens)
                            .build())
                    .build();
        }

        @Test
        @DisplayName("#01: call() c null-опциями использует default-модель, а не падает с NPE")
        void nullOptions_usesDefaultModel() {
            when(gigaChatApi.embeddings(any()))
                    .thenReturn(ResponseEntity.ok(EmbeddingsResponse.builder()
                            .model(EmbeddingsModel.EMBEDDINGS.getName())
                            .data(List.of(data(0, 5)))
                            .build()));

            // options == null допустимо контрактом Spring AI (EmbeddingRequest.getOptions() @Nullable)
            EmbeddingResponse response = model.call(new EmbeddingRequest(List.of("привет"), null));

            assertThat(response.getResults()).hasSize(1);
            ArgumentCaptor<EmbeddingsRequest> cap = ArgumentCaptor.forClass(EmbeddingsRequest.class);
            Mockito.verify(gigaChatApi).embeddings(cap.capture());
            assertThat(cap.getValue().getModel()).isEqualTo(EmbeddingsModel.EMBEDDINGS.getName());
        }

        @Test
        @DisplayName("#11: usage метаданных суммирует prompt-токены по всему батчу")
        void batchUsage_isAggregated() {
            when(gigaChatApi.embeddings(any()))
                    .thenReturn(ResponseEntity.ok(EmbeddingsResponse.builder()
                            .model(EmbeddingsModel.EMBEDDINGS.getName())
                            .data(List.of(data(0, 5), data(1, 7), data(2, 11)))
                            .build()));

            EmbeddingResponse response = model.call(new EmbeddingRequest(List.of("a", "b", "c"), null));

            assertThat(response.getResults()).hasSize(3);
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(23);
            assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(23);
        }
    }

    // ----------------------------------------------------------------------------------------
    // #15 — sub-second connectTimeout не должен отвергаться (getSeconds() обрезал доли секунды)
    // ----------------------------------------------------------------------------------------
    @Nested
    class ConnectTimeout {

        private final SSLFactory ssl =
                SSLFactory.builder().withDefaultTrustMaterial().build();

        @Test
        @DisplayName("#15: connectTimeout 500ms принимается")
        void subSecondTimeout_accepted() {
            // не должно бросать
            chat.giga.springai.api.HttpClientUtils.buildHttpClient(ssl, Duration.ofMillis(500));
        }

        @Test
        @DisplayName("#15: нулевой и отрицательный connectTimeout отвергаются")
        void zeroAndNegative_rejected() {
            assertThatThrownBy(() -> chat.giga.springai.api.HttpClientUtils.buildHttpClient(ssl, Duration.ZERO))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> chat.giga.springai.api.HttpClientUtils.buildHttpClient(ssl, Duration.ofMillis(-1)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ----------------------------------------------------------------------------------------
    // #20 — guard stream-флага не должен падать с NPE на null (unboxing)
    // ----------------------------------------------------------------------------------------
    @Nested
    class StreamGuard {

        private GigaChatApi noopApi() {
            return new GigaChatApi(GigaChatApiProperties.builder()
                    .auth(GigaChatAuthProperties.builder().unsafeSsl(true).build())
                    .build());
        }

        @Test
        @DisplayName("#20: chatCompletionStream c stream=null бросает IllegalArgumentException, а не NPE")
        void streamNull_throwsIllegalArgument_notNpe() {
            CompletionRequest req = CompletionRequest.builder().model("GigaChat-2").stream(null)
                    .messages(List.of(new CompletionRequest.Message(CompletionRequest.Role.user, "hi")))
                    .build();

            assertThatThrownBy(() -> noopApi().chatCompletionStream(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stream");
        }
    }

    // ----------------------------------------------------------------------------------------
    // security — GigachatLoggingInterceptor НЕ должен светить секреты в DEBUG-логе (CWE-532).
    // BearerTokenInterceptor проставляет Authorization до логгера; маскируем чувствительные заголовки.
    // ----------------------------------------------------------------------------------------
    @Nested
    class LoggingHeaderRedaction {

        @Test
        @DisplayName("security: Authorization и Set-Cookie маскируются, секрет в лог не утекает")
        void sensitiveHeaders_redacted() throws Exception {
            Logger logger = (Logger) LoggerFactory.getLogger(GigachatLoggingInterceptor.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            Level prev = logger.getLevel();
            logger.setLevel(Level.DEBUG);
            logger.addAppender(appender);
            try {
                HttpHeaders reqHeaders = new HttpHeaders();
                reqHeaders.setBearerAuth("super-secret-token");
                HttpRequest request = Mockito.mock(HttpRequest.class);
                when(request.getURI()).thenReturn(URI.create("https://example/api"));
                when(request.getHeaders()).thenReturn(reqHeaders);

                HttpHeaders respHeaders = new HttpHeaders();
                respHeaders.add(HttpHeaders.SET_COOKIE, "session=secret-cookie");
                ClientHttpResponse response = Mockito.mock(ClientHttpResponse.class);
                when(response.getHeaders()).thenReturn(respHeaders);
                when(response.getBody()).thenReturn(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));

                ClientHttpRequestExecution execution = Mockito.mock(ClientHttpRequestExecution.class);
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                when(execution.execute(request, body)).thenReturn(response);

                new GigachatLoggingInterceptor().intercept(request, body, execution);

                String log = appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .reduce("", String::concat);
                assertThat(log).contains("***REDACTED***");
                assertThat(log).doesNotContain("super-secret-token");
                assertThat(log).doesNotContain("secret-cookie");
            } finally {
                logger.detachAppender(appender);
                logger.setLevel(prev);
            }
        }
    }

    // ----------------------------------------------------------------------------------------
    // security — httpHeaders (могут нести секреты) исключены из GigaChatOptions.toString(),
    // т.к. GigaChatModel логирует весь Prompt на пустом ответе.
    // ----------------------------------------------------------------------------------------
    @Nested
    class OptionsToStringRedaction {

        @Test
        @DisplayName("security: GigaChatOptions.toString() не содержит значений httpHeaders")
        void httpHeaders_excludedFromToString() {
            GigaChatOptions options = GigaChatOptions.builder()
                    .httpHeaders("Authorization", "Bearer super-secret")
                    .build();
            assertThat(options.toString()).doesNotContain("super-secret");
        }
    }

    // ----------------------------------------------------------------------------------------
    // api-contract — GigaToolDefinition должен быть иммутабельным: аксессор fewShotExamples()
    // не должен отдавать живой ArrayList билдера (defensive copy через List.copyOf).
    // ----------------------------------------------------------------------------------------
    @Nested
    class ToolDefinitionImmutability {

        @Test
        @DisplayName("api-contract: fewShotExamples() возвращает иммутабельный список")
        void fewShotExamples_isImmutable() {
            GigaToolDefinition def = GigaToolDefinition.builder()
                    .name("tool")
                    .description("desc")
                    .inputSchema("{}")
                    .build();
            assertThatThrownBy(() -> def.fewShotExamples().add(null)).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
