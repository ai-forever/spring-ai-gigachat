package chat.giga.springai.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class StructuredEntityController {
    private final ChatClient chatClient;

    public StructuredEntityController(ChatClient.Builder chatClientBuilder) {
        this.chatClient =
                chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();
    }

    public record ActorFilms(String actor, List<String> movies) {}

    @PostMapping("/actor-films")
    public ActorFilms entityAnswer(@RequestBody String question) {
        return chatClient
                .prompt(question + System.lineSeparator()
                        + "Ответь на русском языке. Верни ответ в точности с указанным форматом.")
                .call()
                .entity(ActorFilms.class);
    }
}
