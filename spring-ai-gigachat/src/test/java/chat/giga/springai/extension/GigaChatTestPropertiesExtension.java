package chat.giga.springai.extension;

import chat.giga.springai.api.auth.GigaChatApiProperties;
import lombok.SneakyThrows;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Расширение предоставляет параметры для авторизации
 *
 */
public class GigaChatTestPropertiesExtension implements ParameterResolver {

    @Override
    @SneakyThrows
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().equals(GigaChatApiProperties.class);
    }

    @Override
    @SneakyThrows
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return GigaChatApiProperties.builder()
                .scope(GigaChatApiProperties.GigaChatApiScope.valueOf(System.getProperty("GIGACHAT_API_SCOPE")))
                .clientId(System.getProperty("GIGACHAT_API_CLIENT_ID"))
                .clientSecret(System.getProperty("GIGACHAT_API_CLIENT_SECRET"))
                .unsafeSsl(true)
                .build();
    }
}
