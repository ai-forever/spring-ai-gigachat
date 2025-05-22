package chat.giga.springai.api.auth.bearer.interceptors;

import chat.giga.springai.api.auth.bearer.GigaChatBearerAuthApi;
import lombok.SneakyThrows;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class BearerTokenInterceptor implements ClientHttpRequestInterceptor {

    private final GigaChatBearerAuthApi tokenRenewer;

    public BearerTokenInterceptor(GigaChatBearerAuthApi tokenRenewer) {
        this.tokenRenewer = tokenRenewer;
    }

    @Override
    @SneakyThrows
    @SuppressWarnings("NullableProblems")
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
        request.getHeaders().setBearerAuth(tokenRenewer.getAccessToken());
        return execution.execute(request, body);
    }
}
