package chat.giga.springai.autoconfigure;

import static chat.giga.springai.autoconfigure.GigaChatEmbeddingProperties.DEFAULT_EMBEDDINGS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.GigaChatEmbeddingModel;
import chat.giga.springai.GigaChatModel;
import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.MetadataMode;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.client.reactive.ClientHttpConnectorAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

public class GigaChatAutoConfigurationTest {

    AutoConfigurations gigaChatAutoConfigurations = AutoConfigurations.of(GigaChatAutoConfiguration.class);
    AutoConfigurations sslBundlesAutoConfigurations = AutoConfigurations.of(
            GigaChatAutoConfiguration.class, SslAutoConfiguration.class, ClientHttpConnectorAutoConfiguration.class);

    ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(gigaChatAutoConfigurations);

    @Test
    @DisplayName("Тест проверяет корректную сборку всех бинов автоконфигурации с дефолтными параметрами")
    void defaultBeanAutoConfigurationTest() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GigaChatApi.class);
            assertThat(context).hasSingleBean(GigaChatModel.class);
            assertThat(context).hasSingleBean(GigaChatChatProperties.class);
            assertThat(context).hasSingleBean(GigaChatEmbeddingModel.class);
            assertThat(context).hasSingleBean(GigaChatEmbeddingProperties.class);
            assertThat(context).hasSingleBean(GigaChatInternalProperties.class);
            assertThat(context).hasSingleBean(GigaChatAuthProperties.class);
            assertThat(context).hasSingleBean(GigaChatApiProperties.class);

            GigaChatChatProperties chatProperties = context.getBean(GigaChatChatProperties.class);
            assertThat(chatProperties.isEnabled()).isTrue();
            assertThat(chatProperties.getOptions().getModel()).isEqualTo("GigaChat-2");
            assertThat(chatProperties.getOptions().getTemperature()).isNull();
            assertThat(chatProperties.getOptions().getTopP()).isNull();
            assertThat(chatProperties.getOptions().getMaxTokens()).isNull();
            assertThat(chatProperties.getOptions().getRepetitionPenalty()).isNull();

            GigaChatEmbeddingProperties embeddingProperties = context.getBean(GigaChatEmbeddingProperties.class);
            assertThat(embeddingProperties.isEnabled()).isTrue();
            assertThat(embeddingProperties.getEmbeddingsPath()).isEqualTo(DEFAULT_EMBEDDINGS_PATH);
            assertThat(embeddingProperties.getMetadataMode()).isEqualTo(MetadataMode.EMBED);
            assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("Embeddings");
            assertThat(embeddingProperties.getOptions().getDimensions()).isNull();
        });
    }

    @Test
    @DisplayName("Тест проверяет автоконфигурацию кастомных параметров Chat модели")
    void customChatPropertiesAutoConfigurationTest() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.gigachat.chat.options.model=GigaChat-2-Max",
                        "spring.ai.gigachat.chat.options.temperature=0.7",
                        "spring.ai.gigachat.chat.options.top-p=0.5",
                        "spring.ai.gigachat.chat.options.max-tokens=200",
                        "spring.ai.gigachat.chat.options.repetition-penalty=2.0")
                .run(context -> {
                    GigaChatChatProperties chatProperties = context.getBean(GigaChatChatProperties.class);
                    assertThat(chatProperties.isEnabled()).isTrue();
                    assertThat(chatProperties.getOptions().getModel()).isEqualTo("GigaChat-2-Max");
                    assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.7);
                    assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.5);
                    assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(200);
                    assertThat(chatProperties.getOptions().getRepetitionPenalty())
                            .isEqualTo(2.0);
                });
    }

    @Test
    @DisplayName("Тест проверяет автоконфигурацию кастомных параметров Embedding модели")
    void customEmbeddingPropertiesAutoConfigurationTest() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.gigachat.embedding.options.model=Embeddings-2",
                        "spring.ai.gigachat.embedding.options.dimensions=1024")
                .run(context -> {
                    GigaChatEmbeddingProperties embeddingProperties =
                            context.getBean(GigaChatEmbeddingProperties.class);
                    assertThat(embeddingProperties.isEnabled()).isTrue();
                    assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("Embeddings-2");
                    assertThat(embeddingProperties.getOptions().getDimensions()).isEqualTo(1024);
                });
    }

    @Test
    @DisplayName("Тест проверяет сборку всех бинов автоконфигурации при использовании web application context")
    void webAutoConfigurationTest() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GigaChatAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(GigaChatApi.class);
                    assertThat(context).hasSingleBean(GigaChatModel.class);
                    assertThat(context).hasSingleBean(GigaChatChatProperties.class);
                    assertThat(context).hasSingleBean(GigaChatEmbeddingModel.class);
                    assertThat(context).hasSingleBean(GigaChatEmbeddingProperties.class);
                    assertThat(context).hasSingleBean(GigaChatInternalProperties.class);
                    assertThat(context).hasSingleBean(GigaChatAuthProperties.class);
                    assertThat(context).hasSingleBean(GigaChatApiProperties.class);
                });
    }

    @Test
    @DisplayName(
            "Тест проверяет сборку всех бинов автоконфигурации при использовании web application context (servlet)")
    void servletWebAutoConfigurationTest() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GigaChatAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(GigaChatApi.class);
                    assertThat(context).hasSingleBean(GigaChatModel.class);
                    assertThat(context).hasSingleBean(GigaChatChatProperties.class);
                    assertThat(context).hasSingleBean(GigaChatEmbeddingModel.class);
                    assertThat(context).hasSingleBean(GigaChatEmbeddingProperties.class);
                    assertThat(context).hasSingleBean(GigaChatInternalProperties.class);
                    assertThat(context).hasSingleBean(GigaChatAuthProperties.class);
                    assertThat(context).hasSingleBean(GigaChatApiProperties.class);
                });
    }

    @Test
    @DisplayName("Тест проверяет сборку всех бинов автоконфигурации при использовании web + sslBundles")
    void sslBundleBbeanAutoConfigurationTest() {
        new WebApplicationContextRunner()
                .withConfiguration(sslBundlesAutoConfigurations)
                .run(context -> {
                    assertThat(context).hasSingleBean(GigaChatApi.class);
                    assertThat(context).hasSingleBean(GigaChatModel.class);
                    assertThat(context).hasSingleBean(GigaChatChatProperties.class);
                    assertThat(context).hasSingleBean(GigaChatEmbeddingModel.class);
                    assertThat(context).hasSingleBean(GigaChatEmbeddingProperties.class);
                    assertThat(context).hasSingleBean(GigaChatInternalProperties.class);
                    assertThat(context).hasSingleBean(GigaChatAuthProperties.class);
                    assertThat(context).hasSingleBean(GigaChatApiProperties.class);
                });
    }

    @Test
    @DisplayName(
            "Тест проверяет сборку всех бинов автоконфигурации при использовании reactive web application context (webflux)")
    void reactiveWebfluxAutoConfigurationTest() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GigaChatAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(GigaChatApi.class);
                    assertThat(context).hasSingleBean(GigaChatModel.class);
                    assertThat(context).hasSingleBean(GigaChatChatProperties.class);
                    assertThat(context).hasSingleBean(GigaChatEmbeddingModel.class);
                    assertThat(context).hasSingleBean(GigaChatEmbeddingProperties.class);
                    assertThat(context).hasSingleBean(GigaChatInternalProperties.class);
                    assertThat(context).hasSingleBean(GigaChatAuthProperties.class);
                    assertThat(context).hasSingleBean(GigaChatApiProperties.class);
                });
    }

    @Test
    @DisplayName("Тест проверяет сборку всех бинов автоконфигурации при использовании webflux + sslBundles")
    void webfluxSslBundlesBeanAutoConfigurationTest() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(sslBundlesAutoConfigurations)
                .run(context -> {
                    assertThat(context).hasSingleBean(GigaChatApi.class);
                    assertThat(context).hasSingleBean(GigaChatModel.class);
                    assertThat(context).hasSingleBean(GigaChatChatProperties.class);
                    assertThat(context).hasSingleBean(GigaChatEmbeddingModel.class);
                    assertThat(context).hasSingleBean(GigaChatEmbeddingProperties.class);
                    assertThat(context).hasSingleBean(GigaChatInternalProperties.class);
                    assertThat(context).hasSingleBean(GigaChatAuthProperties.class);
                    assertThat(context).hasSingleBean(GigaChatApiProperties.class);
                });
    }
}
