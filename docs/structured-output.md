# Нативный структурированный вывод

GigaChat API поддерживает генерацию JSON по JSON Schema через поле `response_format`.
`GigaChatOptions` реализует `StructuredOutputChatOptions`, поэтому схему можно передать
явно или получить из Java-типа средствами Spring AI.

## Java-тип через ChatClient

```java
public record Order(String item, int quantity, int total) {}

Order order = ChatClient.create(gigaChatModel)
        .prompt()
        .user("В заказе 3 яблока по 20 рублей. Посчитай стоимость.")
        .call()
        .entity(Order.class, spec -> spec.useProviderStructuredOutput());
```

Spring AI строит схему из `Order` и передаёт её через `response_format.schema`.
Для этого режима библиотека устанавливает `type=json_schema` и `strict=true`.
Инструкции о формате не добавляются в промпт.

По умолчанию native structured output выключен. Без `useProviderStructuredOutput()`
и без соответствующего advisor-параметра `.entity(Order.class)` добавляет инструкции
о формате в промпт, как и в стандартном Spring AI.

## Включение через AdvisorParams

Для одного запроса можно использовать тот же параметр, что и в
[документации Spring AI](https://docs.spring.io/spring-ai/reference/api/structured-output/native.html):

```java
import org.springframework.ai.chat.client.AdvisorParams;

Order order = chatClient.prompt()
        .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .user("В заказе 3 яблока по 20 рублей. Посчитай стоимость.")
        .call()
        .entity(Order.class);
```

Чтобы включить режим для всех вызовов `.entity(...)` данного клиента:

```java
@Bean
ChatClient chatClient(ChatClient.Builder builder) {
    return builder
            .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
            .build();
}
```

В этих примерах Spring AI сам генерирует схему из Java-типа. Задавать
`GigaChatOptions` или `response-format` в конфигурации не требуется.
Обычный `.call().content()` без заданной схемы не отправляет `response_format`.

## Проверка схемы и повторные попытки

Штатную проверку Spring AI можно включить вместе с native structured output:

```java
Order order = chatClient.prompt()
        .user("В заказе 3 яблока по 20 рублей. Посчитай стоимость.")
        .call()
        .entity(Order.class, spec -> spec
                .useProviderStructuredOutput()
                .validateSchema());
```

Spring AI проверяет полный ответ по схеме и при ошибке повторяет запрос с описанием
проблемы. При повторных попытках `response_format` сохраняется.
`validateSchema()` применяется к обычному вызову `.call()`, а не к стримингу.
Поддержка конкретных ограничений JSON Schema зависит от GigaChat и выбранной модели.

## Явная схема

```java
String schema = """
        {
          "type": "object",
          "properties": {
            "item": {"type": "string"},
            "quantity": {"type": "integer"},
            "total": {"type": "integer"}
          },
          "required": ["item", "quantity", "total"],
          "additionalProperties": false
        }
        """;

String json = ChatClient.create(gigaChatModel)
        .prompt()
        .user("В заказе 3 яблока по 20 рублей. Посчитай стоимость.")
        .options(GigaChatOptions.builder().outputSchema(schema))
        .call()
        .content();
```

`outputSchema(schema)` включает строгий режим. Чтобы управлять `strict` явно,
используйте `chat.giga.springai.api.chat.completion.ResponseFormat`:

```java
GigaChatOptions options = GigaChatOptions.builder()
        .responseFormat(ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .schema(schema)
                .strict(false)
                .build())
        .build();
```

Схема задаётся строкой с валидным JSON-объектом. В HTTP-запросе она сериализуется как
объект, а не как экранированная строка:

```json
{
  "response_format": {
    "type": "json_schema",
    "schema": {
      "type": "object",
      "properties": {"name": {"type": "string"}},
      "required": ["name"]
    },
    "strict": true
  }
}
```

GigaChat использует поле `schema` непосредственно внутри `response_format`.
Для строгого вывода указывайте `required`; поддерживаемые ограничения схемы описаны
в [документации GigaChat](https://developers.sber.ru/docs/ru/gigachat/guides/structured-output).

## Настройки по умолчанию и переопределение

```yaml
spring:
  ai:
    gigachat:
      chat:
        model: GigaChat-2-Max
        response-format:
          type: json_schema
          strict: true
          schema: |
            {
              "type": "object",
              "properties": {"name": {"type": "string"}},
              "required": ["name"]
            }
```

Формат конкретного запроса заменяет формат по умолчанию целиком. Чтобы получить
обычный текст при настроенной схеме по умолчанию, передайте:

```java
.options(GigaChatOptions.builder()
        .responseFormat(ResponseFormat.builder()
                .type(ResponseFormat.Type.TEXT)
                .build()))
```

Без настроенного формата поле `response_format` не отправляется.

`outputSchema(null)` очищает формат в текущем билдере. При слиянии опций `null`
означает отсутствие переопределения, поэтому для отключения схемы по умолчанию
нужен явный формат с `type=TEXT`.

## Стриминг

Та же настройка работает с `.stream().content()` и `GigaChatModel.stream(...)`.
Фрагменты SSE могут содержать незавершённый JSON; перед разбором в объект нужно
собрать полный ответ:

```java
String json = ChatClient.create(gigaChatModel)
        .prompt()
        .user("В заказе 3 яблока по 20 рублей. Посчитай стоимость.")
        .options(GigaChatOptions.builder().outputSchema(schema))
        .stream()
        .content()
        .reduce("", String::concat)
        .block();
```

При достижении `max_tokens` ответ также может остаться незавершённым: учитывайте
`finish_reason` при обработке результата.

Низкоуровневый `CompletionRequest` тоже принимает `responseFormat(...)`.
Для проверки реального API предусмотрен `GigaChatStructuredOutputIT`: он использует
`GIGACHAT_API_CLIENT_ID`, `GIGACHAT_API_CLIENT_SECRET` и `GIGACHAT_API_SCOPE` из окружения
и проверяет `GigaChat-2-Max` в обычном и потоковом режимах.
