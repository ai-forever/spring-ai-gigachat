/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.giga.springai.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import chat.giga.springai.example.rag.VectorStoreInitializer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .user(question)
                .call()
                .content();
    }
}
