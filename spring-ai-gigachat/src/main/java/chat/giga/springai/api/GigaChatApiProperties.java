package chat.giga.springai.api;

import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GigaChatApiProperties {
    public static final String CONFIG_PREFIX = "spring.ai.gigachat";

    @Builder.Default
    private String baseUrl = GigaChatApi.DEFAULT_BASE_URL;

    @Builder.Default
    private GigaChatAuthProperties auth = new GigaChatAuthProperties();

    @Builder.Default
    private GigaChatInternalProperties internal = new GigaChatInternalProperties();
}
