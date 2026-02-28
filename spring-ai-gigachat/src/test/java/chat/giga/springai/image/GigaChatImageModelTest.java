package chat.giga.springai.image;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import io.micrometer.observation.ObservationRegistry;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;

class GigaChatImageModelTest {

    private static final String GIGA_CHAT_2_MAX = "GigaChat-2-Max";

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

    @Test
    void testOptionsMerging_usesPromptOptionsWhenProvided() {
        CompletionResponse completionResponse = createCompletionResponse();
        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);

        Mockito.when(gigaChatApi.chatCompletionEntity(requestCaptor.capture()))
                .thenReturn(ResponseEntity.ok(completionResponse));

        GigaChatImageOptions promptOptions = GigaChatImageOptions.builder()
                .model("CustomModel")
                .style("Custom style")
                .responseFormat(GigaChatImageOptions.RESPONSE_FORMAT_URL)
                .build();

        ImagePrompt prompt = new ImagePrompt(List.of(new ImageMessage("Draw a bird", 1.0f)), promptOptions);

        imageModel.call(prompt);

        CompletionRequest capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals("CustomModel", capturedRequest.getModel());

        var systemMessage = capturedRequest.getMessages().stream()
                .filter(m -> m.getRole() == CompletionRequest.Role.system)
                .findFirst()
                .orElse(null);
        assertNotNull(systemMessage);
        assertEquals("Custom style", systemMessage.getContent());
    }

    @Test
    void testOptionsMerging_usesDefaultOptionsWhenPromptOptionsAreNull() {
        CompletionResponse completionResponse = createCompletionResponse();
        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);

        Mockito.when(gigaChatApi.chatCompletionEntity(requestCaptor.capture()))
                .thenReturn(ResponseEntity.ok(completionResponse));

        byte[] fakeJpg = new byte[] {1, 2, 3, 4};
        Mockito.when(gigaChatApi.downloadFile("11111111-2222-3333-4444-555555555555"))
                .thenReturn(fakeJpg);

        ImagePrompt prompt = new ImagePrompt(List.of(new ImageMessage("Draw a dog", 1.0f)));

        imageModel.call(prompt);

        CompletionRequest capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals("GigaChat-2-Max", capturedRequest.getModel());

        var systemMessage = capturedRequest.getMessages().stream()
                .filter(m -> m.getRole() == CompletionRequest.Role.system)
                .findFirst()
                .orElse(null);
        assertNull(systemMessage, "System message should not be present when defaultOptions.style is null");
    }

    @Test
    void testOptionsMerging_mergesPromptAndDefaultOptions() {
        CompletionResponse completionResponse = createCompletionResponse();
        ArgumentCaptor<CompletionRequest> requestCaptor = ArgumentCaptor.forClass(CompletionRequest.class);

        Mockito.when(gigaChatApi.chatCompletionEntity(requestCaptor.capture()))
                .thenReturn(ResponseEntity.ok(completionResponse));

        byte[] fakeJpg = new byte[] {1, 2, 3, 4};
        Mockito.when(gigaChatApi.downloadFile("11111111-2222-3333-4444-555555555555"))
                .thenReturn(fakeJpg);

        GigaChatImageOptions promptOptions = GigaChatImageOptions.builder()
                .model(null)
                .style("Custom style")
                .responseFormat(null)
                .build();

        ImagePrompt prompt = new ImagePrompt(List.of(new ImageMessage("Draw a cat", 1.0f)), promptOptions);

        imageModel.call(prompt);

        CompletionRequest capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals(
                "GigaChat-2-Max", capturedRequest.getModel(), "Should use default model when prompt model is null");

        var systemMessage = capturedRequest.getMessages().stream()
                .filter(m -> m.getRole() == CompletionRequest.Role.system)
                .findFirst()
                .orElse(null);
        assertNotNull(systemMessage);
        assertEquals("Custom style", systemMessage.getContent());

        Mockito.verify(gigaChatApi, Mockito.times(1)).downloadFile("11111111-2222-3333-4444-555555555555");
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
}
