package chat.giga.springai.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
