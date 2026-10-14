/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.giga.springai.api.auth.bearer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GigaChatOAuthClientTest {

    @Test
    @DisplayName("#06: requestToken() при scope==null бросает понятное исключение, "
            + "а не отправляет строку \"scope=null\"")
    void requestToken_nullScope_throwsClearError() {
        // scope не задан (null) — но заданы client-id/secret, так что путь bearer активен
        GigaChatApiProperties properties = GigaChatApiProperties.builder()
                .auth(GigaChatAuthProperties.builder()
                        .unsafeSsl(true)
                        .bearer(GigaChatAuthProperties.Bearer.builder()
                                .clientId("id")
                                .clientSecret("secret")
                                .build())
                        .build())
                .build();

        GigaChatOAuthClient client = new GigaChatOAuthClient(
                properties,
                RestClient.builder(),
                new SimpleGigaAuthToken(properties.getAuth().getApiKey()));

        assertThatThrownBy(client::requestToken)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope");
    }
}
