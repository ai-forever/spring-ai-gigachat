package chat.giga.springai;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.auth.GigaChatApiScope;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.param.FunctionCallParam;
import chat.giga.springai.tool.GigaTools;
import chat.giga.springai.tool.annotation.GigaTool;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

/**
 * Полное покрытие высокоуровневого {@link GigaChatModel} на реальном API: call/stream во всех
 * вариантах, КАЖДЫЙ параметр {@link GigaChatOptions}, все режимы функций и мультимодальность.
 * Креды из переменных окружения GIGACHAT_API_*, секреты не печатаются.
 */
@Slf4j
class GigaChatModelFullIT {

    private static GigaChatApi api() {
        GigaChatApiProperties properties = GigaChatApiProperties.builder()
                .auth(GigaChatAuthProperties.builder()
                        .scope(GigaChatApiScope.valueOf(System.getenv("GIGACHAT_API_SCOPE")))
                        .unsafeSsl(true)
                        .bearer(GigaChatAuthProperties.Bearer.builder()
                                .clientId(System.getenv("GIGACHAT_API_CLIENT_ID"))
                                .clientSecret(System.getenv("GIGACHAT_API_CLIENT_SECRET"))
                                .build())
                        .build())
                .build();
        return new GigaChatApi(properties);
    }

    private static GigaChatModel model(GigaChatOptions options) {
        return GigaChatModel.builder()
                .gigaChatApi(api())
                .defaultOptions(options)
                .internalProperties(new GigaChatInternalProperties())
                .build();
    }

    private static GigaChatModel model() {
        return model(GigaChatOptions.builder()
                .model(GigaChatApi.ChatModel.GIGA_CHAT_2_MAX)
                .build());
    }

    @Test
    @DisplayName("call(String) / call(Prompt) / stream(String) / stream(Prompt)")
    void callAndStreamVariants() {
        GigaChatModel model = model();

        assertThat(model.call("Привет, как дела?")).isNotBlank();

        ChatResponse callResponse = model.call(new Prompt(List.of(new UserMessage("Назови город-герой"))));
        assertThat(callResponse.getResult().getOutput().getText()).isNotBlank();

        List<String> streamString =
                model.stream("Посчитай до трёх").collectList().block();
        assertThat(streamString).isNotEmpty();

        List<ChatResponse> streamPrompt = model.stream(new Prompt(List.of(new UserMessage("Скажи привет"))))
                .collectList()
                .block();
        assertThat(streamPrompt).isNotEmpty();
    }

    @Test
    @DisplayName("Каждый параметр GigaChatOptions доходит до API и принимается")
    void allOptionParamsAccepted() {
        GigaChatOptions options = GigaChatOptions.builder()
                .model(GigaChatApi.ChatModel.GIGA_CHAT_2_MAX)
                .temperature(0.6)
                .topP(0.9)
                .maxTokens(60)
                .repetitionPenalty(1.1)
                .updateInterval(0.0)
                .profanityCheck(true)
                .httpHeaders(Map.of("X-Custom-Header", "bughunt"))
                .build();

        String answer = model(options).call("Расскажи короткий факт о море");
        assertThat(answer).isNotBlank();
    }

    @Test
    @DisplayName("Параметр model: GIGA_CHAT_2 / _MAX / _PRO — все рабочие")
    void modelParamPerValue() {
        for (GigaChatApi.ChatModel chatModel : List.of(
                GigaChatApi.ChatModel.GIGA_CHAT_2,
                GigaChatApi.ChatModel.GIGA_CHAT_2_MAX,
                GigaChatApi.ChatModel.GIGA_CHAT_2_PRO)) {
            GigaChatModel model =
                    model(GigaChatOptions.builder().model(chatModel).build());
            ChatResponse response = model.call(new Prompt(List.of(new UserMessage("Привет"))));
            assertThat(response.getResult().getOutput().getText()).isNotBlank();
            assertThat(response.getMetadata().getModel()).contains(chatModel.getName());
            log.info(
                    "Модель {} ответила, metadata.model={}",
                    chatModel.getName(),
                    response.getMetadata().getModel());
        }
    }

    @Test
    @DisplayName("maxTokens реально ограничивает длину ответа")
    void maxTokensLimitsLength() {
        GigaChatModel model = model(GigaChatOptions.builder()
                .model(GigaChatApi.ChatModel.GIGA_CHAT_2_MAX)
                .maxTokens(5)
                .build());
        ChatResponse response = model.call(new Prompt(List.of(new UserMessage("Подробно опиши Вселенную"))));
        Integer completionTokens = response.getMetadata().getUsage().getCompletionTokens();
        log.info("maxTokens=5 -> completionTokens={}", completionTokens);
        assertThat(completionTokens).isLessThanOrEqualTo(8);
    }

    @Test
    @DisplayName("functionCallMode=AUTO: модель возвращает запрос на вызов инструмента (PATH A: без исполнения)")
    void functionCallMode_auto_returnsToolCall() {
        GigaChatModel model = model(GigaChatOptions.builder()
                .model(GigaChatApi.ChatModel.GIGA_CHAT_2_MAX)
                .functionCallMode(GigaChatOptions.FunctionCallMode.AUTO)
                .toolCallbacks(GigaTools.from(new WeatherTool()))
                .build());

        ChatResponse response =
                model.call(new Prompt(List.of(new UserMessage("Какая погода в Сочи? Используй инструмент."))));
        log.info("AUTO finishReason={}", response.getResult().getMetadata().getFinishReason());
        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.getResult().getOutput().getToolCalls().get(0).name())
                .isEqualTo("getCurrentWeather");
    }

    @Test
    @DisplayName("functionCallMode=NONE: инструмент не вызывается, модель отвечает текстом")
    void functionCallMode_none_textOnly() {
        GigaChatModel model = model(GigaChatOptions.builder()
                .model(GigaChatApi.ChatModel.GIGA_CHAT_2_MAX)
                .functionCallMode(GigaChatOptions.FunctionCallMode.NONE)
                .toolCallbacks(GigaTools.from(new WeatherTool()))
                .build());

        ChatResponse response =
                model.call(new Prompt(List.of(new UserMessage("Какая погода в Сочи? Используй инструмент."))));
        assertThat(response.hasToolCalls()).isFalse();
        assertThat(response.getResult().getOutput().getText()).isNotBlank();
    }

    @Test
    @DisplayName("functionCallMode=CUSTOM_FUNCTION + functionCallParam: принудительный вызов указанной функции")
    void functionCallMode_custom_forcesFunction() {
        GigaChatModel model = model(GigaChatOptions.builder()
                .model(GigaChatApi.ChatModel.GIGA_CHAT_2_MAX)
                .functionCallMode(GigaChatOptions.FunctionCallMode.CUSTOM_FUNCTION)
                .functionCallParam(
                        FunctionCallParam.builder().name("getCurrentWeather").build())
                .toolCallbacks(GigaTools.from(new WeatherTool()))
                .build());

        ChatResponse response = model.call(new Prompt(List.of(new UserMessage("Привет"))));
        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.getResult().getOutput().getToolCalls().get(0).name())
                .isEqualTo("getCurrentWeather");
    }

    @Test
    @DisplayName("Мультимодальность: media в сообщении загружается и обрабатывается")
    void multimodality_mediaUploadedAndProcessed() {
        Media media = Media.builder()
                // данные media обязаны быть byte[]/Resource (контракт Spring AI Media.getDataAsByteArray)
                .data("В этом файле записано секретное слово: БАГХАНТ."
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .mimeType(MimeTypeUtils.TEXT_PLAIN)
                .name("secret.txt")
                .build();

        Prompt prompt = new Prompt(List.of(UserMessage.builder()
                .text("Какое секретное слово записано в файле?")
                .media(media)
                .build()));

        ChatResponse response = model().call(prompt);
        String text = response.getResult().getOutput().getText();
        log.info("Ответ по файлу: {}", text);
        assertThat(text).isNotBlank();
        // id загруженного медиа попадает в метаданные
        assertThat((Object) response.getMetadata().get(GigaChatModel.UPLOADED_MEDIA_IDS))
                .isNotNull();
    }

    @Test
    @DisplayName("models(): высокоуровневый список моделей")
    void modelsList() {
        assertThat(model().models()).isNotEmpty();
    }

    public static class WeatherTool {
        @GigaTool(name = "getCurrentWeather", description = "Возвращает текущую погоду в указанном городе")
        public String getCurrentWeather(String city) {
            return "В городе " + city + " сейчас 42 градуса и солнечно";
        }
    }
}
