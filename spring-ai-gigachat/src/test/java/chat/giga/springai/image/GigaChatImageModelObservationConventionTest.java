package chat.giga.springai.image;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.api.chat.GigaChatApi;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.observation.conventions.AiObservationAttributes;

/**
 * Unit tests for {@link GigaChatImageModelObservationConvention}. Each test exercises the
 * convention in isolation by feeding it a hand-built {@link ImageModelObservationContext}
 * and inspecting the produced low-cardinality {@link KeyValues}, so that the rules used
 * to populate {@code gen_ai.response.model} stay explicit and regress-free.
 */
class GigaChatImageModelObservationConventionTest {

    private static final String GIGA_CHAT_2 = "GigaChat-2";
    private static final String GIGA_CHAT_2_PRO = "GigaChat-2-Pro";

    private final GigaChatImageModelObservationConvention convention = new GigaChatImageModelObservationConvention();

    /**
     * Builds a minimal {@link ImageModelObservationContext} representing a request to model
     * {@link #GIGA_CHAT_2}. Tests that need a populated response call {@code setResponse}
     * on the returned context.
     *
     * @return fresh context shared across the test methods
     */
    private ImageModelObservationContext context() {
        ImagePrompt prompt = new ImagePrompt(
                List.of(new ImageMessage("Нарисуй кота", 1.0f)),
                GigaChatImageOptions.builder().model(GIGA_CHAT_2).build());
        return ImageModelObservationContext.builder()
                .imagePrompt(prompt)
                .provider(GigaChatApi.PROVIDER_NAME)
                .build();
    }

    /**
     * At observation start the response is not yet available — the long-task timer
     * captures tags exactly at this moment, so emitting {@code none} keeps tag-set parity
     * with the chat convention which also reports {@code none} on the start side.
     */
    @Test
    @DisplayName("At observation start (response not set yet) the gen_ai.response.model tag is none")
    void responseModelTagIsNoneBeforeResponseIsSet() {
        KeyValues keyValues = convention.getLowCardinalityKeyValues(context());

        assertThat(keyValues)
                .contains(KeyValue.of(AiObservationAttributes.RESPONSE_MODEL.value(), KeyValue.NONE_VALUE));
    }

    /**
     * Once {@link GigaChatImageResponseMetadata} is attached to the response, the tag must
     * carry the model that the API actually used — which is the value finally written to
     * the {@code gen_ai.client.operation} timer.
     */
    @Test
    @DisplayName("After setResponse with a real model — the tag carries the API value")
    void responseModelTagReflectsActualResponseModel() {
        ImageModelObservationContext ctx = context();
        ImageResponse response = new ImageResponse(
                List.of(new ImageGeneration(new Image(null, "AAAA"))),
                new GigaChatImageResponseMetadata(GIGA_CHAT_2_PRO));
        ctx.setResponse(response);

        KeyValues keyValues = convention.getLowCardinalityKeyValues(ctx);

        assertThat(keyValues).contains(KeyValue.of(AiObservationAttributes.RESPONSE_MODEL.value(), GIGA_CHAT_2_PRO));
    }

    /**
     * Defensive default: if a third party sets a plain {@code ImageResponseMetadata} on
     * the response (i.e. not our subclass), the convention must not crash and must fall
     * back to {@code none}.
     */
    @Test
    @DisplayName("If response.metadata is not GigaChatImageResponseMetadata — the tag falls back to none")
    void responseModelTagIsNoneForUnknownMetadataType() {
        ImageModelObservationContext ctx = context();
        ImageResponse response = new ImageResponse(List.of(new ImageGeneration(new Image(null, "AAAA"))));
        ctx.setResponse(response);

        KeyValues keyValues = convention.getLowCardinalityKeyValues(ctx);

        assertThat(keyValues)
                .contains(KeyValue.of(AiObservationAttributes.RESPONSE_MODEL.value(), KeyValue.NONE_VALUE));
    }

    /**
     * An empty string from the API ({@code ""}) is treated like a missing value to avoid
     * publishing a meter with an empty label, which is generally undesirable for Prometheus
     * scrape outputs.
     */
    @Test
    @DisplayName("Empty model in metadata → the tag falls back to none, avoiding meters with empty labels")
    void responseModelTagIsNoneForEmptyModel() {
        ImageModelObservationContext ctx = context();
        ImageResponse response = new ImageResponse(
                List.of(new ImageGeneration(new Image(null, "AAAA"))), new GigaChatImageResponseMetadata(""));
        ctx.setResponse(response);

        KeyValues keyValues = convention.getLowCardinalityKeyValues(ctx);

        assertThat(keyValues)
                .contains(KeyValue.of(AiObservationAttributes.RESPONSE_MODEL.value(), KeyValue.NONE_VALUE));
    }

    /**
     * Pins the structural contract: the image convention exposes exactly the same four
     * low-cardinality keys that {@code DefaultChatModelObservationConvention} does. This is
     * the property that protects against the GH-111 Prometheus warning.
     */
    @Test
    @DisplayName(
            "Image low-cardinality tag set is symmetric with chat (operation, provider, request.model, response.model)")
    void lowCardinalityTagSetMatchesChatMetric() {
        KeyValues keyValues = convention.getLowCardinalityKeyValues(context());

        List<String> tagKeys = keyValues.stream().map(KeyValue::getKey).sorted().toList();

        assertThat(tagKeys)
                .containsExactlyInAnyOrder(
                        AiObservationAttributes.AI_OPERATION_TYPE.value(),
                        AiObservationAttributes.AI_PROVIDER.value(),
                        AiObservationAttributes.REQUEST_MODEL.value(),
                        AiObservationAttributes.RESPONSE_MODEL.value());
    }

    /**
     * Sanity check that we extend the default convention rather than replace it: every
     * tag the upstream {@link DefaultImageModelObservationConvention} produces must remain
     * present in our augmented output.
     */
    @Test
    @DisplayName("All tags emitted by DefaultImageModelObservationConvention are preserved alongside response.model")
    void allDefaultTagsArePreserved() {
        KeyValues defaultTags = new DefaultImageModelObservationConvention().getLowCardinalityKeyValues(context());
        KeyValues customTags = convention.getLowCardinalityKeyValues(context());

        assertThat(customTags).containsAll(defaultTags);
    }
}
