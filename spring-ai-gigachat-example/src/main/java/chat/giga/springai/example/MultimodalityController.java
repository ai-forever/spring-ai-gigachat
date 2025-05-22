package chat.giga.springai.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import chat.giga.springai.GigaChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.content.Media;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value = "", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class MultimodalityController {

    private final ChatClient chatClient;

    public MultimodalityController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultOptions(
                        GigaChatOptions.builder().model("GigaChat-2-Max").build())
                .build();
    }

    @PostMapping(value = "/multimodality/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String chatWithMultimodality(
            @RequestParam String userMessage, @RequestParam("file") MultipartFile multipartFile) {
        return chatClient
                .prompt()
                .user(u -> u.text(userMessage)
                        .media(new Media(
                                MimeType.valueOf(multipartFile.getContentType()), multipartFile.getResource())))
                .call()
                .content();
    }
}
