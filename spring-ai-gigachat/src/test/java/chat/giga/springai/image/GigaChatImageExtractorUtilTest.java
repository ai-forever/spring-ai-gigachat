package chat.giga.springai.image;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GigaChatImageExtractorUtilTest {
    @ParameterizedTest
    @MethodSource("smokeTestParameters")
    @DisplayName("Тест проверяет извлечение mediaId из ответного сообщения агента")
    void smokeTest(String responseContent, List<String> expectedMediaIds) {
        List<String> actualMediaIds = GigaChatImageExtractorUtil.extract(responseContent);
        Assertions.assertEquals(expectedMediaIds, actualMediaIds);
    }

    public static Stream<Arguments> smokeTestParameters() {
        return Stream.of(
                Arguments.of(
                        "Привет <img src=\"c2cac967-e8d3-4851-a93c-649e086b1856\" fuse=\"true\"/> также приложил визуализацию текста «Привет».",
                        List.of("c2cac967-e8d3-4851-a93c-649e086b1856")),
                Arguments.of(
                        "Привет <img src=\"e5f8ce06-9742-48b9-b7f4-85e92acea7aa\" fuse=\"true\"/> <img src=\"2011ccb8-b54d-4647-bd34-6b57d5df90cb\" fuse=\"true\"/> Тест",
                        List.of("e5f8ce06-9742-48b9-b7f4-85e92acea7aa", "2011ccb8-b54d-4647-bd34-6b57d5df90cb")),
                Arguments.of("Привет, это проверка без изображений", List.of()));
    }
}
