package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

/**
 * Test upgrade from 029 to latest version
 */
@Testcontainers
class PgVectorEmbeddingStoreUpgradeIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    EmbeddingStore<TextSegment> embeddingStore029;

    EmbeddingStore<TextSegment> embeddingStore;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void beforeEach() {
        final var tableName = "test" + nextInt(3000, 4000);
        embeddingStore029 = PgVectorEmbeddingStore029.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableName)
                .dimension(384)
                .dropTableFirst(true)
                .build();

        embeddingStore = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableName)
                .dimension(384)
                .build();
    }

    @Test
    void upgrade() {
        var embedding = embeddingModel.embed("hello").content();

        var id = embeddingStore029.add(embedding);
        assertThat(id).isNotBlank();

        // Check 029 results
        var relevant = embeddingStore029.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        var match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();

        // new API
        assertThat(embeddingStore029.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build()).matches()).isEqualTo(relevant);

        // Check Latest Store results
        relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();

        // new API
        assertThat(embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build()).matches()).isEqualTo(relevant);
    }
}
