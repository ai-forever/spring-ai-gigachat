package chat.giga.springai.tool.annotation;

import java.lang.annotation.*;

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
