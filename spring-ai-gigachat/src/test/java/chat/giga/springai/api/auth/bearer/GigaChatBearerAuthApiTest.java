package chat.giga.springai.api.auth.bearer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import chat.giga.springai.api.auth.GigaChatApiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@ExtendWith(MockitoExtension.class)
public class GigaChatBearerAuthApiTest {

    @Test
    void getClientHttpRequestFactory_WhenUnsafeSslFalse_ReturnsStandardFactory() {
        final GigaChatApiProperties properties = new GigaChatApiProperties();
        properties.setUnsafeSsl(false);
        final GigaChatBearerAuthApi api = spy(new GigaChatBearerAuthApi(properties));
        final ClientHttpRequestFactory factory = api.getClientHttpRequestFactory(properties);

        assertNotNull(factory);
        assertInstanceOf(JdkClientHttpRequestFactory.class, factory);

        verify(api, times(0)).unsafeSsl();
    }

    @Test
    void getClientHttpRequestFactory_WhenUnsafeSslTrue_ReturnsUnsafeFactory() {
        final GigaChatApiProperties properties = new GigaChatApiProperties();
        properties.setUnsafeSsl(true);
        final GigaChatBearerAuthApi api = spy(new GigaChatBearerAuthApi(properties));
        final ClientHttpRequestFactory factory = api.getClientHttpRequestFactory(properties);

        assertNotNull(factory);
        assertInstanceOf(JdkClientHttpRequestFactory.class, factory);

        verify(api, times(1)).unsafeSsl();
    }

    @Test
    void unsafeSsl_ReturnsCustomJdkClientHttpRequestFactory() {
        final GigaChatBearerAuthApi api = new GigaChatBearerAuthApi(new GigaChatApiProperties());
        final ClientHttpRequestFactory factory = api.unsafeSsl();

        assertNotNull(factory);
        assertInstanceOf(JdkClientHttpRequestFactory.class, factory);
    }
}
