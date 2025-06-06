package chat.giga.springai.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import chat.giga.springai.GigaChatOptions;
import chat.giga.springai.advisor.GigaChatCachingAdvisor;
import chat.giga.springai.api.chat.GigaChatApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class ChatController {
    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(5)
                .build();
        this.chatClient = chatClientBuilder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new GigaChatCachingAdvisor(),
                        new SimpleLoggerAdvisor())
                .defaultOptions(GigaChatOptions.builder()
                        .model(GigaChatApi.ChatModel.GIGA_CHAT_2)
                        .build())
                .build();
    }

    @PostMapping("/chat")
    public String chat(@RequestParam String chatId, @RequestBody String userMessage) {
        return chatClient
                .prompt(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
    }

    @PostMapping("/session")
    public String session(@RequestParam String chatId, @RequestBody String userMessage) {
        return chatClient
                .prompt(userMessage)
                .advisors(a -> a.param(GigaChatCachingAdvisor.X_SESSION_ID, chatId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
    }
}
