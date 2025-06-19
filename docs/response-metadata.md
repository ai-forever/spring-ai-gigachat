## Дополнительная метаинформация в ответе ChatResponse

Для получения дополнительной информации об ответе создан утилитный класс
[GigaChatResponseUtils](../spring-ai-gigachat/src/main/java/chat/giga/springai/support/GigaChatResponseUtils.java)

### Получение всей истории переписки с GigaChat

Если Вам необходимо получить информацию обо всех сообщениях,
которые были получены и отправлены под капотом фреймфорка Spring AI
(например - какие тулы были вызваны, с какими параметрами, каковы результаты вызова тулов),
можно воспользоваться утилитным методом `GigaChatResponseUtils.getConversationHistory(chatResponse)`.

Пример:

```java
ChatResponse chatResponse = chatClient
        .prompt(question)
        .toolCallbacks(GigaTools.from(new WeatherTools()))
        .call()
        .chatResponse();
List<Message> toolResponseMessages = GigaChatResponseUtils.getConversationHistory(chatResponse)
        .stream()
        .filter(msg -> MessageType.TOOL.equals(msg.getMessageType()))
        .toList();
log.info("Было вызвано {} функций", toolResponseMessages.size());
```

