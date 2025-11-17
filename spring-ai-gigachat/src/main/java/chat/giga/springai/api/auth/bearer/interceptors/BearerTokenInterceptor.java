package chat.giga.springai.api.auth.bearer.interceptors;

import lombok.SneakyThrows;
import org.springframework.ai.model.ApiKey;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * A {@link ClientHttpRequestInterceptor} implementation that adds Bearer token authentication
 * to synchronous RestTemplate and RestClient requests.
 *
 * <p>This interceptor intercepts outgoing HTTP requests and automatically injects a Bearer token
 * into the Authorization header. The token is obtained from the provided {@link ApiKey}
 * instance, which may implement token renewal logic.
 *
 * @param tokenRenewer the API key provider that supplies the Bearer token value
 *
 * @see ClientHttpRequestInterceptor
 * @see ApiKey
 */
public record BearerTokenInterceptor(ApiKey tokenRenewer) implements ClientHttpRequestInterceptor {

    /**
     * Intercepts the HTTP request and adds Bearer token authentication to the Authorization header.
     *
     * <p>This method modifies the request headers by setting the Bearer authentication token
     * retrieved from the {@link #tokenRenewer}, then proceeds with the request execution.
     *
     * @param request the HTTP request being intercepted
     * @param body the request body as a byte array
     * @param execution the request execution chain
     * @return the HTTP response after the request has been executed with authentication
     */
    @Override
    @SneakyThrows
    @SuppressWarnings("NullableProblems")
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
        request.getHeaders().setBearerAuth(tokenRenewer.getValue());
        return execution.execute(request, body);
    }
}
