package chat.giga.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chat.giga.springai.api.chat.param.FunctionCallParam;
import chat.giga.springai.tool.function.GigaFunctionToolCallback;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Тест ключевого инварианта миграции PATH A: в Spring AI 2.0 GA слияние runtime+default опций
 * выполняет ChatClient через {@code modelDefaults.mutate().combineWith(runtime.mutate())}
 * (см. DefaultChatClientUtils). Здесь воспроизводится ровно эта цепочка и проверяется, что
 * НИ ОДИН заявленный параметр не теряется: базовые поля и tool-поля сливает родитель,
 * GigaChat-специфичные — {@link GigaChatOptions.Builder#combineWith}.
 */
class GigaChatOptionsMergeTest {

    @Test
    @DisplayName("PATH A: combineWith накладывает runtime на defaults, сохраняя все заявленные поля")
    void combineWith_runtimeOverridesDefaults_keepsAllDeclaredFields() {
        GigaChatOptions defaults = GigaChatOptions.builder()
                .model("default-model")
                .temperature(0.1)
                .topP(0.2)
                .maxTokens(100)
                .repetitionPenalty(1.1)
                .updateInterval(0.5)
                .profanityCheck(false)
                .functionCallMode(GigaChatOptions.FunctionCallMode.AUTO)
                .httpHeaders(Map.of("X-Default", "d"))
                .toolContext(Map.of("tenant", "acme"))
                .build();

        // runtime задаёт часть полей — они должны победить; не заданные берутся из defaults
        GigaChatOptions runtime = GigaChatOptions.builder()
                .model("runtime-model")
                .temperature(0.9)
                .profanityCheck(true)
                .functionCallMode(GigaChatOptions.FunctionCallMode.CUSTOM_FUNCTION)
                .functionCallParam(FunctionCallParam.builder().name("doIt").build())
                .httpHeaders(Map.of("X-Runtime", "r"))
                .build();

        // ровно то, что делает ChatClient в GA
        ChatOptions.Builder<?> builder = defaults.mutate();
        builder.combineWith(runtime.mutate());
        GigaChatOptions merged = (GigaChatOptions) builder.build();

        // переопределённые runtime поля
        assertThat(merged.getModel()).isEqualTo("runtime-model");
        assertThat(merged.getTemperature()).isEqualTo(0.9);
        assertThat(merged.getProfanityCheck()).isTrue();
        assertThat(merged.getFunctionCallMode()).isEqualTo(GigaChatOptions.FunctionCallMode.CUSTOM_FUNCTION);
        assertThat(merged.getFunctionCallParam()).isNotNull();
        assertThat(merged.getFunctionCallParam().getName()).isEqualTo("doIt");

        // не заданные в runtime — взяты из defaults (не потеряны)
        assertThat(merged.getTopP()).isEqualTo(0.2);
        assertThat(merged.getMaxTokens()).isEqualTo(100);
        assertThat(merged.getRepetitionPenalty()).isEqualTo(1.1);
        assertThat(merged.getUpdateInterval()).isEqualTo(0.5);
        assertThat(merged.getToolContext()).containsEntry("tenant", "acme");

        // httpHeaders сливаются (merge), а не затираются
        assertThat(merged.getHttpHeaders()).containsEntry("X-Default", "d").containsEntry("X-Runtime", "r");
    }

    @Test
    @DisplayName("httpHeaders: контракт ревью — @Nullable по умолчанию, merge-сеттер, overload, immutable результат")
    void httpHeaders_nullableImmutableMergeContract() {
        // (1) по умолчанию null, а не пустая мапа (контракт как у toolContext)
        assertThat(GigaChatOptions.builder().build().getHttpHeaders()).isNull();

        // (2) сеттер мержит, а не заменяет; (3) overload httpHeaders(key,value) докидывает
        GigaChatOptions options = GigaChatOptions.builder()
                .httpHeaders(Map.of("a", "1"))
                .httpHeaders(Map.of("b", "2"))
                .httpHeaders("c", "3")
                .build();
        assertThat(options.getHttpHeaders())
                .containsEntry("a", "1")
                .containsEntry("b", "2")
                .containsEntry("c", "3");

        // (4) ядро замечания #3: результат геттера immutable — .put() обязан падать
        assertThatThrownBy(() -> options.getHttpHeaders().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static ToolCallback toolCallback(String name) {
        return GigaFunctionToolCallback.builder(name, (String in) -> "ok")
                .inputType(String.class)
                .build();
    }

    /**
     * Регресс по замечанию ревью: коерсия {@code null -> new ArrayList<>()} в build() нарушала
     * {@code @Nullable}-контракт {@code getToolCallbacks()} — после mutate()/build() пустой объект
     * терял null и возвращал пустой список ("звоночек"). Проверяем, что:
     * (1) null переживает round-trip mutate()/build() и остаётся null;
     * (2) при combineWith непустой список из 2 элементов НЕ теряется, когда вторая сторона "пустая".
     */
    @Test
    @DisplayName("combineWith: дословный сценарий ревью — round-trip null + слияние не теряет toolCallbacks")
    void combineWith_toolCallbacks_reviewScenario() {
        // шаг 1: исходный объект, у которого toolCallbacks == null
        GigaChatOptions options = GigaChatOptions.builder().build();
        assertThat(options.getToolCallbacks())
                .as("по умолчанию null (@Nullable-контракт)")
                .isNull();

        // шаг 2: options = options.mutate().build() — на старом коде здесь null превращался в пустой
        // список ("звоночек" из ревью); теперь обязан остаться null. Этот ассерт упал бы на старом коде.
        options = options.mutate().build();
        assertThat(options.getToolCallbacks())
                .as("после mutate()/build() null остаётся null, а не пустым списком")
                .isNull();

        // шаг 3: объект с двумя инструментами
        ToolCallback t1 = toolCallback("t1");
        ToolCallback t2 = toolCallback("t2");
        GigaChatOptions optionsWithToolCallbacks =
                GigaChatOptions.builder().toolCallbacks(List.of(t1, t2)).build();

        // шаг 4: optionsWithToolCallbacks.mutate().combineWith(options.mutate()).build() — ожидаем 2 элемента
        GigaChatOptions merged = (GigaChatOptions)
                optionsWithToolCallbacks.mutate().combineWith(options.mutate()).build();
        assertThat(merged.getToolCallbacks())
                .as("слияние с round-trip'нутой null-стороной сохраняет оба инструмента")
                .containsExactly(t1, t2);

        // обратный порядок слияния — тоже сохраняет 2 элемента
        GigaChatOptions mergedReverse = (GigaChatOptions)
                options.mutate().combineWith(optionsWithToolCallbacks.mutate()).build();
        assertThat(mergedReverse.getToolCallbacks()).containsExactly(t1, t2);
    }

    /** То же замечание для toolContext (ревьюер: «та же история») — дословный сценарий. */
    @Test
    @DisplayName("combineWith: дословный сценарий ревью — round-trip null + слияние не теряет toolContext")
    void combineWith_toolContext_reviewScenario() {
        GigaChatOptions options = GigaChatOptions.builder().build();
        assertThat(options.getToolContext()).isNull();

        options = options.mutate().build();
        assertThat(options.getToolContext())
                .as("после mutate()/build() null остаётся null")
                .isNull();

        GigaChatOptions optionsWithToolContext = GigaChatOptions.builder()
                .toolContext(Map.of("k1", "v1", "k2", "v2"))
                .build();

        GigaChatOptions merged = (GigaChatOptions)
                optionsWithToolContext.mutate().combineWith(options.mutate()).build();
        assertThat(merged.getToolContext()).containsEntry("k1", "v1").containsEntry("k2", "v2");

        GigaChatOptions mergedReverse = (GigaChatOptions)
                options.mutate().combineWith(optionsWithToolContext.mutate()).build();
        assertThat(mergedReverse.getToolContext()).containsEntry("k1", "v1").containsEntry("k2", "v2");
    }
}
