package chat.giga.springai.api.auth.bearer.interceptors;

import chat.giga.springai.api.auth.bearer.GigaChatBearerAuthApi;
import org.springframework.ai.model.ApiKey;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

public class BearerTokenFilter implements ExchangeFilterFunction {

    private final GigaChatBearerAuthApi tokenRenewer;

    private final ApiKey apiKey;

    public BearerTokenFilter(GigaChatBearerAuthApi tokenRenewer, @Nullable ApiKey apiKey) {
        this.tokenRenewer = tokenRenewer;
        this.apiKey = apiKey;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String token = apiKey != null ? apiKey.getValue() : tokenRenewer.getAccessToken();

        if (token == null || token.isEmpty()) return next.exchange(request);

        return next.exchange(ClientRequest.from(request)
                .header("Authorization", "Bearer " + token)
                .build());
    }
}
