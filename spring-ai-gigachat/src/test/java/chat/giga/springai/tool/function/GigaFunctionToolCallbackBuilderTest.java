package chat.giga.springai.tool.function;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

class GigaFunctionToolCallbackBuilderTest {

    @Test
    @DisplayName("#26: outputType(null) сообщает про outputType, а не про inputType")
    void outputTypeNull_reportsOutputType() {
        Supplier<String> supplier = () -> "ok";

        assertThatThrownBy(() -> GigaFunctionToolCallback.builder("tool", supplier)
                        .outputType((ParameterizedTypeReference<?>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outputType");
    }
}
