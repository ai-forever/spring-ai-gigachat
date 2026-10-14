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

package chat.giga.springai.autoconfigure;

/**
 * Общий тестовый хелпер: собирает набор spring-property-значений для авторизации GigaChat из
 * переменных окружения, чтобы интеграционные тесты не дублировали один и тот же код.
 *
 * <p>Приоритет: если задан {@code GIGACHAT_API_KEY} — используется bearer api-key, иначе —
 * client-id/client-secret.
 */
public final class GigaChatAuthTestProperties {

    private GigaChatAuthTestProperties() {}

    /**
     * @return массив property-значений для {@code ApplicationContextRunner.withPropertyValues(...)}
     */
    public static String[] fromEnv() {
        String scope = System.getenv("GIGACHAT_API_SCOPE");
        String apiKey = System.getenv("GIGACHAT_API_KEY");
        String clientId = System.getenv("GIGACHAT_API_CLIENT_ID");
        String clientSecret = System.getenv("GIGACHAT_API_CLIENT_SECRET");
        if (apiKey != null && !apiKey.isBlank()) {
            return new String[] {
                "spring.ai.gigachat.auth.scope=" + scope, "spring.ai.gigachat.auth.bearer.api-key=" + apiKey
            };
        }
        return new String[] {
            "spring.ai.gigachat.auth.scope=" + scope,
            "spring.ai.gigachat.auth.bearer.client-id=" + clientId,
            "spring.ai.gigachat.auth.bearer.client-secret=" + clientSecret
        };
    }
}
