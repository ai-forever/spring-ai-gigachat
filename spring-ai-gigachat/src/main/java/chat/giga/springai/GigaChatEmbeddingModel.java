package chat.giga.springai;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.embedding.EmbeddingsModel;
import chat.giga.springai.api.chat.embedding.EmbeddingsRequest;
import chat.giga.springai.api.chat.embedding.EmbeddingsResponse;
import chat.giga.springai.support.GigaRetryTemplate;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public class GigaChatEmbeddingModel extends AbstractEmbeddingModel {
    private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION =
            new DefaultEmbeddingModelObservationConvention();

    // ConcurrentHashMap: dimensions() кладёт сюда вычисленные размерности для неизвестных моделей,
    // а модель может использоваться из нескольких потоков (computeIfAbsent на обычном HashMap не потокобезопасен).
    private static final Map<String, Integer> KNOWN_EMBEDDING_DIMENSIONS =
            new ConcurrentHashMap<>(Stream.of(EmbeddingsModel.values())
                    .collect(Collectors.toMap(EmbeddingsModel::getName, EmbeddingsModel::getDimensions)));

    private final GigaChatApi gigaChatApi;
    private final GigaChatEmbeddingOptions defaultOptions;
    private final GigaRetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;
    private final MetadataMode metadataMode;

    private EmbeddingModelObservationConvention observationConvention;

    public GigaChatEmbeddingModel(
            GigaChatApi gigaChatApi,
            GigaChatEmbeddingOptions defaultOptions,
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry) {
        this(gigaChatApi, defaultOptions, retryTemplate, observationRegistry, MetadataMode.EMBED);
    }

    public GigaChatEmbeddingModel(
            GigaChatApi gigaChatApi,
            GigaChatEmbeddingOptions defaultOptions,
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry,
            MetadataMode metadataMode) {
        this.gigaChatApi = gigaChatApi;
        this.defaultOptions = defaultOptions;
        this.retryTemplate = new GigaRetryTemplate(retryTemplate);
        this.observationRegistry = observationRegistry;
        this.metadataMode = metadataMode != null ? metadataMode : MetadataMode.EMBED;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        log.debug("Embedding call request: {}", String.join("\n", request.getInstructions()));
        // EmbeddingRequest.getOptions() объявлен @Nullable в Spring AI 2.x — обращаемся через null-guard,
        // иначе model.call(new EmbeddingRequest(list, null)) падал бы с NPE вместо подстановки default-модели.
        String requestModel =
                request.getOptions() != null ? request.getOptions().getModel() : null;
        String model = StringUtils.hasText(requestModel) ? requestModel : defaultOptions.getModel();
        EmbeddingsRequest embeddingsRequest = new EmbeddingsRequest(model, request.getInstructions());

        // В контекст наблюдения кладём запрос с УЖЕ разрешённой моделью, иначе тег gen_ai.request.model
        // оказывался бы 'none', когда вызывающий передал options=null или без модели (как у Mistral).
        EmbeddingRequest observedRequest = new EmbeddingRequest(
                request.getInstructions(),
                GigaChatEmbeddingOptions.builder().withModel(model).build());

        var observationContext = EmbeddingModelObservationContext.builder()
                .embeddingRequest(observedRequest)
                .provider(GigaChatApi.PROVIDER_NAME)
                .build();

        return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
                .observation(
                        this.observationConvention,
                        DEFAULT_OBSERVATION_CONVENTION,
                        () -> observationContext,
                        this.observationRegistry)
                .observe(() -> {
                    ResponseEntity<EmbeddingsResponse> embeddingsResponseResponseEntity =
                            this.retryTemplate.execute(() -> gigaChatApi.embeddings(embeddingsRequest));

                    Optional<EmbeddingsResponse> embeddingsResponseOptional = Optional.ofNullable(
                                    embeddingsResponseResponseEntity)
                            .map(ResponseEntity::getBody);

                    if (embeddingsResponseOptional.isEmpty()
                            || embeddingsResponseOptional
                                    .map(EmbeddingsResponse::getData)
                                    .orElseGet(List::of)
                                    .isEmpty()) {
                        log.warn("No embeddings returned for request: {}", request);
                        return new EmbeddingResponse(List.of());
                    }

                    EmbeddingsResponse apiEmbeddingResponse = embeddingsResponseOptional.get();

                    // Усредняем/суммируем usage по всему батчу: раньше в метаданные попадали токены только
                    // первого эмбеддинга, из-за чего при нескольких входах totalTokens занижался.
                    var metadata = new EmbeddingResponseMetadata(
                            apiEmbeddingResponse.getModel(), aggregateUsage(apiEmbeddingResponse.getData()));

                    List<Embedding> embeddings = apiEmbeddingResponse.getData().stream()
                            .map(e -> new Embedding(e.getEmbedding(), e.getIndex()))
                            .toList();

                    EmbeddingResponse embeddingResponse = new EmbeddingResponse(embeddings, metadata);

                    observationContext.setResponse(embeddingResponse);

                    return embeddingResponse;
                });
    }

    // Суммирует prompt-токены по всем эмбеддингам батча в один Usage для метаданных ответа.
    private EmbeddingsResponse.GigaChatEmbeddingsUsage aggregateUsage(List<EmbeddingsResponse.EmbeddingData> data) {
        int totalPromptTokens = data.stream()
                .map(EmbeddingsResponse.EmbeddingData::getUsage)
                .filter(Objects::nonNull)
                .map(EmbeddingsResponse.GigaChatEmbeddingsUsage::getPromptTokens)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return EmbeddingsResponse.GigaChatEmbeddingsUsage.builder()
                .promptTokens(totalPromptTokens)
                .build();
    }

    @Override
    public float[] embed(Document document) {
        Assert.notNull(document, "Document must not be null");
        EmbeddingRequest embeddingRequest =
                new EmbeddingRequest(List.of(getEmbeddingContent(document)), this.defaultOptions);
        EmbeddingResponse embeddingResponse = this.call(embeddingRequest);
        return embeddingResponse.getResult().getOutput();
    }

    /**
     * Форматирует содержимое документа с учётом настроенного {@link MetadataMode}
     * (свойство {@code spring.ai.gigachat.embedding.metadata-mode}). Используется как для
     * {@link #embed(Document)}, так и для батч-эмбеддинга документов в базовом классе.
     */
    @Override
    public String getEmbeddingContent(Document document) {
        return document.getFormattedContent(this.metadataMode);
    }

    /**
     * Use the provided convention for reporting observation data
     *
     * @param observationConvention The provided convention
     */
    public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
        Assert.notNull(observationConvention, "observationConvention cannot be null");
        this.observationConvention = observationConvention;
    }

    @Override
    public int dimensions() {
        String model = this.defaultOptions.getModel();
        if (model == null) {
            return super.dimensions();
        }
        return KNOWN_EMBEDDING_DIMENSIONS.computeIfAbsent(model, m -> super.dimensions());
    }
}
