package chat.giga.springai.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;

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

    /**
     * Защищённые границы — проверка контрактов Spring 7 и поведения на «сломанном»/нестандартном
     * {@link RetryTemplate}. Этот класс отвечает на ревью-замечание про сценарий
     * {@code RuntimeException} напрямую из {@code RetryTemplate.execute()} с {@code cause == null}.
     */
    @Nested
    @DisplayName("Защищённые границы и hardening")
    class BrokenDelegateHardening {

        /**
         * Факт из исходников Spring Framework 7.0.6: {@code RetryException(message, cause)}
         * защищён {@code Objects.requireNonNull(cause, ...)}. Это значит, что реальный
         * {@code RetryException} в путь «cause == null» физически не попадёт — NPE
         * выбросится раньше в конструкторе.
         */
        @Test
        @DisplayName("Факт Spring 7: RetryException запрещает null cause в конструкторе")
        void retryExceptionForbidsNullCauseByContract() {
            assertThrows(NullPointerException.class, () -> new RetryException("boom", (Throwable) null));
        }

        /**
         * Факт из исходников Spring 7.0.6: {@code RetryTemplate.execute()} объявлен
         * {@code throws RetryException} и внутри метода — только {@code throw retryException}.
         * Любое исключение из action ловится через {@code catch (Throwable)} и заворачивается.
         * Проверяем, что даже для «голого» {@link RuntimeException} без message/cause дефолтный
         * {@link RetryTemplate} гарантированно оборачивает его в {@link RetryException}, а не
         * пробрасывает напрямую.
         */
        @Test
        @DisplayName("Факт Spring 7: дефолтный RetryTemplate всегда оборачивает RuntimeException в RetryException")
        void defaultRetryTemplateAlwaysWrapsIntoRetryException() {
            RetryTemplate fast = new RetryTemplate();
            fast.setRetryPolicy(RetryPolicy.builder()
                    .maxRetries(0)
                    .delay(Duration.ofMillis(1))
                    .build());

            RetryException thrown = assertThrows(
                    RetryException.class,
                    () -> fast.execute(() -> {
                        throw new RuntimeException(); // без message/cause
                    }));

            assertNotNull(thrown.getCause(), "getCause() защищён Objects.requireNonNull — не null");
            assertEquals(RuntimeException.class, thrown.getCause().getClass());
        }

        /**
         * Ревью-сценарий: если в будущем (или через пользовательский subclass) {@code RetryTemplate}
         * бросит {@code RuntimeException} <b>напрямую</b> и с {@code cause == null}, то текущий
         * fallback {@code new RuntimeException(cause != null ? cause : e)} обернул бы исходный
         * {@code RuntimeException} в ещё один {@code RuntimeException}, потеряв тип.
         *
         * <p>Этот тест симулирует такое поведение через subclass и проверяет, что {@link GigaRetryTemplate}
         * пробрасывает исходное исключение с сохранением типа — т.е. hardening работает.
         */
        @Test
        @DisplayName("Сломанный RetryTemplate, бросающий RuntimeException напрямую (cause=null) — тип сохраняется")
        void directRuntimeExceptionWithNullCauseIsPropagatedByType() {
            RetryTemplate brokenTemplate = new RetryTemplate() {
                @Override
                public <R> R execute(Retryable<R> retryable) {
                    throw new IllegalStateException("direct runtime without cause");
                }
            };
            GigaRetryTemplate wrapper = new GigaRetryTemplate(brokenTemplate);

            IllegalStateException thrown =
                    assertThrows(IllegalStateException.class, () -> wrapper.execute(() -> "never"));

            assertEquals("direct runtime without cause", thrown.getMessage());
            assertNull(thrown.getCause(), "симуляция: cause намеренно null");
        }

        /**
         * Ещё одно проявление того же сценария: прямой {@link NonTransientAiException} (важный для
         * Spring AI тип) с {@code cause == null}. Без hardening мы бы потеряли его тип и клиентский
         * {@code catch(NonTransientAiException)} не сработал бы.
         */
        @Test
        @DisplayName("Прямой NonTransientAiException из сломанного RetryTemplate — ловится по типу")
        void directNonTransientAiExceptionWithNullCausePreservesType() {
            RetryTemplate brokenTemplate = new RetryTemplate() {
                @Override
                public <R> R execute(Retryable<R> retryable) {
                    throw new NonTransientAiException("model not found");
                }
            };
            GigaRetryTemplate wrapper = new GigaRetryTemplate(brokenTemplate);

            NonTransientAiException thrown =
                    assertThrows(NonTransientAiException.class, () -> wrapper.execute(() -> "never"));
            assertEquals("model not found", thrown.getMessage());
        }
    }
}
