package chat.giga.springai.api.auth.bearer.interceptors;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.springframework.ai.model.ApiKey;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * An {@link ExchangeFilterFunction} implementation that adds Bearer token authentication
 * to reactive WebClient requests.
 *
 * <p>This filter intercepts outgoing HTTP requests and automatically injects a Bearer token
 * into the Authorization header. The token is obtained from the provided {@link ApiKey}
 * instance, which may implement token renewal logic.
 *
 * @param tokenRenewer the API key provider that supplies the Bearer token value
 *
 * @see ExchangeFilterFunction
 * @see ApiKey
 */
public record BearerTokenFilter(ApiKey tokenRenewer) implements ExchangeFilterFunction {

    /**
     * Filters the client request by adding Bearer token authentication to the Authorization header.
     *
     * <p>This method creates a new request with the Authorization header set to
     * {@code "Bearer <token>"}, where the token is retrieved from the {@link #tokenRenewer}.
     *
     * @param request the original client request
     * @param next the next exchange function in the filter chain
     * @return a {@link Mono} that emits the client response after authentication is applied
     */
    @Override
    @SuppressWarnings("NullableProblems")
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return next.exchange(ClientRequest.from(request)
                .header(AUTHORIZATION, "Bearer " + tokenRenewer.getValue())
                .build());
    }
}
