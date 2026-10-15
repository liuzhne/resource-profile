package com.edu.mental.service;

import com.edu.common.exception.BusinessException;
import com.edu.mental.dto.WarningActionRequest;
import com.edu.mental.mapper.MentalAssessmentMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MentalManagementServiceTest {
    @Test void csvPreservesCollegeGroupsEscapesQuotesAndPreventsSpreadsheetFormulaExecution() {
        var mapper=mock(MentalAssessmentMapper.class);when(mapper.selectDeptDistribution()).thenReturn(List.of(Map.of("dept","=MOCK(1),\"学院\"","good",0,"attention",2,"intervention",1),Map.of("dept","虚构第二学院","good",3,"attention",0,"intervention",0)));
        var csv=new String(new MentalManagementService(mapper).exportAnalysis(),StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF维度,"));assertTrue(csv.contains("\"'=MOCK(1),\"\"学院\"\"\""));assertTrue(csv.contains("虚构第二学院"));assertEquals(3,csv.split("\r\n").length);assertFalse(csv.contains("\\r\\n"));
    }
    @Test void invalidPaginationStatusAndDispositionDoNotTouchMapper() {
        var mapper=mock(MentalAssessmentMapper.class);var service=new MentalManagementService(mapper);
        assertThrows(BusinessException.class,()->service.warnings(0,10,null));assertThrows(BusinessException.class,()->service.warnings(1,101,null));assertThrows(BusinessException.class,()->service.warnings(1,10,"MISSING"));
        var request=new WarningActionRequest();request.setStatus("RESOLVED");assertThrows(BusinessException.class,()->service.handleWarning(1L,request,91L));request.setNote("X".repeat(1001));assertThrows(BusinessException.class,()->service.handleWarning(1L,request,91L));verifyNoInteractions(mapper);
    }
}
