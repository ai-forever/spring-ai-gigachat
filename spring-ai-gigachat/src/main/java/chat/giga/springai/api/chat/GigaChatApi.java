package chat.giga.springai.api.chat;

import static chat.giga.springai.api.HttpClientUtils.buildHttpClient;
import static chat.giga.springai.api.HttpClientUtils.buildSslFactory;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.bearer.GigaChatBearerAuthApi;
import chat.giga.springai.api.auth.bearer.GigaChatOAuthClient;
import chat.giga.springai.api.auth.bearer.interceptors.BearerTokenFilter;
import chat.giga.springai.api.auth.bearer.interceptors.BearerTokenInterceptor;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import chat.giga.springai.api.chat.embedding.EmbeddingsRequest;
import chat.giga.springai.api.chat.embedding.EmbeddingsResponse;
import chat.giga.springai.api.chat.file.DeleteFileResponse;
import chat.giga.springai.api.chat.file.UploadFileResponse;
import chat.giga.springai.api.chat.models.ModelsResponse;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class GigaChatApi {
    public static final String USER_AGENT_SPRING_AI_GIGACHAT = "Spring-AI-GigaChat";
    public static final String PROVIDER_NAME = "gigachat";
    public static final String X_REQUEST_ID = "x-request-id";
    private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

    private final RestClient restClient;
    private final WebClient webClient;

    public GigaChatApi(GigaChatApiProperties properties) {
        this(properties, null, null);
    }

    public GigaChatApi(
            GigaChatApiProperties properties, @Nullable KeyManagerFactory kmf, @Nullable TrustManagerFactory tmf) {
        this(
                properties,
                RestClient.builder(),
                WebClient.builder(),
                RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER,
                kmf,
                tmf);
    }

    public GigaChatApi(
            GigaChatApiProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ResponseErrorHandler responseErrorHandler,
            @Nullable KeyManagerFactory kmf,
            @Nullable TrustManagerFactory tmf) {
        if (properties.isBearer()) {
            final SimpleApiKey apiKey = new SimpleApiKey(properties.getApiKey());
            final GigaChatOAuthClient gigaChatOAuthClient =
                    new GigaChatOAuthClient(properties, restClientBuilder, null, tmf, apiKey);
            final GigaChatBearerAuthApi gigaChatBearerAuthApi = new GigaChatBearerAuthApi(gigaChatOAuthClient);
            restClientBuilder.requestInterceptor(new BearerTokenInterceptor(gigaChatBearerAuthApi));
            webClientBuilder.filter(new BearerTokenFilter(gigaChatBearerAuthApi));
        }

        var internalProps = properties.getInternal();

        var clientHttpRequestFactory = new JdkClientHttpRequestFactory(buildHttpClient(
                buildSslFactory(kmf, tmf, properties.isUnsafeSsl()), internalProps.getConnectTimeout()));
        if (internalProps.getReadTimeout() != null) {
            clientHttpRequestFactory.setReadTimeout(internalProps.getReadTimeout());
        }
        this.restClient = restClientBuilder
                .clone()
                .requestFactory(clientHttpRequestFactory)
                .requestInterceptor(new GigachatLoggingInterceptor())
                .defaultStatusHandler(responseErrorHandler)
                .baseUrl(properties.getBaseUrl())
                .build();

        var clientHttpConnector = new JdkClientHttpConnector(buildHttpClient(
                buildSslFactory(kmf, tmf, properties.isUnsafeSsl()), internalProps.getConnectTimeout()));
        if (internalProps.getReadTimeout() != null) {
            clientHttpConnector.setReadTimeout(internalProps.getReadTimeout());
        }
        this.webClient = webClientBuilder
                .clone()
                .clientConnector(clientHttpConnector)
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    /**
     * <a href="https://developers.sber.ru/docs/ru/gigachat/models">Список доступных моделей</a>
     */
    @AllArgsConstructor
    public enum ChatModel implements ChatModelDescription {
        GIGA_CHAT("GigaChat"),
        GIGA_CHAT_PRO("GigaChat-Pro"),
        GIGA_CHAT_MAX("GigaChat-Max"),
        GIGA_CHAT_2("GigaChat-2"),
        GIGA_CHAT_2_MAX("GigaChat-2-Max"),
        GIGA_CHAT_2_PRO("GigaChat-2-Pro");

        public final String value;

        @JsonValue
        public String getName() {
            return this.value;
        }
    }

    public ResponseEntity<CompletionResponse> chatCompletionEntity(final CompletionRequest chatRequest) {
        return chatCompletionEntity(chatRequest, null);
    }

    public ResponseEntity<CompletionResponse> chatCompletionEntity(
            final CompletionRequest chatRequest, @Nullable final HttpHeaders headers) {
        Assert.notNull(chatRequest, "The request body can not be null.");
        Assert.isTrue(!chatRequest.getStream(), "Request must set the stream property to false.");
        return this.restClient
                .post()
                .uri("/chat/completions")
                .headers(applyHeaders(headers))
                .body(chatRequest)
                .retrieve()
                .toEntity(CompletionResponse.class);
    }

    public Flux<CompletionResponse> chatCompletionStream(final CompletionRequest chatRequest) {
        return chatCompletionStream(chatRequest, null);
    }

    public Flux<CompletionResponse> chatCompletionStream(
            final CompletionRequest chatRequest, @Nullable final HttpHeaders headers) {
        Assert.notNull(chatRequest, "The request body can not be null.");
        Assert.isTrue(chatRequest.getStream(), "Request must set the steam property to true.");
        return this.webClient
                .post()
                .uri("/chat/completions")
                .headers(applyHeaders(headers))
                .body(Mono.just(chatRequest), CompletionRequest.class)
                .exchangeToFlux(rs -> {
                    String id = rs.headers().asHttpHeaders().getFirst(X_REQUEST_ID);
                    return rs.bodyToFlux(String.class)
                            .takeUntil(SSE_DONE_PREDICATE)
                            .filter(SSE_DONE_PREDICATE.negate())
                            .map(content -> {
                                CompletionResponse completionResponse =
                                        ModelOptionsUtils.jsonToObject(content, CompletionResponse.class);
                                completionResponse.setId(id);
                                return completionResponse;
                            });
                });
    }

    public ResponseEntity<EmbeddingsResponse> embeddings(final EmbeddingsRequest embeddingRequest) {
        Assert.notNull(embeddingRequest, "The request body can not be null.");
        Assert.notNull(embeddingRequest.getInput(), "The input can not be null.");
        Assert.isTrue(!embeddingRequest.getInput().isEmpty(), "The input can not be empty.");
        return this.restClient
                .post()
                .uri("/embeddings")
                .header(HttpHeaders.USER_AGENT, USER_AGENT_SPRING_AI_GIGACHAT)
                .body(embeddingRequest)
                .retrieve()
                .toEntity(EmbeddingsResponse.class);
    }

    public ResponseEntity<UploadFileResponse> uploadFile(Media media) {
        Assert.notNull(media, "Media can not be null.");
        Assert.notNull(media.getData(), "Media data can not be null.");

        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        builder.part("file", media.getDataAsByteArray())
                .contentType(MediaType.valueOf(media.getMimeType().toString()))
                .header("Content-Disposition", "form-data; name=\"file\"; filename=\"" + media.getName() + "\"");

        builder.part("purpose", "general", MediaType.TEXT_PLAIN)
                .header("Content-Disposition", "form-data; name=\"purpose\"");

        return this.restClient
                .post()
                .uri("/files")
                .header(HttpHeaders.USER_AGENT, USER_AGENT_SPRING_AI_GIGACHAT)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .toEntity(UploadFileResponse.class);
    }

    public ResponseEntity<DeleteFileResponse> deleteFile(String fileId) {
        return this.restClient
                .post()
                .uri("/files/{fileId}/delete", fileId)
                .header(HttpHeaders.USER_AGENT, USER_AGENT_SPRING_AI_GIGACHAT)
                .retrieve()
                .toEntity(DeleteFileResponse.class);
    }

    public ResponseEntity<ModelsResponse> models() {
        return this.restClient.get().uri("/models").retrieve().toEntity(ModelsResponse.class);
    }

    private Consumer<HttpHeaders> applyHeaders(@Nullable HttpHeaders headers) {
        return httpHeaders -> {
            if (!CollectionUtils.isEmpty(headers)) {
                httpHeaders.addAll(headers);
            }
            httpHeaders.set(HttpHeaders.USER_AGENT, USER_AGENT_SPRING_AI_GIGACHAT);
        };
    }
}
