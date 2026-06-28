# Миграция spring-ai-gigachat 1.1.x → 2.x.x

Версия 2.x.x переведена на **Spring AI 2.0.0 (GA)**. Документ описывает все изменения,
затрагивающие пользователей.

## Требования

|  Компонент  | 1.1.x |   2.x.x   |
|-------------|-------|-----------|
| Spring AI   | 1.1.x | **2.0.0** |
| Spring Boot | 3.5.x | **4.0.x** |
| Java        | 17    | **21**    |

> ⚠️ 2.x требует **Java 21** (компиляция с `release 21`) и **Spring Boot 4.0.x** — это
> отдельный крупный апгрейд платформы помимо самого Spring AI.
>
> 🔴 **Главное изменение** — исполнение инструментов (function calling) больше не выполняется
> внутри `GigaChatModel`, а вынесено на уровень `ChatClient` (см. раздел 4). Если вы вызываете
> `ChatClient`, всё работает автоматически. Если вызываете `GigaChatModel` напрямую и полагались
> на автоматическое исполнение инструментов — поведение изменилось.

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

#### Удалённые элементы API

Опции теперь иммутабельны (только через builder). Удалены:

|                               Удалено в 2.x                               |                                    Чем заменить                                    |
|---------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| сеттеры (`setTemperature(...)` и т.п.)                                    | `GigaChatOptions.builder()...build()` / `options.mutate()...build()`               |
| `toBuilder()`                                                             | `mutate()`                                                                         |
| `getToolNames()` / `toolNames(...)`                                       | `toolCallbacks(...)` (Spring AI 2.0 убрал `toolNames` из `ToolCallingChatOptions`) |
| `getInternalToolExecutionEnabled()` / `internalToolExecutionEnabled(...)` | исполнение инструментов управляется на уровне `ChatClient` (раздел 4)              |

> Поля `toolNames` и `internalToolExecutionEnabled` удалены не нами, а самим Spring AI 2.0 GA
> из интерфейса `ToolCallingChatOptions`. В опциях остались `toolCallbacks` и `toolContext`.

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

> ⚠️ **Breaking change.** Новый путь `spring.ai.gigachat.chat.*` и legacy-путь
> `spring.ai.gigachat.chat.options.*` пишут в одни и те же поля. Не задавайте один и тот же
> параметр одновременно через новый и legacy-ключ — при одновременном задании итоговое значение
> зависит от порядка биндинга Spring Boot. Legacy-ключи помечены как `@Deprecated(forRemoval = true)`
> и будут удалены в следующем мажорном релизе; переходите на плоский `spring.ai.gigachat.chat.*`.

### 4. Исполнение инструментов (function calling) — поведение изменилось

В 1.1.x модель исполняла инструменты сама: внутри `GigaChatModel` крутился цикл
`ToolCallingManager.executeToolCalls(...)` с предикатом `ToolExecutionEligibilityPredicate`,
после чего выполнялся повторный запрос к API.

В Spring AI 2.0 GA эта ответственность вынесена из модели в `ChatClient`: при создании
`ChatClient` автоматически регистрируется `ToolCallingAdvisor`, который и исполняет инструменты.
`GigaChatModel` теперь делает один запрос и возвращает ответ модели как есть (включая запрос на
вызов инструмента, без его исполнения).

**Если вы используете `ChatClient` — менять ничего не нужно**, инструменты исполняются автоматически:

```java
String answer = ChatClient.create(gigaChatModel)
        .prompt("Какая погода в Сочи?")
        .tools(new WeatherTools())
        .call()
        .content();
```

**Если вы вызываете `GigaChatModel.call(...)`/`stream(...)` напрямую** и полагались на то, что модель
сама исполнит инструмент, — теперь вы получите ответ с `finishReason = function_call` и запросом на
вызов, но **без исполнения**. Перейдите на `ChatClient` (рекомендуется) либо обрабатывайте вызовы
инструментов самостоятельно через `ToolCallingManager`.

Сопутствующие удаления:

* из `GigaChatModel.Builder` удалён `toolExecutionEligibilityPredicate(...)`;
* из автоконфигурации удалён бин `ToolExecutionEligibilityPredicate`;
* из опций удалены `toolNames` и `internalToolExecutionEnabled` (раздел 1).

### 5. Изменения в эмбеддингах

|                  Было (1.1.x)                  |                                   Стало (2.x)                                    |
|------------------------------------------------|----------------------------------------------------------------------------------|
| `spring.ai.gigachat.embedding.enabled: false`  | `spring.ai.model.embedding: none` (как у других стартеров Spring AI)             |
| `spring.ai.gigachat.embedding.embeddings-path` | удалено (было неработающим; путь `/embeddings` фиксирован относительно base-url) |
| `spring.ai.gigachat.embedding.metadata-mode`   | сохранено и **теперь реально применяется** (через `getEmbeddingContent`)         |

Конструктор `GigaChatEmbeddingModel` получил необязательный параметр `MetadataMode`; прежний
4-аргументный конструктор сохранён для обратной совместимости.

> 🔧 **Исправление поведения.** В метаданных ответа `usage.totalTokens` для эмбеддингов больше
> не удваивается (раньше `getCompletionTokens()` ошибочно возвращал `promptTokens`). Для батча
> токены теперь суммируются по всем входам, а не берутся только от первого.

### 6. Автоконфигурация: независимый выбор моделей

В 1.1.x класс автоконфигурации был целиком завязан на `spring.ai.model.chat=gigachat`, поэтому
выбор другого чат-провайдера (`spring.ai.model.chat=openai`) отключал заодно эмбеддинги и генерацию
изображений GigaChat.

В 2.x каждый тип модели выбирается независимо — как принято в Spring AI:

```yaml
spring:
  ai:
    model:
      chat: gigachat       # spring.ai.model.chat
      embedding: gigachat  # spring.ai.model.embedding
      image: gigachat      # spring.ai.model.image
```

Любой ключ можно выставить в `none`, чтобы отключить только соответствующую модель, не затрагивая
остальные.

### 7. Потоковая генерация: 400x и 500x ошибки теперь обрабатываются нормально

В 1.1.x ошибочный HTTP-ответ (4xx/5xx) при стриминге (`stream(...)`) мог приводить к молча пустому
потоку. В 2.x такие ответы пробрасываются как `WebClientResponseException` — обрабатывайте их в
`doOnError`/`onErrorResume`, как у остальных реактивных клиентов Spring AI.

### 8. Новые возможности (не breaking)

* `GigaChatApi.getFiles()` — `GET /files` (список загруженных файлов);
* `GigaChatApi.getFile(fileId)` — `GET /files/{id}` (метаданные одного файла);
* поле ответа `reasoning_content` (рассуждения reasoning-моделей) пробрасывается в метаданные
  генерации под ключом `reasoningContent`.

### 9. Переименование артефакта Spring AI для RAG (если используете QuestionAnswerAdvisor)

Это изменение самого Spring AI 2.0.0 GA, но оно затрагивает RAG-пример: артефакт
`spring-ai-advisors-vector-store` переименован в `spring-ai-vector-store-advisor`. Класс и пакет
`org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor` не изменились —
поменять нужно только координаты зависимости:

```xml
<!-- было -->
<artifactId>spring-ai-advisors-vector-store</artifactId>
<!-- стало -->
<artifactId>spring-ai-vector-store-advisor</artifactId>
```

