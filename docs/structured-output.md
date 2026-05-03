## Structured Output (Структурированный вывод)

Structured Output позволяет получать ответы от GigaChat в строго определённом формате,
автоматически преобразуя их в Java-объекты.

Spring AI GigaChat поддерживает два режима structured output:

| Режим                | Механизм             | Надёжность | Описание                                                        |
|----------------------|----------------------|------------|-----------------------------------------------------------------|
| **Virtual Function** | Function Calling     | Высокая    | Использует виртуальную функцию для гарантированного JSON-ответа |
| **Prompt Based**     | Инструкции в промпте | Средняя    | Добавляет инструкции форматирования в промпт                    |

### Virtual Function Structured Output (рекомендуется)

Этот режим использует механизм function calling GigaChat для гарантированного
получения ответа в заданном формате. Под капотом создаётся виртуальная функция
с JSON Schema, и модель вызывает её с данными в качестве аргументов.

#### Включение Virtual Function Structured Output

**Глобально (рекомендуется):**

```java

@Bean
ChatClient chatClient(ChatClient.Builder builder) {
    return builder
            .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
            .build();
}
```

**Per-request:**

```java
ActorFilms result = chatClient
        .prompt("Какие фильмы снял Тарантино?")
        .advisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
        .call()
        .entity(ActorFilms.class);
```

#### Примеры использования

**Простой record:**

```java
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

record ActorFilms(
        @JsonPropertyDescription("Имя актёра или режиссёра") String actor,
        @JsonPropertyDescription("Список названий фильмов") List<String> movies) {
}

ActorFilms result = chatClient
        .prompt("Назови 5 фильмов Стивена Спилберга")
        .call()
        .entity(ActorFilms.class);

// result.actor() = "Стивен Спилберг"
// result.movies() = ["Список Шиндлера", "Индиана Джонс", "Парк Юрского периода", ...]
```

**Вложенные структуры:**

```java
record MovieDetails(
        @JsonPropertyDescription("Название фильма") String title,
        @JsonPropertyDescription("Год выхода фильма") int year,
        @JsonPropertyDescription("Имя режиссёра фильма") String director) {
}

record DirectorInfo(
        @JsonPropertyDescription("Имя режиссёра") String name,
        @JsonPropertyDescription("Список фильмов режиссёра") List<MovieDetails> filmography) {
}

DirectorInfo result = chatClient
        .prompt("Расскажи о 3 фильмах Кристофера Нолана с годами выхода")
        .call()
        .entity(DirectorInfo.class);

// result.name() = "Кристофер Нолан"
// result.filmography() = [MovieDetails[title=Начало, year=2010, ...], ...]
```

**С enum:**

```java
enum Sentiment {POSITIVE, NEGATIVE, NEUTRAL}

record SentimentAnalysis(
        @JsonPropertyDescription("Анализируемый текст") String text,
        @JsonPropertyDescription("Определённая тональность текста") Sentiment sentiment,
        @JsonPropertyDescription("Уверенность в оценке от 0 до 1") double confidence) {
}

SentimentAnalysis result = chatClient
        .prompt("Проанализируй тональность: 'Это был отличный день!'")
        .call()
        .entity(SentimentAnalysis.class);

// result.sentiment() = POSITIVE
// result.confidence() = 0.8
```

**Списки объектов:**

```java
record Country(
        @JsonPropertyDescription("Название страны") String name,
        @JsonPropertyDescription("Столица страны") String capital,
        @JsonPropertyDescription("Население страны") long population) {
}

record CountryList(
        @JsonPropertyDescription("Список стран") List<Country> countries) {
}

CountryList result = chatClient
        .prompt("Назови 3 европейские страны с их столицами и населением")
        .call()
        .entity(CountryList.class);

// result.countries() = [Country[name=Франция, capital=Париж, population=67900000], ...]
```

#### Комбинация с Tool Calling

Virtual Function Structured Output можно использовать совместно с вызовом функций.
Модель сначала вызовет нужные функции для получения данных, а затем вернёт
структурированный ответ:

```java
// Определяем функцию для получения погоды
record WeatherRequest(@JsonPropertyDescription("Название города") String city) {
}

record WeatherResponse(String city, int temperature, String condition) {
}

Function<WeatherRequest, WeatherResponse> weatherFunction = request ->
        new WeatherResponse(request.city(), 22, "Солнечно");

ToolCallback weatherTool = FunctionToolCallback.builder("getWeather", weatherFunction)
        .description("Получить текущую погоду в городе")
        .inputType(WeatherRequest.class)
        .build();

// Создаём клиент с функцией и virtual function structured output
ChatClient chatClient = builder
        .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
        .defaultToolCallbacks(weatherTool)
        .build();

record TravelAdvice(
        @JsonPropertyDescription("Название города") String city,
        @JsonPropertyDescription("Описание текущей погоды") String weather,
        @JsonPropertyDescription("Рекомендация для путешественника") String recommendation) {
}

TravelAdvice result = chatClient
        .prompt("Какая погода в Москве? Дай совет для путешественника.")
        .call()
        .entity(TravelAdvice.class);

// Модель вызовет getWeather, получит данные и вернёт структурированный TravelAdvice
```

**Цепочка вызовов нескольких функций (Chain Tool Calling):**

Модель может последовательно вызвать несколько функций для сбора данных из разных источников,
а затем агрегировать результаты в структурированный ответ:

```java
// Функция для проверки погоды
record WeatherRequest(@JsonPropertyDescription("Название города") String city) {
}

record WeatherResponse(String city, int temperature, String condition) {
}

Function<WeatherRequest, WeatherResponse> weatherFunction = request ->
        new WeatherResponse(request.city(), 18, "Облачно");

ToolCallback weatherTool = FunctionToolCallback.builder("checkWeather", weatherFunction)
        .description("Проверить погоду в городе для планирования доставки")
        .inputType(WeatherRequest.class)
        .build();

// Функция для проверки остатков на складе
record StockRequest(@JsonPropertyDescription("Код товара") String itemCode) {
}

record StockInfo(String itemCode, int quantity, String requestId) {
}

Function<StockRequest, StockInfo> stockFunction = request ->
        new StockInfo(request.itemCode(), 42, "REQ-" + System.currentTimeMillis());

ToolCallback stockTool = FunctionToolCallback.builder("checkStock", stockFunction)
        .description("Проверить наличие товара на складе")
        .inputType(StockRequest.class)
        .build();

// Структура для агрегированного результата
record DeliveryPlan(
        @JsonPropertyDescription("Город доставки") String city,
        @JsonPropertyDescription("Погодные условия") String weatherConditions,
        @JsonPropertyDescription("Количество товара для отправки") int itemsToShip,
        @JsonPropertyDescription("Идентификатор для отслеживания") String trackingInfo,
        @JsonPropertyDescription("Рекомендации по доставке") String deliveryNotes) {
}

ChatClient chatClient = builder
        .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
        .defaultToolCallbacks(weatherTool, stockTool)
        .build();

DeliveryPlan result = chatClient
        .prompt("Планирую доставку в Санкт-Петербург. Проверь погоду и наличие товара SKU-789 на складе.")
        .call()
        .entity(DeliveryPlan.class);

// Модель:
// 1. Вызовет checkWeather для Санкт-Петербурга
// 2. Вызовет checkStock для SKU-789
// 3. Агрегирует данные в структурированный DeliveryPlan
```

### Как это работает под капотом

При включённом Virtual Function Structured Output:

1. Spring AI генерирует JSON Schema из целевого класса через `BeanOutputConverter`
2. GigaChat стартер создаёт виртуальную функцию `_structured_output_function`
3. Schema передаётся как `parameters` функции
4. Устанавливается `function_call: {"name": "_structured_output_function"}`
5. Модель вызывает функцию с данными в `arguments`
6. Ответ извлекается и парсится в целевой объект

Пример запроса к API:

```json
{
  "model": "GigaChat",
  "messages": [
    {
      "role": "user",
      "content": "Назови 3 фильма Тарантино"
    }
  ],
  "functions": [
    {
      "name": "_structured_output_function",
      "description": "Формирует структурированный ответ на основе диалога. Сначала вызови ВСЕ необходимые функции для получения данных, затем вызови эту функцию с данными ответа. Эта функция должна быть вызвана ПОСЛЕДНЕЙ.",
      "parameters": {
        "type": "object",
        "properties": {
          "actor": {
            "type": "string",
            "description": "Имя актёра или режиссёра"
          },
          "movies": {
            "type": "array",
            "items": {
              "type": "string"
            },
            "description": "Список названий фильмов"
          }
        },
        "required": [
          "actor",
          "movies"
        ]
      }
    }
  ],
  "function_call": {
    "name": "_structured_output_function"
  }
}
```

### Prompt-based Structured Output

Если не включать `VIRTUAL_FUNCTION_STRUCTURED_OUTPUT`, Spring AI будет использовать
prompt-based подход через `BeanOutputConverter`. В этом режиме инструкции по
форматированию добавляются в конец промпта.

```java
// Без advisor - используется prompt-based подход
ActorFilms result = chatClient
    .prompt("Назови 5 фильмов Тарантино")
    .call()
    .entity(ActorFilms.class);
```

#### Как это работает под капотом

При использовании Prompt-based Structured Output:

1. Spring AI генерирует JSON Schema из целевого класса через `BeanOutputConverter`
2. К пользовательскому промпту добавляются инструкции форматирования
3. Модель получает текстовые указания вернуть ответ в JSON формате
4. Ответ парсится в целевой объект

Пример запроса к API:

```json
{
  "model": "GigaChat",
  "messages": [
    {
      "role": "user",
      "content": "<см. ниже>"
    }
  ]
}
```

Содержимое `content`:

```text
Назови 5 фильмов Тарантино

Your response should be in JSON format.
Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
Do not include markdown code blocks in your response.
Remove the ```json markdown from the output.

Here is the JSON Schema instance your output must adhere to:
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "actor" : {
      "type" : "string",
      "description" : "Имя актёра или режиссёра"
    },
    "movies" : {
      "description" : "Список названий фильмов",
      "type" : "array",
      "items" : {
        "type" : "string"
      }
    }
  },
  "required" : [ "actor", "movies" ],
  "additionalProperties" : false
}
```

Этот режим менее надёжен, так как модель может не следовать текстовым инструкциям.

### Сравнение режимов

| Аспект        | Virtual Function                     | Prompt         |
|---------------|--------------------------------------|----------------|
| Надёжность    | Высокая                              | Средняя        |
| Запросы к API | 1 запрос                             | 1 запрос       |
| Стоимость     | Сопоставима                          | Сопоставима    |
| Скорость      | Сопоставима                          | Сопоставима    |
| Совместимость | Модели с поддержкой function calling | Все модели     |
| Применение    | Production                           | Простые случаи |

### Рекомендации

1. **Используйте Virtual Function режим** для production-систем, где важен точный формат ответа
2. **Используйте `@JsonPropertyDescription`** для описания полей — модель лучше понимает контекст и заполняет данные
   корректнее
3. **Используйте `@JsonProperty(required = true)`** для обязательных полей в records
4. **Избегайте слишком сложных вложенных структур** — это может повлиять на качество генерации
5. **Тестируйте с реальными данными** — поведение модели может варьироваться
6. **Для экономии** рассмотрите Prompt-based режим, если допустима меньшая надёжность формата
7. **Streaming не поддерживается** — используйте только blocking вызов `.call().entity(Class)`, метод `.stream()` не
   имеет `entity()`.
   Это ограничение Spring AI, а не GigaChat — `StreamResponseSpec` не предоставляет метод `entity()` ни для одного
   провайдера.
   В будущих версиях Spring AI планируется добавить поддержку streaming для structured output.