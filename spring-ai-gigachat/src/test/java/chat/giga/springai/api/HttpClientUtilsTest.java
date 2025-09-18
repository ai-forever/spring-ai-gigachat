package chat.giga.springai.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.http.HttpClient;
import java.time.Duration;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.keymanager.DummyX509ExtendedKeyManager;
import nl.altindag.ssl.trustmanager.DummyX509ExtendedTrustManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

class HttpClientUtilsTest {

    private KeyManagerFactory kmfMock;
    private TrustManagerFactory tmfMock;
    private SSLFactory sslFactoryMock;

    @BeforeEach
    void setUp() {
        kmfMock = mock(KeyManagerFactory.class, Answers.RETURNS_DEEP_STUBS);
        tmfMock = mock(TrustManagerFactory.class, Answers.RETURNS_DEEP_STUBS);
        sslFactoryMock = mock(SSLFactory.class, Answers.RETURNS_DEEP_STUBS);
    }

    @Test
    void testBuildHttpClientWithSslFactoryAndConnectTimeout() {
        Duration connectTimeout = Duration.ofSeconds(10);
        HttpClient httpClient = HttpClientUtils.buildHttpClient(sslFactoryMock, connectTimeout);

        assertNotNull(httpClient);
        assertNotNull(httpClient.connectTimeout());
        assertTrue(httpClient.connectTimeout().isPresent());
        assertEquals(connectTimeout, httpClient.connectTimeout().get());
        verify(sslFactoryMock, times(1)).getSslParameters();
        verify(sslFactoryMock, times(1)).getSslContext();
    }

    @Test
    void testBuildHttpClientWithNullSslFactory() {
        assertThrows(
                IllegalArgumentException.class, () -> HttpClientUtils.buildHttpClient(null, Duration.ofSeconds(10)));
    }

    @Test
    void testBuildHttpClientWithNullConnectTimeout() {
        assertThrows(IllegalArgumentException.class, () -> HttpClientUtils.buildHttpClient(sslFactoryMock, null));
    }

    @Test
    void testBuildHttpClientWithZeroConnectTimeout() {
        assertThrows(IllegalStateException.class, () -> HttpClientUtils.buildHttpClient(sslFactoryMock, Duration.ZERO));
    }

    @Test
    void testBuildHttpClientWithNegativeConnectTimeout() {
        assertThrows(
                IllegalStateException.class,
                () -> HttpClientUtils.buildHttpClient(sslFactoryMock, Duration.ofSeconds(-1)));
    }

    @Test
    void testBuildSslFactoryWithKmfAndTmfAndSafeSsl() {
        Mockito.when(kmfMock.getKeyManagers()).thenReturn(new KeyManager[] {DummyX509ExtendedKeyManager.getInstance()});
        Mockito.when(tmfMock.getTrustManagers())
                .thenReturn(new TrustManager[] {DummyX509ExtendedTrustManager.getInstance()});

        SSLFactory sslFactory = HttpClientUtils.buildSslFactory(kmfMock, tmfMock, false);

        assertNotNull(sslFactory);
        verify(kmfMock, times(1)).getKeyManagers();
        verify(tmfMock, times(1)).getTrustManagers();
    }

    @Test
    void testBuildSslFactoryWithNullKmfAndNullTmfAndSafeSsl() {
        SSLFactory sslFactory = HttpClientUtils.buildSslFactory(null, null, false);

        assertNotNull(sslFactory);
        verifyNoInteractions(kmfMock);
        verifyNoInteractions(tmfMock);
    }

    @Test
    void testBuildSslFactoryWithNullKmfAndNullTmfAndUnsafeSsl() {
        SSLFactory sslFactory = HttpClientUtils.buildSslFactory(null, null, true);

        assertNotNull(sslFactory);
        verifyNoInteractions(kmfMock);
        verifyNoInteractions(tmfMock);
    }
}
