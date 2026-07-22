package chat.giga.springai.image;

import org.springframework.ai.image.ImageResponseMetadata;

/**
 * GigaChat-specific {@link ImageResponseMetadata} that exposes the response model returned
 * by the API. Without this extra field the
 * {@link GigaChatImageModelObservationConvention} would not be able to populate the
 * {@code gen_ai.response.model} low-cardinality tag, which keeps the image metric tag set
 * in parity with the chat metric and avoids the Prometheus "tag keys mismatch" warning
 * (issue GH-111).
 */
public class GigaChatImageResponseMetadata extends ImageResponseMetadata {

    private final String model;

    /**
     * Creates metadata with the given model name and a creation timestamp set to "now"
     * (delegated to {@link ImageResponseMetadata#ImageResponseMetadata()}).
     *
     * @param model the model that actually produced the image, as reported by the API in
     *     {@code CompletionResponse.model}; may be {@code null} when GigaChat returned an
     *     empty completion and we have nothing to record
     */
    public GigaChatImageResponseMetadata(String model) {
        super();
        this.model = model;
    }

    /**
     * Creates metadata with explicit creation timestamp and model name.
     *
     * @param created creation timestamp in epoch millis, see
     *     {@link ImageResponseMetadata#ImageResponseMetadata(Long)}
     * @param model the model that actually produced the image (see {@link #GigaChatImageResponseMetadata(String)})
     */
    public GigaChatImageResponseMetadata(Long created, String model) {
        super(created);
        this.model = model;
    }

    /**
     * Returns the response model — the value of {@code model} from the underlying
     * GigaChat {@code /chat/completions} response. May differ from the requested model
     * (e.g. when GigaChat routes to a specific sub-version).
     *
     * @return response model id, or {@code null} if it was unavailable
     */
    public String getModel() {
        return model;
    }
}
