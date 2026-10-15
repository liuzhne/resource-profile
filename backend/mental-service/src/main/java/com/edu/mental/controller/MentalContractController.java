package com.edu.mental.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import com.edu.mental.dto.*;
import com.edu.mental.entity.Question;
import com.edu.mental.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequestMapping("/mental") @RequiredArgsConstructor
public class MentalContractController {
    private final MentalAssessmentService assessments;
    private final QuestionnaireService questionnaires;
    private final MentalManagementService management;
    private final AccessGuard access;

    @GetMapping("/students/{studentId}/assessments")
    public Result<List<MentalHistoryDto>> studentHistory(@PathVariable Long studentId,
            @RequestHeader(value="Authorization",required=false) String auth) {
        if(!access.allowSelfRoleOrInternal(auth,null,Roles.STAFF_VIEW)) return Result.error(403,"无权访问心理管理数据");
        return Result.success(assessments.historyForStudent(studentId));
    }
    @GetMapping("/analysis/export.csv")
    public ResponseEntity<byte[]> export(@RequestHeader(value="Authorization",required=false) String auth) {
        if(!access.hasAnyRole(auth,Roles.STAFF_VIEW)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=mental-analysis.csv").body(management.exportAnalysis());
    }
    @PostMapping(value="/questionnaires/{id}/import/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<Question>> preview(@PathVariable Long id,@RequestPart("file") MultipartFile file,
            @RequestHeader(value="Authorization",required=false) String auth) {
        if(!access.hasAnyRole(auth,Roles.MENTAL_WRITE)) return Result.error(403,"仅管理员或心理咨询师可导入题目");
        return Result.success(questionnaires.previewImport(id,file));
    }
    @PostMapping(value="/questionnaires/{id}/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> importFile(@PathVariable Long id,@RequestPart("file") MultipartFile file,
            @RequestHeader(value="Authorization",required=false) String auth) {
        if(!access.hasAnyRole(auth,Roles.MENTAL_WRITE)) return Result.error(403,"仅管理员或心理咨询师可导入题目");
        questionnaires.importQuestionsFromExcel(id,file); return Result.success();
    }
    @GetMapping("/warnings")
    public Result<Page<Map<String,Object>>> warnings(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int size,@RequestParam(required=false) String status,
            @RequestHeader(value="Authorization",required=false) String auth) {
        if(!access.hasAnyRole(auth,Roles.STAFF_VIEW)) return Result.error(403,"无权访问心理预警");
        var result=management.warnings(page,size,status);
        if(!access.hasAnyRole(auth,Roles.EXTREME_VIEW)) result.getRecords().forEach(r -> r.remove("warningNote"));
        return Result.success(result);
    }
    @PatchMapping("/warnings/{id}")
    public Result<Void> handle(@PathVariable Long id,@RequestBody WarningActionRequest action,
            @RequestHeader(value="Authorization",required=false) String auth) {
        if(!access.hasAnyRole(auth,Roles.MENTAL_WRITE)) return Result.error(403,"仅管理员或心理咨询师可处置心理预警");
        management.handleWarning(id,action,access.currentUserId(auth)); return Result.success();
    }
}
