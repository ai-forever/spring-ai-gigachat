package chat.giga.springai.support;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

/**
 * Проверяет, что {@link GigaRetryTemplate#execute} распаковывает оригинальное исключение из
 * {@code RetryException}, не теряя его тип. Это критично для Spring AI: по типу исключения
 * различаются retriable/non-retriable ошибки.
 */
class GigaRetryTemplateTest {

    private final GigaRetryTemplate retryTemplate = new GigaRetryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE);

    @Nested
    @DisplayName("Сохранение типа исключения")
    class ExceptionTypePreservation {

        @Test
        @DisplayName("NonTransientAiException пробрасывается как есть")
        void nonTransientAiExceptionPreserved() {
            NonTransientAiException thrown = assertThrows(
                    NonTransientAiException.class,
                    () -> retryTemplate.execute(() -> {
                        throw new NonTransientAiException("API error: model not found");
                    }));

            assertEquals("API error: model not found", thrown.getMessage());
        }

        @Test
        @DisplayName("Вызывающий код может поймать NonTransientAiException напрямую")
        void callerCanCatchConcreteType() {
            boolean caughtCorrectType = false;
            try {
                retryTemplate.execute(() -> {
                    throw new NonTransientAiException("API error");
                });
            } catch (NonTransientAiException ignored) {
                caughtCorrectType = true;
            } catch (Exception e) {
                fail("Ожидали NonTransientAiException, получили " + e.getClass().getName());
            }

            assertTrue(caughtCorrectType, "catch(NonTransientAiException) должен сработать");
        }

        @Test
        @DisplayName("IllegalArgumentException (не AI) тоже сохраняет тип")
        void arbitraryRuntimeExceptionPreserved() {
            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> retryTemplate.execute(() -> {
                        throw new IllegalArgumentException("bad arg");
                    }));

            assertEquals("bad arg", thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("Успешное выполнение")
    class SuccessfulExecution {

        @Test
        @DisplayName("Результат действия возвращается без изменений")
        void returnsResult() {
            String result = retryTemplate.execute(() -> "ok");
            assertEquals("ok", result);
        }
    }

    @Nested
    @DisplayName("TransientAiException ретраится и финально пробрасывается с исходным типом")
    class TransientRetry {

        /**
         * Быстрый RetryTemplate без backoff — чтобы не зависеть от дефолтного политики Spring AI,
         * которая использует экспоненциальный backoff в минутах и делает тест на ретраи непригодным
         * для юнит-тестирования.
         */
        private GigaRetryTemplate fastRetryTemplate() {
            RetryTemplate template = new RetryTemplate();
            template.setRetryPolicy(RetryPolicy.builder()
                    .maxRetries(2)
                    .delay(Duration.ofMillis(1))
                    .build());
            return new GigaRetryTemplate(template);
        }

        @Test
        @DisplayName("После исчерпания ретраев — пробрасывается TransientAiException, не RuntimeException-обёртка")
        void transientAiExceptionPreservedAfterRetryExhaustion() {
            AtomicInteger attempts = new AtomicInteger(0);
            GigaRetryTemplate fast = fastRetryTemplate();

            TransientAiException thrown = assertThrows(
                    TransientAiException.class,
                    () -> fast.execute(() -> {
                        attempts.incrementAndGet();
                        throw new TransientAiException("temporary outage");
                    }));

            assertEquals("temporary outage", thrown.getMessage());
            // 1 первая попытка + 2 ретрая = 3 вызова
            assertEquals(3, attempts.get(), "должно быть ровно 3 вызова: 1 исходный + 2 ретрая");
        }

        @Test
        @DisplayName("Успешная попытка после двух сбоев — результат возвращается без исключения")
        void recoveryAfterTransientFailures() {
            AtomicInteger attempts = new AtomicInteger(0);
            GigaRetryTemplate fast = fastRetryTemplate();

            String result = fast.execute(() -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new TransientAiException("try again");
                }
                return "recovered";
            });

            assertEquals("recovered", result);
            assertEquals(3, attempts.get());
        }
    }

    @Nested
    @DisplayName("Валидация аргументов конструктора")
    class ConstructorValidation {

        @Test
        @DisplayName("null delegate — IllegalArgumentException")
        void nullDelegateRejected() {
            assertThrows(IllegalArgumentException.class, () -> new GigaRetryTemplate(null));
        }
    }
}
