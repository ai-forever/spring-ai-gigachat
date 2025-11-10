package chat.giga.springai.api.auth.bearer.interceptors;

import chat.giga.springai.api.auth.bearer.GigaChatBearerAuthApi;
import lombok.SneakyThrows;
import org.springframework.ai.model.ApiKey;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.Nullable;

public class BearerTokenInterceptor implements ClientHttpRequestInterceptor {

    private final GigaChatBearerAuthApi tokenRenewer;

    private final ApiKey apiKey;

    public BearerTokenInterceptor(GigaChatBearerAuthApi tokenRenewer, @Nullable ApiKey apiKey) {
        this.tokenRenewer = tokenRenewer;
        this.apiKey = apiKey;
    }

    @Override
    @SneakyThrows
    @SuppressWarnings("NullableProblems")
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
        String token = apiKey != null ? apiKey.getValue() : tokenRenewer.getAccessToken();

        if (token != null && !token.isEmpty())
            request.getHeaders().setBearerAuth(token);

        return execution.execute(request, body);
    }
}
