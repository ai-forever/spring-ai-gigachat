package chat.giga.springai.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class StreamAnswerController {
    private final ChatClient chatClient;

    public StreamAnswerController(ChatClient.Builder chatClientBuilder) {
        this.chatClient =
                chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();
    }

    @PostMapping(value = "/stream/answer", consumes = APPLICATION_JSON_VALUE, produces = TEXT_EVENT_STREAM_VALUE)
    public Flux<String> answer(@RequestBody String question) {
        return chatClient.prompt(question).stream().chatResponse().log().map(rs -> rs.getResult()
                .getOutput()
                .getText());
    }
}
