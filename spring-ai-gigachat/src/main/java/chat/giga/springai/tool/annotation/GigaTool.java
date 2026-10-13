/*
 * Copyright 2025 the original author or authors.
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

package chat.giga.springai.tool.annotation;

import chat.giga.springai.tool.execution.GigaToolCallResultConverter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.core.annotation.AliasFor;

/**
 * Marks a method as a tool in Spring AI for GigaChat LLM.<br>
 * GigaChat LLM alternative of ${@link org.springframework.ai.tool.annotation.Tool}.
 *
 * @author Linar Abzaltdinov
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tool
@Inherited
public @interface GigaTool {

    /**
     * The name of the tool. If not provided, the method name will be used.
     */
    @AliasFor(annotation = Tool.class, attribute = "name")
    String name() default "";

    /**
     * The description of the tool. If not provided, the method name will be used.
     */
    @AliasFor(annotation = Tool.class, attribute = "description")
    String description() default "";

    /**
     * Whether the tool result should be returned directly or passed back to the model.
     */
    @AliasFor(annotation = Tool.class, attribute = "returnDirect")
    boolean returnDirect() default false;

    /**
     * The class to use to convert the tool call result to a String.
     */
    @AliasFor(annotation = Tool.class, attribute = "resultConverter")
    Class<? extends ToolCallResultConverter> resultConverter() default GigaToolCallResultConverter.class;

    /**
     * Tool calling examples
     */
    FewShotExample[] fewShotExamples() default {};

    /**
     * Whether to generate 'return_parameters' in function description for request to GigaChat LLM.
     */
    boolean generateOutputSchema() default true;
}
