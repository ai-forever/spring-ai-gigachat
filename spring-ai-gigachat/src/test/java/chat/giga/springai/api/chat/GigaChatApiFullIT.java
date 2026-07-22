package chat.giga.springai.api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatApiScope;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import chat.giga.springai.api.chat.embedding.EmbeddingsModel;
import chat.giga.springai.api.chat.embedding.EmbeddingsRequest;
import chat.giga.springai.api.chat.embedding.EmbeddingsResponse;
import chat.giga.springai.api.chat.file.DeleteFileResponse;
import chat.giga.springai.api.chat.file.UploadFileResponse;
import chat.giga.springai.api.chat.models.ModelsResponse;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.content.Media;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;

/**
 * Полное покрытие низкоуровневого {@link GigaChatApi} на реальном API: каждый публичный метод и
 * каждый сериализуемый параметр запроса. Креды берутся из переменных окружения GIGACHAT_API_*,
 * секреты не печатаются.
 */
@Slf4j
class GigaChatApiFullIT {

    private static final String MODEL = "GigaChat-2-Max";

    private static GigaChatApi api() {
        GigaChatApiProperties properties = GigaChatApiProperties.builder()
                .auth(GigaChatAuthProperties.builder()
                        .scope(GigaChatApiScope.valueOf(System.getenv("GIGACHAT_API_SCOPE")))
                        .unsafeSsl(true)
                        .bearer(GigaChatAuthProperties.Bearer.builder()
                                .clientId(System.getenv("GIGACHAT_API_CLIENT_ID"))
                                .clientSecret(System.getenv("GIGACHAT_API_CLIENT_SECRET"))
                                .build())
                        .build())
                .build();
        return new GigaChatApi(properties);
    }

    private static CompletionRequest.Message userMessage(String text) {
        return CompletionRequest.Message.builder()
                .role(CompletionRequest.Role.user)
                .content(text)
                .build();
    }

    @Test
    @DisplayName("models(): список моделей доступен")
    void models() {
        ResponseEntity<ModelsResponse> response = api().models();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNotEmpty();
        log.info(
                "Доступные модели: {}",
                response.getBody().getData().stream().map(m -> m.getId()).toList());
    }

    @Test
    @DisplayName("chatCompletionEntity(req): синхронный чат")
    void chatCompletionEntity_sync() {
        CompletionRequest req = CompletionRequest.builder()
                .model(MODEL)
                .messages(List.of(userMessage("Назови столицу России одним словом")))
                .build();

        ResponseEntity<CompletionResponse> response = api().chatCompletionEntity(req);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getChoices().get(0).getMessage().getContent())
                .isNotBlank();
    }

    @Test
    @DisplayName("chatCompletionEntity(req, headers): кастомный заголовок принимается")
    void chatCompletionEntity_withHeaders() {
        CompletionRequest req = CompletionRequest.builder()
                .model(MODEL)
                .messages(List.of(userMessage("Привет")))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.add(GigaChatApi.X_REQUEST_ID, UUID.randomUUID().toString());

        ResponseEntity<CompletionResponse> response = api().chatCompletionEntity(req, headers);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("chatCompletionEntity: ВСЕ параметры запроса принимаются API")
    void chatCompletionEntity_allParamsAccepted() {
        CompletionRequest req = CompletionRequest.builder()
                .model(MODEL)
                .messages(List.of(userMessage("Расскажи короткий факт о космосе")))
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(50)
                .repetitionPenalty(1.1)
                .updateInterval(0.0)
                .profanityCheck(true)
                .build();

        ResponseEntity<CompletionResponse> response = api().chatCompletionEntity(req);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getChoices()).isNotEmpty();
    }

    @Test
    @DisplayName("chatCompletionEntity: maxTokens реально ограничивает длину ответа")
    void chatCompletionEntity_maxTokensLimits() {
        CompletionRequest req = CompletionRequest.builder()
                .model(MODEL)
                .messages(List.of(userMessage("Подробно расскажи историю Москвы")))
                .maxTokens(5)
                .build();

        ResponseEntity<CompletionResponse> response = api().chatCompletionEntity(req);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Integer completionTokens = response.getBody().getUsage().getCompletionTokens();
        log.info("maxTokens=5 -> completionTokens={}", completionTokens);
        assertThat(completionTokens).isLessThanOrEqualTo(8);
    }

    @Test
    @DisplayName("chatCompletionStream(req) и (req, headers): потоковый чат")
    void chatCompletionStream() {
        CompletionRequest req = CompletionRequest.builder().model(MODEL).stream(true)
                .messages(List.of(userMessage("Посчитай от одного до пяти")))
                .build();

        List<CompletionResponse> chunks =
                api().chatCompletionStream(req).collectList().block();
        assertThat(chunks).isNotEmpty();

        HttpHeaders headers = new HttpHeaders();
        headers.add(GigaChatApi.X_REQUEST_ID, UUID.randomUUID().toString());
        List<CompletionResponse> chunksWithHeaders =
                api().chatCompletionStream(req, headers).collectList().block();
        assertThat(chunksWithHeaders).isNotEmpty();
    }

    @Test
    @DisplayName("embeddings(req): эмбеддинги напрямую")
    void embeddings() {
        EmbeddingsRequest req = EmbeddingsRequest.builder()
                .model(EmbeddingsModel.EMBEDDINGS.getName())
                .input(List.of("первый", "второй"))
                .build();

        ResponseEntity<EmbeddingsResponse> response = api().embeddings(req);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getData()).hasSize(2);
        assertThat(response.getBody().getData().get(0).getEmbedding()).isNotEmpty();
    }

    @Test
    @DisplayName("Файлы: uploadFile -> getFileUrl -> downloadFile -> deleteFile (полный жизненный цикл)")
    void fileLifecycle() {
        Media media = Media.builder()
                // данные media обязаны быть byte[]/Resource (контракт Spring AI Media.getDataAsByteArray)
                .data("Содержимое тестового файла для bughunt".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .mimeType(MimeTypeUtils.TEXT_PLAIN)
                .name("bughunt.txt")
                .build();

        // upload
        ResponseEntity<UploadFileResponse> uploaded = api().uploadFile(media);
        assertThat(uploaded.getStatusCode().value()).isEqualTo(200);
        UUID fileId = uploaded.getBody().id();
        assertThat(fileId).isNotNull();
        log.info("Загружен файл id={}", fileId);

        // getFile (метаданные одного файла) и getFiles (список) — #07/#08
        var fileMeta = api().getFile(fileId.toString());
        assertThat(fileMeta.getStatusCode().value()).isEqualTo(200);
        assertThat(fileMeta.getBody().id()).isEqualTo(fileId);

        var allFiles = api().getFiles();
        assertThat(allFiles.getStatusCode().value()).isEqualTo(200);
        assertThat(allFiles.getBody().data()).anyMatch(f -> fileId.equals(f.id()));

        // getFileUrl
        String url = api().getFileUrl(fileId.toString());
        assertThat(url).endsWith("/files/" + fileId + "/content");

        // downloadFile
        byte[] content = api().downloadFile(fileId.toString());
        assertThat(content).isNotEmpty();

        byte[] contentWithHeaders = api().downloadFile(fileId.toString(), new HttpHeaders());
        assertThat(contentWithHeaders).isNotEmpty();

        // deleteFile
        ResponseEntity<DeleteFileResponse> deleted = api().deleteFile(fileId.toString());
        assertThat(deleted.getStatusCode().value()).isEqualTo(200);
        assertThat(deleted.getBody().deleted()).isTrue();
        log.info("Файл удалён: {}", deleted.getBody().deleted());
    }
}
