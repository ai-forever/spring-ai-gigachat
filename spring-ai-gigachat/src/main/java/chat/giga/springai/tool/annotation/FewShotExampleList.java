package chat.giga.springai.tool.annotation;

import java.lang.annotation.*;

/**
 * @author Matvey Spiridonov
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface FewShotExampleList {

    /**
     * Tool calling examples
     */
    FewShotExample[] value() default {};
}
