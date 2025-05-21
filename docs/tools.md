### Вызов пользовательских функций

Для вызова внешних функций пользуйтесь официальной документацией
Spring AI - https://docs.spring.io/spring-ai/reference/api/tools.html.

Весь функционал Spring AI работает с GigaChat.

Однако, если вы хотите использовать больше возможностей GigaChat
(например, параметры `few_shot_examples` и `return_parameters`),
которых нет в Spring AI из коробки, то можно использовать следующие альтернативы:
* `@Tool` -> `@GigaTool`
* * **Важно** В Spring AI разрешена конструкция `chatClient.prompt("").tools(new ToolsService())`.
Если используете `@GigaTool`-ы, то ссылайтесь на них через `.toolCallbacks(GigaTools.from(...))`, т.е. :
`chatClient.prompt("").toolCallbacks(GigaTools.from(new ToolsService()))`
* `ToolDefinition` -> `GigaToolDefinition`
* `FunctionToolCallback` -> `GigaFunctionToolCallback`
* resultConverter: `GigaToolCallResultConverter`

Также появилась возможность возвращать результат работы функции напрямую пользователю, а не в GigaChat.
Для этого используйте `@Tool(returnDirect=true)` / `@GigaTool(returnDirect=true)` /
`ToolMetadata.builder().returnDirect(true).build()`

#### Управление вызовом функций

Для управления вызовом функций в GigaChatOptions существует параметр functionCallMode, принимающий одно из возможных значений:
- AUTO
- NONE
- CUSTOM_FUNCTION - принудительный вызов функции

Если functionCallMode не указать, то будет использоваться AUTO при наличии хотя бы одной функции/тула. В противном случае не используется и никак не передается в GigaChat.

Детальное описание каждого из параметров можно узнать в [документации GigaChat](https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/post-chat).

Пример:

```java
chatClientBuilder
    .defaultOptions(
            GigaChatOptions
                    .builder()
                    .model(GigaChatApi.ChatModel.GIGA_CHAT)
                    .functionCallMode(GigaChatOptions.FunctionCallMode.AUTO)
                    .build()
    )
    .build();
```

#### Принудительный вызов функции

В [GigaChat есть возможность](https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/post-chat) гарантировать вызов конкретной функции по результатом работы GigaChat. Для этого необходимо передать параметр с названием функции.

В Spring AI GigaChat для этого используется GigaChatOptions и параметры `functionCallMode` и `functionCallParam`:

```java
chatClientBuilder
    .defaultTools(GigaTools.from(new TemperatureTool()))
    .defaultOptions(
            GigaChatOptions
                    .builder()
                    .model(GigaChatApi.ChatModel.GIGA_CHAT_2_MAX)
                    .functionCallMode(GigaChatOptions.FunctionCallMode.CUSTOM_FUNCTION)
                    .functionCallParam(myFunctionCallParam)
                    .build()
    )
    .build();
```

`functionCallParam` на вход ожидает объект `FunctionCallParam` состоящий из двух полей:
- name - имя функции/тула
- partialArguments - key-value подмножество аргументов функции/тула, которые GigaChat будет использовать для вызова функции

Пример:

```java
var myFunctionCallParam = FunctionCallParam.builder()
        .name("getTemperature")
        .partialArguments(Map.of("unit", "C"))
        .build();
```

### Миграция с FunctionCalling API на Tool Calling API

Для миграции также советую ознакомиться с официальной документацией
Spring AI - https://docs.spring.io/spring-ai/reference/api/tools-migration.html.

Примеры миграции:
1) Было:

```java
FunctionCallback.builder()
       .function("getTemperatureFunction", (City city) -> "-7 градусов")
       .description("Функция получения погоды в градусах цельсия для конкретного города")
       .inputType(City.class)
       .build()
```

Стало:

```java
FunctionToolCallback.builder("getTemperatureFunction", (City city) -> "-7 градусов")
       .description("Функция получения погоды в градусах цельсия для конкретного города")
       .inputType(City.class)
       .build())
```

2) Было:

```java
GigaChatFunctionCallback.builder()
       .function("getTemperatureFunction", (City city) -> "-7 градусов")
       .description("Функция получения погоды в градусах цельсия для конкретного города")
       .inputType(City.class)
       .build()
```

Стало:

```java
GigaFunctionToolCallback.builder("getTemperatureFunction", (City city) -> "-7 градусов")
       .description("Функция получения погоды в градусах цельсия для конкретного города")
       .inputType(City.class)
       .build())
```

### Функционал `few_shot_examples`

`few_shot_examples` используется в модели GigaChat для улучшения понимания
контекста и формирования правильных ответов путём предоставления небольшого набора
примеров («шотов») перед заданием.

Пример использования совместно с аннотацией `@GigaTool`:

```java
@GigaTool(
        description = "Функция получения погоды в градусах цельсия для конкретного города на заданную дату",
        fewShotExamples = {
        @FewShotExample(
                request = "Сколько градусов в Москве будет завтра?",
                params = "{}")
})
String getTemperature(
    @ToolParam(description = "Название города") String cityName,
    @ToolParam(description = "Дата") String date) {...}
```

Пример **альтернативного** объявления совместно с аннотацией `@GigaTool`:

```java
@GigaTool(description = "Функция получения погоды в градусах цельсия для конкретного города на заданную дату")
@FewShotExample(
        request = "Сколько градусов в Питере будет завтра?",
        params = "{}")
@FewShotExample(
        request = "Какая температура была в Москве 12 июля 2023 года?",
        params =
                """
                 {
                   "cityName": "Москва",
                   "date": "2023-07-12"
                 }
                 """)
String getTemperature(
        @ToolParam(description = "Название города") String cityName,
        @ToolParam(description = "Дата") String date) {...}
```

Аналогичным образом работает с аннотацией `@Tool`

Более подробно можно ознакомиться в примере [WeatherToolController](../spring-ai-gigachat-example/src/main/java/ru/sber/credit/machine/ai/gigachat/example/WeatherToolController.java)
