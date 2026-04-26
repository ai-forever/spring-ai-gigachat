package chat.giga.springai.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;

/**
 * Integration test for the observability behavior of {@link GigaChatImageModel}. Reproduces
 * the scenario from issue GH-111: when chat and image observations share the same meter name
 * {@code gen_ai.client.operation}, their low-cardinality tag keys must match — otherwise
 * Prometheus rejects the second registration. This test asserts that the image flow emits
 * the {@code gen_ai.response.model} tag both at observation start (with NONE_VALUE) and at
 * stop (with the real model returned by the API), keeping tag-set parity with chat.
 */
class GigaChatImageObservationIT {

    private static final String MODEL_REQUESTED = "GigaChat-2";
    private static final String MODEL_RESPONDED = "GigaChat-2-Pro";

    private final GigaChatApi gigaChatApi = Mockito.mock(GigaChatApi.class);
    private final GigaChatImageOptions defaultOptions =
            GigaChatImageOptions.builder().model(MODEL_REQUESTED).build();
    private final RetryTemplate retryTemplate = org.springframework.ai.retry.RetryUtils.DEFAULT_RETRY_TEMPLATE;

    /**
     * Stubs both API calls used by {@link GigaChatImageModel#call} so the test does not
     * touch the network. The stubbed completion intentionally returns a different model
     * name than the one requested, mirroring how GigaChat may route to a sub-version and
     * giving us a real value to assert against on the {@code gen_ai.response.model} tag.
     */
    private void stubSuccessfulImageGeneration() {
        CompletionResponse.MessagesRes message = new CompletionResponse.MessagesRes();
        message.setRole(CompletionResponse.Role.assistant);
        message.setContent("Generated <img src=\"33333333-4444-5555-6666-777777777777\"/>");

        CompletionResponse.Choice choice = new CompletionResponse.Choice();
        choice.setMessage(message);
        choice.setFinishReason(CompletionResponse.FinishReason.STOP);
        choice.setIndex(0);

        CompletionResponse completionResponse = new CompletionResponse();
        completionResponse.setChoices(List.of(choice));
        completionResponse.setModel(MODEL_RESPONDED);

        Mockito.when(gigaChatApi.chatCompletionEntity(any())).thenReturn(ResponseEntity.ok(completionResponse));
        Mockito.when(gigaChatApi.downloadFile("33333333-4444-5555-6666-777777777777"))
                .thenReturn(new byte[] {1, 2, 3});
    }

    /**
     * Drives an image call through the full Spring AI observation pipeline backed by a
     * {@link SimpleMeterRegistry}, then inspects the resulting {@code gen_ai.client.operation}
     * timer. Confirms that all four required low-cardinality keys are present and that
     * {@code gen_ai.response.model} carries the model from {@code CompletionResponse}, not
     * the requested one.
     */
    @Test
    @DisplayName("Final timer gen_ai.client.operation carries gen_ai.response.model with the API-reported model")
    void finalTimerContainsResponseModelTagFromApi() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry
                .observationConfig()
                .observationHandler(
                        new io.micrometer.core.instrument.observation.DefaultMeterObservationHandler(meterRegistry));

        GigaChatImageModel imageModel =
                new GigaChatImageModel(gigaChatApi, defaultOptions, observationRegistry, retryTemplate);

        stubSuccessfulImageGeneration();

        imageModel.call(new ImagePrompt(
                List.of(new ImageMessage("Нарисуй кота", 1.0f)),
                GigaChatImageOptions.builder().model(MODEL_REQUESTED).build()));

        Meter timer = meterRegistry.find("gen_ai.client.operation").meter();
        assertThat(timer)
                .as("gen_ai.client.operation meter must be registered for the image operation")
                .isNotNull();

        Set<String> tagKeys = timer.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet());
        assertThat(tagKeys)
                .as(
                        "Image timer must expose the same low-cardinality tag keys as chat — otherwise Prometheus fails the registration")
                .contains(
                        AiObservationAttributes.AI_OPERATION_TYPE.value(),
                        AiObservationAttributes.AI_PROVIDER.value(),
                        AiObservationAttributes.REQUEST_MODEL.value(),
                        AiObservationAttributes.RESPONSE_MODEL.value());

        assertThat(timer.getId().getTag(AiObservationAttributes.RESPONSE_MODEL.value()))
                .as("gen_ai.response.model tag must reflect CompletionResponse.model, not the requested model")
                .isEqualTo(MODEL_RESPONDED);
    }

    /**
     * Same flow asserted via {@link TestObservationRegistry} TCK helpers — instead of
     * inspecting meters, this test verifies the observation lifecycle (started → stopped)
     * and the four low-cardinality key-value pairs the convention is supposed to publish.
     * The two tests complement each other: the meter-level test guards the Prometheus
     * symptom, the observation-level test guards the contract on the Observation API side.
     */
    @Test
    @DisplayName("TestObservationRegistry: image observation completes with the gen_ai.response.model tag")
    void observationCompletesWithResponseModelTag() {
        TestObservationRegistry observationRegistry = TestObservationRegistry.create();

        GigaChatImageModel imageModel =
                new GigaChatImageModel(gigaChatApi, defaultOptions, observationRegistry, retryTemplate);

        stubSuccessfulImageGeneration();

        imageModel.call(new ImagePrompt(
                List.of(new ImageMessage("Нарисуй кота", 1.0f)),
                GigaChatImageOptions.builder().model(MODEL_REQUESTED).build()));

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("gen_ai.client.operation")
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue(AiObservationAttributes.AI_OPERATION_TYPE.value(), "image")
                .hasLowCardinalityKeyValue(AiObservationAttributes.AI_PROVIDER.value(), GigaChatApi.PROVIDER_NAME)
                .hasLowCardinalityKeyValue(AiObservationAttributes.REQUEST_MODEL.value(), MODEL_REQUESTED)
                .hasLowCardinalityKeyValue(AiObservationAttributes.RESPONSE_MODEL.value(), MODEL_RESPONDED);
    }
}
