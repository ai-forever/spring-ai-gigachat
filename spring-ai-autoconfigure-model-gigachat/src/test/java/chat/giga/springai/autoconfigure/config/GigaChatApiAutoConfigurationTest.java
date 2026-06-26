package chat.giga.springai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.auth.bearer.GigaAuthToken;
import chat.giga.springai.api.auth.bearer.NoopGigaAuthToken;
import chat.giga.springai.api.auth.bearer.SimpleGigaAuthToken;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.autoconfigure.GigaChatApiAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;

public class GigaChatApiAutoConfigurationTest {

    private final AutoConfigurations baseAutoConfigurations = AutoConfigurations.of(
            GigaChatApiAutoConfiguration.class,
            RestClientAutoConfiguration.class,
            WebClientAutoConfiguration.class,
            ConfigurationPropertiesAutoConfiguration.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.ai.gigachat.auth.bearer.api-key=test")
            .withConfiguration(baseAutoConfigurations);

    @Test
    @DisplayName("Проверяет создание всех бинов GigaChatApiAutoConfiguration с bearer-аутентификацией")
    void defaultBeansCreatedWithBearerAuth() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GigaChatApi.class);
            assertThat(context).hasSingleBean(GigaChatAuthProperties.class);
            assertThat(context).hasSingleBean(GigaChatInternalProperties.class);
            assertThat(context).hasSingleBean(GigaChatApiProperties.class);
            assertThat(context).hasSingleBean(GigaAuthToken.class);

            GigaAuthToken token = context.getBean(GigaAuthToken.class);
            assertThat(token).isInstanceOf(SimpleGigaAuthToken.class);

            GigaChatAuthProperties authProps = context.getBean(GigaChatAuthProperties.class);
            assertThat(authProps.isBearerAuth()).isTrue();
            assertThat(authProps.getApiKey()).isEqualTo("test");
        });
    }

    @Test
    @DisplayName("При сертификатной аутентификации должен создаваться NoopGigaAuthToken")
    void certsAuthCreatesNoopToken() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.ai.gigachat.auth.certs.enabled=true", "spring.ai.gigachat.auth.bearer.enabled=false")
                .withConfiguration(baseAutoConfigurations)
                .run(context -> {
                    assertThat(context).hasSingleBean(GigaAuthToken.class);
                    GigaAuthToken token = context.getBean(GigaAuthToken.class);
                    assertThat(token).isInstanceOf(NoopGigaAuthToken.class);
                });
    }

    @Test
    @DisplayName("В WebApplicationContext (servlet) бины создаются корректно")
    void webApplicationContextTest() {
        new WebApplicationContextRunner()
                .withPropertyValues("spring.ai.gigachat.auth.bearer.api-key=test")
                .withConfiguration(baseAutoConfigurations)
                .run(context -> {
                    assertThat(context).hasSingleBean(GigaChatApi.class);
                    assertThat(context).hasSingleBean(GigaChatAuthProperties.class);
                    assertThat(context).hasSingleBean(GigaChatInternalProperties.class);
                    assertThat(context).hasSingleBean(GigaChatApiProperties.class);
                    assertThat(context).hasSingleBean(GigaAuthToken.class);
                });
    }

    @Test
    @DisplayName("В ReactiveWebApplicationContext (webflux) бины создаются корректно")
    void reactiveWebApplicationContextTest() {
        new ReactiveWebApplicationContextRunner()
                .withPropertyValues("spring.ai.gigachat.auth.bearer.api-key=test")
                .withConfiguration(baseAutoConfigurations)
                .run(context -> {
                    assertThat(context).hasSingleBean(GigaChatApi.class);
                    assertThat(context).hasSingleBean(GigaChatAuthProperties.class);
                    assertThat(context).hasSingleBean(GigaChatInternalProperties.class);
                    assertThat(context).hasSingleBean(GigaChatApiProperties.class);
                    assertThat(context).hasSingleBean(GigaAuthToken.class);
                });
    }

    @Test
    @DisplayName("При наличии SslBundles бины создаются без ошибок")
    void sslBundlesIntegrationTest() {
        AutoConfigurations sslConfigurations = AutoConfigurations.of(
                GigaChatApiAutoConfiguration.class,
                RestClientAutoConfiguration.class,
                WebClientAutoConfiguration.class,
                SslAutoConfiguration.class);

        new WebApplicationContextRunner()
                .withPropertyValues("spring.ai.gigachat.auth.bearer.api-key=test")
                .withConfiguration(sslConfigurations)
                .run(context -> {
                    assertThat(context).hasSingleBean(GigaChatApi.class);
                    assertThat(context).hasSingleBean(SslBundles.class);
                });
    }
}
