package chat.giga.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import chat.giga.springai.api.GigaChatInternalProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

/**
 * GH-120: юнит-тесты на телеметрию {@link GigaChatModel} через {@link TestObservationRegistry} —
 * без реального обращения к GigaChat API. Имя observation ({@code gen_ai.client.operation}) и
 * имена тегов взяты из ядра ({@link ChatModelObservationDocumentation}, {@link
 * AiObservationAttributes}), а не угаданы — см. также {@code GigaChatImageObservationIT} с тем же
 * паттерном для image-модели.
 */
@ExtendWith(MockitoExtension.class)
class GigaChatModelObservationTest {

    private static final String OBSERVATION_NAME = "gen_ai.client.operation";
    private static final String TAG_REQUEST_STREAM = "gen_ai.request.stream";
    private static final String TAG_CACHE_READ = "gen_ai.usage.cache_read.input_tokens";
    private static final String TAG_CACHE_WRITE = "gen_ai.usage.cache_creation.input_tokens";

    @Mock
    private GigaChatApi gigaChatApi;

    @Mock
    private GigaChatInternalProperties gigaChatInternalProperties;

    private TestObservationRegistry observationRegistry;
    private GigaChatModel gigaChatModel;

    @BeforeEach
    void setUp() {
        observationRegistry = TestObservationRegistry.create();
        gigaChatModel = GigaChatModel.builder()
                .gigaChatApi(gigaChatApi)
                .internalProperties(gigaChatInternalProperties)
                .observationRegistry(observationRegistry)
                .build();
    }

    private Prompt prompt() {
        return new Prompt(
                List.of(new UserMessage("Hello")),
                GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                        .build());
    }

    private CompletionResponse completionResponse(CompletionResponse.Usage usage) {
        var message = new CompletionResponse.MessagesRes()
                .setRole(CompletionResponse.Role.assistant)
                .setContent("ответ");
        var choice = new CompletionResponse.Choice()
                .setMessage(message)
                .setIndex(0)
                .setFinishReason(CompletionResponse.FinishReason.STOP);
        return new CompletionResponse()
                .setId(UUID.randomUUID().toString())
                .setModel("GigaChat-2")
                .setChoices(List.of(choice))
                .setUsage(usage);
    }

    private static CompletionResponse.Usage usage(int prompt, int completion, Integer precached) {
        var usage = new CompletionResponse.Usage()
                .setPromptTokens(prompt)
                .setCompletionTokens(completion)
                .setTotalTokens(prompt + completion);
        usage.setPrecachedPromptTokens(precached);
        return usage;
    }

    @Test
    @DisplayName("stream(): observation несёт high-cardinality тег gen_ai.request.stream=true")
    void stream_hasRequestStreamTag() {
        var streamChunk = new CompletionResponse()
                .setId(UUID.randomUUID().toString())
                .setModel("GigaChat-2")
                .setChoices(List.of(new CompletionResponse.Choice()
                        .setIndex(0)
                        .setFinishReason(CompletionResponse.FinishReason.STOP)
                        .setDelta(new CompletionResponse.MessagesRes()
                                .setRole(CompletionResponse.Role.assistant)
                                .setContent("ответ"))));
        when(gigaChatApi.chatCompletionStream(any(), any())).thenReturn(Flux.just(streamChunk));

        gigaChatModel.stream(prompt()).blockLast();

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo(OBSERVATION_NAME)
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasHighCardinalityKeyValue(TAG_REQUEST_STREAM, "true");
    }

    @Test
    @DisplayName("call(): тега gen_ai.request.stream нет — ядро эмитит его только при streaming=true")
    void call_hasNoRequestStreamTag() {
        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(completionResponse(usage(10, 5, null)), HttpStatusCode.valueOf(200)));

        gigaChatModel.call(prompt());

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo(OBSERVATION_NAME)
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .doesNotHaveHighCardinalityKeyValueWithKey(TAG_REQUEST_STREAM);
    }

    @Test
    @DisplayName("call() с precached_prompt_tokens>0: тег cache_read есть со значением, cache_write нет")
    void call_withPrecached_hasCacheReadTagOnly() {
        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(completionResponse(usage(10, 5, 7)), HttpStatusCode.valueOf(200)));

        gigaChatModel.call(prompt());

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo(OBSERVATION_NAME)
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasHighCardinalityKeyValue(TAG_CACHE_READ, "7")
                // cacheWriteInputTokens в GigaChatModel.getDefaultUsage() всегда null — GigaChat не
                // возвращает отдельный счётчик записи в кэш, поэтому тег не эмитится вовсе.
                .doesNotHaveHighCardinalityKeyValueWithKey(TAG_CACHE_WRITE);
    }

    @Test
    @DisplayName("call() без precached_prompt_tokens: тега cache_read нет")
    void call_withoutPrecached_hasNoCacheReadTag() {
        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(completionResponse(usage(10, 5, null)), HttpStatusCode.valueOf(200)));

        gigaChatModel.call(prompt());

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo(OBSERVATION_NAME)
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .doesNotHaveHighCardinalityKeyValueWithKey(TAG_CACHE_READ);
    }

    @Test
    @DisplayName("sanity: gen_ai.operation.name, gen_ai.request.model и provider-тег присутствуют")
    void call_hasCoreLowCardinalityTags() {
        when(gigaChatApi.chatCompletionEntity(any(), any()))
                .thenReturn(new ResponseEntity<>(completionResponse(usage(10, 5, null)), HttpStatusCode.valueOf(200)));

        gigaChatModel.call(prompt());

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo(OBSERVATION_NAME)
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue(AiObservationAttributes.AI_OPERATION_TYPE.value(), "chat")
                .hasLowCardinalityKeyValue(AiObservationAttributes.AI_PROVIDER.value(), GigaChatApi.PROVIDER_NAME)
                .hasLowCardinalityKeyValue(
                        AiObservationAttributes.REQUEST_MODEL.value(), GigaChatApi.ChatModel.GIGA_CHAT_2.getName());
    }

    /**
     * Каждый чанк потока несёт собственный {@code Usage} с precached_prompt_tokens. Проверяем два
     * факта, а не один: (1) каждый отдельный {@link ChatResponse}, дошедший до подписчика, несёт
     * cacheReadInputTokens именно своего чанка — {@link GigaChatModel#internalStream} не суммирует
     * его с предыдущими чанками ТОГО ЖЕ стрима (аккумулирует только с {@code previousChatResponse}
     * извне, а он тут null); (2) итоговая, полностью остановленная observation — НЕ несёт тег
     * cache_read вовсе. Это не баг GigaChatModel: {@code MessageAggregator} (ядро spring-ai)
     * строит финальный агрегированный usage через собственный record с тремя полями
     * (prompt/completion/total, без cache-полей) — и берёт значения от последнего чанка с
     * ненулевым counter'ом (перезапись, а не суммирование), и именно этот агрегат попадает в
     * observationContext к моменту остановки observation. cache_read/cache_write в нём нет вовсе
     * (default {@code Usage.getCacheReadInputTokens()} == null). Тег отражает лишь то, что
     * прилетело реально — этот тест фиксирует факт, а не ожидание.
     */
    @Test
    @DisplayName(
            "streaming: per-chunk cacheReadInputTokens корректен, но в финальной observation тега cache_read нет (MessageAggregator теряет cache-поля)")
    void stream_multipleChunksWithPrecached_perChunkUsageCorrect_finalObservationHasNoCacheTag() {
        var chunk1 = new CompletionResponse()
                .setId(UUID.randomUUID().toString())
                .setModel("GigaChat-2")
                .setUsage(usage(1, 1, 5))
                .setChoices(List.of(new CompletionResponse.Choice()
                        .setIndex(0)
                        .setDelta(new CompletionResponse.MessagesRes()
                                .setRole(CompletionResponse.Role.assistant)
                                .setContent("Привет"))));
        var chunk2 = new CompletionResponse()
                .setId(UUID.randomUUID().toString())
                .setModel("GigaChat-2")
                .setUsage(usage(1, 2, 7))
                .setChoices(List.of(new CompletionResponse.Choice()
                        .setIndex(0)
                        .setFinishReason(CompletionResponse.FinishReason.STOP)
                        .setDelta(new CompletionResponse.MessagesRes()
                                .setRole(CompletionResponse.Role.assistant)
                                .setContent(", мир"))));
        when(gigaChatApi.chatCompletionStream(any(), any())).thenReturn(Flux.just(chunk1, chunk2));

        List<ChatResponse> chunks = gigaChatModel.stream(prompt()).collectList().block();

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata().getUsage().getCacheReadInputTokens())
                .isEqualTo(5L);
        assertThat(chunks.get(1).getMetadata().getUsage().getCacheReadInputTokens())
                .isEqualTo(7L);

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo(OBSERVATION_NAME)
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .doesNotHaveHighCardinalityKeyValueWithKey(TAG_CACHE_READ)
                // MessageAggregator берёт значения последнего чанка с ненулевым counter'ом
                // (перезапись, не сумма) — итог соответствует usage(1, 2, 7) из chunk2
                .hasHighCardinalityKeyValue(AiObservationAttributes.USAGE_INPUT_TOKENS.value(), "1")
                .hasHighCardinalityKeyValue(AiObservationAttributes.USAGE_OUTPUT_TOKENS.value(), "2")
                .hasHighCardinalityKeyValue(AiObservationAttributes.USAGE_TOTAL_TOKENS.value(), "3");
    }
}
