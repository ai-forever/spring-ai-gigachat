package chat.giga.springai;

import static chat.giga.springai.advisor.GigaChatCachingAdvisor.X_SESSION_ID;
import static chat.giga.springai.api.chat.GigaChatApi.X_REQUEST_ID;

import chat.giga.springai.api.auth.GigaChatInternalProperties;
import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.completion.CompletionRequest;
import chat.giga.springai.api.chat.completion.CompletionResponse;
import chat.giga.springai.api.chat.models.ModelDescription;
import chat.giga.springai.tool.definition.GigaToolDefinition;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.*;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Slf4j
public class GigaChatModel implements ChatModel {
    public static final String DEFAULT_MODEL_NAME = GigaChatApi.ChatModel.GIGA_CHAT_2.getName();
    public static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION =
            new DefaultChatModelObservationConvention();
    public static final String CONVERSATION_HISTORY = "conversationHistory";
    private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER =
            ToolCallingManager.builder().build();

    /**
     * The lower-level API for the GigaChat service.
     */
    private final GigaChatApi gigaChatApi;

    /**
     * The default options used for the chat completion requests.
     */
    private final GigaChatOptions defaultOptions;

    /**
     * The retry template used to retry the GigaChat API calls.
     */
    private final RetryTemplate retryTemplate;

    /**
     * The observation registry used for instrumentation.
     */
    private final ObservationRegistry observationRegistry;

    private final ToolCallingManager toolCallingManager;

    private final GigaChatInternalProperties internalProperties;

    /**
     * The tool execution eligibility predicate used to determine if a tool can be
     * executed.
     */
    private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

    /**
     * Conventions to use for generating observations.
     */
    @Setter
    private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

    public GigaChatModel(
            GigaChatApi gigaChatApi,
            GigaChatOptions defaultOptions,
            ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry,
            GigaChatInternalProperties internalProperties,
            ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
        Assert.notNull(gigaChatApi, "gigaChatApi cannot be null");
        Assert.notNull(defaultOptions, "defaultOptions cannot be null");
        Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
        Assert.notNull(retryTemplate, "retryTemplate cannot be null");
        Assert.notNull(observationRegistry, "observationRegistry cannot be null");
        Assert.notNull(internalProperties, "internalProperties must not be null");
        Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate cannot be null");
        this.gigaChatApi = gigaChatApi;
        this.defaultOptions = defaultOptions;
        this.toolCallingManager = toolCallingManager;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
        this.internalProperties = internalProperties;
        this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // Before moving any further, build the final request Prompt,
        // merging runtime and default options.
        Prompt requestPrompt = buildRequestPrompt(prompt);
        return this.internalCall(requestPrompt, null);
    }

    public ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {
        CompletionRequest request = createRequest(prompt, false);

        ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
                .prompt(prompt)
                .provider(GigaChatApi.PROVIDER_NAME)
                .build();

        ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
                .observation(
                        this.observationConvention,
                        DEFAULT_OBSERVATION_CONVENTION,
                        () -> observationContext,
                        this.observationRegistry)
                .observe(() -> {
                    ResponseEntity<CompletionResponse> completionEntity = this.retryTemplate.execute(
                            ctx -> this.gigaChatApi.chatCompletionEntity(request, buildHeaders(prompt.getOptions())));

                    CompletionResponse completionResponse = completionEntity.getBody();

                    if (completionResponse == null) {
                        log.warn("No chat completion returned for prompt: {}", prompt);
                        return new ChatResponse(List.of());
                    }

                    completionResponse.setId(completionEntity.getHeaders().getFirst(X_REQUEST_ID));

                    Usage currentChatResponseUsage = buildUsage(completionResponse.getUsage());
                    Usage accumulatedUsage =
                            UsageCalculator.getCumulativeUsage(currentChatResponseUsage, previousChatResponse);

                    ChatResponse chatResponse =
                            toChatResponse(completionResponse, accumulatedUsage, false, prompt.getInstructions());
                    observationContext.setResponse(chatResponse);

                    return chatResponse;
                });

        if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
            var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
            if (toolExecutionResult.returnDirect()) {
                // Return tool execution result directly to the client.
                return ChatResponse.builder()
                        .from(response)
                        .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                        .build();
            } else {
                // Send the tool execution result back to the model.
                return this.internalCall(
                        new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()), response);
            }
        }

        return response;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // Before moving any further, build the final request Prompt,
        // merging runtime and default options.
        Prompt requestPrompt = buildRequestPrompt(prompt);
        return this.internalStream(requestPrompt, null).log();
    }

    public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
        return Flux.deferContextual(contextView -> {
            CompletionRequest request = createRequest(prompt, true);

            ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
                    .prompt(prompt)
                    .provider(GigaChatApi.PROVIDER_NAME)
                    .build();

            Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
                    .observation(
                            this.observationConvention,
                            DEFAULT_OBSERVATION_CONVENTION,
                            () -> observationContext,
                            this.observationRegistry)
                    .parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null))
                    .start();

            Flux<CompletionResponse> response = this.retryTemplate.execute(
                    ctx -> this.gigaChatApi.chatCompletionStream(request, buildHeaders(prompt.getOptions())));

            Flux<ChatResponse> chatResponseFlux = response.switchMap(completionResponse -> {
                        if (completionResponse == null) {
                            log.warn("No chat completion returned for prompt: {}", prompt);
                            return Flux.just(new ChatResponse(List.of()));
                        }
                        Usage currentChatResponseUsage = buildUsage(completionResponse.getUsage());
                        Usage accumulatedUsage =
                                UsageCalculator.getCumulativeUsage(currentChatResponseUsage, previousChatResponse);

                        ChatResponse chatResponse =
                                toChatResponse(completionResponse, accumulatedUsage, true, prompt.getInstructions());

                        if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(
                                prompt.getOptions(), chatResponse)) {
                            var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, chatResponse);
                            if (toolExecutionResult.returnDirect()) {
                                // Return tool execution result directly to the client.
                                return Flux.just(ChatResponse.builder()
                                        .from(chatResponse)
                                        .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                                        .build());
                            } else {
                                // Send the tool execution result back to the model.
                                return this.internalStream(
                                        new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
                                        chatResponse);
                            }
                        }

                        return Flux.just(chatResponse);
                    })
                    .doOnError(observation::error)
                    .doFinally(s -> observation.stop())
                    .contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

            return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
        });
    }

    @SuppressWarnings("DataFlowIssue")
    public List<ModelDescription> models() {
        return gigaChatApi.models().getBody().getData();
    }

    Prompt buildRequestPrompt(Prompt prompt) {
        // Process runtime options
        GigaChatOptions runtimeOptions = null;
        if (prompt.getOptions() != null) {
            if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
                runtimeOptions = ModelOptionsUtils.copyToTarget(
                        toolCallingChatOptions, ToolCallingChatOptions.class, GigaChatOptions.class);
            } else {
                runtimeOptions =
                        ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class, GigaChatOptions.class);
            }
        }

        // Define request options by merging runtime options and default options
        GigaChatOptions requestOptions =
                ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions, GigaChatOptions.class);

        // Merge @JsonIgnore-annotated options explicitly since they are ignored by
        // Jackson, used by ModelOptionsUtils.
        if (runtimeOptions != null) {
            requestOptions.setInternalToolExecutionEnabled(ModelOptionsUtils.mergeOption(
                    runtimeOptions.getInternalToolExecutionEnabled(),
                    this.defaultOptions.getInternalToolExecutionEnabled()));
            requestOptions.setToolNames(ToolCallingChatOptions.mergeToolNames(
                    runtimeOptions.getToolNames(), this.defaultOptions.getToolNames()));
            requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(
                    runtimeOptions.getToolCallbacks(), this.defaultOptions.getToolCallbacks()));
            requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(
                    runtimeOptions.getToolContext(), this.defaultOptions.getToolContext()));
        } else {
            requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.getInternalToolExecutionEnabled());
            requestOptions.setToolNames(this.defaultOptions.getToolNames());
            requestOptions.setToolCallbacks(this.defaultOptions.getToolCallbacks());
            requestOptions.setToolContext(this.defaultOptions.getToolContext());
        }

        ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

        return new Prompt(prompt.getInstructions(), requestOptions);
    }

    private CompletionRequest createRequest(Prompt prompt, boolean stream) {
        List<CompletionRequest.Message> messages = prompt.getInstructions().stream()
                .map(message -> {
                    if (message instanceof UserMessage userMessage) {
                        if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
                            List<UUID> filesIds = userMessage.getMedia().stream()
                                    .map(media -> gigaChatApi
                                            .uploadFile(media)
                                            .getBody()
                                            .id())
                                    .toList();

                            return List.of(new CompletionRequest.Message(
                                    CompletionRequest.Role.user, userMessage.getText(), filesIds));
                        }
                        return List.of(
                                new CompletionRequest.Message(CompletionRequest.Role.user, userMessage.getText()));
                    } else if (message instanceof SystemMessage systemMessage) {
                        return List.of(
                                new CompletionRequest.Message(CompletionRequest.Role.system, systemMessage.getText()));
                    } else if (message instanceof AssistantMessage assistantMessage) {
                        CompletionRequest.Message.MessageBuilder messageBuilder = CompletionRequest.Message.builder()
                                .role(CompletionRequest.Role.assistant)
                                .content(assistantMessage.getText());
                        if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                            if (assistantMessage.getToolCalls().size() > 1) {
                                log.warn(
                                        "Too many function calls, only first one used: {}",
                                        assistantMessage.getToolCalls());
                            }
                            AssistantMessage.ToolCall toolCall =
                                    assistantMessage.getToolCalls().get(0);
                            messageBuilder
                                    .functionsStateId(toolCall.id())
                                    .functionCall(new CompletionResponse.FunctionCall()
                                            .setName(toolCall.name())
                                            .setArguments(toolCall.arguments()));
                        }
                        return List.of(messageBuilder.build());
                    } else if (message instanceof ToolResponseMessage toolResponseMessage) {
                        // по идее для гигачата тут всегда должен быть только один результат вызова функции
                        return toolResponseMessage.getResponses().stream()
                                .map(toolResponse -> CompletionRequest.Message.builder()
                                        .role(CompletionRequest.Role.function)
                                        .content(toolResponse.responseData())
                                        .name(toolResponse.name())
                                        .build())
                                .toList();
                    } else {
                        throw new IllegalStateException("Unexpected message type: " + message);
                    }
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());

        makeSystemPromptMessageFirst(messages);

        var request =
                CompletionRequest.builder().messages(messages).stream(stream).build();

        GigaChatOptions requestOptions = (GigaChatOptions) prompt.getOptions();
        request = ModelOptionsUtils.merge(requestOptions, request, CompletionRequest.class);

        // Add the tool definitions to the request's tools parameter.
        List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);

        request.setFunctionCall(getFunctionCall(requestOptions, toolDefinitions));
        // Add the enabled functions definitions to the request's tools parameter.
        if (!CollectionUtils.isEmpty(toolDefinitions)) {
            request.setFunctions(this.getFunctionDescriptions(toolDefinitions));
        }
        return request;
    }

    private Object getFunctionCall(GigaChatOptions requestOptions, List<ToolDefinition> toolDefinitions) {
        var callMode = requestOptions.getFunctionCallMode();

        if (callMode == GigaChatOptions.FunctionCallMode.CUSTOM_FUNCTION
                && requestOptions.getFunctionCallParam() != null) {
            var functionCallName = requestOptions.getFunctionCallParam().getName();

            if (functionCallName != null
                    && toolDefinitions.stream().noneMatch(it -> it.name().equals(functionCallName))) {

                log.warn("Specified function '{}' not found among available functions", functionCallName);
            }

            return requestOptions.getFunctionCallParam();
        }

        if (callMode == null) {
            if (!CollectionUtils.isEmpty(toolDefinitions)) {
                return GigaChatOptions.FunctionCallMode.AUTO.getValue();
            } else {
                return null;
            }
        }

        return callMode.getValue();
    }

    private List<CompletionRequest.FunctionDescription> getFunctionDescriptions(List<ToolDefinition> toolDefinitions) {
        return toolDefinitions.stream()
                .map(toolDefinition -> {
                    if (toolDefinition instanceof GigaToolDefinition gigaToolDefinition) {
                        return new CompletionRequest.FunctionDescription(
                                gigaToolDefinition.name(),
                                gigaToolDefinition.description(),
                                gigaToolDefinition.inputSchema(),
                                gigaToolDefinition.fewShotExamples().stream()
                                        .map(fewShotExample -> new CompletionRequest.FewShotExample(
                                                fewShotExample.getRequest(), fewShotExample.getParams()))
                                        .toList(),
                                gigaToolDefinition.outputSchema());
                    } else {
                        return new CompletionRequest.FunctionDescription(
                                toolDefinition.name(),
                                toolDefinition.description(),
                                toolDefinition.inputSchema(),
                                null,
                                null);
                    }
                })
                .toList();
    }

    private ChatResponse toChatResponse(
            CompletionResponse completionResponse, Usage usage, boolean streaming, List<Message> conversationHistory) {
        List<Generation> generations = completionResponse.getChoices().stream()
                .map(choice -> buildGeneration(completionResponse.getId(), choice, streaming))
                .toList();
        return new ChatResponse(
                generations, from(completionResponse, usage, Map.of(CONVERSATION_HISTORY, conversationHistory)));
    }

    private Generation buildGeneration(String id, CompletionResponse.Choice choice, boolean streaming) {
        CompletionResponse.MessagesRes message = streaming ? choice.getDelta() : choice.getMessage();
        String finishReason = choice.getFinishReason() != null ? choice.getFinishReason() : "";
        Map<String, Object> metadata = Map.of(
                "id",
                id,
                "index",
                choice.getIndex(),
                "role",
                message.getRole() != null ? message.getRole().name() : "",
                "finishReason",
                finishReason);
        List<AssistantMessage.ToolCall> toolCalls;
        if (CompletionResponse.FinishReason.FUNCTION_CALL.equals(finishReason)) {
            String functionsStateId = message.getFunctionsStateId();
            AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                    functionsStateId,
                    "function",
                    message.getFunctionCall().getName(),
                    message.getFunctionCall().getArguments());
            toolCalls = List.of(toolCall);
        } else {
            toolCalls = List.of();
        }
        var assistantMessage = new AssistantMessage(message.getContent(), metadata, toolCalls);
        var generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(choice.getFinishReason())
                .build();
        return new Generation(assistantMessage, generationMetadata);
    }

    private ChatResponseMetadata from(CompletionResponse completionResponse) {
        return from(completionResponse, buildUsage(completionResponse.getUsage()));
    }

    private ChatResponseMetadata from(CompletionResponse completionResponse, Usage usage) {
        return from(completionResponse, usage, Map.of());
    }

    private ChatResponseMetadata from(
            CompletionResponse completionResponse, Usage usage, Map<String, Object> metadata) {
        Assert.notNull(completionResponse, "GigaChat CompletionResponse must not be null");
        return ChatResponseMetadata.builder()
                .id(completionResponse.getId())
                .model(completionResponse.getModel())
                .usage(usage)
                .keyValue("created", completionResponse.getCreated())
                .keyValue("object", completionResponse.getObject())
                .metadata(metadata)
                .build();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return this.defaultOptions.copy();
    }

    private Usage buildUsage(CompletionResponse.Usage usage) {
        return usage != null ? this.getDefaultUsage(usage) : new EmptyUsage();
    }

    private DefaultUsage getDefaultUsage(CompletionResponse.Usage usage) {
        return new DefaultUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens(), usage);
    }

    // Ставит сообщение с системным промптом на первое место в списке сообщений (Из-за требований GigaChat API)
    // удалить когда исправят на стороне GigaChat
    private void makeSystemPromptMessageFirst(List<CompletionRequest.Message> messages) {
        if (messages.size() < 2) return;
        long systemMessageCount = messages.stream()
                .filter(it -> it.getRole() == CompletionRequest.Role.system)
                .count();
        if (systemMessageCount > 1) throw new IllegalStateException("System prompt message must be the only one");

        if (!internalProperties.isMakeSystemPromptFirstMessageInMemory()) return;

        for (int i = 0; i < messages.size(); i++) {
            CompletionRequest.Message currentMessage = messages.get(i);
            if (i == 0 && currentMessage.getRole() == CompletionRequest.Role.system) return;
            if (currentMessage.getRole() != CompletionRequest.Role.system) continue;

            // Помещаем сообщение с сист. промптом в начало списка сообщений
            messages.remove(i);
            messages.add(0, currentMessage);
            log.info("Sorting has been applied to make the system prompt the first message");
            return;
        }
    }

    private HttpHeaders buildHeaders(@Nullable ChatOptions options) {
        return Optional.ofNullable(options)
                .map(GigaChatOptions.class::cast)
                .map(it -> {
                    HttpHeaders httpHeaders = new HttpHeaders();
                    if (StringUtils.hasText(it.getSessionId())) {
                        httpHeaders.add(X_SESSION_ID, it.getSessionId());
                    }
                    return httpHeaders;
                })
                .orElseGet(HttpHeaders::new);
    }

    public static GigaChatModel.Builder builder() {
        return new GigaChatModel.Builder();
    }

    public static class Builder {

        private GigaChatApi gigaChatApi;

        private GigaChatOptions defaultOptions = GigaChatOptions.builder()
                .model(GigaChatModel.DEFAULT_MODEL_NAME)
                .build();

        private ToolCallingManager toolCallingManager;

        private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

        private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

        private GigaChatInternalProperties internalProperties;

        private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate =
                new DefaultToolExecutionEligibilityPredicate();

        private Builder() {}

        public GigaChatModel.Builder gigaChatApi(GigaChatApi gigaChatApi) {
            this.gigaChatApi = gigaChatApi;
            return this;
        }

        public GigaChatModel.Builder defaultOptions(GigaChatOptions defaultOptions) {
            this.defaultOptions = defaultOptions;
            return this;
        }

        public GigaChatModel.Builder toolCallingManager(ToolCallingManager toolCallingManager) {
            this.toolCallingManager = toolCallingManager;
            return this;
        }

        public GigaChatModel.Builder retryTemplate(RetryTemplate retryTemplate) {
            this.retryTemplate = retryTemplate;
            return this;
        }

        public GigaChatModel.Builder observationRegistry(ObservationRegistry observationRegistry) {
            this.observationRegistry = observationRegistry;
            return this;
        }

        public GigaChatModel.Builder internalProperties(GigaChatInternalProperties internalProperties) {
            this.internalProperties = internalProperties;
            return this;
        }

        public GigaChatModel.Builder toolExecutionEligibilityPredicate(
                ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
            this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
            return this;
        }

        public GigaChatModel build() {
            return new GigaChatModel(
                    gigaChatApi,
                    defaultOptions,
                    Objects.requireNonNullElse(toolCallingManager, DEFAULT_TOOL_CALLING_MANAGER),
                    retryTemplate,
                    observationRegistry,
                    internalProperties,
                    toolExecutionEligibilityPredicate);
        }
    }
}
