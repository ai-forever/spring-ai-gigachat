package chat.giga.springai.tool.support;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import chat.giga.springai.tool.annotation.FewShotExample;
import chat.giga.springai.tool.annotation.FewShotExampleList;
import chat.giga.springai.tool.annotation.GigaTool;
import java.lang.reflect.Method;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.core.annotation.AnnotatedElementUtils;

@ExtendWith(MockitoExtension.class)
public class GigaToolUtilsTest {

    @Mock
    private Method method;

    private MockedStatic<AnnotatedElementUtils> annotatedElementUtilsMock;

    @BeforeEach
    public void setUp() {
        Mockito.reset(method);
        annotatedElementUtilsMock = mockStatic(AnnotatedElementUtils.class);
    }

    @AfterEach
    public void tearDown() {
        annotatedElementUtilsMock.close();
    }

    @Test
    public void testGenerateJsonSchemaForMethodOutput_withoutAnnotations() {
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(GigaTool.class)))
                .thenReturn(null);
        String jsonSchema = GigaToolUtils.generateJsonSchemaForMethodOutput(method);

        assertNull(jsonSchema);
    }

    @Test
    public void testGenerateJsonSchemaForMethodOutput_withGigaToolAnnotation_generationDisabled() {
        GigaTool gigaToolAnnotation = Mockito.mock(GigaTool.class);
        Mockito.when(gigaToolAnnotation.generateOutputSchema()).thenReturn(false);
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(GigaTool.class)))
                .thenReturn(gigaToolAnnotation);
        String jsonSchema = GigaToolUtils.generateJsonSchemaForMethodOutput(method);

        assertNull(jsonSchema);
    }

    record TestRecord(String testField) {}

    @Test
    public void testGenerateJsonSchemaForMethodOutput_withGigaToolAnnotation_generationEnabled() {
        GigaTool gigaToolAnnotation = Mockito.mock(GigaTool.class);
        Mockito.when(gigaToolAnnotation.generateOutputSchema()).thenReturn(true);
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(GigaTool.class)))
                .thenReturn(gigaToolAnnotation);
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
    public void testGetFewShotExample_withoutAnnotations() {
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(Tool.class)))
                .thenReturn(null);
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(GigaTool.class)))
                .thenReturn(null);

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
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(Tool.class)))
                .thenReturn(null);
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(GigaTool.class)))
                .thenReturn(gigaToolAnnotation);
        when(method.getAnnotation(FewShotExampleList.class)).thenReturn(fewShotExampleIntefaceList);

        var fewShotExamples = GigaToolUtils.getFewShotExamples(method);

        assertEquals(2, fewShotExamples.length);
        assertEquals("request", fewShotExamples[0].getRequest());
        assertEquals("{}", fewShotExamples[0].getParams());
    }

    @Test
    public void testGetFewShotExample_withToolAnnotation_ListEmpty() {
        Tool toolAnnotation = Mockito.mock(Tool.class);
        FewShotExample fewShotExample = Mockito.mock(FewShotExample.class);
        FewShotExampleList fewShotExampleList = Mockito.mock(FewShotExampleList.class);
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(Tool.class)))
                .thenReturn(toolAnnotation);
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(GigaTool.class)))
                .thenReturn(null);
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
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(Tool.class)))
                .thenReturn(toolAnnotation);
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(GigaTool.class)))
                .thenReturn(null);
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
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(Tool.class)))
                .thenReturn(toolAnnotation);
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(GigaTool.class)))
                .thenReturn(null);
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
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(Tool.class)))
                .thenReturn(toolAnnotation);
        annotatedElementUtilsMock
                .when(() -> AnnotatedElementUtils.findMergedAnnotation(eq(method), eq(GigaTool.class)))
                .thenReturn(null);
        when(method.getAnnotation(FewShotExample.class)).thenReturn(fewShotExample);
        when(method.getAnnotation(FewShotExampleList.class)).thenReturn(fewShotExampleList);
        when(fewShotExampleList.value()).thenReturn(new FewShotExample[] {fewShotExample});

        chat.giga.springai.tool.definition.FewShotExample[] result = GigaToolUtils.getFewShotExamples(method);

        assertEquals(2, result.length);
    }
}
