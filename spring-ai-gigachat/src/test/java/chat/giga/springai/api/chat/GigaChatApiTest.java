package chat.giga.springai.api.chat;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.never;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.auth.bearer.NoopGigaAuthToken;
import chat.giga.springai.api.auth.bearer.SimpleGigaAuthToken;
import chat.giga.springai.api.auth.bearer.interceptors.BearerTokenFilter;
import chat.giga.springai.api.auth.bearer.interceptors.BearerTokenInterceptor;
import java.util.ArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

public class GigaChatApiTest {

    @Test
    @DisplayName(
            """
            Тест проверяет добавление BearerTokenInterceptor/BearerTokenFilter
            в RestClient.Builder и WebClient.Builder при bearer-авторизации
            """)
    void bearerAuth_bearerTokenInterceptorExistsTest() {
        final GigaChatApiProperties properties = GigaChatApiProperties.builder()
                .auth(GigaChatAuthProperties.builder()
                        .bearer(GigaChatAuthProperties.Bearer.builder()
                                .apiKey("apiKey")
                                .build())
                        .build())
                .build();

        RestClient.Builder mockRestClientBuilder = Mockito.spy(RestClient.builder());
        WebClient.Builder mockWebClientBuilder = Mockito.spy(WebClient.builder());

        final GigaChatApi gigaChatApi = new GigaChatApi(
                properties,
                new SimpleGigaAuthToken(properties.getApiKey()),
                mockRestClientBuilder,
                mockWebClientBuilder,
                RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER,
                null,
                null);

        Mockito.verify(mockRestClientBuilder).requestInterceptor(Mockito.any(BearerTokenInterceptor.class));
        Mockito.verify(mockWebClientBuilder).filter(Mockito.any(BearerTokenFilter.class));
    }

    @Test
    @DisplayName(
            """
            Тест проверяет отсутствие BearerTokenInterceptor/BearerTokenFilter
            в RestClient.Builder и WebClient.Builder при авторизации по TLS-сертификату
            """)
    void certAuth_noBearerTokenInterceptorTest() {
        final GigaChatApiProperties properties = GigaChatApiProperties.builder()
                .auth(GigaChatAuthProperties.builder()
                        .certs(GigaChatAuthProperties.Certificates.builder()
                                .privateKey(new ByteArrayResource(new byte[] {1}))
                                .certificate(new ByteArrayResource(new byte[] {2}))
                                .build())
                        .build())
                .build();

        RestClient.Builder mockRestClientBuilder = Mockito.spy(RestClient.builder());
        WebClient.Builder mockWebClientBuilder = Mockito.spy(WebClient.builder());

        final GigaChatApi gigaChatApi = new GigaChatApi(
                properties,
                new NoopGigaAuthToken(),
                mockRestClientBuilder,
                mockWebClientBuilder,
                RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER,
                null,
                null);

        Mockito.verify(mockRestClientBuilder, never()).requestInterceptor(Mockito.any(BearerTokenInterceptor.class));
        Mockito.verify(mockRestClientBuilder, never()).requestInterceptors(Mockito.argThat(listConsumer -> {
            var list = new ArrayList<ClientHttpRequestInterceptor>();
            listConsumer.accept(list);
            return not(hasItem(instanceOf(BearerTokenInterceptor.class))).matches(list);
        }));
        Mockito.verify(mockWebClientBuilder, never()).filter(Mockito.any(BearerTokenFilter.class));
        Mockito.verify(mockWebClientBuilder, never()).filters(Mockito.argThat(listConsumer -> {
            var list = new ArrayList<ExchangeFilterFunction>();
            listConsumer.accept(list);
            return not(hasItem(instanceOf(BearerTokenFilter.class))).matches(list);
        }));
    }
}
