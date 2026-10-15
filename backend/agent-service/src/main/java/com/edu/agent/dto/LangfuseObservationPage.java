package com.edu.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** A deliberately small read model: only fixed operational metadata, never content or upstream error text. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LangfuseObservationPage(
        List<Observation> records,
        String nextCursor,
        boolean hasMore,
        String from,
        String to
) {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Observation(
            String id,
            String traceId,
            String name,
            String type,
            String startTime,
            String endTime,
            String model,
            Long inputTokens,
            Long outputTokens,
            Long totalTokens,
            Double latencyMs,
            String level,
            String agentStatus,
            Integer iterations,
            String errorCode,
            String errorStage,
            Integer httpStatus
    ) { }
}
