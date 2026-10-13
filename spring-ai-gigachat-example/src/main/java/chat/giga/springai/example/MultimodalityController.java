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

import chat.giga.springai.GigaChatOptions;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.support.GigaChatResponseUtils;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value = "", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class MultimodalityController {

    private final ChatClient chatClient;
    private final GigaChatApi gigaChatApi;

    public MultimodalityController(ChatClient.Builder chatClientBuilder, GigaChatApi gigaChatApi) {
        this.gigaChatApi = gigaChatApi;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultOptions(GigaChatOptions.builder().model("GigaChat-2-Max"))
                .build();
    }

    @PostMapping(value = "/multimodality/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String chatWithMultimodality(
            @RequestParam String userMessage, @RequestParam("file") MultipartFile multipartFile) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(u -> u.text(userMessage)
                        .media(new Media(
                                MimeType.valueOf(multipartFile.getContentType()), multipartFile.getResource())))
                .call()
                .chatResponse();

        List<String> uploadedMediaIds = GigaChatResponseUtils.getUploadedMediaIds(chatResponse);
        uploadedMediaIds.forEach(gigaChatApi::deleteFile);

        return chatResponse.getResult().getOutput().getText();
    }
}
