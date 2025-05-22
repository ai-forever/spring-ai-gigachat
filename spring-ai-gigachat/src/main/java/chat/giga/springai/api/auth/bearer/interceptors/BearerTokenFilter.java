package chat.giga.springai.api.auth.bearer.interceptors;

import chat.giga.springai.api.auth.bearer.GigaChatBearerAuthApi;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

public class BearerTokenFilter implements ExchangeFilterFunction {

    private final GigaChatBearerAuthApi tokenRenewer;

    public BearerTokenFilter(GigaChatBearerAuthApi tokenRenewer) {
        this.tokenRenewer = tokenRenewer;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return next.exchange(ClientRequest.from(request)
                .header("Authorization", "Bearer " + tokenRenewer.getAccessToken())
                .build());
    }
}
