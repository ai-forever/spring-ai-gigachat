package chat.giga.springai.image;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
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
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

@Slf4j
public class GigaChatImageModel implements ImageModel {

    private static final String FUNCTION_CALL_AUTO = "auto";
    private static final Pattern IMG_ID_PATTERN = Pattern.compile("<img\\s+src=\"([a-fA-F0-9\\-]{36})\"");

    private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION =
            new DefaultImageModelObservationConvention();

    private final GigaChatApi gigaChatApi;
    private final GigaChatImageOptions defaultOptions;
    private final ObservationRegistry observationRegistry;
    private final RetryTemplate retryTemplate;

    private ImageModelObservationConvention observationConvention;

    public GigaChatImageModel(
            GigaChatApi gigaChatApi,
            GigaChatImageOptions defaultOptions,
            ObservationRegistry observationRegistry,
            RetryTemplate retryTemplate) {

        this.gigaChatApi = gigaChatApi;
        this.defaultOptions = defaultOptions;
        this.observationRegistry = observationRegistry;
        this.retryTemplate = retryTemplate;
    }

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
                .observe(() -> processRequest(effectivePrompt, request));
    }

    private ImageResponse processRequest(ImagePrompt prompt, CompletionRequest request) {
        CompletionResponse completion = executeCompletion(request);

        if (isEmptyCompletion(completion)) {
            log.warn("GigaChat returned empty image result for prompt: {}", prompt);
            return new ImageResponse(List.of());
        }

        String fileId = extractFileId(completion);
        if (fileId == null) {
            log.warn("Unable to extract file_id from GigaChat response for prompt: {}", prompt);
            return new ImageResponse(List.of());
        }

        byte[] imageBytes = gigaChatApi.downloadFile(fileId);
        if (imageBytes == null) {
            throw new IllegalStateException("Failed to download image for fileId: " + fileId);
        }

        return buildImageResponse(fileId, imageBytes);
    }

    private ImagePrompt normalizePrompt(ImagePrompt prompt) {
        return prompt.getOptions() == null ? new ImagePrompt(prompt.getInstructions(), defaultOptions) : prompt;
    }

    private CompletionResponse executeCompletion(CompletionRequest request) {
        ResponseEntity<CompletionResponse> entity =
                retryTemplate.execute(ctx -> gigaChatApi.chatCompletionEntity(request));

        return Optional.ofNullable(entity).map(ResponseEntity::getBody).orElse(null);
    }

    private boolean isEmptyCompletion(CompletionResponse completion) {
        return completion == null
                || completion.getChoices() == null
                || completion.getChoices().isEmpty();
    }

    private ImageResponse buildImageResponse(String fileId, byte[] imageBytes) {

        String base64 = Base64.getEncoder().encodeToString(imageBytes);

        Image image = new Image(null, base64);
        ImageGenerationMetadata metadata = new GigaChatImageGenerationMetadata(fileId);
        ImageGeneration generation = new ImageGeneration(image, metadata);

        return new ImageResponse(List.of(generation), new ImageResponseMetadata());
    }

    private String extractFileId(CompletionResponse response) {
        String content = response.getChoices().get(0).getMessage().getContent();
        Matcher matcher = IMG_ID_PATTERN.matcher(content);
        if (!matcher.find()) {
            throw new IllegalStateException("No <img src=\"...\"> tag found in GigaChat response: " + content);
        }

        return matcher.group(1);
    }

    private CompletionRequest buildCompletionRequest(ImagePrompt prompt) {

        CompletionRequest req = new CompletionRequest();

        req.setModel(prompt.getOptions().getModel());
        req.setStream(false);
        req.setFunctionCall(FUNCTION_CALL_AUTO);

        List<CompletionRequest.Message> messages = new ArrayList<>();

        CompletionRequest.Message sys = new CompletionRequest.Message();
        sys.setRole(CompletionRequest.Role.system);
        sys.setContent(prompt.getOptions().getStyle());

        messages.add(sys);

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
     * Use the provided convention for reporting observation data
     *
     * @param observationConvention The provided convention
     */
    public void setObservationConvention(ImageModelObservationConvention observationConvention) {
        Assert.notNull(observationConvention, "observationConvention cannot be null");
        this.observationConvention = observationConvention;
    }
}
