package chat.giga.springai.api.chat;

import chat.giga.springai.api.auth.GigaChatApiProperties;
import chat.giga.springai.api.auth.bearer.GigaChatBearerAuthApi;
import chat.giga.springai.api.auth.bearer.interceptors.BearerTokenFilter;
import chat.giga.springai.api.auth.bearer.interceptors.BearerTokenInterceptor;
import chat.giga.springai.api.auth.certificates.HttpClientUtils;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import chat.giga.springai.api.chat.embedding.EmbeddingsRequest;
import chat.giga.springai.api.chat.embedding.EmbeddingsResponse;
import chat.giga.springai.api.chat.file.UploadFileResponse;
import chat.giga.springai.api.chat.models.ModelsResponse;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.util.Assert;
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
        this(properties, RestClient.builder(), WebClient.builder(), RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
    }

    public GigaChatApi(
            GigaChatApiProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ResponseErrorHandler responseErrorHandler) {
        if (properties.isBearer()) {
            final GigaChatBearerAuthApi gigaChatBearerAuthApi = new GigaChatBearerAuthApi(properties);
            restClientBuilder.requestInterceptor(new BearerTokenInterceptor(gigaChatBearerAuthApi));
            webClientBuilder.filter(new BearerTokenFilter(gigaChatBearerAuthApi));
        }
        this.restClient = restClientBuilder
                .clone()
                .requestFactory(new JdkClientHttpRequestFactory(HttpClientUtils.build(properties)))
                .requestInterceptor(new GigachatLoggingInterceptor())
                .defaultStatusHandler(responseErrorHandler)
                .baseUrl(properties.getBaseUrl())
                .build();
        this.webClient = webClientBuilder
                .clone()
                .clientConnector(new JdkClientHttpConnector(HttpClientUtils.build(properties)))
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
        Assert.notNull(chatRequest, "The request body can not be null.");
        Assert.isTrue(!chatRequest.getStream(), "Request must set the stream property to false.");
        return this.restClient
                .post()
                .uri("/chat/completions")
                .header(HttpHeaders.USER_AGENT, USER_AGENT_SPRING_AI_GIGACHAT)
                .body(chatRequest)
                .retrieve()
                .toEntity(CompletionResponse.class);
    }

    public Flux<CompletionResponse> chatCompletionStream(final CompletionRequest chatRequest) {
        Assert.notNull(chatRequest, "The request body can not be null.");
        Assert.isTrue(chatRequest.getStream(), "Request must set the steam property to true.");
        return this.webClient
                .post()
                .uri("/chat/completions")
                .header(HttpHeaders.USER_AGENT, USER_AGENT_SPRING_AI_GIGACHAT)
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

        builder.part("file", new ByteArrayResource(media.getDataAsByteArray()) {
                    @Override
                    public String getFilename() {
                        return media.getName();
                    }
                })
                .contentType(MediaType.valueOf(media.getMimeType().toString()))
                .header("Content-Disposition", "form-data; name=file; filename=" + media.getName());

        builder.part("purpose", "general", MediaType.TEXT_PLAIN);

        return this.restClient
                .post()
                .uri("/files")
                .header(HttpHeaders.USER_AGENT, USER_AGENT_SPRING_AI_GIGACHAT)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .toEntity(UploadFileResponse.class);
    }

    public ResponseEntity<ModelsResponse> models() {
        return this.restClient.get().uri("/models").retrieve().toEntity(ModelsResponse.class);
    }
}
