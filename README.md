<div align="center">
  <h1>Spring AI GigaChat</h1>
  <a href="https://www.apache.org/licenses/LICENSE-2.0">
    <img alt="Apache 2.0 License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg">
  </a>
  <a href="https://central.sonatype.com/artifact/chat.giga/spring-ai-starter-model-gigachat">
    <img alt="Maven Central Version" src="https://img.shields.io/maven-central/v/chat.giga/spring-ai-starter-model-gigachat">
  </a>
  <a href="https://github.com/ai-forever/spring-ai-gigachat/actions/workflows/maven-build.yml">
    <img alt="Build Status" src="https://github.com/ai-forever/spring-ai-gigachat/actions/workflows/maven-build.yml/badge.svg">
  </a>
  <p>
    <img src="docs/img/spring-ai-gigachat-logo-2048-2048.png" alt="Логотип" width="300">
  </p>
</div>

Данная библиотека позволяет интегрироваться с GigaChat API с использованием фреймворка [Spring AI](https://docs.spring.io/spring-ai/reference/index.html).

Реализованы:

- Вызов Chat модели, в том числе:
  - блокирующий вызов, когда ответ от LLM приходит полностью
  - потоковая генерация токенов через Server Sent Events (SSE)
- Вызов внешних функций, в том числе через Model Context Protocol (MCP)
- Работа с файлами и изображениями - можно спросить, что изображено на Вашей фотографии
- Вызов Embedding модели - может использоваться для построения RAG-системы
- [Наблюдаемость](https://docs.spring.io/spring-ai/reference/observability/index.html) (метрики, трейсы, логи)

## Содержание

- [Требования](#требования)
- [Быстрый старт](#быстрый-старт)
- [Конфигурация](#конфигурация)
- [Способы авторизации](#способы-авторизации)
  - [По Authorization Key (apiKey)](#по-authorization-key-apikey)
  - [По Client ID + Client Secret](#по-client-id--client-secret)
  - [С помощью TLS-сертификатов](#с-помощью-tls-сертификатов)
  - [Настройка доверия сертификатам НУЦ Минцифры](#настройка-доверия-сертификатам-нуц-минцифры)
- [Вызов пользовательских функций](docs/tools.md)
  - [Использование @GigaTool](docs/tools.md#использование-gigatool)
  - [Управление вызовом функций](docs/tools.md#управление-вызовом-функций)
  - [Принудительный вызов функции](docs/tools.md#принудительный-вызов-функции)
- [Отправка HTTP-заголовков в GigaChat](docs/custom-http-headers.md)
  - [Статические значения HTTP-заголовоков](docs/custom-http-headers.md#статические-значения-http-заголовоков)
  - [Динамически вычисляемые значения HTTP-заголовоков](docs/custom-http-headers.md#динамически-вычисляемые-значения-http-заголовоков)
  - [Отправка X-Session-Id](docs/custom-http-headers.md#отправка-x-session-id)
- [Дополнительная метаинформация в ответе ChatResponse](docs/response-metadata.md)
  - [Получение всей переписки с GigaChat под капотом Spring AI](docs/response-metadata.md#получение-всей-переписки-с-gigachat-под-капотом-spring-ai)
  - [Получение идентификаторов загруженных файлов при использовании Multimodality](docs/response-metadata.md#получение-иденификаторов-загруженных-файлов-при-использовании-multimodality)
- [GigaChat Аутентификация: Использование GigaAuthToken](docs/auth.md)
- [Примеры](#примеры)

## Требования

- Java 17+
- Spring Boot 3.4/3.5

## Быстрый старт

Для работы с библиотекой вам понадобится ключ авторизации API.

Чтобы получить ключ авторизации:
1. Создайте проект **GigaChat API** в личном кабинете Studio.
2. В интерфейсе проекта, в левой панели выберите раздел **Настройки API**.
3. Нажмите кнопку **Получить ключ**.

В открывшемся окне скопируйте и сохраните значение поля **Client Secret**.
Этот ключ отображается только один раз и не хранится в личном кабинете.
При компрометации или утере ключа авторизации вы можете сгенерировать его повторно.

1) Подключите в ваш pom.xml зависимость

```xml
<dependency>
    <groupId>chat.giga</groupId>
    <artifactId>spring-ai-starter-model-gigachat</artifactId>
    <version>1.1.1</version>
</dependency>
```

2) Пропишите параметры подключения к GigaChat в application.yml:

```yaml
spring:
  ai:
    gigachat:
      auth:
        bearer:
           api-key: ${GIGACHAT_API_KEY}    # Ваш Authorization Key, полученный в личном кабинете GigaChat
        scope: GIGACHAT_API_PERS           # доступны также GIGACHAT_API_B2B, GIGACHAT_API_CORP
        unsafe-ssl: true                   # отключает проверку серверных сертификатов, не рекомендуется использовать в production!
```

3) Создайте Main-класс:

```java
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    public CommandLineRunner runner(ChatClient.Builder builder) {
        return args -> {
            ChatClient chatClient = builder.build();
            String response = chatClient.prompt("Расскажи шутку").call().content();
            System.out.println(response);
        };
    }
}
```

## Конфигурация

В application.yml можно задать дефолтные параметры для запроса к API GigaChat,
а также параметры для подключения и авторизации.

Описание параметров можно найти в [официальной документации](https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/post-chat).

```yaml
spring:
  ai:
    gigachat:
      chat:
        options:
          # дефолтные параметры для обращения к API GigaChat.
          model: GigaChat        # GigaChat по дефолту, список доступных моделей - https://developers.sber.ru/docs/ru/gigachat/models
          temperature: 0.5       # null по дефолту
          top-p: 0.5             # null по дефолту
          max-tokens: 200        # null по дефолту
          repetition-penalty: 1  # null по дефолту 
          update-interval: 0     # null по дефолту
      embedding:
        options:
          model: Embeddings      # Embeddings по дефолту
          dimensions: 1024       # null по дефолту, вычисляется при первом обращении к Embedding-модели
      internal:
        connect-timeout: 15s     # 15 секунд по дефолту. Таймаут на установление соединения с севрером
        read-timeout: 15s        # null по дефолту (без таймаута). Таймаут на получение ответа от сервера
        make-system-prompt-first-message-in-memory: true  # true по дефолту; перемещает сообщение с ситемным промптом в начало
```

## Способы авторизации

### По Authorization Key (apiKey)

Пример application.yml для авторизации по Authorization Key:

```yaml
spring:
  ai:
    gigachat:
      auth:
        bearer:
          api-key: <ваш_authorization_key>     # Ваш Authorization Key, можно посмотреть в личном кабинете GigaChat в разделе "Настройки API" в вашем проекте
        scope: GIGACHAT_API_PERS               # Можно посмотреть в личном кабинете GigaChat в разделе "Настройки API" в вашем проекте
```

Также необходимо [настроить доверие сертификатам НУЦ Минцифры](#настройка-доверия-сертификатам-нуц-минцифры)
или отключить проверку серверных сертификатов (не рекомендуется!).

### По Client ID + Client Secret

Пример application.yml для авторизации по Client ID и Client Secret:

```yaml
spring:
  ai:
    gigachat:
      auth:
        bearer:
          client-id: <ваш_client_id>             # Можно посмотреть в личном кабинете GigaChat в разделе "Настройки API" в вашем проекте
          client-secret: <ваш_client_secret>     # Можно посмотреть в личном кабинете GigaChat в разделе "Настройки API" в вашем проекте
        scope: GIGACHAT_API_PERS
```

Также необходимо [настроить доверие сертификатам НУЦ Минцифры](#настройка-доверия-сертификатам-нуц-минцифры)
или отключить проверку серверных сертификатов (не рекомендуется!).

### С помощью TLS-сертификатов

Пример application.yml для подключения к GigaChat с помощью TLS-сертификатов:

```yaml
spring:
  ai:
    gigachat:
      auth:
        certs:
          certificate: file:/path/to/tls.crt     # Путь до вашего сертификата. Если у вас терминация TLS настроена на Egress-gateway, то можно пропустить этот параметр
          private-key: file:/path/to/tls.key     # Путь до вашего сертификата. Если у вас терминация TLS настроена на Egress-gateway, то можно пропустить этот параметр
          ca-certs: file:/path/to/ca.crt         # Путь до ваших доверенных сертификатов. Если у вас терминация TLS настроена на Egress-gateway, то можно пропустить этот параметр
        scope: GIGACHAT_API_PERS
```

Пример application.yml для подключения к GigaChat с помощью TLS-сертификатов,
объявленных через [Spring Boot SSL Bundles](https://docs.spring.io/spring-boot/reference/features/ssl.html):

```yaml
spring:
  ai:
    gigachat:
      auth:
        certs:
          ssl-bundle: "gigachat-ssl-bundle"        # Название вашего ssl-bundle
        scope: GIGACHAT_API_PERS
  ssl:
    bundle:
      pem: 
        gigachat-ssl-bundle:
          keystore:
            private-key: file:/path/to/tls.key     # Путь до вашего сертификата
            certificate: file:/path/to/tls.crt     # Путь до вашего сертификата
          truststore:
            certificates: file:/path/to/ca.crt     # Путь до ваших доверенных сертификатов
```

### Настройка доверия сертификатам НУЦ Минцифры

Для настройки доверия сертификатам НУЦ Минцифры, необходимо:
1) Скачать сертификат https://gu-st.ru/content/Other/doc/russian_trusted_root_ca.cer
2) Указать путь до сертификата `application.yml`:

```yaml
spring:
  ai:
    gigachat:
      auth:
        certs:
          ca-certs: file:/path/to/russian_trusted_root_ca.cer
```

## Примеры

Примеры работы с библиотекой - в отдельном модуле [spring-ai-gigachat-example](./spring-ai-gigachat-example/README.md).

