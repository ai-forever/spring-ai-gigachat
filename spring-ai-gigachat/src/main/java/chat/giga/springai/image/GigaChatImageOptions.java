package chat.giga.springai.image;

import chat.giga.springai.GigaChatModel;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.ImageOptions;

@Slf4j
@Builder
public class GigaChatImageOptions implements ImageOptions {

    public static final String SYSTEM_PROMPT = "You are an artist. If the user asks you to draw something,"
            + "generate an image using the built-in text2image function"
            + "and return a tag in the form <img src=\"FILE_ID\"/>.";

    @Builder.Default
    private String style = SYSTEM_PROMPT;

    @Builder.Default
    private String model = GigaChatModel.DEFAULT_MODEL_NAME;

    public GigaChatImageOptions(String style, String model) {
        this.style = style;
        this.model = model;
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
        // GigaChat does not support ResponseFormat
        return null;
    }

    @Override
    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
