/*
 * Copyright 2026 the original author or authors.
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

package chat.giga.springai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.ResponseFormat;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class GigaChatStructuredOutputTest {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String SCHEMA = """
            {"type":"object","properties":{"name":{"type":"string"},"age":{"type":"integer"}},
             "required":["name","age"],"additionalProperties":false}
            """;
    private static final String PROMPT = "Ивану 30 лет.";
    private static final String CONTENT = "{\"name\":\"Иван\",\"age\":30}";
    private WireMockServer server;

    @BeforeEach
    void start() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(completion(CONTENT))));
    }

    private static String completion(String content) {
        return JSON_MAPPER.writeValueAsString(Map.of(
                "model",
                "GigaChat-2-Max",
                "choices",
                List.of(Map.of(
                        "index",
                        0,
                        "finish_reason",
                        "stop",
                        "message",
                        Map.of("role", "assistant", "content", content)))));
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private GigaChatModel model(GigaChatOptions defaults) {
        var api = new GigaChatApi(GigaChatApiProperties.builder()
                .baseUrl("http://localhost:" + server.port() + "/v1")
                .auth(GigaChatAuthProperties.builder().build())
                .build());
        return GigaChatModel.builder()
                .gigaChatApi(api)
                .defaultOptions(defaults)
                .internalProperties(new GigaChatInternalProperties())
                .build();
    }

    private Map<String, Object> requestBody() {
        return JSON_MAPPER.readValue(
                server.getAllServeEvents().get(0).getRequest().getBodyAsString(), MAP_TYPE);
    }

    private Map<String, Object> format() {
        return JSON_MAPPER.convertValue(requestBody().get("response_format"), MAP_TYPE);
    }

    public record Person(String name, int age) {}

    @Test
    void nativeEntitySendsSchemaWithoutAddingInstructionsToPrompt() {
        var model = model(GigaChatOptions.builder().model("GigaChat-2-Max").build());
        Person result = ChatClient.create(model)
                .prompt()
                .user(PROMPT)
                .call()
                .entity(Person.class, spec -> spec.useProviderStructuredOutput());

        assertThat(result).isEqualTo(new Person("Иван", 30));
        assertThat(format()).containsEntry("type", "json_schema").containsEntry("strict", true);
        assertThat(format().get("schema")).isInstanceOf(Map.class);
        assertThat(JSON_MAPPER.convertValue(format().get("schema"), MAP_TYPE))
                .isEqualTo(JSON_MAPPER.readValue(new BeanOutputConverter<>(Person.class).getJsonSchema(), MAP_TYPE));
        assertThat(requestBody().get("messages")).isEqualTo(List.of(Map.of("role", "user", "content", PROMPT)));
        assertThat(model.getOptions()).isInstanceOf(StructuredOutputChatOptions.class);
        assertThat(((StructuredOutputChatOptions) model.getOptions()).getOutputSchema())
                .isNull();
    }

    @Test
    void nativeAdvisorParameterEnablesSchemaForOneRequest() {
        var model = model(GigaChatOptions.builder().model("GigaChat-2-Max").build());
        var client = ChatClient.create(model);
        Person result = client.prompt()
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .user(PROMPT)
                .call()
                .entity(Person.class);

        assertThat(result).isEqualTo(new Person("Иван", 30));
        assertThat(format()).containsEntry("type", "json_schema").containsEntry("strict", true);
        assertThat(requestBody().get("messages")).isEqualTo(List.of(Map.of("role", "user", "content", PROMPT)));

        client.prompt().user(PROMPT).call().entity(Person.class);
        assertThat(requestBody()).doesNotContainKey("response_format");
        assertThat(requestBody().get("messages").toString()).contains("properties");
    }

    @Test
    void nativeDefaultAdvisorEnablesSchemaForEntityCallsOnly() {
        var model = model(GigaChatOptions.builder().model("GigaChat-2-Max").build());
        var client = ChatClient.builder(model)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
        Person result = client.prompt().user(PROMPT).call().entity(Person.class);

        assertThat(result).isEqualTo(new Person("Иван", 30));
        assertThat(format()).containsEntry("type", "json_schema").containsEntry("strict", true);
        assertThat(requestBody().get("messages")).isEqualTo(List.of(Map.of("role", "user", "content", PROMPT)));

        client.prompt().user(PROMPT).call().content();
        assertThat(requestBody()).doesNotContainKey("response_format");
        assertThat(((StructuredOutputChatOptions) model.getOptions()).getOutputSchema())
                .isNull();
    }

    @Test
    void ordinaryEntityUsesPromptInstructionsWithoutNativeSchema() {
        Person result = ChatClient.create(
                        model(GigaChatOptions.builder().model("GigaChat-2-Max").build()))
                .prompt()
                .user(PROMPT)
                .call()
                .entity(Person.class);

        assertThat(result).isEqualTo(new Person("Иван", 30));
        assertThat(requestBody()).doesNotContainKey("response_format");
        assertThat(requestBody().get("messages").toString()).contains(PROMPT, "properties");
    }

    @Test
    void nativeValidationRetriesInvalidResponseAndPreservesSchema() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario("schema-validation")
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("corrected")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(completion("{\"name\":\"Иван\",\"age\":\"invalid\"}"))));
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario("schema-validation")
                .whenScenarioStateIs("corrected")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(completion(CONTENT))));
        var model = model(GigaChatOptions.builder().model("GigaChat-2-Max").build());
        Person result = ChatClient.create(model)
                .prompt()
                .user(PROMPT)
                .call()
                .entity(Person.class, spec -> spec.useProviderStructuredOutput().validateSchema());

        assertThat(result).isEqualTo(new Person("Иван", 30));
        assertThat(server.getAllServeEvents()).hasSize(2);
        var expectedSchema = JSON_MAPPER.readValue(new BeanOutputConverter<>(Person.class).getJsonSchema(), MAP_TYPE);
        assertThat(server.getAllServeEvents()).allSatisfy(event -> {
            var body = JSON_MAPPER.readValue(event.getRequest().getBodyAsString(), MAP_TYPE);
            assertThat(JSON_MAPPER.convertValue(body.get("response_format"), MAP_TYPE))
                    .isEqualTo(Map.of("type", "json_schema", "strict", true, "schema", expectedSchema));
        });
        assertThat(requestBody().get("messages").toString()).contains(PROMPT, "Output JSON validation failed");
    }

    @Test
    void explicitFormatReplacesDefaultSchemaAndPreservesStrictFalse() {
        var model = model(GigaChatOptions.builder()
                .model("GigaChat-2-Max")
                .outputSchema("{}")
                .build());
        model.call(new Prompt(
                PROMPT,
                GigaChatOptions.builder()
                        .responseFormat(ResponseFormat.builder()
                                .type(ResponseFormat.Type.JSON_SCHEMA)
                                .schema(SCHEMA)
                                .strict(false)
                                .build())
                        .build()));

        assertThat(format())
                .isEqualTo(Map.of(
                        "type", "json_schema", "strict", false, "schema", JSON_MAPPER.readValue(SCHEMA, MAP_TYPE)));
    }

    @Test
    void streamingSendsNativeSchemaAndCombinesJsonFragments() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("data: "
                                + JSON_MAPPER.writeValueAsString(Map.of(
                                        "choices",
                                        List.of(Map.of(
                                                "index",
                                                0,
                                                "delta",
                                                Map.of("role", "assistant", "content", CONTENT.substring(0, 10))))))
                                + "\n\ndata: "
                                + JSON_MAPPER.writeValueAsString(Map.of(
                                        "choices",
                                        List.of(Map.of(
                                                "index",
                                                0,
                                                "finish_reason",
                                                "stop",
                                                "delta",
                                                Map.of("content", CONTENT.substring(10))))))
                                + "\n\ndata: [DONE]\n\n")));
        var model = model(GigaChatOptions.builder().model("GigaChat-2-Max").build());
        String result = model.stream(new Prompt(
                        PROMPT,
                        StructuredOutputChatOptions.builder()
                                .outputSchema(SCHEMA)
                                .build()))
                .map(response -> response.getResult().getOutput().getText())
                .reduce("", String::concat)
                .block(Duration.ofSeconds(10));

        assertThat(result).isEqualTo(CONTENT);
        assertThat(requestBody()).containsEntry("stream", true);
        assertThat(format())
                .isEqualTo(Map.of(
                        "type", "json_schema", "strict", true, "schema", JSON_MAPPER.readValue(SCHEMA, MAP_TYPE)));
    }

    @Test
    void ordinaryChatOmitsFormatAndTextExplicitlyOverridesDefaultSchema() {
        model(GigaChatOptions.builder().model("GigaChat-2-Max").build()).call(PROMPT);
        assertThat(requestBody()).doesNotContainKey("response_format");

        var model = model(GigaChatOptions.builder()
                .model("GigaChat-2-Max")
                .outputSchema(SCHEMA)
                .build());
        model.call(new Prompt(
                PROMPT,
                GigaChatOptions.builder()
                        .responseFormat(ResponseFormat.builder()
                                .type(ResponseFormat.Type.TEXT)
                                .build())
                        .build()));
        assertThat(format()).isEqualTo(Map.of("type", "text"));
    }

    @Test
    void invalidFormatsFailBeforeSendingRequests() {
        assertThatThrownBy(() -> ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_SCHEMA)
                        .schema(" ")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResponseFormat.builder()
                        .type(ResponseFormat.Type.TEXT)
                        .schema(SCHEMA)
                        .strict(true)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(server.getAllServeEvents()).isEmpty();
    }
}
