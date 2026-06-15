### 1. Изменения в GigaChatOptions

Начиная со Spring AI 2.0, модель конфигурации ChatOptions была переработана и переведена на builder-based API.

Если ранее параметры модели могли изменяться через setters:

```java
GigaChatOptions options = new GigaChatOptions();
options.setTemperature(0.7);
options.setMaxTokens(200);
```

то теперь рекомендуется использовать builder:

```java
GigaChatOptions options = GigaChatOptions.builder()
        .temperature(0.7)
        .maxTokens(200)
        .build();
```

Для поддержки нового механизма объединения настроек Spring AI были реализованы методы:

* `mutate()`
* `clone()`
* `combineWith()`

### 2. Изменения в GigaChatModel

В Spring AI 2.0 были удалены утилиты `ModelOptionsUtils.copyToTarget(...)` и `ModelOptionsUtils.merge(...)`.

В связи с этим логика объединения настроек была перенесена на уровень `GigaChatOptions.Builder` и адаптирована под новый механизм Spring AI.

Пользовательских изменений API не требуется.

### 3. Изменения в конфигурации

Конфигурация Chat-модели была вынесена в отдельный класс `GigaChatChatProperties`.

Это позволило:

* сохранить совместимость с механизмом `@ConfigurationProperties`;
* поддержать новый builder-based API Spring AI;
* разделить runtime-настройки модели и Spring Boot конфигурацию.

#### Что нужно сделать

Конфигурация через `chat.options.*` сохранена для обратной совместимости:

**Было:**

```yaml
spring:
  ai:
    gigachat:
      chat:
        options:
          model: GigaChat-2-Max
          temperature: 0.7
          max-tokens: 200
```

**Стало:**

```yaml
spring:
  ai:
    gigachat:
      chat:
        model: GigaChat-2-Max
        temperature: 0.7
        max-tokens: 200
```

