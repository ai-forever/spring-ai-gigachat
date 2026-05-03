package chat.giga.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chat.giga.springai.advisor.GigaChatAdvisorParams;
import chat.giga.springai.api.GigaChatApiProperties;
import chat.giga.springai.api.auth.GigaChatApiScope;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

/**
 * Integration tests for Virtual Function Structured Output feature.
 *
 * <p>These tests verify that the GigaChat model correctly returns structured output
 * when using the VIRTUAL_FUNCTION_STRUCTURED_OUTPUT advisor with the entity() method.
 *
 * <p>Tests require the following environment variables:
 * <ul>
 *     <li>GIGACHAT_API_SCOPE - API scope (e.g., GIGACHAT_API_CORP)</li>
 *     <li>GIGACHAT_API_CLIENT_ID - Client ID for authentication</li>
 *     <li>GIGACHAT_API_CLIENT_SECRET - Client secret for authentication</li>
 * </ul>
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "GIGACHAT_API_SCOPE", matches = ".+")
class StructuredOutputIT {

    private static GigaChatModel gigaChatModel;

    @BeforeAll
    static void setUp() {
        GigaChatApiProperties apiProperties = GigaChatApiProperties.builder()
                .auth(GigaChatAuthProperties.builder()
                        .scope(GigaChatApiScope.valueOf(System.getenv("GIGACHAT_API_SCOPE")))
                        .unsafeSsl(true)
                        .bearer(GigaChatAuthProperties.Bearer.builder()
                                .clientId(System.getenv("GIGACHAT_API_CLIENT_ID"))
                                .clientSecret(System.getenv("GIGACHAT_API_CLIENT_SECRET"))
                                .build())
                        .build())
                .build();
        GigaChatApi gigaChatApi = new GigaChatApi(apiProperties);

        gigaChatModel = GigaChatModel.builder()
                .gigaChatApi(gigaChatApi)
                .internalProperties(new chat.giga.springai.api.GigaChatInternalProperties())
                .build();
    }

    // Records for structured output tests
    record ActorFilms(
            @JsonPropertyDescription("Имя актёра или режиссёра") String actor,
            @JsonPropertyDescription("Список названий фильмов") List<String> movies) {}

    record MovieDetails(
            @JsonPropertyDescription("Название фильма") String title,
            @JsonPropertyDescription("Год выхода фильма") int year,
            @JsonPropertyDescription("Имя режиссёра фильма") String director) {}

    record DirectorInfo(
            @JsonPropertyDescription("Имя режиссёра") String name,
            @JsonPropertyDescription("Список фильмов режиссёра") List<MovieDetails> filmography) {}

    // Records for tools + structured output tests
    record WeatherRequest(@JsonPropertyDescription("Название города") String city) {}

    record WeatherResponse(
            @JsonPropertyDescription("Название города") String city,
            @JsonPropertyDescription("Температура в градусах Цельсия") int temperature,
            @JsonPropertyDescription("Описание погодных условий") String condition) {}

    record TravelAdvice(
            @JsonPropertyDescription("Название города") String city,
            @JsonPropertyDescription("Описание текущей погоды") String weather,
            @JsonPropertyDescription("Рекомендация для путешественника") String recommendation) {}

    record CurrencyRequest(
            @JsonPropertyDescription("Код исходной валюты") String from,
            @JsonPropertyDescription("Код целевой валюты") String to) {}

    record CurrencyResponse(
            @JsonPropertyDescription("Код исходной валюты") String from,
            @JsonPropertyDescription("Код целевой валюты") String to,
            @JsonPropertyDescription("Курс обмена") double rate) {}

    record TripPlan(
            @JsonPropertyDescription("Место назначения поездки") String destination,
            @JsonPropertyDescription("Температура в градусах Цельсия") int temperature,
            @JsonPropertyDescription("Курс обмена валюты") double currencyRate,
            @JsonPropertyDescription("Совет для путешественника") String advice) {}

    record Country(
            @JsonPropertyDescription("Название страны") String name,
            @JsonPropertyDescription("Столица страны") String capital,
            @JsonPropertyDescription("Население страны") long population) {}

    record CountryList(@JsonPropertyDescription("Список стран") List<Country> countries) {}

    enum Sentiment {
        POSITIVE,
        NEGATIVE,
        NEUTRAL
    }

    record SentimentAnalysis(
            @JsonPropertyDescription("Анализируемый текст") String text,
            @JsonPropertyDescription("Определённая тональность текста") Sentiment sentiment,
            @JsonPropertyDescription("Уверенность в оценке от 0 до 1") double confidence) {}

    @Test
    @DisplayName("Virtual function structured output with default advisor returns valid ActorFilms")
    void testNativeStructuredOutput() {
        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .build();

        ActorFilms result =
                chatClient.prompt("Назови 3 фильма Квентина Тарантино").call().entity(ActorFilms.class);

        log.info("Result: actor={}, movies={}", result.actor(), result.movies());

        assertThat(result).isNotNull();
        assertThat(result.actor()).isNotBlank();
        assertThat(result.movies()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Virtual function structured output per-request with advisor returns valid ActorFilms")
    void testNativeStructuredOutputPerRequest() {
        ChatClient chatClient = ChatClient.builder(gigaChatModel).build();

        ActorFilms result = chatClient
                .prompt("Назови 3 фильма Стивена Спилберга")
                .advisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .call()
                .entity(ActorFilms.class);

        log.info("Result: actor={}, movies={}", result.actor(), result.movies());

        assertThat(result).isNotNull();
        assertThat(result.actor()).isNotBlank();
        assertThat(result.movies()).isNotEmpty();
    }

    @Test
    @DisplayName("Virtual function structured output with nested records returns valid DirectorInfo")
    void testNestedStructuredOutput() {
        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .build();

        DirectorInfo result = chatClient
                .prompt("Расскажи о 2 фильмах Кристофера Нолана с годами выхода")
                .call()
                .entity(DirectorInfo.class);

        log.info("Result: name={}, filmography={}", result.name(), result.filmography());

        assertThat(result).isNotNull();
        assertThat(result.name()).isNotBlank();
        assertThat(result.filmography()).isNotEmpty();
        assertThat(result.filmography().get(0).title()).isNotBlank();
        assertThat(result.filmography().get(0).year()).isGreaterThan(1900);
    }

    @Test
    @DisplayName("Tools with structured output combined - weather tool returns structured travel advice")
    void testToolsWithStructuredOutput() {
        Function<WeatherRequest, WeatherResponse> weatherFunction =
                request -> new WeatherResponse(request.city(), 22, "Солнечно");

        ToolCallback weatherTool = FunctionToolCallback.builder("getWeatherForTravel", weatherFunction)
                .description("Получить текущую погоду в городе")
                .inputType(WeatherRequest.class)
                .build();

        // Create fresh ChatClient to avoid tool accumulation from shared builder
        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .defaultToolCallbacks(weatherTool)
                .build();

        TravelAdvice result = chatClient
                .prompt("Какая погода в Москве? Дай совет для путешественника.")
                .call()
                .entity(TravelAdvice.class);

        log.info(
                "Result: city={}, weather={}, recommendation={}",
                result.city(),
                result.weather(),
                result.recommendation());

        assertThat(result).isNotNull();
        assertThat(result.city()).isNotBlank();
        assertThat(result.recommendation()).isNotBlank();
    }

    @Test
    @DisplayName("Multiple tools with structured output - weather and currency tools return trip plan")
    void testMultipleToolsWithStructuredOutput() {
        Function<WeatherRequest, WeatherResponse> weatherFunction =
                request -> new WeatherResponse(request.city(), 25, "Ясно");

        ToolCallback weatherTool = FunctionToolCallback.builder("getWeatherForTrip", weatherFunction)
                .description("Получить температуру в городе")
                .inputType(WeatherRequest.class)
                .build();

        Function<CurrencyRequest, CurrencyResponse> currencyFunction =
                request -> new CurrencyResponse(request.from(), request.to(), 92.5);

        ToolCallback currencyTool = FunctionToolCallback.builder("getCurrencyRate", currencyFunction)
                .description("Получить курс валюты")
                .inputType(CurrencyRequest.class)
                .build();

        // Create fresh ChatClient to avoid tool accumulation from shared builder
        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .defaultToolCallbacks(weatherTool, currencyTool)
                .build();

        TripPlan result = chatClient
                .prompt("Планирую поездку в Париж. Какая там погода и какой курс евро к рублю?")
                .call()
                .entity(TripPlan.class);

        log.info(
                "Result: destination={}, temperature={}, currencyRate={}, advice={}",
                result.destination(),
                result.temperature(),
                result.currencyRate(),
                result.advice());

        assertThat(result).isNotNull();
        assertThat(result.destination()).isNotBlank();
    }

    @Test
    @DisplayName("Structured output with list of objects returns valid CountryList")
    void testStructuredOutputWithList() {
        // Create fresh ChatClient to avoid tool accumulation from shared builder
        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .build();

        CountryList result = chatClient
                .prompt("Назови 3 европейские страны с их столицами и населением")
                .call()
                .entity(CountryList.class);

        log.info("Result: countries={}", result.countries());

        assertThat(result).isNotNull();
        assertThat(result.countries()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.countries().get(0).name()).isNotBlank();
        assertThat(result.countries().get(0).capital()).isNotBlank();
    }

    @Test
    @DisplayName("Structured output with enum returns valid SentimentAnalysis")
    void testStructuredOutputWithEnum() {
        // Create fresh ChatClient to avoid tool accumulation from shared builder
        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .build();

        SentimentAnalysis result = chatClient
                .prompt("Проанализируй тональность текста: 'Это был отличный день, я очень рад!'")
                .call()
                .entity(SentimentAnalysis.class);

        log.info(
                "Result: text={}, sentiment={}, confidence={}", result.text(), result.sentiment(), result.confidence());

        assertThat(result).isNotNull();
        assertThat(result.sentiment()).isNotNull();
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    // ==================== Complex Tool + Structured Output Integration Tests ====================

    // Records for complex tool tests with unique identifiable values
    record ProductRequest(@JsonPropertyDescription("Идентификатор продукта") String productId) {}

    record ProductInfo(
            @JsonPropertyDescription("Идентификатор продукта") String productId,
            @JsonPropertyDescription("Название продукта") String name,
            @JsonPropertyDescription("Цена продукта") double price,
            @JsonPropertyDescription("Уникальный код из базы данных") String uniqueCode) {}

    record ProductSummary(
            @JsonPropertyDescription("Название продукта") String productName,
            @JsonPropertyDescription("Цена продукта в рублях") double price,
            @JsonPropertyDescription("Уникальный код для проверки") String verificationCode,
            @JsonPropertyDescription("Рекомендация по покупке") String recommendation) {}

    record StockRequest(@JsonPropertyDescription("Код товара") String itemCode) {}

    record StockInfo(
            @JsonPropertyDescription("Код товара") String itemCode,
            @JsonPropertyDescription("Количество на складе") int quantity,
            @JsonPropertyDescription("Уникальный идентификатор запроса") String requestId) {}

    record InventoryReport(
            @JsonPropertyDescription("Название товара") String itemName,
            @JsonPropertyDescription("Доступное количество") int availableQuantity,
            @JsonPropertyDescription("Идентификатор запроса для аудита") String auditId,
            @JsonPropertyDescription("Статус наличия") String availabilityStatus) {}

    @Test
    @DisplayName("Tool is actually called - verify with call counter and unique return value")
    void testToolActuallyCalled_WithCallCounterAndUniqueValue() {
        AtomicInteger callCount = new AtomicInteger(0);
        String uniqueCode = "UNIQUE-" + System.currentTimeMillis();

        Function<ProductRequest, ProductInfo> productFunction = request -> {
            callCount.incrementAndGet();
            log.info("Tool called! productId={}, returning uniqueCode={}", request.productId(), uniqueCode);
            return new ProductInfo(request.productId(), "Тестовый продукт", 1999.99, uniqueCode);
        };

        ToolCallback productTool = FunctionToolCallback.builder("getProductInfo", productFunction)
                .description("Получить информацию о продукте по его идентификатору")
                .inputType(ProductRequest.class)
                .build();

        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .defaultToolCallbacks(productTool)
                .build();

        ProductSummary result = chatClient
                .prompt("Расскажи о продукте с id=ABC123. Обязательно укажи уникальный код из базы данных.")
                .call()
                .entity(ProductSummary.class);

        log.info(
                "Result: productName={}, price={}, verificationCode={}, recommendation={}",
                result.productName(),
                result.price(),
                result.verificationCode(),
                result.recommendation());

        // Verify tool was actually called
        assertThat(callCount.get()).as("Tool should be called at least once").isGreaterThanOrEqualTo(1);

        // Verify structured output contains data from tool
        assertThat(result).isNotNull();
        assertThat(result.productName()).isNotBlank();
        assertThat(result.price()).isGreaterThan(0);
        // The unique code from tool should appear in the response
        assertThat(result.verificationCode())
                .as("Structured output should contain unique code from tool response")
                .contains("UNIQUE");
    }

    @Test
    @DisplayName("Multiple tools called sequentially before structured output")
    void testMultipleToolsCalledSequentially_BeforeStructuredOutput() {
        AtomicInteger weatherCallCount = new AtomicInteger(0);
        AtomicInteger stockCallCount = new AtomicInteger(0);
        String weatherRequestId = "WEATHER-" + System.currentTimeMillis();
        String stockRequestId = "STOCK-" + System.currentTimeMillis();

        Function<WeatherRequest, WeatherResponse> weatherFunction = request -> {
            weatherCallCount.incrementAndGet();
            log.info("Weather tool called for city={}", request.city());
            return new WeatherResponse(request.city(), 18, "Облачно, " + weatherRequestId);
        };

        Function<StockRequest, StockInfo> stockFunction = request -> {
            stockCallCount.incrementAndGet();
            log.info("Stock tool called for itemCode={}", request.itemCode());
            return new StockInfo(request.itemCode(), 42, stockRequestId);
        };

        ToolCallback weatherTool = FunctionToolCallback.builder("checkWeather", weatherFunction)
                .description("Проверить погоду в городе для планирования доставки")
                .inputType(WeatherRequest.class)
                .build();

        ToolCallback stockTool = FunctionToolCallback.builder("checkStock", stockFunction)
                .description("Проверить наличие товара на складе")
                .inputType(StockRequest.class)
                .build();

        record DeliveryPlan(
                @JsonPropertyDescription("Город доставки") String city,
                @JsonPropertyDescription("Погодные условия") String weatherConditions,
                @JsonPropertyDescription("Количество товара для отправки") int itemsToShip,
                @JsonPropertyDescription("Идентификатор для отслеживания") String trackingInfo,
                @JsonPropertyDescription("Рекомендации по доставке") String deliveryNotes) {}

        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .defaultToolCallbacks(weatherTool, stockTool)
                .build();

        DeliveryPlan result = chatClient
                .prompt("Планирую доставку в Санкт-Петербург. Проверь погоду и наличие товара SKU-789 на складе.")
                .call()
                .entity(DeliveryPlan.class);

        log.info(
                "Result: city={}, weather={}, items={}, tracking={}, notes={}",
                result.city(),
                result.weatherConditions(),
                result.itemsToShip(),
                result.trackingInfo(),
                result.deliveryNotes());

        // Verify at least one tool was called (model may not call both)
        int totalCalls = weatherCallCount.get() + stockCallCount.get();
        assertThat(totalCalls).as("At least one tool should be called").isGreaterThanOrEqualTo(1);
        log.info("Weather tool calls: {}, Stock tool calls: {}", weatherCallCount.get(), stockCallCount.get());

        // Verify structured output
        assertThat(result).isNotNull();
        assertThat(result.city()).isNotBlank();
        assertThat(result.deliveryNotes()).isNotBlank();
    }

    @Test
    @DisplayName("Tool provides critical data that must appear in structured output")
    void testToolProvidesCriticalData_MustAppearInStructuredOutput() {
        AtomicInteger callCount = new AtomicInteger(0);
        // Use very specific, unlikely-to-be-guessed values
        int specificQuantity = 7777;
        String specificAuditId = "AUDIT-XYZ-" + System.currentTimeMillis();

        Function<StockRequest, StockInfo> stockFunction = request -> {
            callCount.incrementAndGet();
            log.info(
                    "Stock tool called, returning specific quantity={} and auditId={}",
                    specificQuantity,
                    specificAuditId);
            return new StockInfo(request.itemCode(), specificQuantity, specificAuditId);
        };

        ToolCallback stockTool = FunctionToolCallback.builder("getInventory", stockFunction)
                .description("Получить точные данные об остатках товара на складе")
                .inputType(StockRequest.class)
                .build();

        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .defaultToolCallbacks(stockTool)
                .build();

        InventoryReport result = chatClient
                .prompt(
                        "Сколько единиц товара ITEM-999 есть на складе? Мне нужны точные данные с идентификатором запроса.")
                .call()
                .entity(InventoryReport.class);

        log.info(
                "Result: itemName={}, quantity={}, auditId={}, status={}",
                result.itemName(),
                result.availableQuantity(),
                result.auditId(),
                result.availabilityStatus());

        // Verify tool was called
        assertThat(callCount.get()).as("Tool must be called").isGreaterThanOrEqualTo(1);

        // Verify critical data from tool appears in structured output
        assertThat(result).isNotNull();
        assertThat(result.availableQuantity())
                .as("Quantity from tool (%d) should appear in structured output", specificQuantity)
                .isEqualTo(specificQuantity);
        assertThat(result.auditId())
                .as("Audit ID from tool should appear in structured output")
                .contains("AUDIT");
    }

    @Test
    @DisplayName("Verify tool execution order - tools called before structured output with call sequence tracking")
    void testToolExecutionOrder_ToolsCalledBeforeStructuredOutput() {
        List<String> callSequence = new ArrayList<>();
        String secretCode = "SECRET-" + System.currentTimeMillis();

        Function<ProductRequest, ProductInfo> productFunction = request -> {
            callSequence.add("TOOL_CALLED:" + System.currentTimeMillis());
            log.info(">>> Tool execution at position {} in sequence", callSequence.size());
            return new ProductInfo(request.productId(), "Секретный продукт", 999.0, secretCode);
        };

        ToolCallback productTool = FunctionToolCallback.builder("getSecretProduct", productFunction)
                .description("Получить секретную информацию о продукте, которую знает только эта функция")
                .inputType(ProductRequest.class)
                .build();

        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .defaultToolCallbacks(productTool)
                .build();

        long startTime = System.currentTimeMillis();

        ProductSummary result = chatClient
                .prompt("Мне нужна информация о секретном продукте PROD-SECRET. "
                        + "Обязательно используй функцию getSecretProduct чтобы получить секретный код. "
                        + "Без вызова функции ты не узнаешь секретный код!")
                .call()
                .entity(ProductSummary.class);

        long endTime = System.currentTimeMillis();
        callSequence.add("STRUCTURED_OUTPUT_RECEIVED:" + endTime);

        log.info("Call sequence: {}", callSequence);
        log.info(
                "Result: productName={}, price={}, verificationCode={}",
                result.productName(),
                result.price(),
                result.verificationCode());

        // Verify tool was called
        long toolCalls =
                callSequence.stream().filter(s -> s.startsWith("TOOL_CALLED")).count();
        assertThat(toolCalls).as("Tool should be called at least once").isGreaterThanOrEqualTo(1);

        // Verify secret code from tool appears in result
        assertThat(result.verificationCode())
                .as("Secret code from tool must appear in structured output")
                .isEqualTo(secretCode);

        // Verify sequence: TOOL_CALLED should come before STRUCTURED_OUTPUT_RECEIVED
        int toolCallIndex = -1;
        int structuredOutputIndex = -1;
        for (int i = 0; i < callSequence.size(); i++) {
            if (callSequence.get(i).startsWith("TOOL_CALLED") && toolCallIndex == -1) {
                toolCallIndex = i;
            }
            if (callSequence.get(i).startsWith("STRUCTURED_OUTPUT_RECEIVED")) {
                structuredOutputIndex = i;
            }
        }

        assertThat(toolCallIndex)
                .as("Tool should be called before structured output is received")
                .isLessThan(structuredOutputIndex);

        log.info(
                "Execution order verified: Tool called at index {}, Structured output at index {}",
                toolCallIndex,
                structuredOutputIndex);
    }

    @Test
    @DisplayName("Two different tools both called and data from both appears in structured output")
    void testTwoToolsBothCalled_DataFromBothInStructuredOutput() {
        AtomicInteger tool1Calls = new AtomicInteger(0);
        AtomicInteger tool2Calls = new AtomicInteger(0);

        // Use very specific values that model cannot guess
        int magicNumber1 = 31415; // pi digits
        int magicNumber2 = 27182; // e digits
        String token1 = "ALPHA-" + System.currentTimeMillis();
        String token2 = "BETA-" + (System.currentTimeMillis() + 1);

        record MagicRequest(@JsonPropertyDescription("Тип магического числа") String type) {}

        record MagicResponse(
                @JsonPropertyDescription("Магическое число") int number,
                @JsonPropertyDescription("Токен доступа") String token) {}

        record CombinedMagic(
                @JsonPropertyDescription("Первое магическое число") int firstNumber,
                @JsonPropertyDescription("Второе магическое число") int secondNumber,
                @JsonPropertyDescription("Первый токен") String firstToken,
                @JsonPropertyDescription("Второй токен") String secondToken,
                @JsonPropertyDescription("Сумма чисел") int sum) {}

        Function<MagicRequest, MagicResponse> magic1Function = request -> {
            tool1Calls.incrementAndGet();
            log.info(">>> Magic1 tool called, returning number={}, token={}", magicNumber1, token1);
            return new MagicResponse(magicNumber1, token1);
        };

        Function<MagicRequest, MagicResponse> magic2Function = request -> {
            tool2Calls.incrementAndGet();
            log.info(">>> Magic2 tool called, returning number={}, token={}", magicNumber2, token2);
            return new MagicResponse(magicNumber2, token2);
        };

        ToolCallback magic1Tool = FunctionToolCallback.builder("getFirstMagicNumber", magic1Function)
                .description("Получить первое магическое число и токен ALPHA")
                .inputType(MagicRequest.class)
                .build();

        ToolCallback magic2Tool = FunctionToolCallback.builder("getSecondMagicNumber", magic2Function)
                .description("Получить второе магическое число и токен BETA")
                .inputType(MagicRequest.class)
                .build();

        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .defaultToolCallbacks(magic1Tool, magic2Tool)
                .build();

        CombinedMagic result = chatClient
                .prompt("Мне нужны ОБА магических числа! "
                        + "Вызови getFirstMagicNumber чтобы получить первое число и токен ALPHA. "
                        + "Вызови getSecondMagicNumber чтобы получить второе число и токен BETA. "
                        + "Затем посчитай сумму двух чисел.")
                .call()
                .entity(CombinedMagic.class);

        log.info(
                "Result: first={}, second={}, token1={}, token2={}, sum={}",
                result.firstNumber(),
                result.secondNumber(),
                result.firstToken(),
                result.secondToken(),
                result.sum());
        log.info("Tool calls: magic1={}, magic2={}", tool1Calls.get(), tool2Calls.get());

        // Note: We cannot guarantee model will call ALL tools - it decides which ones to use.
        // Verify at least one tool was called
        int totalToolCalls = tool1Calls.get() + tool2Calls.get();
        assertThat(totalToolCalls)
                .as("At least one magic tool should be called")
                .isGreaterThanOrEqualTo(1);

        // Verify data from called tool(s) appears in structured output
        assertThat(result).isNotNull();

        // If first tool was called, verify its data
        if (tool1Calls.get() > 0) {
            assertThat(result.firstNumber())
                    .as("First magic number from tool should appear")
                    .isEqualTo(magicNumber1);
            assertThat(result.firstToken())
                    .as("First token should contain ALPHA")
                    .contains("ALPHA");
        }

        // If second tool was called, verify its data
        if (tool2Calls.get() > 0) {
            assertThat(result.secondNumber())
                    .as("Second magic number from tool should appear")
                    .isEqualTo(magicNumber2);
            assertThat(result.secondToken())
                    .as("Second token should contain BETA")
                    .contains("BETA");
        }

        // Log which tools were actually called for debugging
        log.info("Test completed: {} of 2 tools called. Model decides which tools to invoke.", totalToolCalls);
    }

    // ==================== Edge Cases and Real-World Agent Scenarios ====================

    // Records for edge case tests
    record PartialResponse(
            @JsonPropertyDescription("Обязательное поле") @JsonProperty(required = true) String requiredField,
            @JsonPropertyDescription("Опциональное поле") String optionalField,
            @JsonPropertyDescription("Числовое поле") Integer numericField) {}

    record NullableResponse(
            @JsonPropertyDescription("Поле которое может быть null") String nullableField,
            @JsonPropertyDescription("Список который может быть пустым") List<String> items) {}

    record MemoryTestResponse(
            @JsonPropertyDescription("Имя пользователя из контекста") String userName,
            @JsonPropertyDescription("Предыдущая тема разговора") String previousTopic,
            @JsonPropertyDescription("Текущий ответ") String currentResponse) {}

    @Test
    @DisplayName("Streaming works but structured output requires blocking call with entity()")
    void testStreamingWithoutEntityReturnsPlainText() throws InterruptedException {
        // Note: Streaming API (stream()) does not have entity() method in Spring AI.
        // Structured output requires schema which is generated by entity(Class).
        // This test verifies that streaming still works, but returns plain text.
        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .build();

        List<String> chunks = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        chatClient.prompt("Назови столицу Франции").stream()
                .chatResponse()
                .doOnNext(response -> {
                    if (response.getResult() != null && response.getResult().getOutput() != null) {
                        String content = response.getResult().getOutput().getText();
                        if (content != null && !content.isEmpty()) {
                            chunks.add(content);
                            log.info("Received chunk: {}", content);
                        }
                    }
                })
                .doOnError(error::set)
                .doOnComplete(latch::countDown)
                .subscribe();

        boolean completed = latch.await(30, TimeUnit.SECONDS);

        assertThat(completed).as("Streaming should complete within timeout").isTrue();
        assertThat(error.get()).as("No errors should occur").isNull();
        log.info("Streaming completed with {} chunks", chunks.size());
        // Streaming returns plain text, not structured output (no entity() in stream API)
        assertThat(chunks).as("Should receive at least one chunk").isNotEmpty();
        // Content is plain text, not JSON
        String fullContent = String.join("", chunks);
        log.info("Full streaming content: {}", fullContent);
        assertThat(fullContent).as("Streaming returns plain text answer").containsIgnoringCase("Париж");
    }

    @Test
    @DisplayName("Tool throws exception - verify graceful handling")
    void testToolThrowsException_GracefulHandling() {
        AtomicInteger callCount = new AtomicInteger(0);

        Function<ProductRequest, ProductInfo> failingFunction = request -> {
            callCount.incrementAndGet();
            log.info("Tool called, about to throw exception");
            throw new RuntimeException("Simulated tool failure: Database connection lost");
        };

        ToolCallback failingTool = FunctionToolCallback.builder("getFailingProduct", failingFunction)
                .description("Получить информацию о продукте (может упасть)")
                .inputType(ProductRequest.class)
                .build();

        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .defaultToolCallbacks(failingTool)
                .build();

        // Tool exception should propagate or be handled gracefully
        assertThatThrownBy(() -> chatClient
                        .prompt("Получи информацию о продукте PROD-FAIL")
                        .call()
                        .entity(ProductSummary.class))
                .as("Tool exception should propagate")
                .isInstanceOf(Exception.class);

        assertThat(callCount.get()).as("Tool should have been called").isGreaterThanOrEqualTo(1);
        log.info("Tool was called {} times before exception", callCount.get());
    }

    @Test
    @DisplayName("Partial response - model returns only some fields")
    void testPartialResponse_ModelReturnsOnlySomeFields() {
        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .build();

        // Ask for minimal information to test partial response handling
        PartialResponse result = chatClient
                .prompt("Скажи только слово 'тест'. Заполни только обязательное поле.")
                .call()
                .entity(PartialResponse.class);

        log.info(
                "Partial response: required={}, optional={}, numeric={}",
                result.requiredField(),
                result.optionalField(),
                result.numericField());

        assertThat(result).isNotNull();
        // Required field should be present
        assertThat(result.requiredField()).as("Required field should be filled").isNotNull();
        // Optional fields may be null or have default values
        log.info("Optional field is: {}", result.optionalField() == null ? "null" : "present");
        log.info("Numeric field is: {}", result.numericField() == null ? "null" : result.numericField());
    }

    @Test
    @DisplayName("Null values handling in structured output")
    void testNullValuesHandling() {
        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .build();

        NullableResponse result = chatClient
                .prompt("Верни пустой ответ. Поле nullableField должно быть null, список items должен быть пустым.")
                .call()
                .entity(NullableResponse.class);

        log.info("Nullable response: field={}, items={}", result.nullableField(), result.items());

        assertThat(result).isNotNull();
        // Model may or may not respect null instructions
        log.info("nullableField is: {}", result.nullableField() == null ? "null" : "'" + result.nullableField() + "'");
        log.info(
                "items is: {}",
                result.items() == null ? "null" : "size=" + result.items().size());
    }

    @Test
    @DisplayName("Tool with timeout simulation - long running operation")
    void testToolWithTimeout_LongRunningOperation() {
        AtomicInteger callCount = new AtomicInteger(0);
        long simulatedDelay = 2000; // 2 seconds

        Function<ProductRequest, ProductInfo> slowFunction = request -> {
            callCount.incrementAndGet();
            long startTime = System.currentTimeMillis();
            log.info("Slow tool started, will take {}ms", simulatedDelay);
            try {
                Thread.sleep(simulatedDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Tool interrupted", e);
            }
            long duration = System.currentTimeMillis() - startTime;
            log.info("Slow tool completed after {}ms", duration);
            return new ProductInfo(request.productId(), "Slow Product", 100.0, "SLOW-" + duration);
        };

        ToolCallback slowTool = FunctionToolCallback.builder("getSlowProduct", slowFunction)
                .description("Получить информацию о продукте (медленная операция)")
                .inputType(ProductRequest.class)
                .build();

        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .defaultToolCallbacks(slowTool)
                .build();

        long startTime = System.currentTimeMillis();

        ProductSummary result = chatClient
                .prompt("Получи информацию о медленном продукте PROD-SLOW")
                .call()
                .entity(ProductSummary.class);

        long totalTime = System.currentTimeMillis() - startTime;

        log.info("Total execution time: {}ms, result: {}", totalTime, result);

        assertThat(callCount.get()).as("Slow tool should be called").isGreaterThanOrEqualTo(1);
        assertThat(result).isNotNull();
        assertThat(result.verificationCode())
                .as("Should contain SLOW marker from tool")
                .contains("SLOW");
        assertThat(totalTime).as("Total time should include tool delay").isGreaterThan(simulatedDelay);
    }

    @Test
    @DisplayName("ChatMemory integration - structured output with conversation history")
    void testChatMemoryIntegration_StructuredOutputWithHistory() {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();
        String conversationId = "test-conversation-" + System.currentTimeMillis();

        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(conversationId)
                        .build())
                .build();

        // First message - establish context
        String firstResponse = chatClient
                .prompt("Привет! Меня зовут Александр и я интересуюсь программированием на Java.")
                .call()
                .content();

        log.info("First response: {}", firstResponse);

        // Second message - use structured output with memory context
        MemoryTestResponse result = chatClient
                .prompt("Вспомни как меня зовут и чем я интересуюсь. Ответь структурированно.")
                .advisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .call()
                .entity(MemoryTestResponse.class);

        log.info(
                "Memory test result: userName={}, previousTopic={}, currentResponse={}",
                result.userName(),
                result.previousTopic(),
                result.currentResponse());

        assertThat(result).isNotNull();
        // Model should remember context from first message
        assertThat(result.userName())
                .as("Should remember user name from context")
                .containsIgnoringCase("Александр");
        assertThat(result.previousTopic())
                .as("Should remember topic from context")
                .containsIgnoringCase("Java");
    }

    @Test
    @DisplayName("Conflict with explicit function_call mode - verify behavior")
    void testConflictWithExplicitFunctionCallMode() {
        Function<ProductRequest, ProductInfo> productFunction =
                request -> new ProductInfo(request.productId(), "Test Product", 500.0, "EXPLICIT-TEST");

        ToolCallback productTool = FunctionToolCallback.builder("getProductForConflictTest", productFunction)
                .description("Получить информацию о продукте")
                .inputType(ProductRequest.class)
                .build();

        // Create options with explicit function call mode (CUSTOM_FUNCTION forces specific function)
        GigaChatOptions options = GigaChatOptions.builder()
                .functionCallMode(GigaChatOptions.FunctionCallMode.CUSTOM_FUNCTION)
                .functionCallParam(chat.giga.springai.api.chat.param.FunctionCallParam.builder()
                        .name("getProductForConflictTest")
                        .build())
                .build();

        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultToolCallbacks(productTool)
                .defaultOptions(options)
                .build();

        // This tests what happens when user sets explicit function_call AND uses structured output
        // Current implementation: structured output instruction should guide model behavior
        ProductSummary result = chatClient
                .prompt("Получи информацию о продукте CONFLICT-TEST")
                .advisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .call()
                .entity(ProductSummary.class);

        log.info(
                "Conflict test result: productName={}, verificationCode={}",
                result.productName(),
                result.verificationCode());

        assertThat(result).isNotNull();
        // Result should still be valid structured output
        assertThat(result.productName()).isNotBlank();
    }

    @Test
    @DisplayName("Multiple sequential calls with same ChatClient - verify isolation")
    void testMultipleSequentialCalls_VerifyIsolation() {
        AtomicInteger callCount = new AtomicInteger(0);

        Function<ProductRequest, ProductInfo> countingFunction = request -> {
            int count = callCount.incrementAndGet();
            return new ProductInfo(request.productId(), "Product-" + count, count * 100.0, "CALL-" + count);
        };

        ToolCallback countingTool = FunctionToolCallback.builder("getCountingProduct", countingFunction)
                .description("Получить информацию о продукте с подсчётом вызовов")
                .inputType(ProductRequest.class)
                .build();

        ChatClient chatClient = ChatClient.builder(gigaChatModel)
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .defaultToolCallbacks(countingTool)
                .build();

        // First call
        ProductSummary result1 =
                chatClient.prompt("Получи информацию о продукте FIRST").call().entity(ProductSummary.class);

        log.info("First call result: {}", result1);

        // Second call - should be independent
        ProductSummary result2 =
                chatClient.prompt("Получи информацию о продукте SECOND").call().entity(ProductSummary.class);

        log.info("Second call result: {}", result2);

        // Third call
        ProductSummary result3 =
                chatClient.prompt("Получи информацию о продукте THIRD").call().entity(ProductSummary.class);

        log.info("Third call result: {}", result3);

        assertThat(callCount.get()).as("Tool should be called for each request").isGreaterThanOrEqualTo(3);

        // Each result should be independent
        assertThat(result1.verificationCode()).contains("CALL");
        assertThat(result2.verificationCode()).contains("CALL");
        assertThat(result3.verificationCode()).contains("CALL");

        log.info("Total tool calls: {}", callCount.get());
    }
}
