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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class GigaToolTest {

    @Test
    void testFewExampleAsCollection() throws NoSuchMethodException {
        Method method = TestClass.class.getDeclaredMethod("testMethod1");

        FewShotExampleList annotationsContainer = method.getAnnotation(FewShotExampleList.class);

        // Проверка, что контейнерная аннотация существует
        assertNotNull(annotationsContainer, "Контейнерная аннотация отсутствует");

        // Проверка количества отдельных аннотаций внутри контейнера
        FewShotExample[] individualAnnotations = annotationsContainer.value();
        assertEquals(3, individualAnnotations.length, "Количество аннотаций не соответствует");

        List<String> expectedRequests = Arrays.asList("request1", "request2", "request3");
        List<String> expectedParams = Arrays.asList("param1", "param2", "param3");

        // Проверка значений каждой отдельной аннотации
        for (int i = 0; i < individualAnnotations.length; i++) {
            assertEquals(
                    expectedRequests.get(i),
                    individualAnnotations[i].request(),
                    String.format("Запрос не совпадает! [%s]", i));
            assertEquals(
                    expectedParams.get(i),
                    individualAnnotations[i].params(),
                    String.format("Параметр не совпадает! [%s]", i));
        }
    }

    @Test
    void testFewExampleAsCollectionWithTool() throws NoSuchMethodException {
        Method method = TestClass.class.getDeclaredMethod("testMethod2");
        FewShotExample annotation = method.getAnnotation(FewShotExample.class);

        GigaTool gigaTool = method.getAnnotation(GigaTool.class);

        // Проверка, что контейнерная аннотация существует
        assertNotNull(annotation, "Контейнерная аннотация отсутствует");

        assertEquals("description", gigaTool.description(), "Неверный description");
    }
}
