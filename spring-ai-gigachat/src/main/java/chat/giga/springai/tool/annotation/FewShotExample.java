package chat.giga.springai.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Linar Abzaltdinov
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Repeatable(FewShotExampleList.class)
public @interface FewShotExample {
    /**
     * User request
     */
    String request();

    /**
     * JSON representing Tool input params corresponding to request
     */
    String params();
}
