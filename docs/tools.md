## Вызов пользовательских функций

Для вызова внешних функций пользуйтесь официальной документацией
Spring AI - https://docs.spring.io/spring-ai/reference/api/tools.html.

Весь функционал Spring AI работает с GigaChat.

Однако, если вы хотите использовать больше возможностей GigaChat
(например, параметры `few_shot_examples` и `return_parameters`),
которых нет в Spring AI из коробки, то можно использовать следующие альтернативы:

- `@Tool` -> `@GigaTool`
  - **Важно**: В Spring AI разрешена конструкция `chatClient.prompt("").tools(new ToolsService())`.
    Если используете `@GigaTool`-ы, то ссылайтесь на них через `.toolCallbacks(GigaTools.from(...))`, т.е. :
    `chatClient.prompt("").toolCallbacks(GigaTools.from(new ToolsService()))`
- `ToolDefinition` -> `GigaToolDefinition`
- `FunctionToolCallback` -> `GigaFunctionToolCallback`
- resultConverter: `GigaToolCallResultConverter`

Также есть возможность возвращать результат работы функции напрямую пользователю, а не в GigaChat.
Для этого используйте `@Tool(returnDirect=true)` / `@GigaTool(returnDirect=true)` /
`ToolMetadata.builder().returnDirect(true).build()`

### Использование @GigaTool

Аннотация `@GigaTool` полностью наследует все параметры стандартной аннотации `@Tool`,
а также добавляет 2 необязательных параметра:

- `fewShotExamples` - массив, в котором вы можете описать примеры того, как модель должна сгенерировать аргументы.
  Наличие примеров повышает качество генерации аргументов.
- `generateOutputSchema` - включение генерации json-схемы ответа от функции. По-умолчанию включено.
  Помогает лучше генерировать аргументы для пользовательских функций.

Пример использования `@GigaTool`:

```java
// Объявление функции
@Component
class WeatherTools {
    @GigaTool(
            description = "Функция получения погоды в градусах цельсия для конкретного города на заданную дату",
            generateOutputSchema = true,
            fewShotExamples = {
                    @FewShotExample(
                            request = "Какая температура была в Москве 12 июля 2023 года?",
                            params = """
                                    {
                                      "cityName": "Москва",
                                      "date": "2023-07-12"
                                    }
                                    """)})
    String getTemperature(
            @ToolParam(description = "Название города") String cityName,
            @ToolParam(description = "Дата") String date) { /* тут логика вашей функции */ }
}

// Подключение функции
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    public CommandLineRunner runner(ChatClient.Builder builder, WeatherTools weatherTools) {
        return args -> {
            ChatClient chatClient = builder.defaultToolCallbacks(GigaTools.from(weatherTools)).build();
            String response = chatClient.prompt("Какая погода была в Сочи 19 августа 2023?").call().content();
            System.out.println(response);
        };
    }
}
```

Аннотацию `@FewShotExample` также можно использовать на одном уровне с `@Tool` или `@GigaTool`:

```java
@Tool(description = "Функция получения погоды в градусах цельсия для конкретного города на заданную дату")
// или @GigaTool(description = "Функция получения погоды в градусах цельсия для конкретного города на заданную дату")
@FewShotExample(
    request = "Какая температура была в Москве 12 июля 2023 года?",
    params = """
        {
          "cityName": "Москва",
          "date": "2023-07-12"
        }
        """)
String getTemperature(
        @ToolParam(description = "Название города") String cityName,
        @ToolParam(description = "Дата") String date) { /* тут логика вашей функции */ }
```

Более подробно со всеми вариантами подключения пользовательских функций можно ознакомиться в примере [WeatherToolController](../spring-ai-gigachat-example/src/main/java/chat/giga/springai/example/WeatherToolController.java).

### Управление вызовом функций

Для управления вызовом функций в GigaChatOptions существует параметр `functionCallMode`, принимающий одно из возможных значений:
- `AUTO`
- `NONE` - запрет вызова функции
- `CUSTOM_FUNCTION` - принудительный вызов функции.

Если `functionCallMode` не указать, то будет использоваться `AUTO` при наличии хотя бы одной функции/тула. В противном случае не используется и никак не передается в GigaChat.

Детальное описание каждого из параметров можно узнать в [документации GigaChat](https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/post-chat).

Пример:

```java
void configure(ChatClient.Builder chatClientBuilder) {
chatClientBuilder
    .defaultOptions(
            GigaChatOptions
                    .builder()
                    .model(GigaChatApi.ChatModel.GIGA_CHAT)
                    .functionCallMode(GigaChatOptions.FunctionCallMode.AUTO)
                    .build()
    )
    .build();
}
```

### Принудительный вызов функции

В [GigaChat есть возможность](https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/post-chat) гарантировать вызов конкретной функции по результатам работы GigaChat.
Для этого необходимо передать параметр с названием функции.

Реализовать это можно с помощью параметров `functionCallMode` и `functionCallParam` класса `GigaChatOptions`:

```java
void configure(ChatClient.Builder chatClientBuilder) {
    chatClientBuilder
            .defaultTools(GigaTools.from(new TemperatureTool()))
            .defaultOptions(
                    GigaChatOptions
                            .builder()
                            .model(GigaChatApi.ChatModel.GIGA_CHAT_2_MAX)
                            .functionCallMode(GigaChatOptions.FunctionCallMode.CUSTOM_FUNCTION)
                            .functionCallParam(FunctionCallParam.builder()
                                    .name("getTemperature")
                                    .partialArguments(Map.of("unit", "C"))
                                    .build())
                            .build()
            )
            .build();
}
```

`functionCallParam` на вход ожидает объект `FunctionCallParam` состоящий из двух полей:
- `name` - имя функции/тула
- `partialArguments` - key-value подмножество аргументов функции/тула, которые GigaChat будет использовать для вызова функции
