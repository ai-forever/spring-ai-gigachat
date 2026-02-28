package chat.giga.springai.image;

import chat.giga.springai.GigaChatModel;
import lombok.Builder;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.ImageOptions;

@Setter
@Slf4j
@Builder
public class GigaChatImageOptions implements ImageOptions {

    public static final String RESPONSE_FORMAT_B64_JSON = "b64_json";
    public static final String RESPONSE_FORMAT_URL = "url";

    private String style;

    @Builder.Default
    private String model = GigaChatModel.DEFAULT_MODEL_NAME;

    @Builder.Default
    private String responseFormat = RESPONSE_FORMAT_B64_JSON;

    public GigaChatImageOptions(String style, String model, String responseFormat) {
        this.style = style;
        this.model = model;
        this.responseFormat = responseFormat;
    }

    @Override
    public Integer getN() {
        // GigaChat does not support N
        return null;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public Integer getWidth() {
        // GigaChat does not support Width
        return null;
    }

    @Override
    public Integer getHeight() {
        // GigaChat does not support Height
        return null;
    }

    @Override
    public String getResponseFormat() {
        return responseFormat;
    }

    @Override
    public String getStyle() {
        return style;
    }
}
