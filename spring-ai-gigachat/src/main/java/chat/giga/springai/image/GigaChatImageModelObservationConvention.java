package chat.giga.springai.image;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.util.StringUtils;

/**
 * GigaChat-specific convention for image model observations. Extends the Spring AI default
 * by adding the {@code gen_ai.response.model} low-cardinality tag — Spring AI 2.0.0-M4
 * omits it for image observations while emitting it for chat observations under the same
 * meter name {@code gen_ai.client.operation}, which causes Prometheus to reject the second
 * registration with a tag-keys-mismatch warning. Adding the tag (with {@code "none"} when
 * the response is unavailable, e.g. on the start side of the long-task timer) restores
 * tag-set symmetry between the two AI operation types and silences the warning reported
 * in issue GH-111.
 */
public class GigaChatImageModelObservationConvention extends DefaultImageModelObservationConvention {

    private static final String RESPONSE_MODEL = AiObservationAttributes.RESPONSE_MODEL.value();

    private static final KeyValue RESPONSE_MODEL_NONE = KeyValue.of(RESPONSE_MODEL, KeyValue.NONE_VALUE);

    /**
     * Returns the low-cardinality tags exposed by Spring AI's default image convention plus
     * the additional {@code gen_ai.response.model} tag computed by {@link #responseModel}.
     * The order of tags is irrelevant for Prometheus — what matters is that the resulting
     * <em>set of keys</em> matches the chat convention so that both observations can
     * coexist under the shared {@code gen_ai.client.operation} meter name.
     *
     * @param context observation context populated by {@link GigaChatImageModel}
     * @return augmented {@link KeyValues}
     */
    @Override
    public KeyValues getLowCardinalityKeyValues(ImageModelObservationContext context) {
        return super.getLowCardinalityKeyValues(context).and(responseModel(context));
    }

    /**
     * Computes the {@code gen_ai.response.model} tag. Reads the model name from
     * {@link GigaChatImageResponseMetadata} when available; otherwise falls back to
     * {@link KeyValue#NONE_VALUE}. The fallback covers two cases:
     * <ul>
     *     <li>observation start — context.response is still {@code null}, the long-task
     *         timer captures tags at this moment and the chat convention also reports
     *         {@code none} here, so emitting {@code none} keeps tag-set parity;</li>
     *     <li>foreign metadata or an empty model string — defensive defaults to avoid
     *         publishing a meter with an empty tag value.</li>
     * </ul>
     *
     * @param context observation context whose {@code response} is populated by
     *     {@link GigaChatImageModel} once the API call succeeds
     * @return key/value pair for the response model tag
     */
    private KeyValue responseModel(ImageModelObservationContext context) {
        ImageResponse response = context.getResponse();
        if (response != null
                && response.getMetadata() instanceof GigaChatImageResponseMetadata metadata
                && StringUtils.hasText(metadata.getModel())) {
            return KeyValue.of(RESPONSE_MODEL, metadata.getModel());
        }
        return RESPONSE_MODEL_NONE;
    }
}
