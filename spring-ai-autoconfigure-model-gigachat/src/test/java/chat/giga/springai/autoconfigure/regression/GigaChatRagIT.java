package chat.giga.springai.autoconfigure.regression;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.GigaChatEmbeddingModel;
import chat.giga.springai.GigaChatModel;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Регрессионный end-to-end тест связки RAG (embeddings + chat) на реальном API GigaChat.
 *
 * <p>Проверяет полную цепочку: документы эмбеддятся {@link GigaChatEmbeddingModel} в
 * {@link SimpleVectorStore}, релевантный документ извлекается {@link QuestionAnswerAdvisor} по
 * векторному сходству и передаётся в {@link GigaChatModel}, который формирует ответ строго из
 * извлечённого контекста. Магическое значение (секретный код) присутствует ТОЛЬКО в одном
 * документе, поэтому его появление в ответе подтверждает работу всей связки embeddings -&gt;
 * retrieval -&gt; chat.
 *
 * <p>ВАЖНО: эмбеддинги GigaChat могут требовать платный тариф и отдавать HTTP 402 (Payment
 * Required). В этом случае тест помечается как SKIPPED через {@link Assumptions#abort}, а не падает.
 */
@ActiveProfiles("it")
@EnableAutoConfiguration
@SpringBootTest(classes = GigaChatRagIT.MyCustomApplication.class)
public class GigaChatRagIT {

    private static final Logger log = LoggerFactory.getLogger(GigaChatRagIT.class);

    @SpringBootConfiguration
    public static class MyCustomApplication {}

    @Autowired
    GigaChatModel gigaChatModel;

    @Autowired
    GigaChatEmbeddingModel gigaChatEmbeddingModel;

    @Test
    @DisplayName("RAG end-to-end: секретный код извлекается из векторного хранилища и попадает в ответ модели")
    void ragEndToEndTest() {
        SimpleVectorStore vectorStore =
                SimpleVectorStore.builder(gigaChatEmbeddingModel).build();

        List<Document> documents = List.of(
                new Document("Секретный код проекта Гагарин — 4271."),
                new Document("Столица Франции — Париж, крупный культурный и туристический центр."),
                new Document("Вода закипает при температуре 100 градусов Цельсия на уровне моря."),
                new Document("Проект Королёв занимается разработкой ракетных двигателей нового поколения."));

        // Шаг, требующий платного тарифа: эмбеддинг документов. Если тариф недоступен — SKIP.
        try {
            vectorStore.add(documents);
        } catch (RuntimeException ex) {
            if (isPaymentRequired(ex)) {
                Assumptions.abort("Embeddings require paid GigaChat tariff (HTTP 402)");
            }
            throw ex;
        }

        ChatClient chatClient = ChatClient.create(gigaChatModel);

        String answer;
        try {
            answer = chatClient
                    .prompt()
                    .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                    .user("Какой секретный код проекта Гагарин?")
                    .call()
                    .content();
        } catch (RuntimeException ex) {
            // Эмбеддинг запроса при retrieval тоже может упереться в платный тариф.
            if (isPaymentRequired(ex)) {
                Assumptions.abort("Embeddings require paid GigaChat tariff (HTTP 402)");
                return; // недостижимо: abort бросает исключение, но удовлетворяет компилятор
            }
            throw ex;
        }

        log.info("Ответ модели на RAG-вопрос: {}", answer);
        assertThat(answer)
                .as("ответ обязан содержать секретный код 4271 из единственного релевантного документа "
                        + "(подтверждает связку embeddings -> retrieval -> chat)")
                .contains("4271");
    }

    /**
     * Определяет, что исключение (или любое из его звеньев в цепочке причин) вызвано ответом
     * HTTP 402 Payment Required — без хардкода и логирования секретов.
     */
    private static boolean isPaymentRequired(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof RestClientResponseException restEx) {
                if (matches402(restEx.getStatusCode())) {
                    return true;
                }
            }
            if (current instanceof WebClientResponseException webEx) {
                if (matches402(webEx.getStatusCode())) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null && (message.contains("402") || message.contains("Payment Required"))) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    private static boolean matches402(HttpStatusCode statusCode) {
        return statusCode != null && statusCode.value() == 402;
    }
}
