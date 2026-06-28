package chat.giga.springai;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.api.chat.param.FunctionCallParam;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;

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
}
