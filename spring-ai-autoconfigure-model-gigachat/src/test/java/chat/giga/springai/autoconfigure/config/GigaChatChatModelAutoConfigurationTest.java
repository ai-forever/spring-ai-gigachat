package chat.giga.springai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.autoconfigure.GigaChatApiAutoConfiguration;
import chat.giga.springai.autoconfigure.GigaChatChatModelAutoConfiguration;
import chat.giga.springai.autoconfigure.GigaChatChatProperties;
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

public class GigaChatChatModelAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.ai.gigachat.auth.bearer.api-key=test", "spring.ai.model.chat=gigachat")
            .withConfiguration(AutoConfigurations.of(
                    GigaChatApiAutoConfiguration.class,
                    GigaChatChatModelAutoConfiguration.class,
                    RestClientAutoConfiguration.class,
                    WebClientAutoConfiguration.class,
                    SpringAiRetryAutoConfiguration.class));

    @Test
    @DisplayName("Проверяет создание GigaChatModel и GigaChatChatProperties")
    void chatModelBeanCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GigaChatModel.class);
            assertThat(context).hasSingleBean(GigaChatChatProperties.class);
        });
    }

    @Test
    @DisplayName("Тест проверяет автоконфигурацию кастомных параметров Chat модели")
    void customChatPropertiesAutoConfigurationTest() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.gigachat.chat.model=GigaChat-2-Max",
                        "spring.ai.gigachat.chat.temperature=0.7",
                        "spring.ai.gigachat.chat.top-p=0.5",
                        "spring.ai.gigachat.chat.max-tokens=200",
                        "spring.ai.gigachat.chat.repetition-penalty=2.0")
                .run(context -> {
                    GigaChatChatProperties chatProperties = context.getBean(GigaChatChatProperties.class);
                    assertThat(chatProperties.getModel()).isEqualTo("GigaChat-2-Max");
                    assertThat(chatProperties.getTemperature()).isEqualTo(0.7);
                    assertThat(chatProperties.getTopP()).isEqualTo(0.5);
                    assertThat(chatProperties.getMaxTokens()).isEqualTo(200);
                    assertThat(chatProperties.getRepetitionPenalty()).isEqualTo(2.0);
                });
    }

    @Test
    @DisplayName("Тест проверяет автоконфигурацию deprecated параметров через options")
    void legacyChatPropertiesAutoConfigurationTest() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.gigachat.chat.options.model=GigaChat-2-Legacy",
                        "spring.ai.gigachat.chat.options.temperature=0.8",
                        "spring.ai.gigachat.chat.options.top-p=0.6",
                        "spring.ai.gigachat.chat.options.max-tokens=150",
                        "spring.ai.gigachat.chat.options.repetition-penalty=1.5")
                .run(context -> {
                    GigaChatChatProperties chatProperties = context.getBean(GigaChatChatProperties.class);
                    // deprecated options.* делегируют значения в top-level свойства
                    assertThat(chatProperties.getModel()).isEqualTo("GigaChat-2-Legacy");
                    assertThat(chatProperties.getTemperature()).isEqualTo(0.8);
                    assertThat(chatProperties.getTopP()).isEqualTo(0.6);
                    assertThat(chatProperties.getMaxTokens()).isEqualTo(150);
                    assertThat(chatProperties.getRepetitionPenalty()).isEqualTo(1.5);
                });
    }

    @ParameterizedTest
    @CsvSource({"gigachat, true", "openai, false", ", true"})
    @DisplayName("Условное создание модели в зависимости от spring.ai.model.chat")
    void conditionalOnChatModelProperty(String propertyValue, boolean beanShouldExist) {
        ApplicationContextRunner runner = contextRunner;
        if (propertyValue != null) {
            runner = runner.withPropertyValues(SpringAIModelProperties.CHAT_MODEL + "=" + propertyValue);
        }
        runner.run(context -> {
            if (beanShouldExist) {
                assertThat(context).hasSingleBean(GigaChatModel.class);
            } else {
                assertThat(context).doesNotHaveBean(GigaChatModel.class);
            }
        });
    }
}
