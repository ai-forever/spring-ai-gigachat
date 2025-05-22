package chat.giga.springai.example.rag;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Сервис для демонстрации <a href="https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html">ETL pipeline</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorStoreInitializer {

    public static final String RAG_FILE_PATH = "rag/rag.docx";
    private static boolean isInitialized = false;

    private final TextSplitter textSplitter;

    @SneakyThrows
    public void initialize(VectorStore vectorStore) {
        if (isInitialized) return;

        // Создаем TikaDocumentReader для чтения DOCX файла
        ClassPathResource ragFileResource = new ClassPathResource(RAG_FILE_PATH);
        TikaDocumentReader documentReader = new TikaDocumentReader(ragFileResource);

        // Читаем документ
        List<Document> documents = documentReader.get();

        // Разбиваем документ на меньшие части, если необходимо
        List<Document> splitDocuments = textSplitter.apply(documents);

        // Загружаем данные в vector store
        vectorStore.add(splitDocuments);

        isInitialized = true;
    }
}
