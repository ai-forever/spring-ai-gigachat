# spring-ai-gigachat-example

Примеры использования Spring AI для интеграции с GigaChat.

## Простые примеры с разными системными промптами

[QuestionAnswerController](src/main/java/chat/giga/springai/example/QuestionAnswerController.java)

```shell
curl localhost:8080/answer -d "Кто ты?" -H "content-type:application/json"
curl localhost:8080/developer/answer -d "Кто ты?" -H "content-type:application/json"
curl localhost:8080/math/answer -d "Кто ты?" -H "content-type:application/json"
```

## Примеры с сохранением контекста

[ChatController](src/main/java/chat/giga/springai/example/ChatController.java)

```shell
curl "localhost:8080/chat?chatId=123" -d "Как установить GigaCode в IntelliJ IDEA?" -H "content-type:application/json"
# после второго запроса GigaChat должен понять, что ранее речь шла про GigaCode, и дать инструкцию по его использованию
curl "localhost:8080/chat?chatId=123" -d "Как его использовать?" -H "content-type:application/json"
# если выполнить такой же запрос с другим chatId, то GigaChat не поймет, о чем речь.
curl "localhost:8080/chat?chatId=456" -d "Как его использовать?" -H "content-type:application/json"
```

## Примеры с сохранением контекста и кешированием на стороне GigaChat

[ChatController](src/main/java/chat/giga/springai/example/ChatController.java)

```shell
curl "localhost:8080/session?chatId=123" -d "Ты знаешь Пушкина?" -H "content-type:application/json"
# после второго запроса GigaChat должен понять, что ранее речь шла про Пушкина, но не будет считать токены для всей истории.
# Количество кэшированных токенов, которые не учитываются в расчете стоимости, вернется в ответе в поле precached_prompt_tokens.
curl "localhost:8080/session?chatId=123" -d "Какие его стихи ты знаешь?" -H "content-type:application/json"
# Eсли выполнить запрос без сессии, но по тому же chatId, контекст сохраниться, но токенов потратиться больше
curl "localhost:8080/chat?chatId=123" -d "Какие его стихи ты знаешь?" -H "content-type:application/json"
```

## Примеры с запросом ответа в формате json и конвертацией в POJO

[StructuredEntityController](src/main/java/chat/giga/springai/example/StructuredEntityController.java)

```shell
curl "localhost:8080/actor-films" -d "Назови 5 популярных фильмов с Сергеем Безруковым" -H "content-type:application/json"
```

Пример ответа: `{"actor":"Сергей Безруков","movies":["Бригада","Есенин","Адмиралъ","Высоцкий. Спасибо, что живой","Участок"]}`

## Virtual Function Structured Output (гарантированный JSON)

[StructuredOutputVirtualFunctionController](src/main/java/chat/giga/springai/example/StructuredOutputVirtualFunctionController.java)

Использует механизм function calling для гарантированного получения ответа в заданном JSON-формате.
Под капотом создаётся виртуальная функция с JSON Schema, и модель вызывает её с данными в качестве аргументов.

Подробнее: [Structured Output Documentation](../docs/structured-output.md)

```shell
# Получить фильмографию режиссёра
curl "localhost:8080/structured-virtual-function/actor-films?actor=Тарантино"

# Получить информацию о книге
curl "localhost:8080/structured-virtual-function/book-info?query=Война%20и%20мир"
```

Примеры ответов:
```json
{"actor":"Квентин Тарантино","movies":["Криминальное чтиво","Убить Билла","Бесславные ублюдки","Джанго освобождённый","Однажды в Голливуде"]}
```

```json
{"title":"Война и мир","author":"Лев Николаевич Толстой","year":1869,"genre":"роман-эпопея"}
```

## Примеры со стримингом ответа

[StreamAnswerController](src/main/java/chat/giga/springai/example/StreamAnswerController.java)

```shell
# флаг -N позволяет выводить ответ сразу как он пришел, без буфферизации
curl -N "localhost:8080/stream/answer" -d "Как интегрироваться с GigaChat?" -H "content-type:application/json"
```

В результате ответ модели на экране будет появляться не весь сразу, а небольшими кусочками.

## Примеры с вызовом внешних функций

[WeatherFunctionController](src/main/java/chat/giga/springai/example/WeatherToolController.java)

Функция определения температуры описана в нашем коде (для простоты выдает рандомную температуру).

Гигачат определяет, что надо вызвать эту функцию, и в ответе передает параметры для её вызова;
затем Гигачат на основе результатов вызова функции генерирует ответ
(также есть возможность возвращать результат функции напрямую пользователю).

Различия во внутренней работе api версий v1/v2/v3/v4 описаны в [коде WeatherToolController](src/main/java/chat/giga/springai/example/WeatherToolController.java).

```shell
curl localhost:8080/tool/v1/weather -d "Какая температура в Казани?" -H "content-type:application/json"
curl localhost:8080/tool/v2/weather -d "Сколько градусов в Спб?" -H "content-type:application/json"
curl localhost:8080/tool/v3/weather -d "Сколько градусов в Москве?" -H "content-type:application/json"
curl localhost:8080/tool/v4/weather -d "Сколько градусов в Сочи будет завтра?" -H "content-type:application/json"
curl localhost:8080/tool/v4/weather -d "Сколько градусов и какое давление в Сочи будет завтра?" -H "content-type:application/json"
```

## Примеры использования RAG

[RagController](src/main/java/chat/giga/springai/example/RagController.java)

### !!! Внимание При инициализации VectorStore и вызове API тратятся токены для модели Embeddings[]() !!!

В данном примере используется SimpleVectorStore(in-memory). При первичном вызове API происходит инициализация Vector store
в соответствии с [ETL процессом](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html). Для контекста используется
выдуманная история про программиста Джона, которая лежит в docx файле [rag.docx](src/main/resources/rag/rag.docx)

Для включения RAG при вызове к цепочке chatClient добавляется QuestionAnswerAdvisor. !!! При каждом вызове выполняется
дополнительное обращение к GigaChat для построения embeddings.

```shell
curl localhost:8080/rag -d "Какую платформу создал Джон?" -H "content-type:application/json"
curl localhost:8080/rag -d "В какой компании работал Джон?" -H "content-type:application/json"
```

## Примеры использования мультимодальности (работа с файлами)

[MultimodalityController.java](src/main/java/chat/giga/springai/example/MultimodalityController.java)

Перед работой с мультимодальностью проверьте, поддерживает ли конкретная модель GigaChat работу с файлами

Пример работы с изображениями

```shell
curl -X POST \
  http://localhost:8080/multimodality/chat \
  -F "userMessage=Что ты видишь на картинке?" \
  -F "file=@src/main/resources/multimodality/cat.png;type=image/png"
```

Пример работы с текстовыми файлами

```shell
curl -X POST \
  http://localhost:8080/multimodality/chat \
  -F "userMessage=Кто автор произведения?" \
  -F "file=@src/main/resources/multimodality/poem.txt;type=text/plain"
```

## Пример отправки кастомных HTTP-заголовков в GigaChat API

[CustomHttpHeadersController.java](src/main/java/chat/giga/springai/example/CustomHttpHeadersController.java)

```shell
# в первом запросе x-request-id сгенерируется случайным образом
curl localhost:8080/sendWithHeaders -d "Расскажи шутку" -H "Content-Type:application/json"
# во втором запросе x-request-id пробросится в запрос к GigaChat API
curl localhost:8080/sendWithHeaders -d "Расскажи шутку" -H "x-request-id:1234567890" -H "Content-Type:application/json"
```

## Примеры генерации изображений

[ImageController.java](src/main/java/chat/giga/springai/example/ImageController.java)

```shell
# Генерация изображения с возвратом base64
curl localhost:8080/image/base64 -d "Нарисуй кота в шляпе" -H "Content-Type:application/json"

# Генерация изображения с указанием конкретной модели и возвратом в формате JPEG
curl localhost:8080/image/raw -d "Нарисуй пейзаж с горами и озером" -H "Content-Type:application/json" -H "Accept:image/jpeg" -o test.jpg

# Генерация изображения с возвратом ссылки на сгенерированный файл
curl localhost:8080/image/url -d "Нарисуй супермена" -H "Content-Type:application/json"
```

## Внешние примеры

Еще больше примеров Вы можете найти в официальном репозитории Spring
https://github.com/spring-projects/spring-ai-examples
