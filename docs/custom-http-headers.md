# Отправка HTTP-заголовков в GigaChat

Для отправки произвольных HTTP-заголовков в GigaChat есть несоклько опций - через Options и через Advisor.

## Содержание

- [Статические значения HTTP-заголовоков](#статические-значения-http-заголовоков)
- [Динамически вычисляемые значения HTTP-заголовоков](#динамически-вычисляемые-значения-http-заголовоков)
- [Отправка X-Session-Id](#отправка-x-session-id)

### Статические значения HTTP-заголовоков

Если Вам необходимо отправлять **статические** заголовки, то удобнее задать их через Options -
в application.yml или в коде.

- Пример с `application.yml`:

  ```yaml
  spring:
    ai:
      gigachat:
        chat:
          options:
            http-headers:
              x-client-id: spring-ai-gigachat-example
  ```
- Пример настройки Options в коде:

  ```java
  ChatClient configure(ChatClient.Builder chatClientBuilder) {
      return chatClientBuilder
                .defaultOptions(GigaChatOptions.builder()
                      .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                      .httpHeaders(Map.of("options-header", "options-value")) // задаем значение заголовка
                      .build())
                .build();
  }
  ```

**Стоит отметить:** если Вы одновременно определяете Options и в application.yml, и в коде - то будут использоваться
только Options из кода, т.е. значения параметров не склеиваются.

### Динамически вычисляемые значения HTTP-заголовоков

Если Вам необходимо отправлять заголовки с **динамически вычисляемыми** значениями, то это возможно сделать через
[GigaChatHttpHeadersAdvisor](../spring-ai-gigachat/src/main/java/chat/giga/springai/advisor/GigaChatHttpHeadersAdvisor.java).

Пример, где пробрасывается http-заголовок `x-request-id` из запроса, если он есть (иначе генерируется новый):

```java
import static chat.giga.springai.advisor.GigaChatHttpHeadersAdvisor.httpHeader;
import chat.giga.springai.advisor.GigaChatHttpHeadersAdvisor;

String sendWithHeaders(ChatClient.Builder chatClientBuilder) {
    var chatClient = chatClientBuilder.build();
    return chatClient
            .prompt("Расскажи шутку")
            .advisors(new GigaChatHttpHeadersAdvisor())
            .advisors(a ->
                    // можно задавать как статические заголовки
                    a.param(httpHeader("x-client-id"), "spring-ai-gigachat-example")
                    // так и динамически вычисляемые (если нужно отложить вычисление до момента вызова GigaChat API)
                    .param(httpHeader("x-request-id"), requestIdSupplier()))
            .call()
            .content();
}

private Supplier<String> requestIdSupplier() {
    return () -> {
        ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String requestId = null;
        if (sra != null) {
            requestId = sra.getRequest().getHeader("x-request-id");
        }
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        return requestId;
    };
}
```

### Отправка X-Session-Id

Если Вам необходимо отправлять только заголовок `X-Session-Id` для целей кэширования запросов на стороне GigaChat,
то для этого есть отдельный
[GigaChatCachingAdvisor](../spring-ai-gigachat/src/main/java/chat/giga/springai/advisor/GigaChatCachingAdvisor.java).

```java
String chat(ChatClient.Builder chatClientBuilder, String chatId) {
    var chatClient = chatClientBuilder.defaultAdvisors(new GigaChatCachingAdvisor()).build();
    return chatClient
        .prompt("Расскажи шутку")
        .advisors(a -> a.param(GigaChatCachingAdvisor.X_SESSION_ID, chatId))
        .call()
        .content();
}
```

