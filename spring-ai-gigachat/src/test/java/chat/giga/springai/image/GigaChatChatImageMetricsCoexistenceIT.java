package chat.giga.springai.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;

/**
 * Reproduces the scenario from issue GH-111: chat and image observations publish to the
 * shared {@code gen_ai.client.operation} meter in a single {@link PrometheusMeterRegistry}.
 * If the two observations expose different low-cardinality tag key sets, Prometheus
 * rejects the second registration and logs a WARN. This test exercises both orderings
 * (chat→image and image→chat) and asserts that both time series end up in the registry
 * with identical tag key sets, which is the structural contract that keeps the warning
 * away.
 */
class GigaChatChatImageMetricsCoexistenceIT {

    private static final String CHAT_REQUEST_MODEL = "GigaChat-2";
    private static final String CHAT_RESPONSE_MODEL = "GigaChat-2-Pro";
    private static final String IMAGE_REQUEST_MODEL = "GigaChat-2";
    private static final String IMAGE_RESPONSE_MODEL = "GigaChat-2-Pro";

    private final GigaChatApi gigaChatApi = Mockito.mock(GigaChatApi.class);
    private final RetryTemplate retryTemplate = org.springframework.ai.retry.RetryUtils.DEFAULT_RETRY_TEMPLATE;

    /**
     * Stubs both {@link GigaChatApi#chatCompletionEntity} and
     * {@link GigaChatApi#downloadFile} so {@link GigaChatImageModel} can run end-to-end
     * without a real HTTP backend. The stub deliberately reports a different model in the
     * response than what was requested, mirroring how GigaChat routes traffic to a specific
     * sub-version.
     */
    private void stubImageApi() {
        CompletionResponse.MessagesRes message = new CompletionResponse.MessagesRes();
        message.setRole(CompletionResponse.Role.assistant);
        message.setContent("<img src=\"44444444-5555-6666-7777-888888888888\"/>");

        CompletionResponse.Choice choice = new CompletionResponse.Choice();
        choice.setMessage(message);
        choice.setFinishReason(CompletionResponse.FinishReason.STOP);
        choice.setIndex(0);

        CompletionResponse completion = new CompletionResponse();
        completion.setChoices(List.of(choice));
        completion.setModel(IMAGE_RESPONSE_MODEL);

        Mockito.when(gigaChatApi.chatCompletionEntity(any())).thenReturn(ResponseEntity.ok(completion));
        Mockito.when(gigaChatApi.downloadFile("44444444-5555-6666-7777-888888888888"))
                .thenReturn(new byte[] {1, 2, 3});
    }

    /**
     * Builds an {@link ObservationRegistry} wired to push every observation into the given
     * {@link PrometheusMeterRegistry} via {@link DefaultMeterObservationHandler}. This is the
     * same pipeline Spring Boot sets up for users — issue GH-111 was reported against this
     * exact configuration.
     *
     * @param meterRegistry meter registry that will receive the observations
     * @return observation registry plumbed to the meter registry
     */
    private ObservationRegistry observationRegistry(PrometheusMeterRegistry meterRegistry) {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new DefaultMeterObservationHandler(meterRegistry));
        return registry;
    }

    /**
     * Simulates a chat call the same way {@code GigaChatModel} does — directly through
     * {@link ChatModelObservationDocumentation}, without instantiating the full model. We
     * only care about how the chat observation registers with the meter registry, so this
     * keeps the test self-contained and avoids mocking the heavier chat dependencies.
     *
     * @param observationRegistry the shared registry under test
     */
    private void runChatObservation(ObservationRegistry observationRegistry) {
        Prompt prompt = new Prompt(
                List.of(new UserMessage("hi")),
                ChatOptions.builder().model(CHAT_REQUEST_MODEL).build());
        ChatModelObservationContext ctx = ChatModelObservationContext.builder()
                .prompt(prompt)
                .provider(GigaChatApi.PROVIDER_NAME)
                .build();

        ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
                .observation(null, new DefaultChatModelObservationConvention(), () -> ctx, observationRegistry)
                .observe(() -> {
                    ctx.setResponse(new ChatResponse(
                            List.of(),
                            ChatResponseMetadata.builder()
                                    .model(CHAT_RESPONSE_MODEL)
                                    .build()));
                    return null;
                });
    }

    /**
     * Drives a real {@link GigaChatImageModel} call against the shared registry. The image
     * side goes through the production path (including
     * {@link GigaChatImageModelObservationConvention}) — that is exactly the code we want
     * to verify behaves symmetrically with chat.
     *
     * @param observationRegistry the shared registry under test
     */
    private void runImageCall(ObservationRegistry observationRegistry) {
        GigaChatImageModel imageModel = new GigaChatImageModel(
                gigaChatApi,
                GigaChatImageOptions.builder().model(IMAGE_REQUEST_MODEL).build(),
                observationRegistry,
                retryTemplate);
        imageModel.call(new ImagePrompt(
                List.of(new ImageMessage("Нарисуй кота", 1.0f)),
                GigaChatImageOptions.builder().model(IMAGE_REQUEST_MODEL).build()));
    }

    /**
     * Asserts the structural invariant that protects against the bug from GH-111:
     * <ul>
     *     <li>two meters registered under {@code gen_ai.client.operation} (one per
     *         operation type — chat and image); if Prometheus rejected the second
     *         registration due to a tag-keys mismatch we would see only one;</li>
     *     <li>both meters expose identical sets of tag keys;</li>
     *     <li>the four required GenAI low-cardinality keys are present;</li>
     *     <li>the {@code gen_ai.operation.name} values are exactly {chat, image}.</li>
     * </ul>
     *
     * @param meterRegistry registry to inspect
     */
    private void assertCoexistence(PrometheusMeterRegistry meterRegistry) {
        Collection<Meter> meters = meterRegistry.find("gen_ai.client.operation").meters();

        assertThat(meters)
                .as("Both series (chat + image) must be registered. "
                        + "If Prometheus rejected the second registration because of mismatched tag keys, "
                        + "this collection will only have one entry.")
                .hasSize(2);

        List<Set<String>> tagKeySets = meters.stream()
                .map(m -> m.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet()))
                .toList();

        assertThat(tagKeySets.get(0))
                .as("Tag key sets for chat and image must be equal — otherwise Prometheus fails the registration")
                .isEqualTo(tagKeySets.get(1));

        assertThat(tagKeySets.get(0))
                .contains(
                        AiObservationAttributes.AI_OPERATION_TYPE.value(),
                        AiObservationAttributes.AI_PROVIDER.value(),
                        AiObservationAttributes.REQUEST_MODEL.value(),
                        AiObservationAttributes.RESPONSE_MODEL.value());

        Set<String> operationTypes = meters.stream()
                .map(m -> m.getId().getTag(AiObservationAttributes.AI_OPERATION_TYPE.value()))
                .collect(Collectors.toSet());
        assertThat(operationTypes)
                .as("One meter must be chat, the other image")
                .containsExactlyInAnyOrder("chat", "image");
    }

    /**
     * Chat observation registers first, image observation second. This is the exact order
     * reported in the issue (a user-facing app starts with chat, then triggers image).
     */
    @Test
    @DisplayName("chat → image: both observations register in one PrometheusMeterRegistry without tag conflict")
    void chatThenImageBothRegister() {
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        ObservationRegistry observationRegistry = observationRegistry(meterRegistry);

        stubImageApi();

        runChatObservation(observationRegistry);
        runImageCall(observationRegistry);

        assertCoexistence(meterRegistry);
    }

    /**
     * Reverse ordering: image observation registers first, then chat. Even though the
     * meter name is the same, switching the registration order should not change the
     * outcome — both must succeed.
     */
    @Test
    @DisplayName("image → chat: reverse order does not trigger a registration conflict either")
    void imageThenChatBothRegister() {
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        ObservationRegistry observationRegistry = observationRegistry(meterRegistry);

        stubImageApi();

        runImageCall(observationRegistry);
        runChatObservation(observationRegistry);

        assertCoexistence(meterRegistry);
    }
}
