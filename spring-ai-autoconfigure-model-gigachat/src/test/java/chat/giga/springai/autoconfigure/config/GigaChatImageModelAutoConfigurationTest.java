package chat.giga.springai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.autoconfigure.GigaChatApiAutoConfiguration;
import chat.giga.springai.autoconfigure.GigaChatImageModelAutoConfiguration;
import chat.giga.springai.autoconfigure.GigaChatImageProperties;
import chat.giga.springai.image.GigaChatImageModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;

public class GigaChatImageModelAutoConfigurationTest {

    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.ai.gigachat.auth.bearer.api-key=test")
            .withConfiguration(AutoConfigurations.of(
                    GigaChatApiAutoConfiguration.class,
                    GigaChatImageModelAutoConfiguration.class,
                    RestClientAutoConfiguration.class,
                    WebClientAutoConfiguration.class,
                    SpringAiRetryAutoConfiguration.class));

    @ParameterizedTest
    @CsvSource({
        "gigachat, true",
        "openai, false",
    })
    @DisplayName("Условное создание ImageModel в зависимости от spring.ai.model.image")
    void imageModelConditionalTest(String propertyValue, boolean beanShouldExist) {
        ApplicationContextRunner runner = baseRunner;
        if (propertyValue != null) {
            runner = runner.withPropertyValues(SpringAIModelProperties.IMAGE_MODEL + "=" + propertyValue);
        }
        runner.run(context -> {
            if (beanShouldExist) {
                assertThat(context).hasSingleBean(ImageModel.class);
                assertThat(context).hasSingleBean(GigaChatImageModel.class);
                assertThat(context).hasSingleBean(GigaChatImageProperties.class);
            } else {
                assertThat(context).doesNotHaveBean(ImageModel.class);
                assertThat(context).doesNotHaveBean(GigaChatImageModel.class);
            }
        });
    }
}
