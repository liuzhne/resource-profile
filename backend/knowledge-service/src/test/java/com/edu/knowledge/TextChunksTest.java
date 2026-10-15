package com.edu.knowledge;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TextChunksTest {
    @Test void noneNeverSilentlyTruncatesUnicode() {
        assertThat(TextChunks.split("学".repeat(1000),new RagContracts.Strategy(null,null,null,null))).containsExactly("学".repeat(1000));
        assertThatThrownBy(()->TextChunks.split("学".repeat(1366),new RagContracts.Strategy(null,null,null,null))).isInstanceOf(KnowledgeException.class);
    }
    @Test void fixedSizePreservesCodePointsAndOverlap() {
        assertThat(TextChunks.split("a😀b😀c",new RagContracts.Strategy("fixed_size",3,1,null))).containsExactly("a😀b","b😀c");
    }
    @Test void sentencesSplitAtPunctuationAndLongSentenceWithoutDataLoss() {
        assertThat(TextChunks.split("你好。世界！abcdefgh",new RagContracts.Strategy("sentence",null,null,3))).containsExactly("你好。","世界！","abc","def","gh");
    }
    @Test void invalidStrategiesAndUnboundedChunkCountsAreRejected() {
        for(var strategy:java.util.List.of(new RagContracts.Strategy("fixed_size",1,1,null),new RagContracts.Strategy("sentence",null,null,0),new RagContracts.Strategy("other",null,null,null)))
            assertThatThrownBy(()->TextChunks.split("text",strategy)).isInstanceOf(KnowledgeException.class);
        assertThatThrownBy(()->TextChunks.split("a".repeat(201),new RagContracts.Strategy("fixed_size",1,0,null))).isInstanceOf(KnowledgeException.class);
    }
}
