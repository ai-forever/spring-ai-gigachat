package chat.giga.springai.image;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatApiScope;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.retry.RetryUtils;

/**
 * Полное покрытие {@link GigaChatImageModel} на реальном API: оба формата ответа (b64_json/url),
 * параметры style и model. Креды из переменных окружения, секреты не печатаются.
 */
@Slf4j
class GigaChatImageModelFullIT {

    private static GigaChatImageModel imageModel(GigaChatImageOptions options) {
        GigaChatApiProperties properties = GigaChatApiProperties.builder()
                .auth(GigaChatAuthProperties.builder()
                        .scope(GigaChatApiScope.valueOf(System.getenv("GIGACHAT_API_SCOPE")))
                        .unsafeSsl(true)
                        .bearer(GigaChatAuthProperties.Bearer.builder()
                                .clientId(System.getenv("GIGACHAT_API_CLIENT_ID"))
                                .clientSecret(System.getenv("GIGACHAT_API_CLIENT_SECRET"))
                                .build())
                        .build())
                .build();
        return new GigaChatImageModel(
                new GigaChatApi(properties), options, ObservationRegistry.NOOP, RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    @Test
    @DisplayName("responseFormat=b64_json (по умолчанию): возвращается base64 изображения")
    void imageB64Json() {
        GigaChatImageModel model = imageModel(
                GigaChatImageOptions.builder().model("GigaChat-2-Max").build());

        ImageResponse response = model.call(new ImagePrompt(List.of(new ImageMessage("Нарисуй синий круг", 1.0f))));

        assertThat(response.getResults()).isNotEmpty();
        assertThat(response.getResult().getOutput().getB64Json()).isNotBlank();
        assertThat(response.getResult().getOutput().getUrl()).isNull();
    }

    @Test
    @DisplayName("responseFormat=url + style + model: возвращается URL изображения")
    void imageUrlWithStyleAndModel() {
        GigaChatImageModel model = imageModel(GigaChatImageOptions.builder()
                .model("GigaChat-2-Max")
                .style("Акварель")
                .responseFormat(GigaChatImageOptions.RESPONSE_FORMAT_URL)
                .build());

        ImageResponse response = model.call(new ImagePrompt(List.of(new ImageMessage("Нарисуй кота", 1.0f))));

        assertThat(response.getResults()).isNotEmpty();
        String url = response.getResult().getOutput().getUrl();
        log.info("URL изображения: {}", url);
        assertThat(url).contains("/files/").contains("/content");
        assertThat(response.getResult().getOutput().getB64Json()).isNull();
    }
}
