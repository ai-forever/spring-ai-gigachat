package chat.giga.springai.image;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import chat.giga.springai.support.GigaRetryTemplate;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageGenerationMetadata;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;

/**
 * {@link ImageModel} implementation backed by GigaChat. Internally calls the regular
 * {@code /chat/completions} endpoint with {@code function_call=auto} — the model replies
 * with an HTML fragment of the form {@code <img src="<file-id>"/>}, from which we extract
 * the {@code file-id}, download the binary via {@link GigaChatApi#downloadFile(String)}
 * and assemble an {@link ImageResponse} with a base64-encoded payload.
 *
 * <p>The class is instrumented through the Spring AI Observation API: each {@link #call}
 * produces a {@code gen_ai.client.operation} observation with the {@code image} operation
 * type. The default convention is {@link GigaChatImageModelObservationConvention} — it
 * augments the standard tag set with {@code gen_ai.response.model}, restoring tag-key
 * parity with the chat metric and silencing the {@code PrometheusMeterRegistry} warning
 * about mismatched tag keys (see issue GH-111). A custom convention can be supplied via
 * {@link #setObservationConvention(ImageModelObservationConvention)}.
 *
 * <p>All network calls to GigaChat are wrapped in {@link GigaRetryTemplate} to provide
 * resilience against transient failures while preserving the original exception type when
 * the retry budget is exhausted.
 */
@Slf4j
public class GigaChatImageModel implements ImageModel {

    private static final String FUNCTION_CALL_AUTO = "auto";
    private static final Pattern IMG_ID_PATTERN = Pattern.compile("<img\\s+src=\"([a-fA-F0-9\\-]{36})\"");

    private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION =
            new GigaChatImageModelObservationConvention();

    private final GigaChatApi gigaChatApi;
    private final GigaChatImageOptions defaultOptions;
    private final ObservationRegistry observationRegistry;
    private final GigaRetryTemplate retryTemplate;

    private ImageModelObservationConvention observationConvention;

    /**
     * Creates a GigaChat image model with the given API client, default options and
     * observability/retry infrastructure.
     *
     * @param gigaChatApi GigaChat client used for {@code /chat/completions} and file downloads
     * @param defaultOptions options applied when an {@link ImagePrompt} does not carry its own
     *     (covers model, style, etc.)
     * @param observationRegistry registry where observations are published; pass
     *     {@link ObservationRegistry#NOOP} in tests that do not care about instrumentation
     * @param retryTemplate retry policy, wrapped into {@link GigaRetryTemplate}
     */
    public GigaChatImageModel(
            GigaChatApi gigaChatApi,
            GigaChatImageOptions defaultOptions,
            ObservationRegistry observationRegistry,
            RetryTemplate retryTemplate) {

        this.gigaChatApi = gigaChatApi;
        this.defaultOptions = defaultOptions;
        this.observationRegistry = observationRegistry;
        this.retryTemplate = new GigaRetryTemplate(retryTemplate);
    }

    /**
     * Synchronously generates an image for the given prompt and wraps execution in a Spring AI
     * observation. If the prompt does not carry options, defaults are applied through
     * {@link #normalizePrompt(ImagePrompt)}. Once the model responds, the resulting
     * {@link ImageResponse} is stashed into the observation context — this is required so that
     * {@code gen_ai.response.model} ends up on the final timer with a real value rather than
     * the {@code none} placeholder.
     *
     * @param prompt image description (text plus generation options)
     * @return a response containing a single {@link ImageGeneration} (base64 + file_id), or an
     *     empty result if the model returned nothing usable
     */
    @Override
    public ImageResponse call(ImagePrompt prompt) {
        ImagePrompt effectivePrompt = normalizePrompt(prompt);

        ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
                .imagePrompt(effectivePrompt)
                .provider(GigaChatApi.PROVIDER_NAME)
                .build();

        CompletionRequest request = buildCompletionRequest(effectivePrompt);

        return ImageModelObservationDocumentation.IMAGE_MODEL_OPERATION
                .observation(
                        this.observationConvention,
                        DEFAULT_OBSERVATION_CONVENTION,
                        () -> observationContext,
                        this.observationRegistry)
                .observe(() -> processRequest(effectivePrompt, request, observationContext));
    }

    /**
     * Executes the request against GigaChat, parses the response, downloads the binary payload
     * and assembles an {@link ImageResponse}. On every early exit (empty completion, missing
     * {@code <img src=...>} tag) the method still publishes a placeholder response into the
     * observation context, so the {@code gen_ai.response.model} tag is populated correctly even
     * in negative scenarios.
     *
     * @param prompt the prompt after normalization (used for log messages)
     * @param request a ready-to-send {@link CompletionRequest}
     * @param observationContext context that receives the final {@link ImageResponse}
     * @return generation result; an empty {@link ImageResponse} when the model returned no data
     *     or the file id could not be extracted
     * @throws IllegalStateException when the file id was extracted but downloading the binary
     *     produced {@code null}
     */
    private ImageResponse processRequest(
            ImagePrompt prompt, CompletionRequest request, ImageModelObservationContext observationContext) {
        CompletionResponse completion = executeCompletion(request);

        if (isEmptyCompletion(completion)) {
            log.warn("GigaChat returned empty image result for prompt: {}", prompt);
            ImageResponse empty = new ImageResponse(List.of(), new GigaChatImageResponseMetadata(null));
            observationContext.setResponse(empty);
            return empty;
        }

        String fileId = extractFileId(completion);
        if (fileId == null) {
            log.warn("Unable to extract file_id from GigaChat response for prompt: {}", prompt);
            ImageResponse empty =
                    new ImageResponse(List.of(), new GigaChatImageResponseMetadata(completion.getModel()));
            observationContext.setResponse(empty);
            return empty;
        }

        String responseFormat = prompt.getOptions().getResponseFormat();
        if (GigaChatImageOptions.RESPONSE_FORMAT_URL.equals(responseFormat)) {
            ImageResponse response = buildUrlImageResponse(fileId, completion.getModel());
            observationContext.setResponse(response);
            return response;
        }

        byte[] imageBytes = gigaChatApi.downloadFile(fileId);
        if (imageBytes == null) {
            throw new IllegalStateException("Failed to download image for fileId: " + fileId);
        }

        ImageResponse response = buildBase64ImageResponse(fileId, imageBytes, completion.getModel());
        observationContext.setResponse(response);
        return response;
    }

    /**
     * If the prompt does not carry options, wraps it in a new {@link ImagePrompt} populated with
     * {@link #defaultOptions}. Otherwise returns the prompt unchanged.
     *
     * @param prompt the prompt as passed in by the caller
     * @return a prompt that is guaranteed to have non-null options
     */
    private ImagePrompt normalizePrompt(ImagePrompt prompt) {
        if (prompt.getOptions() == null) { // safeguard against changes in Spring AI logic
            return new ImagePrompt(prompt.getInstructions(), defaultOptions);
        }

        // Merge options: use values from prompt if present, otherwise use defaults
        ImageOptions promptOptions = prompt.getOptions();
        GigaChatImageOptions mergedOptions = GigaChatImageOptions.builder()
                .model(promptOptions.getModel() != null ? promptOptions.getModel() : defaultOptions.getModel())
                .style(promptOptions.getStyle() != null ? promptOptions.getStyle() : defaultOptions.getStyle())
                .responseFormat(
                        promptOptions.getResponseFormat() != null
                                ? promptOptions.getResponseFormat()
                                : defaultOptions.getResponseFormat())
                .build();

        return new ImagePrompt(prompt.getInstructions(), mergedOptions);
    }

    /**
     * Calls GigaChat {@code /chat/completions} through the retry wrapper and unwraps the
     * response body.
     *
     * @param request the request to execute
     * @return body of the {@link CompletionResponse}, or {@code null} when the HTTP response is empty
     */
    private CompletionResponse executeCompletion(CompletionRequest request) {
        ResponseEntity<CompletionResponse> entity =
                retryTemplate.execute(() -> gigaChatApi.chatCompletionEntity(request));

        return Optional.ofNullable(entity).map(ResponseEntity::getBody).orElse(null);
    }

    /**
     * Checks whether the completion is non-empty and contains at least one {@code choice}.
     *
     * @param completion parsed model response (may be {@code null})
     * @return {@code true} when the completion is missing or carries no choices
     */
    private boolean isEmptyCompletion(CompletionResponse completion) {
        return completion == null
                || completion.getChoices() == null
                || completion.getChoices().isEmpty();
    }

    /**
     * Assembles an {@link ImageResponse} carrying the image as a base64-encoded payload.
     * Used when the requested {@code response_format} is base64 (the Spring AI default for
     * GigaChat). The response metadata is set to {@link GigaChatImageResponseMetadata}
     * populated with the model that the API actually used — this feeds the
     * {@code gen_ai.response.model} observation tag.
     *
     * @param fileId file identifier returned by GigaChat
     * @param imageBytes downloaded image binary
     * @param responseModel value of the {@code model} field from {@link CompletionResponse} —
     *     the model that actually served the request (may differ from the requested one)
     * @return a response with a single {@link ImageGeneration}
     */
    private ImageResponse buildBase64ImageResponse(String fileId, byte[] imageBytes, String responseModel) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        Image image = new Image(null, base64);
        ImageGenerationMetadata metadata = new GigaChatImageGenerationMetadata(fileId);
        ImageGeneration generation = new ImageGeneration(image, metadata);
        return new ImageResponse(List.of(generation), new GigaChatImageResponseMetadata(responseModel));
    }

    /**
     * Assembles an {@link ImageResponse} where {@link Image} carries a downloadable URL
     * instead of a base64 payload. Used when the caller opted into
     * {@link GigaChatImageOptions#RESPONSE_FORMAT_URL}, which lets clients stream the image
     * straight from GigaChat without going through the application memory. The response
     * metadata mirrors the base64 path so {@code gen_ai.response.model} stays consistent
     * across both formats.
     *
     * @param fileId file identifier returned by GigaChat
     * @param responseModel value of the {@code model} field from {@link CompletionResponse}
     * @return a response with a single {@link ImageGeneration} pointing at the file URL
     */
    private ImageResponse buildUrlImageResponse(String fileId, String responseModel) {
        String url = gigaChatApi.getFileUrl(fileId);
        Image image = new Image(url, null);
        ImageGenerationMetadata metadata = new GigaChatImageGenerationMetadata(fileId);
        ImageGeneration generation = new ImageGeneration(image, metadata);
        return new ImageResponse(List.of(generation), new GigaChatImageResponseMetadata(responseModel));
    }

    /**
     * Extracts the {@code file-id} from the content of the first choice — the model returns it
     * wrapped in an HTML fragment {@code <img src="<uuid>"/>}. The method throws when the tag
     * is absent: this is treated as a contract violation by GigaChat rather than a regular
     * empty result.
     *
     * @param response parsed {@link CompletionResponse} (expected to be non-empty)
     * @return file UUID
     * @throws IllegalStateException when the content does not contain the expected
     *     {@code <img src=...>} tag
     */
    private String extractFileId(CompletionResponse response) {
        String content = response.getChoices().get(0).getMessage().getContent();
        Matcher matcher = IMG_ID_PATTERN.matcher(content);
        if (!matcher.find()) {
            log.warn("No <img src=\"...\"> tag found in GigaChat response: {}", content);
            return null;
        }

        return matcher.group(1);
    }

    /**
     * Builds the {@link CompletionRequest} for image generation: a system message carrying the
     * style, a user message with the prompt text, and {@code function_call=auto} (the function
     * GigaChat uses to trigger image generation). When the prompt carries more than one
     * instruction, the first one is used and a warning is logged.
     *
     * @param prompt prompt with non-null options (already normalized via {@link #normalizePrompt})
     * @return request ready to be sent to the API
     */
    private CompletionRequest buildCompletionRequest(ImagePrompt prompt) {

        CompletionRequest req = new CompletionRequest();

        req.setModel(prompt.getOptions().getModel());
        req.setStream(false);
        req.setFunctionCall(FUNCTION_CALL_AUTO);

        List<CompletionRequest.Message> messages = new ArrayList<>();

        // Add system message only if style is provided
        String style = prompt.getOptions().getStyle();
        if (style != null) {
            CompletionRequest.Message sys = new CompletionRequest.Message();
            sys.setRole(CompletionRequest.Role.system);
            sys.setContent(style);
            messages.add(sys);
        }

        if (prompt.getInstructions().size() > 1) {
            log.warn("GigaChat only supports one instruction, using the first one");
        }
        String userText = prompt.getInstructions().get(0).getText();

        CompletionRequest.Message user = new CompletionRequest.Message();
        user.setRole(CompletionRequest.Role.user);
        user.setContent(userText);

        messages.add(user);

        req.setMessages(messages);

        if (log.isDebugEnabled()) {
            log.debug("Request: {}", req);
        }

        return req;
    }

    /**
     * Overrides the convention used to report observation data. When no convention is set,
     * {@link GigaChatImageModelObservationConvention} is used by default — it keeps tag keys
     * symmetric with the chat metric. Pass a custom convention only when the standard tag set
     * needs to be altered.
     *
     * @param observationConvention the convention to install (must not be {@code null})
     */
    public void setObservationConvention(ImageModelObservationConvention observationConvention) {
        Assert.notNull(observationConvention, "observationConvention cannot be null");
        this.observationConvention = observationConvention;
    }
}
