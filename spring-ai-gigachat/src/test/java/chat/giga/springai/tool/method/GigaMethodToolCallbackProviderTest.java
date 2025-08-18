package chat.giga.springai.tool.method;

import static org.junit.jupiter.api.Assertions.*;

import chat.giga.springai.tool.annotation.GigaTool;
import chat.giga.springai.tool.function.GigaFunctionToolCallback;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.function.FunctionToolCallback;

public class GigaMethodToolCallbackProviderTest {
    @Test
    public void testGetToolCallbacks_withGigaTool() {
        GigaMethodToolCallbackProvider methodToolCallbackProvider = GigaMethodToolCallbackProvider.builder()
                .toolObjects(new TestGigaToolOnly())
                .build();

        ToolCallback[] toolCallbacks = methodToolCallbackProvider.getToolCallbacks();

        assertEquals(1, toolCallbacks.length);
        assertTrue(toolCallbacks[0].getToolMetadata().returnDirect());
    }

    @Test
    @DisplayName("Если метод имеет аннотации @Tool и @GigaTool, то у @Tool приоритет выше")
    public void testGetToolCallbacks_withToolAndGigaTool() {
        GigaMethodToolCallbackProvider methodToolCallbackProvider = GigaMethodToolCallbackProvider.builder()
                .toolObjects(new TestToolAndGigaTool())
                .build();

        ToolCallback[] toolCallbacks = methodToolCallbackProvider.getToolCallbacks();

        assertEquals(1, toolCallbacks.length);
        assertFalse(toolCallbacks[0].getToolMetadata().returnDirect());
    }

    @Test
    @DisplayName("Генерация ToolCallback[] из объекта без аннотаций @Tool/@GigaTool завершается ошибкой")
    public void testCreateToolCallbacks_whenObjectionWithoutToolOrGigaToolAnnotations_expectError() {
        FunctionToolCallback testToolCallback = GigaFunctionToolCallback.builder("test", (String input) -> "test")
                .inputType(String.class)
                .build();

        assertThrows(IllegalStateException.class, () -> GigaMethodToolCallbackProvider.builder()
                .toolObjects(testToolCallback)
                .build());
    }

    private static class TestGigaToolOnly {
        @GigaTool(name = "testTool", returnDirect = true)
        public String testMethod() {
            return "test";
        }
    }

    private static class TestToolAndGigaTool {
        @Tool(name = "testTool")
        @GigaTool(name = "testTool", returnDirect = true)
        public String testMethod() {
            return "test";
        }
    }
}
