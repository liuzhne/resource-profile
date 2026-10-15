package com.edu.mental.config;

import com.edu.mental.dto.QuestionnaireUpdateRequest;
import com.edu.mental.dto.SubmitAnswerRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.junit.jupiter.api.Assertions.*;

class MentalJacksonConfigTest {
    private ObjectMapper mapper;
    @BeforeEach
    void setup() {
        var builder = Jackson2ObjectMapperBuilder.json();
        new MentalJacksonConfig().mentalIntegerInputContract().customize(builder);
        mapper = builder.build();
    }
    @Test
    void realIntegerValuesAndZeroAreAccepted() throws Exception {
        var dto = mapper.readValue("{\"userId\":901,\"questionnaireId\":1,\"answers\":[{\"questionId\":10,\"scaleValue\":0,\"optionIndices\":[]}]}", SubmitAnswerRequest.class);
        assertEquals(0, dto.getAnswers().get(0).getScaleValue());
    }
    @Test
    void fractionalScaleIndexesAndIdsAreRejectedBeforeService() {
        for (String answer : new String[]{"{\"questionId\":10,\"scaleValue\":2.5}", "{\"questionId\":10,\"optionIndices\":[0.5]}", "{\"questionId\":10.5}"}) {
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> mapper.readValue("{\"answers\":[" + answer + "]}", SubmitAnswerRequest.class));
        }
    }
    @Test
    void omittedDatesAndExplicitNullRemainDistinctInActualJsonBinding() throws Exception {
        var omitted = mapper.readValue("{\"title\":\"虚构更新\"}", QuestionnaireUpdateRequest.class);
        assertFalse(omitted.isStartTimePresent());
        assertFalse(omitted.isEndTimePresent());
        var cleared = mapper.readValue("{\"startTime\":null,\"endTime\":null}", QuestionnaireUpdateRequest.class);
        assertTrue(cleared.isStartTimePresent());
        assertTrue(cleared.isEndTimePresent());
        assertNull(cleared.getStartTime());
        assertNull(cleared.getEndTime());
    }
}
