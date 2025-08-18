package chat.giga.springai.tool.annotation;

import chat.giga.springai.tool.execution.GigaToolCallResultConverter;
import java.lang.annotation.*;
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
