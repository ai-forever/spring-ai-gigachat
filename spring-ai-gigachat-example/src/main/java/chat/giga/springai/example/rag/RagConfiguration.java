package chat.giga.springai.example.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfiguration {

    @Bean
    public TextSplitter textSplitter(
            @Value("${rag.chunk-size:450}") int chunkSize,
            @Value("${rag.min-chunk-size-chars:50}") int minChunkSizeChars,
            @Value("${rag.min-chunk-length-to-embed:5}") int minChunkLengthToEmbed,
            @Value("${rag.max-num-chunks:10000}") int maxNumChunks,
            @Value("${rag.keep-separator:true}") boolean keepSeparator) {
        return new TokenTextSplitter(chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator);
    }

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
