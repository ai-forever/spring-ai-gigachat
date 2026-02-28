# Генерация изображений с помощью GigaChat

Библиотека поддерживает генерацию изображений с использованием моделей GigaChat через интерфейс `ImageModel` из Spring AI.
GigaChat всегда генерирует картинки в формате JPG. Подробнее о функции генерации изображений - [в документации GigaChat API](https://developers.sber.ru/docs/ru/gigachat/guides/images-generation).

## Базовое использование

Для генерации изображений с помощью GigaChat вы можете использовать стандартный интерфейс `ImageModel` из Spring AI:

```java
@Autowired
private ImageModel imageModel;

// Простая генерация изображения
ImagePrompt prompt = new ImagePrompt("Нарисуй кота в шляпе");
ImageResponse response = imageModel.call(prompt);
String base64Image = response.getResult().getOutput().getB64Json();
```

С полным примером можно ознакомиться в [ImageController](../spring-ai-gigachat-example/src/main/java/chat/giga/springai/example/ImageController.java).

## Форматы ответа: Base64 и URL

GigaChat поддерживает два формата возврата сгенерированных изображений:

### Base64 (по умолчанию)

Изображение возвращается в виде base64-строки:

```java
GigaChatImageOptions options = GigaChatImageOptions.builder()
    .responseFormat(GigaChatImageOptions.RESPONSE_FORMAT_B64_JSON)  // по умолчанию
    .build();

ImagePrompt prompt = new ImagePrompt("Нарисуй кота", options);
ImageResponse response = imageModel.call(prompt);

// Получение base64-строки
String base64 = response.getResult().getOutput().getB64Json();

// Получение идентификатора файла (fileId) для последующего использования
// fileId доступен в метаданных для обоих форматов ответа (b64_json и url)
String fileId = ((GigaChatImageGenerationMetadata) response.getResult().getMetadata()).getFileId();
```

### URL

Изображение возвращается в виде прямой ссылки:

```java
GigaChatImageOptions options = GigaChatImageOptions.builder()
    .responseFormat(GigaChatImageOptions.RESPONSE_FORMAT_URL)
    .build();

ImagePrompt prompt = new ImagePrompt("Нарисуй закат", options);
ImageResponse response = imageModel.call(prompt);

// Получение URL изображения
String imageUrl = response.getResult().getOutput().getUrl();

// fileId также доступен в метаданных (как и для формата b64_json)
String fileId = ((GigaChatImageGenerationMetadata) response.getResult().getMetadata()).getFileId();
```

### Когда использовать Base64:

- Нужно сохранить изображение локально
- Требуется дополнительная обработка изображения на сервере
- Нужно отдать изображение внешнему сервису, у которого нет ключа доступа к GigaChat API

### Когда использовать URL:

- Нужно избежать скачивания файла на сервер
- Важно минимизировать размер ответа API
- Не требуется локальное хранение или обработка

## Настройка параметров генерации

Вы можете настроить параметры генерации с помощью `GigaChatImageOptions`:

```java
GigaChatImageOptions options = GigaChatImageOptions.builder()
    .model("GigaChat-2-Max")  // Указать конкретную модель
    .style("You are an artist. If the user asks you to draw something," +
           "generate an image using the built-in text2image function" +
           "and return a tag in the form <img src=\"FILE_ID\"/>.")
    .build();

ImagePrompt prompt = new ImagePrompt("Нарисуй красивый закат", options);
ImageResponse response = imageModel.call(prompt);
```

## Поддерживаемые параметры

- `model`: Модель GigaChat для генерации изображений (например, "GigaChat-2-Max")
- `style`: Системный промпт, который указывает модели, что она является художником
- `responseFormat`: Формат ответа — `b64_json` (по-умолчанию) или `url`

## Особенности реализации

- Используется API Chat-модели GigaChat, в которой используются внутренние функции для генерации изображений
- Результат возвращается в формате base64 или URL (в зависимости от `responseFormat`)
- Из метаданных ответа всегда можно получить идентификатор файла изображения - `((GigaChatImageGenerationMetadata) response.getResult().getMetadata()).getFileId()`
- Поддерживается наблюдаемость (observability) через Micrometer

