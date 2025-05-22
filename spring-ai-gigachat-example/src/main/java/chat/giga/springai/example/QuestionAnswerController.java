package chat.giga.springai.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class QuestionAnswerController {
    private final ChatClient chatClient;

    public QuestionAnswerController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/answer")
    public String answer(@RequestBody String question) {
        return chatClient.prompt(question).call().content();
    }

    @PostMapping("/developer/answer")
    public String developerAnswer(@RequestBody String devQuestion) {
        return chatClient
                .prompt()
                .system("Ты - эксперт по разработке программного обеспечения")
                .user(devQuestion)
                .call()
                .content();
    }

    @PostMapping("/math/answer")
    public String mathAnswer(@RequestBody String mathQuestion) {
        return chatClient
                .prompt()
                .system("Ты - эксперт в математике")
                .user(mathQuestion)
                .call()
                .content();
    }
}
