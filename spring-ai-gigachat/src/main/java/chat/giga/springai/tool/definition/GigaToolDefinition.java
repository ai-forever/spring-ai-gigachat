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

package chat.giga.springai.tool.definition;

import chat.giga.springai.tool.support.GigaToolUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Implementation of {@link ToolDefinition} for GigaChat LLM.
 *
 * @author Linar Abzaltdinov
 */
public record GigaToolDefinition(
        String name, String description, String inputSchema, String outputSchema, List<FewShotExample> fewShotExamples)
        implements ToolDefinition {

    public GigaToolDefinition {
        Assert.hasText(name, "name cannot be null or empty");
        Assert.hasText(description, "description cannot be null or empty");
        Assert.hasText(inputSchema, "inputSchema cannot be null or empty");
        Assert.noNullElements(fewShotExamples, "fewShotExamples cannot contain null elements");
        if (!StringUtils.hasText(outputSchema)) {
            outputSchema = null;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    static GigaToolDefinition.Builder builder(Method method) {
        Assert.notNull(method, "method cannot be null");
        return GigaToolDefinition.builder()
                .name(GigaToolUtils.getToolName(method))
                .description(GigaToolUtils.getToolDescription(method))
                .inputSchema(JsonSchemaGenerator.generateForMethodInput(method))
                .fewShotExamples(GigaToolUtils.getFewShotExamples(method))
                .outputSchema(GigaToolUtils.generateJsonSchemaForMethodOutput(method)); // надо учесть примитивы?
    }

    public static GigaToolDefinition from(Method method) {
        return GigaToolDefinition.builder(method).build();
    }

    public static class Builder {

        private String name;

        private String description;

        private String inputSchema;
        private String outputSchema;
        private final List<FewShotExample> fewShotExamples = new ArrayList<>();

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(String inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder outputSchema(String outputSchema) {
            this.outputSchema = outputSchema;
            return this;
        }

        public Builder fewShotExample(FewShotExample fewShotExample) {
            this.fewShotExamples.add(fewShotExample);
            return this;
        }

        public Builder fewShotExamples(FewShotExample... fewShotExamples) {
            Stream.of(fewShotExamples).forEach(this::fewShotExample);
            return this;
        }

        public Builder fewShotExamples(List<FewShotExample> fewShotExamples) {
            this.fewShotExamples.addAll(fewShotExamples);
            return this;
        }

        public GigaToolDefinition build() {
            if (!StringUtils.hasText(description)) {
                description = ToolUtils.getToolDescriptionFromName(name);
            }
            return new GigaToolDefinition(name, description, inputSchema, outputSchema, fewShotExamples);
        }
    }
}
