package chat.giga.springai.support;

import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.util.Assert;

/**
 * Тонкая оболочка над {@link RetryTemplate} из Spring Framework 7, сохраняющая оригинальный тип
 * исключения при распаковке {@code RetryException}.
 *
 * <p>В Spring 7 {@code RetryTemplate.execute()} оборачивает любое исключение из действия в
 * {@code RetryException}. Если просто писать {@code catch (Exception e) {...}} и заворачивать
 * в {@code new RuntimeException(e.getCause())}, теряется конкретный тип исключения (например,
 * {@link org.springframework.ai.retry.NonTransientAiException}), по которому Spring AI различает
 * retriable и non-retriable ошибки.
 *
 * <p>Класс используется только внутри моделей GigaChat и сознательно не объявляется как Spring-бин,
 * чтобы не ломать обратную совместимость: публичные конструкторы моделей по-прежнему принимают
 * {@link RetryTemplate}, а оборачивание происходит внутри.
 */
public final class GigaRetryTemplate {

    private final RetryTemplate delegate;

    public GigaRetryTemplate(final RetryTemplate delegate) {
        Assert.notNull(delegate, "delegate RetryTemplate cannot be null");
        this.delegate = delegate;
    }

    /**
     * Выполняет действие через делегата и распаковывает исходное исключение из
     * {@code RetryException}, сохраняя его тип.
     *
     * @param action действие, которое нужно выполнить с retry
     * @param <T>    тип результата
     * @return результат успешного выполнения {@code action}
     * @throws RuntimeException исходное {@link RuntimeException} из {@code action} (например,
     *                          {@link org.springframework.ai.retry.NonTransientAiException}) или
     *                          новое {@code RuntimeException}, если cause был checked/отсутствует
     */
    public <T> T execute(final Retryable<T> action) {
        try {
            return delegate.execute(action);
        } catch (Exception e) {
            // Штатный путь Spring 7: RetryException с ненулевым cause (Objects.requireNonNull
            // в конструкторе RetryException). Пробрасываем cause как есть, сохраняя тип.
            final Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            // Hardening: если делегат (subclass или будущий Spring) выбросил RuntimeException
            // напрямую — не оборачиваем его в ещё один RuntimeException, сохраняем исходный тип.
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause != null ? cause : e);
        }
    }
}
