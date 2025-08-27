/*
 * Copyright 2023-2025 the original author or authors.
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

package chat.giga.springai.tool.support;

import chat.giga.springai.tool.annotation.FewShotExample;
import chat.giga.springai.tool.annotation.FewShotExampleList;
import chat.giga.springai.tool.annotation.GigaTool;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Utils for supporting the work of Tool Calling API with GigaChat LLM.
 *
 * @author Linar Abzaltdinov
 */
@UtilityClass
@Slf4j
public final class GigaToolUtils {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .addModules(JacksonUtils.instantiateAvailableModules())
            .build();

    public static chat.giga.springai.tool.definition.FewShotExample[] getFewShotExamples(Method method) {
        Assert.notNull(method, "method cannot be null");
        return getFewShotExampleStream(method)
                .map(fewShotExample -> chat.giga.springai.tool.definition.FewShotExample.builder()
                        .request(fewShotExample.request())
                        .paramsSchema(fewShotExample.params())
                        .build())
                .toArray(chat.giga.springai.tool.definition.FewShotExample[]::new);
    }

    private static Stream<FewShotExample> getFewShotExampleStream(Method method) {
        var tool = AnnotatedElementUtils.findMergedAnnotation(method, Tool.class);
        var gigaTool = AnnotatedElementUtils.findMergedAnnotation(method, GigaTool.class);
        if (tool == null && gigaTool == null) {
            return Stream.empty();
        }
        var exampleList = method.getAnnotation(FewShotExampleList.class);
        var example = method.getAnnotation(FewShotExample.class);
        Stream<FewShotExample> shotExampleStream = Stream.empty();
        if (example != null) shotExampleStream = Stream.of(example);
        if (exampleList != null && exampleList.value() != null)
            shotExampleStream = Stream.concat(shotExampleStream, Arrays.stream(exampleList.value()));
        if (gigaTool != null && gigaTool.fewShotExamples() != null)
            shotExampleStream = Stream.concat(shotExampleStream, Arrays.stream(gigaTool.fewShotExamples()));
        return shotExampleStream;
    }

    /**
     * Generates json schema for @GigTool-annotated method output type.
     * Returns null if the method's output type is String/Enum/primitive/array or @GigaTool not present.
     */
    public static String generateJsonSchemaForMethodOutput(Method method) {
        Assert.notNull(method, "method cannot be null");
        var gigaTool = AnnotatedElementUtils.findMergedAnnotation(method, GigaTool.class);
        if (gigaTool == null || !gigaTool.generateOutputSchema()) {
            return null;
        }
        Class<?> returnType = method.getReturnType();
        return generateJsonSchemaForOutputType(returnType);
    }

    /**
     * Generates json schema for output type.
     * Returns null if type is String, Enum, primitive or array.
     */
    public static String generateJsonSchemaForOutputType(Type type) {
        if (type instanceof Class clazz) {
            var javaType = ClassUtils.resolvePrimitiveIfNecessary(clazz);
            if (javaType == String.class
                    || javaType == Byte.class
                    || javaType == Integer.class
                    || javaType == Short.class
                    || javaType == Long.class
                    || javaType == Double.class
                    || javaType == Float.class
                    || javaType == Boolean.class
                    || javaType == Void.TYPE
                    || javaType.isEnum()
                    || javaType.isArray()) {
                log.info("Skipping schema generation for primitive or array type: {}", type);
                return null;
            }
        }
        return JsonSchemaGenerator.generateForType(type);
    }

    /**
     * Checks if a given JSON string is valid.
     */
    public static boolean isValidJson(String json) {
        Assert.notNull(json, "json cannot be null");
        try {
            OBJECT_MAPPER.readTree(json);
        } catch (JacksonException e) {
            return false;
        }
        return true;
    }

    /**
     * Converts a Java object to a JSON string.
     */
    public static String toJson(@Nullable Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Conversion from Object to JSON failed", ex);
        }
    }

    public static String toJsonIfNeeded(Object param) {
        if (param instanceof String paramStr && GigaToolUtils.isValidJson(paramStr)) {
            return paramStr;
        } else {
            return GigaToolUtils.toJson(param);
        }
    }
}
