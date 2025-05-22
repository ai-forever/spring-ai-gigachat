package chat.giga.springai.api.models;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.empty;

import chat.giga.springai.api.auth.GigaChatApiProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.models.ModelsResponse;
import chat.giga.springai.extension.GigaChatTestPropertiesExtension;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.ResponseEntity;

@Slf4j
@ExtendWith(GigaChatTestPropertiesExtension.class)
@EnabledIfEnvironmentVariable(named = "GIGACHAT_API_CLIENT_SECRET", matches = ".*")
public class ModelsTest {

    @Test
    @DisplayName("Тест проверяет корректное получение списка моделей")
    void modelsTest(GigaChatApiProperties authProperties) {
        final GigaChatApi gigaChatApi = new GigaChatApi(authProperties);

        final ResponseEntity<ModelsResponse> response = gigaChatApi.models();

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody(), is(not(nullValue())));
        assertThat(response.getBody().getData(), is(not(nullValue())));
        assertThat(response.getBody().getData(), is(not(empty())));
        assertThat(response.getBody().getData(), everyItem(hasProperty("id")));
    }
}
