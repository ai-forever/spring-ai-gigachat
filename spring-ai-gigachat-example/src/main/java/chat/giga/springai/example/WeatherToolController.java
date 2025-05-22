package chat.giga.springai.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import chat.giga.springai.tool.GigaTools;
import chat.giga.springai.tool.annotation.FewShotExample;
import chat.giga.springai.tool.annotation.GigaTool;
import chat.giga.springai.tool.function.GigaFunctionToolCallback;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(value = "", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class WeatherToolController {
    private final ChatClient chatClient;

    public WeatherToolController(ChatClient.Builder chatClientBuilder) {
        this.chatClient =
                chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();
    }

    // request
    record City(@JsonPropertyDescription("Название города") String name) {}

    // response
    record CityTemperature(@JsonPropertyDescription("Температура в градусах цельсия") Integer temperature) {}

    /**
     * Пример использования стандартного для Spring AI функционала вызова внешних функций через FunctionCallback
     */
    @PostMapping("tool/v1/weather")
    public String weatherFunctionToolCallback(@RequestBody String question) {
        return chatClient
                .prompt(question)
                .toolCallbacks(FunctionToolCallback.builder("getTemperatureFunction", (City city) -> {
                            String answer =
                                    "В " + city.name() + " " + (int) ((Math.random() - 0.5) * 50) + " градусов цельсия";
                            log.info(answer);
                            return answer;
                        })
                        .description("Функция получения погоды в градусах цельсия для конкретного города")
                        .inputType(City.class)
                        .build())
                .call()
                .content();
    }

    /**
     * Пример использования вызова внешних функций через кастомный GigaFunctionToolCallback
     */
    @PostMapping("tool/v2/weather")
    public String weatherGigaFunctionToolCallback(@RequestBody String question) {
        return chatClient
                .prompt(question)
                .toolCallbacks(GigaFunctionToolCallback.builder("getTemperatureFunction", (City city) -> {
                            int temperature = (int) ((Math.random() - 0.5) * 50);
                            // return temperature;
                            return new CityTemperature(temperature);
                        })
                        .description("Функция получения погоды в градусах цельсия для конкретного города")
                        .inputType(City.class)
                        .fewShotExample(chat.giga.springai.tool.definition.FewShotExample.builder()
                                .request("Какая температура в Питере?")
                                .params(new City("Санкт-Петербург"))
                                .build())
                        .fewShotExample(chat.giga.springai.tool.definition.FewShotExample.builder()
                                .request("Какая температура в Москве?")
                                .params(new City("Москва"))
                                .build())
                        // .outputType(Integer.class) - не надо так
                        // используйте outputType только со сложными(составными) типами данных
                        .outputType(CityTemperature.class)
                        .build())
                .call()
                .content();
    }

    /**
     * Пример использования вызова внешних функций через FunctionToolCallback + использование returnDirect=true.
     * В этом случае пользователю будет выводиться число - температура.
     */
    @PostMapping("tool/v3/weather")
    public String weatherDirectResponse(@RequestBody String question) {
        return chatClient
                .prompt(question)
                .toolCallbacks(FunctionToolCallback.builder("getTemperatureFunction", (City city) -> {
                            int temperutare = (int) ((Math.random() - 0.5) * 50);
                            log.info(city.name() + ": " + temperutare);
                            return temperutare;
                        })
                        .description("Функция получения погоды в градусах цельсия для конкретного города")
                        .inputType(City.class)
                        // returnDirect=true означает, что результат функции нужно вернуть сразу пользователю, а не LLM
                        .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
                        .build())
                .call()
                .content();
    }

    @RequiredArgsConstructor
    static class WeatherTools {
        @Tool(
                description =
                        "Функция получения атмосферного давления в мм.рт.ст. для конкретного города на заданную дату")
        String getAirPressure(
                @ToolParam(description = "Название города") String cityName,
                @ToolParam(description = "Дата") String date) {
            String answer = date + " В " + cityName + " " + (int) (760 + (Math.random() - 0.5) * 20) + " мм.рт.ст.";
            log.info("@Tool's 'getAirPressure' answer: " + answer);
            return answer;
        }

        @GigaTool(
                description = "Функция получения погоды в градусах цельсия для конкретного города на заданную дату",
                //                generateOutputSchema = false,
                fewShotExamples = {@FewShotExample(request = "Сколько градусов в Москве будет завтра?", params = "{}")})
        @FewShotExample(request = "Сколько градусов в Питере будет завтра?", params = "{}")
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
                @ToolParam(description = "Дата") String date) {
            String answer = date + " В " + cityName + " " + (int) ((Math.random() - 0.5) * 50) + " градусов цельсия";
            log.info("@GigaTool's 'getTemperature' answer: " + answer);
            return answer;
        }
    }

    /**
     * Пример использования вызова внешних функций через @Tool и @GigaTool аннотации.
     */
    @PostMapping("tool/v4/weather")
    public String weatherToolAnnotation(@RequestBody String question) {
        return chatClient
                .prompt(question)
                // Важно использовать .toolCallbacks(GigaTools.from()), чтобы обрабатывались аннотации @GigaTool и @Tool
                // Если использовать конструкцию .tools(new WeatherTools()), то будет использоваться только @Tool
                .toolCallbacks(GigaTools.from(new WeatherTools()))
                .call()
                .content();
    }
}
