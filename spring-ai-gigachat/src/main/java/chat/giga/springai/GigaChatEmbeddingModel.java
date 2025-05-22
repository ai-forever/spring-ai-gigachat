package chat.giga.springai;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.embedding.EmbeddingsRequest;
import chat.giga.springai.api.chat.embedding.EmbeddingsResponse;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public class GigaChatEmbeddingModel implements EmbeddingModel {
    private static final int DIMENSIONS_NOT_SET = -1;

    private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION =
            new DefaultEmbeddingModelObservationConvention();

    private final GigaChatApi gigaChatApi;
    private final GigaChatEmbeddingOptions defaultOptions;
    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;
    private volatile int dimensions = DIMENSIONS_NOT_SET;

    private EmbeddingModelObservationConvention observationConvention;

    public GigaChatEmbeddingModel(
            GigaChatApi gigaChatApi,
            GigaChatEmbeddingOptions defaultOptions,
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry) {
        this.gigaChatApi = gigaChatApi;
        this.defaultOptions = defaultOptions;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
        this.dimensions = Objects.requireNonNullElse(defaultOptions.getDimensions(), DIMENSIONS_NOT_SET);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        log.debug("Embedding call request: {}", String.join("\n", request.getInstructions()));
        String model = StringUtils.hasText(request.getOptions().getModel())
                ? request.getOptions().getModel()
                : defaultOptions.getModel();
        EmbeddingsRequest embeddingsRequest = new EmbeddingsRequest(model, request.getInstructions());

        var observationContext = EmbeddingModelObservationContext.builder()
                .embeddingRequest(request)
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
                            this.retryTemplate.execute(ctx -> gigaChatApi.embeddings(embeddingsRequest));

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
                    EmbeddingsResponse.EmbeddingData embeddingData =
                            apiEmbeddingResponse.getData().stream().findFirst().orElse(null);

                    Assert.notNull(embeddingData, "Embedding data must not be null");

                    var metadata =
                            new EmbeddingResponseMetadata(apiEmbeddingResponse.getModel(), embeddingData.getUsage());

                    List<Embedding> embeddings = apiEmbeddingResponse.getData().stream()
                            .map(e -> new Embedding(e.getEmbedding(), e.getIndex()))
                            .toList();

                    EmbeddingResponse embeddingResponse = new EmbeddingResponse(embeddings, metadata);

                    observationContext.setResponse(embeddingResponse);

                    return embeddingResponse;
                });
    }

    @Override
    public float[] embed(Document document) {
        Assert.notNull(document, "Document must not be null");
        EmbeddingRequest embeddingRequest =
                new EmbeddingRequest(List.of(document.getFormattedContent()), this.defaultOptions);
        EmbeddingResponse embeddingResponse = this.call(embeddingRequest);
        return embeddingResponse.getResult().getOutput();
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
        if (this.dimensions == DIMENSIONS_NOT_SET) {
            this.dimensions = this.embed("Test String").length;
        }
        return this.dimensions;
    }
}
