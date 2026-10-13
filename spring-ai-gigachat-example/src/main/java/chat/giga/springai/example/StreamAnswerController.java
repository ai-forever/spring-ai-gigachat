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
        return chatClient.prompt(question).stream()
                .chatResponse()
                .mapNotNull(rs -> rs.getResult().getOutput().getText());
    }
}
