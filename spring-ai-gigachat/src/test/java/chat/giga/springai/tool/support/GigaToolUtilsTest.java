package chat.giga.springai.tool.support;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import chat.giga.springai.tool.annotation.FewShotExample;
import chat.giga.springai.tool.annotation.FewShotExampleList;
import chat.giga.springai.tool.annotation.GigaTool;
import chat.giga.springai.tool.execution.GigaToolCallResultConverter;
import java.lang.reflect.Method;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

@ExtendWith(MockitoExtension.class)
public class GigaToolUtilsTest {

    @Mock
    private Method method;

    @BeforeEach
    void setUp() {
        Mockito.reset(method);
    }

    @Test
    public void testGetToolName_withoutAnnotations() {
        when(method.getAnnotation(Tool.class)).thenReturn(null);
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);
        when(method.getName()).thenReturn("testMethod");

        String toolName = GigaToolUtils.getToolName(method);

        assertEquals("testMethod", toolName);
    }

    @Test
    public void testGetToolName_withToolAnnotation() {
        Tool toolAnnotation = Mockito.mock(Tool.class);
        Mockito.when(toolAnnotation.name()).thenReturn("toolName");
        when(method.getAnnotation(Tool.class)).thenReturn(toolAnnotation);
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);

        String toolName = GigaToolUtils.getToolName(method);

        assertEquals("toolName", toolName);
    }

    @Test
    public void testGetToolName_withGigaToolAnnotation() {
        GigaTool gigaToolAnnotation = Mockito.mock(GigaTool.class);
        Mockito.when(gigaToolAnnotation.name()).thenReturn("gigaToolName");
        when(method.getAnnotation(Tool.class)).thenReturn(null);
        when(method.getAnnotation(GigaTool.class)).thenReturn(gigaToolAnnotation);

        String toolName = GigaToolUtils.getToolName(method);

        assertEquals("gigaToolName", toolName);
    }

    @Test
    public void testGetToolDescription_withoutAnnotations() {
        when(method.getAnnotation(Tool.class)).thenReturn(null);
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);
        when(method.getName()).thenReturn("testMethod");

        String toolDescription = GigaToolUtils.getToolDescription(method);

        assertEquals("test method", toolDescription);
    }

    @Test
    public void testGetToolDescription_withToolAnnotation() {
        Tool toolAnnotation = Mockito.mock(Tool.class);
        Mockito.when(toolAnnotation.description()).thenReturn("toolDescription");
        when(method.getAnnotation(Tool.class)).thenReturn(toolAnnotation);
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);

        String toolDescription = GigaToolUtils.getToolDescription(method);

        assertEquals("toolDescription", toolDescription);
    }

    @Test
    public void testGetToolDescription_withGigaToolAnnotation() {
        GigaTool gigaToolAnnotation = Mockito.mock(GigaTool.class);
        Mockito.when(gigaToolAnnotation.description()).thenReturn("gigaToolDescription");
        when(method.getAnnotation(Tool.class)).thenReturn(null);
        when(method.getAnnotation(GigaTool.class)).thenReturn(gigaToolAnnotation);

        String toolDescription = GigaToolUtils.getToolDescription(method);

        assertEquals("gigaToolDescription", toolDescription);
    }

    @Test
    public void testGetToolReturnDirect_withoutAnnotations() {
        when(method.getAnnotation(Tool.class)).thenReturn(null);
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);

        boolean toolReturnDirect = GigaToolUtils.getToolReturnDirect(method);

        assertFalse(toolReturnDirect);
    }

    @Test
    public void testGetToolReturnDirect_withToolAnnotation() {
        Tool toolAnnotation = Mockito.mock(Tool.class);
        Mockito.when(toolAnnotation.returnDirect()).thenReturn(true);
        when(method.getAnnotation(Tool.class)).thenReturn(toolAnnotation);
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);

        boolean toolReturnDirect = GigaToolUtils.getToolReturnDirect(method);

        assertTrue(toolReturnDirect);
    }

    @Test
    public void testGetToolReturnDirect_withGigaToolAnnotation() {
        GigaTool gigaToolAnnotation = Mockito.mock(GigaTool.class);
        Mockito.when(gigaToolAnnotation.returnDirect()).thenReturn(true);
        when(method.getAnnotation(Tool.class)).thenReturn(null);
        when(method.getAnnotation(GigaTool.class)).thenReturn(gigaToolAnnotation);

        boolean toolReturnDirect = GigaToolUtils.getToolReturnDirect(method);

        assertTrue(toolReturnDirect);
    }

    @Test
    public void testGetToolCallResultConverter_withoutAnnotation() {
        when(method.getAnnotation(Tool.class)).thenReturn(null);
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);

        ToolCallResultConverter toolCallResultConverter = GigaToolUtils.getToolCallResultConverter(method);

        assertInstanceOf(GigaToolCallResultConverter.class, toolCallResultConverter);
    }

    @Test
    public void testGetToolCallResultConverter_withToolAnnotation() {
        Tool toolAnnotation = Mockito.mock(Tool.class);
        Mockito.when(toolAnnotation.resultConverter()).thenAnswer(invocation -> GigaToolCallResultConverter.class);
        when(method.getAnnotation(Tool.class)).thenReturn(toolAnnotation);
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);

        ToolCallResultConverter toolCallResultConverter = GigaToolUtils.getToolCallResultConverter(method);

        assertInstanceOf(GigaToolCallResultConverter.class, toolCallResultConverter);
    }

    @Test
    public void testGetToolCallResultConverter_withGigaToolAnnotation() {
        GigaTool gigaToolAnnotation = Mockito.mock(GigaTool.class);
        Mockito.when(gigaToolAnnotation.resultConverter())
                .thenAnswer(invocation -> DefaultToolCallResultConverter.class);
        when(method.getAnnotation(Tool.class)).thenReturn(null);
        when(method.getAnnotation(GigaTool.class)).thenReturn(gigaToolAnnotation);

        ToolCallResultConverter toolCallResultConverter = GigaToolUtils.getToolCallResultConverter(method);

        assertInstanceOf(DefaultToolCallResultConverter.class, toolCallResultConverter);
    }

    @Test
    public void testGetFewShotExample_withoutAnnotations() {
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);

        var fewShotExamples = GigaToolUtils.getFewShotExamples(method);

        assertEquals(0, fewShotExamples.length);
    }

    @Test
    public void testGetFewShotExample_withGigaToolAnnotation() {
        var fewShotExampleAnnotation = Mockito.mock(FewShotExample.class);
        when(fewShotExampleAnnotation.request()).thenReturn("request");
        when(fewShotExampleAnnotation.params()).thenReturn("{}");
        GigaTool gigaToolAnnotation = Mockito.mock(GigaTool.class);
        FewShotExampleList fewShotExampleIntefaceList = Mockito.mock(FewShotExampleList.class);
        when(fewShotExampleIntefaceList.value()).thenReturn(new FewShotExample[] {fewShotExampleAnnotation});
        when(gigaToolAnnotation.fewShotExamples()).thenReturn(new FewShotExample[] {fewShotExampleAnnotation});
        when(method.getAnnotation(GigaTool.class)).thenReturn(gigaToolAnnotation);
        when(method.getAnnotation(Tool.class)).thenReturn(null);
        when(method.getAnnotation(FewShotExampleList.class)).thenReturn(fewShotExampleIntefaceList);

        var fewShotExamples = GigaToolUtils.getFewShotExamples(method);

        assertEquals(2, fewShotExamples.length);
        assertEquals("request", fewShotExamples[0].getRequest());
        assertEquals("{}", fewShotExamples[0].getParams());
    }

    @Test
    public void testGenerateJsonSchemaForMethodOutput_withoutAnnotations() {
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);

        String jsonSchema = GigaToolUtils.generateJsonSchemaForMethodOutput(method);

        assertNull(jsonSchema);
    }

    @Test
    public void testGenerateJsonSchemaForMethodOutput_withGigaToolAnnotation_generationDisabled() {
        GigaTool gigaToolAnnotation = Mockito.mock(GigaTool.class);
        Mockito.when(gigaToolAnnotation.generateOutputSchema()).thenReturn(false);
        when(method.getAnnotation(GigaTool.class)).thenReturn(gigaToolAnnotation);

        String jsonSchema = GigaToolUtils.generateJsonSchemaForMethodOutput(method);

        assertNull(jsonSchema);
    }

    record TestRecord(String testField) {}

    @Test
    public void testGenerateJsonSchemaForMethodOutput_withGigaToolAnnotation_generationEnabled() {
        GigaTool gigaToolAnnotation = Mockito.mock(GigaTool.class);
        Mockito.when(gigaToolAnnotation.generateOutputSchema()).thenReturn(true);
        when(method.getAnnotation(GigaTool.class)).thenReturn(gigaToolAnnotation);
        when(method.getReturnType()).thenAnswer(invocation -> TestRecord.class);

        String jsonSchema = GigaToolUtils.generateJsonSchemaForMethodOutput(method);

        assertTrue(StringUtils.isNotBlank(jsonSchema));
    }

    @Test
    public void testGenerateJsonSchemaForOutputType_whenString() {
        Class<?> type = String.class;

        String jsonSchema = GigaToolUtils.generateJsonSchemaForOutputType(type);

        assertNull(jsonSchema);
    }

    @Test
    public void testGenerateJsonSchemaForOutputType_whenPojo() {
        Class<?> type = TestRecord.class;

        String jsonSchema = GigaToolUtils.generateJsonSchemaForOutputType(type);

        assertTrue(StringUtils.isNotBlank(jsonSchema));
        assertThat(jsonSchema, Matchers.containsString("\"testField\""));
    }

    @Test
    public void testGenerateJsonSchemaForOutputType_whenPrimitive() {
        Class<?> type = int.class;

        String jsonSchema = GigaToolUtils.generateJsonSchemaForOutputType(type);

        assertNull(jsonSchema);
    }

    @Test
    public void testGenerateJsonSchemaForOutputType_whenArray() {
        Class<?> type = Boolean[].class;

        String jsonSchema = GigaToolUtils.generateJsonSchemaForOutputType(type);

        assertNull(jsonSchema);
    }

    @Test
    public void testGenerateJsonSchemaForOutputType_whenVoid() {
        Class<?> type = Void.TYPE;

        String jsonSchema = GigaToolUtils.generateJsonSchemaForOutputType(type);

        assertNull(jsonSchema);
    }

    @Test
    public void testIsValidJson() {
        String json = "{\"key\":\"value\"}";

        boolean isValid = GigaToolUtils.isValidJson(json);

        assertTrue(isValid);
    }

    @Test
    public void testToJson() {
        TestRecord object = new TestRecord("test");

        String json = GigaToolUtils.toJson(object);

        assertTrue(StringUtils.isNotBlank(json));
        assertThat(json, Matchers.containsString("\"test\""));
    }

    @Test
    public void testToJsonIfNeeded_whenParamIsJson() {
        String param = "{\"key\":\"value\"}";

        String json = GigaToolUtils.toJsonIfNeeded(param);

        assertEquals(param, json);
    }

    @Test
    public void testToJsonIfNeeded_whenParamIsPojo() {
        TestRecord param = new TestRecord("test");

        String json = GigaToolUtils.toJsonIfNeeded(param);

        assertTrue(StringUtils.isNotBlank(json));
        assertThat(json, Matchers.containsString("\"test\""));
    }

    @Test
    public void testGetFewShotExample_withToolAnnotation_ListEmpty() {
        Tool toolAnnotation = Mockito.mock(Tool.class);
        FewShotExample fewShotExample = Mockito.mock(FewShotExample.class);
        FewShotExampleList fewShotExampleList = Mockito.mock(FewShotExampleList.class);
        when(method.getAnnotation(Tool.class)).thenReturn(toolAnnotation);
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);
        when(method.getAnnotation(FewShotExample.class)).thenReturn(fewShotExample);
        when(method.getAnnotation(FewShotExampleList.class)).thenReturn(fewShotExampleList);
        when(fewShotExampleList.value()).thenReturn(null);

        chat.giga.springai.tool.definition.FewShotExample[] result = GigaToolUtils.getFewShotExamples(method);

        assertEquals(1, result.length);
    }

    @Test
    public void testGetFewShotExample_withToolAnnotation_ListIsNull() {
        Tool toolAnnotation = Mockito.mock(Tool.class);
        FewShotExample fewShotExample = Mockito.mock(FewShotExample.class);
        when(method.getAnnotation(Tool.class)).thenReturn(toolAnnotation);
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);
        when(method.getAnnotation(FewShotExample.class)).thenReturn(fewShotExample);
        when(method.getAnnotation(FewShotExampleList.class)).thenReturn(null);

        chat.giga.springai.tool.definition.FewShotExample[] result = GigaToolUtils.getFewShotExamples(method);

        assertEquals(1, result.length);
    }

    @Test
    public void testGetFewShotExamples_withToolAnnotation_ExampleNull() {
        Tool toolAnnotation = Mockito.mock(Tool.class);
        FewShotExample fewShotExample = Mockito.mock(FewShotExample.class);
        FewShotExampleList fewShotExampleList = Mockito.mock(FewShotExampleList.class);
        when(method.getAnnotation(Tool.class)).thenReturn(toolAnnotation);
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);
        when(method.getAnnotation(FewShotExample.class)).thenReturn(null);
        when(method.getAnnotation(FewShotExampleList.class)).thenReturn(fewShotExampleList);
        when(fewShotExampleList.value()).thenReturn(new FewShotExample[] {fewShotExample});

        chat.giga.springai.tool.definition.FewShotExample[] result = GigaToolUtils.getFewShotExamples(method);

        assertEquals(1, result.length);
    }

    @Test
    public void testGetFewShotExamples_withToolAnnotation_ExampleNotNull() {
        Tool toolAnnotation = Mockito.mock(Tool.class);
        FewShotExample fewShotExample = Mockito.mock(FewShotExample.class);
        FewShotExampleList fewShotExampleList = Mockito.mock(FewShotExampleList.class);
        when(method.getAnnotation(Tool.class)).thenReturn(toolAnnotation);
        when(method.getAnnotation(GigaTool.class)).thenReturn(null);
        when(method.getAnnotation(FewShotExample.class)).thenReturn(fewShotExample);
        when(method.getAnnotation(FewShotExampleList.class)).thenReturn(fewShotExampleList);
        when(fewShotExampleList.value()).thenReturn(new FewShotExample[] {fewShotExample});

        chat.giga.springai.tool.definition.FewShotExample[] result = GigaToolUtils.getFewShotExamples(method);

        assertEquals(2, result.length);
    }
}
