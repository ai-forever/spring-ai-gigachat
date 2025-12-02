package chat.giga.springai.image;

import chat.giga.springai.GigaChatOptions;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGenerationMetadata;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class GigaChatImageModel implements ImageModel {

    private static final String FUNCTION_CALL_AUTO = "auto";
    private static final Pattern IMG_ID_PATTERN =
            Pattern.compile("<img\\s+src=\"([a-fA-F0-9\\-]{36})\"");
    public static final String SYSTEM_PROMPT = "You are an artist. If the user asks you to draw something," +
            "generate an image using the built-in text2image function" +
            "and return a tag in the form <img src=\"FILE_ID\"/>.";

    private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION =
            new DefaultImageModelObservationConvention();

    private final GigaChatApi gigaChatApi;
    private final GigaChatOptions defaultOptions;
    private final ObservationRegistry observationRegistry;
    private final RetryTemplate retryTemplate;

    private ImageModelObservationConvention observationConvention;

    public GigaChatImageModel(
            GigaChatApi gigaChatApi,
            GigaChatOptions defaultOptions,
            ObservationRegistry observationRegistry,
            RetryTemplate retryTemplate) {

        this.gigaChatApi = gigaChatApi;
        this.defaultOptions = defaultOptions;
        this.observationRegistry = observationRegistry;
        this.retryTemplate = retryTemplate;
    }

    @Override
    public ImageResponse call(ImagePrompt prompt) {
        var observationContext = ImageModelObservationContext.builder()
                .imagePrompt(prompt)
                .provider(GigaChatApi.PROVIDER_NAME)
                .build();

        CompletionRequest req = buildCompletionRequest(prompt);

        return ImageModelObservationDocumentation.IMAGE_MODEL_OPERATION
                .observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION,
                        () -> observationContext, this.observationRegistry)
                .observe(() -> {
                    ResponseEntity<CompletionResponse> responseEntity =
                            this.retryTemplate.execute(ctx -> gigaChatApi.chatCompletionEntity(req));

                    CompletionResponse completion =
                            Optional.ofNullable(responseEntity)
                                    .map(ResponseEntity::getBody)
                                    .orElse(null);

                    if (completion == null ||
                            completion.getChoices() == null ||
                            completion.getChoices().isEmpty()) {

                        log.warn("GigaChat returned empty image result for prompt: {}", prompt);
                        return new ImageResponse(List.of());
                    }

                    String fileId = extractFileId(completion);
                    if (fileId == null) {
                        log.warn("Unable to extract file_id from GigaChat response for prompt: {}", prompt);
                        return new ImageResponse(List.of());
                    }

                    byte[] jpgBytes = gigaChatApi.downloadFile(fileId);

                    if (jpgBytes == null) {
                        throw new IllegalStateException("Failed to download image for fileId: " + fileId);
                    }

                    String base64 = Base64.getEncoder().encodeToString(jpgBytes);

                    Image image = new Image(null, base64);
                    ImageGenerationMetadata genMeta = new GigaChatImageGenerationMetadata(fileId);
                    ImageGeneration generation = new ImageGeneration(image, genMeta);

                    ImageResponseMetadata respMeta = new ImageResponseMetadata();

                    return new ImageResponse(List.of(generation), respMeta);
                });
    }


    private static String extractFileId(CompletionResponse response) {
        if (response == null || response.getChoices().isEmpty()) {
            throw new IllegalStateException("Empty response from GigaChat");
        }

        String content = response.getChoices().get(0).getMessage().getContent();
        Matcher matcher = IMG_ID_PATTERN.matcher(content);
        if (!matcher.find()) {
            throw new IllegalStateException(
                    "No <img src=\"...\"> tag found in GigaChat response: " + content
            );
        }

        return matcher.group(1);
    }

    private CompletionRequest buildCompletionRequest(ImagePrompt prompt) {

        CompletionRequest req = new CompletionRequest();

        req.setModel(defaultOptions.getModel());
        req.setStream(false);
        req.setFunctionCall(FUNCTION_CALL_AUTO);

        List<CompletionRequest.Message> messages = new ArrayList<>();

        CompletionRequest.Message sys = new CompletionRequest.Message();
        sys.setRole(CompletionRequest.Role.system);
        sys.setContent(SYSTEM_PROMPT);

        messages.add(sys);

        String userText = prompt.getInstructions().stream()
                .map(ImageMessage::getText)
                .collect(Collectors.joining("\n"));

        CompletionRequest.Message user = new CompletionRequest.Message();
        user.setRole(CompletionRequest.Role.user);
        user.setContent(userText);

        messages.add(user);

        req.setMessages(messages);

        if(log.isDebugEnabled()) {
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
