package chat.giga.springai.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import chat.giga.springai.advisor.GigaChatAdvisorParams;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example controller demonstrating virtual function structured output with GigaChat.
 * Uses VIRTUAL_FUNCTION_STRUCTURED_OUTPUT advisor for reliable JSON responses.
 */
@RestController
@RequestMapping(value = "/structured-virtual-function", produces = APPLICATION_JSON_VALUE)
public class StructuredOutputVirtualFunctionController {

    /** ChatClient configured with virtual function structured output enabled. */
    private final ChatClient chatClient;

    public StructuredOutputVirtualFunctionController(final ChatClient.Builder chatClientBuilder) {
        // Enable virtual function structured output globally for this controller
        this.chatClient = chatClientBuilder
                .defaultAdvisors(GigaChatAdvisorParams.VIRTUAL_FUNCTION_STRUCTURED_OUTPUT)
                .build();
    }

    /** Actor filmography data. */
    public record ActorFilms(String actor, List<String> movies) {}

    /**
     * Get actor filmography using virtual function structured output.
     * Example: GET /structured-virtual-function/actor-films?actor=Тарантино
     */
    @GetMapping("/actor-films")
    public ActorFilms getActorFilms(@RequestParam final String actor) {
        return chatClient
                .prompt("Назови 5 самых известных фильмов режиссёра " + actor)
                .call()
                .entity(ActorFilms.class);
    }

    /** Book information data. */
    public record BookInfo(String title, String author, int year, String genre) {}

    /**
     * Get book information using virtual function structured output.
     * Example: GET /structured-virtual-function/book-info?query=Война и мир
     */
    @GetMapping("/book-info")
    public BookInfo getBookInfo(@RequestParam final String query) {
        return chatClient.prompt("Расскажи о книге: " + query).call().entity(BookInfo.class);
    }
}
