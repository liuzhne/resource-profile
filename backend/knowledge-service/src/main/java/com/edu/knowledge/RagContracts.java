package com.edu.knowledge;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
public final class RagContracts {
    private RagContracts() { }
    public record Retrieve(@NotEmpty @Size(max=10) List<@NotBlank @Size(max=8000) String> queries,
            @JsonProperty("top_k") Integer topK, @Size(max=4) List<@NotBlank String> sources, @JsonProperty("risk_type") String riskType) { }
    public record Strategy(String name, Integer window, Integer overlap, @JsonProperty("max_chars") Integer maxChars) {
        public Strategy {
            name=name==null?"none":name; window=window==null?800:window;
            overlap=overlap==null?80:overlap; maxChars=maxChars==null?1000:maxChars;
        }
    }
    public record Upsert(@NotBlank String collection,
            @JsonProperty("doc_id") @Pattern(regexp="[A-Za-z0-9_-]{1,48}") @NotNull String docId,
            @NotBlank @Size(max=800000) String text, Map<String,Object> metadata,
            @JsonProperty("chunk_strategy") @Valid Strategy strategy) {
        public Upsert { metadata=metadata==null?Map.of():metadata; strategy=strategy==null?new Strategy(null,null,null,null):strategy; }
    }
}
