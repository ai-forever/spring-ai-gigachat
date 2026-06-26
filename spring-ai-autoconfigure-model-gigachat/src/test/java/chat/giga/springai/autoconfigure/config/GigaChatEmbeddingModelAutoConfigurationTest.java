package chat.giga.springai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.GigaChatEmbeddingModel;
import chat.giga.springai.autoconfigure.GigaChatApiAutoConfiguration;
import chat.giga.springai.autoconfigure.GigaChatEmbeddingModelAutoConfiguration;
import chat.giga.springai.autoconfigure.GigaChatEmbeddingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;

public class GigaChatEmbeddingModelAutoConfigurationTest {

    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.ai.gigachat.auth.bearer.api-key=test")
            .withConfiguration(AutoConfigurations.of(
                    GigaChatApiAutoConfiguration.class,
                    GigaChatEmbeddingModelAutoConfiguration.class,
                    RestClientAutoConfiguration.class,
                    WebClientAutoConfiguration.class,
                    SpringAiRetryAutoConfiguration.class));

    @Test
    @DisplayName("Проверяет создание GigaChatEmbeddingModel и GigaChatEmbeddingProperties")
    void embeddingModelBeanCreated() {
        baseRunner.run(context -> {
            assertThat(context).hasSingleBean(GigaChatEmbeddingModel.class);
            assertThat(context).hasSingleBean(GigaChatEmbeddingProperties.class);
        });
    }

    @Test
    @DisplayName("Проверяет кастомные настройки Embedding модели")
    void customEmbeddingPropertiesTest() {
        baseRunner
                .withPropertyValues(
                        "spring.ai.gigachat.embedding.options.model=Embeddings-2",
                        "spring.ai.gigachat.embedding.options.dimensions=1024")
                .run(context -> {
                    GigaChatEmbeddingProperties embeddingProperties =
                            context.getBean(GigaChatEmbeddingProperties.class);
                    assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("Embeddings-2");
                    assertThat(embeddingProperties.getOptions().getDimensions()).isEqualTo(1024);
                });
    }

    @ParameterizedTest
    @CsvSource({
        "gigachat, true",
        "openai, false",
    })
    @DisplayName("Условное создание GigaChatEmbeddingModel в зависимости от spring.ai.model.embedding")
    void embeddingModelConditionalTest(String propertyValue, boolean beanShouldExist) {
        ApplicationContextRunner runner = baseRunner;
        if (propertyValue != null) {
            runner = runner.withPropertyValues(SpringAIModelProperties.EMBEDDING_MODEL + "=" + propertyValue);
        }
        runner.run(context -> {
            if (beanShouldExist) {
                assertThat(context).hasSingleBean(GigaChatEmbeddingModel.class);
                assertThat(context).hasSingleBean(GigaChatEmbeddingProperties.class);
            } else {
                assertThat(context).doesNotHaveBean(GigaChatEmbeddingModel.class);
            }
        });
    }
}
