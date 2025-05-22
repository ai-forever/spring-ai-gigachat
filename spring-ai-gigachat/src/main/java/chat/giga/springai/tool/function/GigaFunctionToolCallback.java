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

package chat.giga.springai.tool.function;

import chat.giga.springai.tool.definition.FewShotExample;
import chat.giga.springai.tool.definition.GigaToolDefinition;
import chat.giga.springai.tool.execution.GigaToolCallResultConverter;
import chat.giga.springai.tool.support.GigaToolUtils;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@UtilityClass
public class GigaFunctionToolCallback<I, O> {
    private static final ToolCallResultConverter DEFAULT_RESULT_CONVERTER = new GigaToolCallResultConverter();
    /**
     * Build a {@link GigaFunctionToolCallback} from a {@link BiFunction}.
     */
    public static <I, O> Builder<I, O> builder(String name, BiFunction<I, ToolContext, O> function) {
        return new Builder<>(name, function);
    }

    /**
     * Build a {@link GigaFunctionToolCallback} from a {@link Function}.
     */
    public static <I, O> Builder<I, O> builder(String name, Function<I, O> function) {
        Assert.notNull(function, "function cannot be null");
        return new Builder<>(name, (request, context) -> function.apply(request));
    }

    /**
     * Build a {@link GigaFunctionToolCallback} from a {@link Supplier}.
     */
    public static <O> Builder<Void, O> builder(String name, Supplier<O> supplier) {
        Assert.notNull(supplier, "supplier cannot be null");
        Function<Void, O> function = input -> supplier.get();
        return builder(name, function).inputType(Void.class);
    }

    /**
     * Build a {@link GigaFunctionToolCallback} from a {@link Consumer}.
     */
    public static <I> Builder<I, Void> builder(String name, Consumer<I> consumer) {
        Assert.notNull(consumer, "consumer cannot be null");
        Function<I, Void> function = (I input) -> {
            consumer.accept(input);
            return null;
        };
        return builder(name, function);
    }

    public static class Builder<I, O> {

        private String name;

        private String description;

        private String inputSchema;

        private Type inputType;

        private ToolMetadata toolMetadata;

        private BiFunction<I, ToolContext, O> toolFunction;

        private ToolCallResultConverter toolCallResultConverter;

        private String outputSchema;

        private Type outputType;

        private List<FewShotExample> fewShotExamples = new ArrayList<>();

        private Builder(String name, BiFunction<I, ToolContext, O> toolFunction) {
            Assert.hasText(name, "name cannot be null or empty");
            Assert.notNull(toolFunction, "toolFunction cannot be null");
            this.name = name;
            this.toolFunction = toolFunction;
        }

        public Builder<I, O> description(String description) {
            this.description = description;
            return this;
        }

        public Builder<I, O> inputSchema(String inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder<I, O> inputType(Type inputType) {
            this.inputType = inputType;
            return this;
        }

        public Builder<I, O> inputType(ParameterizedTypeReference<?> inputType) {
            Assert.notNull(inputType, "inputType cannot be null");
            this.inputType = inputType.getType();
            return this;
        }

        public Builder<I, O> toolMetadata(ToolMetadata toolMetadata) {
            this.toolMetadata = toolMetadata;
            return this;
        }

        public Builder<I, O> toolCallResultConverter(ToolCallResultConverter toolCallResultConverter) {
            this.toolCallResultConverter = toolCallResultConverter;
            return this;
        }

        public Builder<I, O> outputSchema(String outputSchema) {
            this.outputSchema = outputSchema;
            return this;
        }

        public Builder<I, O> outputType(Type outputType) {
            this.outputType = outputType;
            return this;
        }

        public Builder<I, O> outputType(ParameterizedTypeReference<?> outputType) {
            Assert.notNull(outputType, "inputType cannot be null");
            this.outputType = outputType.getType();
            return this;
        }

        public Builder<I, O> fewShotExample(FewShotExample fewShotExample) {
            Assert.notNull(fewShotExample, "fewShotExample cannot be null");
            this.fewShotExamples.add(fewShotExample);
            return this;
        }

        public Builder<I, O> fewShotExamples(List<FewShotExample> fewShotExamples) {
            Assert.noNullElements(fewShotExamples, "fewShotExamples cannot contain null elements");
            this.fewShotExamples.addAll(fewShotExamples);
            return this;
        }

        public FunctionToolCallback<I, O> build() {
            Assert.notNull(inputType, "inputType cannot be null");
            var toolDefinitionBuilder = GigaToolDefinition.builder()
                    .name(name)
                    .description(
                            StringUtils.hasText(description) ? description : ToolUtils.getToolDescriptionFromName(name))
                    .inputSchema(
                            StringUtils.hasText(inputSchema)
                                    ? inputSchema
                                    : JsonSchemaGenerator.generateForType(inputType));
            fewShotExamples.forEach(toolDefinitionBuilder::fewShotExample);
            if (StringUtils.hasText(outputSchema)) {
                toolDefinitionBuilder.outputSchema(outputSchema);
            } else if (outputType != null) {
                toolDefinitionBuilder.outputSchema(GigaToolUtils.generateJsonSchemaForOutputType(outputType));
            }
            return new FunctionToolCallback<>(
                    toolDefinitionBuilder.build(),
                    toolMetadata,
                    inputType,
                    toolFunction,
                    toolCallResultConverter != null ? toolCallResultConverter : DEFAULT_RESULT_CONVERTER);
        }
    }
}
