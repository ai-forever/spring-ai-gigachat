package chat.giga.springai.image;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import io.micrometer.observation.ObservationRegistry;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;

class GigaChatImageModelTest {

    public static final String GIGA_CHAT_2_MAX = "GigaChat-2-Max";
    GigaChatApi gigaChatApi = Mockito.mock(GigaChatApi.class);

    GigaChatImageOptions defaultOptions =
            GigaChatImageOptions.builder().model("GigaChat-2-Max").build();

    RetryTemplate retryTemplate = RetryTemplate.defaultInstance();

    GigaChatImageModel imageModel =
            new GigaChatImageModel(gigaChatApi, defaultOptions, ObservationRegistry.NOOP, retryTemplate);

    @Test
    void testSuccessfulImageGenerationB64Json() {
        CompletionResponse completionResponse = createCompletionResponse();

        Mockito.when(gigaChatApi.chatCompletionEntity(any())).thenReturn(ResponseEntity.ok(completionResponse));

        byte[] fakeJpg = new byte[] {1, 2, 3, 4};

        Mockito.when(gigaChatApi.downloadFile("11111111-2222-3333-4444-555555555555"))
                .thenReturn(fakeJpg);

        ImagePrompt prompt = new ImagePrompt(
                List.of(new ImageMessage("Draw a cat", 1.0f)),
                ImageOptionsBuilder.builder().build());

        ImageResponse response = imageModel.call(prompt);

        assertNotNull(response);
        assertEquals(1, response.getResults().size(), "There must be one image result");

        var gen = response.getResult();
        assertNotNull(gen);

        String expectedBase64 = Base64.getEncoder().encodeToString(fakeJpg);
        assertEquals(expectedBase64, gen.getOutput().getB64Json());
        assertNull(gen.getOutput().getUrl());

        assertInstanceOf(GigaChatImageGenerationMetadata.class, gen.getMetadata());
        assertEquals(
                "11111111-2222-3333-4444-555555555555",
                ((GigaChatImageGenerationMetadata) gen.getMetadata()).getFileId());

        Mockito.verify(gigaChatApi, Mockito.times(1)).chatCompletionEntity(any());
        Mockito.verify(gigaChatApi, Mockito.times(1)).downloadFile("11111111-2222-3333-4444-555555555555");
    }

    @Test
    void testSuccessfulImageGenerationUrl() {
        CompletionResponse completionResponse = createCompletionResponse();

        Mockito.when(gigaChatApi.chatCompletionEntity(any())).thenReturn(ResponseEntity.ok(completionResponse));
        Mockito.when(gigaChatApi.getFileUrl("11111111-2222-3333-4444-555555555555"))
                .thenReturn(
                        "https://gigachat.devices.sberbank.ru/api/v1/files/11111111-2222-3333-4444-555555555555/content");

        GigaChatImageOptions options = GigaChatImageOptions.builder()
                .responseFormat(GigaChatImageOptions.RESPONSE_FORMAT_URL)
                .build();

        ImagePrompt prompt = new ImagePrompt(List.of(new ImageMessage("Draw a cat", 1.0f)), options);

        ImageResponse response = imageModel.call(prompt);

        assertNotNull(response);
        assertEquals(1, response.getResults().size(), "There must be one image result");

        var gen = response.getResult();
        assertNotNull(gen);

        assertEquals(
                "https://gigachat.devices.sberbank.ru/api/v1/files/11111111-2222-3333-4444-555555555555/content",
                gen.getOutput().getUrl());
        assertNull(gen.getOutput().getB64Json());

        assertInstanceOf(GigaChatImageGenerationMetadata.class, gen.getMetadata());
        assertEquals(
                "11111111-2222-3333-4444-555555555555",
                ((GigaChatImageGenerationMetadata) gen.getMetadata()).getFileId());

        Mockito.verify(gigaChatApi, Mockito.times(1)).chatCompletionEntity(any());
        Mockito.verify(gigaChatApi, Mockito.never()).downloadFile(any());
    }

    private CompletionResponse createCompletionResponse() {
        CompletionResponse.MessagesRes message = new CompletionResponse.MessagesRes();
        message.setRole(CompletionResponse.Role.assistant);
        message.setContent("Generated <img src=\"11111111-2222-3333-4444-555555555555\"/>");

        CompletionResponse.Choice choice = new CompletionResponse.Choice();
        choice.setMessage(message);
        choice.setFinishReason(CompletionResponse.FinishReason.STOP);
        choice.setIndex(0);

        CompletionResponse completionResponse = new CompletionResponse();
        completionResponse.setChoices(List.of(choice));
        completionResponse.setModel(GIGA_CHAT_2_MAX);
        completionResponse.setUsage(null);
        return completionResponse;
    }

    @Test
    void testNormalizePromptWithNullOptions() {
        ImagePrompt prompt = new ImagePrompt(List.of(new ImageMessage("Draw a dog", 1.0f)));

        ImagePrompt normalizedPrompt = imageModel.normalizePrompt(prompt);

        assertNotNull(normalizedPrompt.getOptions());
        assertEquals(defaultOptions.getModel(), normalizedPrompt.getOptions().getModel());
        assertEquals(defaultOptions.getStyle(), normalizedPrompt.getOptions().getStyle());
    }

    @Test
    void testNormalizePromptWithOptionsWithNullFields() {
        GigaChatImageOptions options = GigaChatImageOptions.builder()
                .model(null) // Explicitly set model to null
                .style("Custom style")
                .build();

        ImagePrompt prompt = new ImagePrompt(List.of(new ImageMessage("Draw a cat", 1.0f)), options);

        ImagePrompt normalizedPrompt = imageModel.normalizePrompt(prompt);

        assertNotNull(normalizedPrompt.getOptions());
        // Should use default model since prompt model is null
        assertEquals(defaultOptions.getModel(), normalizedPrompt.getOptions().getModel());
        // Should use custom style since it's not null
        assertEquals("Custom style", normalizedPrompt.getOptions().getStyle());
    }

    @Test
    void testNormalizePromptWithOptionsWithNonNullFields() {
        GigaChatImageOptions options = GigaChatImageOptions.builder()
                .model("CustomModel")
                .style("Custom style")
                .build();

        ImagePrompt prompt = new ImagePrompt(List.of(new ImageMessage("Draw a bird", 1.0f)), options);

        ImagePrompt normalizedPrompt = imageModel.normalizePrompt(prompt);

        assertNotNull(normalizedPrompt.getOptions());
        // Should use custom model since it's not null
        assertEquals("CustomModel", normalizedPrompt.getOptions().getModel());
        // Should use custom style since it's not null
        assertEquals("Custom style", normalizedPrompt.getOptions().getStyle());
    }

    @Test
    void testNormalizePromptWithOptionsWithNullStyle() {
        GigaChatImageOptions options = GigaChatImageOptions.builder()
                .model("CustomModel")
                .style(null) // Explicitly set style to null
                .build();

        ImagePrompt prompt = new ImagePrompt(List.of(new ImageMessage("Draw a fish", 1.0f)), options);

        ImagePrompt normalizedPrompt = imageModel.normalizePrompt(prompt);

        assertNotNull(normalizedPrompt.getOptions());
        // Should use custom model since it's not null
        assertEquals("CustomModel", normalizedPrompt.getOptions().getModel());
        // Should use default style since prompt style is null
        assertEquals(defaultOptions.getStyle(), normalizedPrompt.getOptions().getStyle());
    }
}
