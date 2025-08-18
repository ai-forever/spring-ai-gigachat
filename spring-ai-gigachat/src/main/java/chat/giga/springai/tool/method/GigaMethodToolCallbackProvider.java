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

package chat.giga.springai.tool.method;

import chat.giga.springai.tool.definition.GigaToolDefinition;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Implementation of {@link ToolCallbackProvider} for GigaChat LLM.
 *
 * @author Linar Abzaltdinov
 */
@Slf4j
public class GigaMethodToolCallbackProvider implements ToolCallbackProvider {

    private final List<Object> toolObjects;

    private GigaMethodToolCallbackProvider(List<Object> toolObjects) {
        Assert.notNull(toolObjects, "toolObjects cannot be null");
        Assert.noNullElements(toolObjects, "toolObjects cannot contain null elements");
        assertToolAnnotatedMethodsPresent(toolObjects);
        this.toolObjects = toolObjects;
        validateToolCallbacks(getToolCallbacks());
    }

    private void assertToolAnnotatedMethodsPresent(List<Object> toolObjects) {
        for (Object toolObject : toolObjects) {
            List<Method> toolMethods = Stream.of(getDeclaredMethods(toolObject))
                    .filter(this::isToolAnnotatedMethod)
                    .toList();

            if (toolMethods.isEmpty()) {
                throw new IllegalStateException(
                        "No @GigaTool/@Tool annotated methods found in " + toolObject + "."
                                + "Did you mean to pass a ToolCallback or ToolCallbackProvider? If so, you have to use .toolCallbacks() instead of .tools()");
            }
        }
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        var toolCallbacks = toolObjects.stream()
                .map(toolObject -> Stream.of(getDeclaredMethods(toolObject))
                        .filter(this::isToolAnnotatedMethod)
                        .map(toolMethod -> MethodToolCallback.builder()
                                .toolDefinition(GigaToolDefinition.from(toolMethod))
                                .toolMetadata(ToolMetadata.from(toolMethod))
                                .toolMethod(toolMethod)
                                .toolObject(toolObject)
                                .toolCallResultConverter(ToolUtils.getToolCallResultConverter(toolMethod))
                                .build())
                        .toArray(ToolCallback[]::new))
                .flatMap(Stream::of)
                .toArray(ToolCallback[]::new);

        validateToolCallbacks(toolCallbacks);

        return toolCallbacks;
    }

    private Method[] getDeclaredMethods(Object toolObject) {
        return ReflectionUtils.getDeclaredMethods(
                AopUtils.isAopProxy(toolObject) ? AopUtils.getTargetClass(toolObject) : toolObject.getClass());
    }

    private boolean isToolAnnotatedMethod(Method method) {
        Tool annotation = AnnotationUtils.findAnnotation(method, Tool.class);
        return annotation != null && !isFunctionalType(method) && ReflectionUtils.USER_DECLARED_METHODS.matches(method);
    }

    private boolean isFunctionalType(Method toolMethod) {
        var isFunction = ClassUtils.isAssignable(Function.class, toolMethod.getReturnType())
                || ClassUtils.isAssignable(Supplier.class, toolMethod.getReturnType())
                || ClassUtils.isAssignable(Consumer.class, toolMethod.getReturnType());

        if (isFunction) {
            log.warn(
                    "Method {} is annotated with @Tool/@GigaTool, but returns a functional type. "
                            + "This is not supported and the method will be ignored.",
                    toolMethod.getName());
        }

        return isFunction;
    }

    private void validateToolCallbacks(ToolCallback[] toolCallbacks) {
        List<String> duplicateToolNames = ToolUtils.getDuplicateToolNames(toolCallbacks);
        if (!duplicateToolNames.isEmpty()) {
            throw new IllegalStateException("Multiple tools with the same name (%s) found in sources: %s"
                    .formatted(
                            String.join(", ", duplicateToolNames),
                            this.toolObjects.stream()
                                    .map(o -> o.getClass().getName())
                                    .collect(Collectors.joining(", "))));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<Object> toolObjects;

        private Builder() {}

        public Builder toolObjects(Object... toolObjects) {
            Assert.notNull(toolObjects, "toolObjects cannot be null");
            this.toolObjects = Arrays.asList(toolObjects);
            return this;
        }

        public GigaMethodToolCallbackProvider build() {
            return new GigaMethodToolCallbackProvider(toolObjects);
        }
    }
}
