package chat.giga.springai.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import chat.giga.springai.example.rag.VectorStoreInitializer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class RagController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final VectorStoreInitializer vectorStoreInitializer;

    public RagController(
            ChatClient.Builder chatClientBuilder,
            VectorStore vectorStore,
            VectorStoreInitializer vectorStoreInitializer) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.vectorStoreInitializer = vectorStoreInitializer;
    }

    @PostMapping(value = "/rag")
    @ResponseStatus(HttpStatus.OK)
    public String rag(@RequestBody String question) {
        // Инициализируется только при вызове, в целях экономии токенов
        vectorStoreInitializer.initialize(vectorStore);

        return chatClient
                .prompt()
                .advisors(new QuestionAnswerAdvisor(vectorStore))
                .user(question)
                .call()
                .content();
    }
}
