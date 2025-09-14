## Дополнительная метаинформация в ответе ChatResponse

Для получения дополнительной информации об ответе создан утилитный класс
[GigaChatResponseUtils](../spring-ai-gigachat/src/main/java/chat/giga/springai/support/GigaChatResponseUtils.java)

### Получение всей переписки с GigaChat под капотом Spring AI

Если Вам необходимо получить информацию обо всех сообщениях,
которые были получены и отправлены под капотом фреймфорка Spring AI
(например - какие тулы были вызваны, с какими параметрами, каковы результаты вызова тулов),
можно воспользоваться утилитным методом `GigaChatResponseUtils.getInternalMessages(chatResponse)`.

Пример:

```java
ChatResponse chatResponse = chatClient
        .prompt(question)
        .toolCallbacks(GigaTools.from(new WeatherTools()))
        .call()
        .chatResponse();
List<Message> toolResponseMessages = GigaChatResponseUtils.getInternalMessages(chatResponse)
        .stream()
        .filter(msg -> MessageType.TOOL.equals(msg.getMessageType()))
        .toList();
log.info("Было вызвано {} функций", toolResponseMessages.size());
```

### Получение иденификаторов загруженных файлов при использовании Multimodality

Если Вам необходимо получить идентификаторы загруженных файлов,
(например - чтобы затем повторно использовать их в запросе или наоборот удалить),
можно воспользоваться утилитным методом `GigaChatResponseUtils.getUploadedMediaIds(chatResponse)`.

Пример:

```java
ChatResponse chatResponse = chatClient
        .prompt()
        .user(u -> u.text("Какая порода кота на фото?")
                .media(new Media(MimeType.valueOf(multipartFile.getContentType()), multipartFile.getResource())))
        .call()
        .chatResponse();

String mediaId = GigaChatResponseUtils.getUploadedMediaIds(chatResponse).get(0);

// переиспользуем загруженные файлы в последующем запросе
chatClient.prompt()
        .user(u -> u.text("Какого цвета шерсть у кота на фото?")
                // при повторном использовании mimeType и data не важны, но не должны быть null
                .media(Media.builder().id(mediaId).mimeType(MediaType.ALL).data("").build()))
        .call()
        .chatResponse();

// удаляем файлы
uploadedMediaIds.forEach(gigaChatApi::deleteFile);
```

