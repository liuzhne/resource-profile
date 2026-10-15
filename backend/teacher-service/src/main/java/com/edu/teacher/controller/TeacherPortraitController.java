package com.edu.teacher.controller;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import com.edu.teacher.dto.*;
import com.edu.teacher.entity.*;
import com.edu.teacher.service.TeacherPortraitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/teacher")
@RequiredArgsConstructor
public class TeacherPortraitController {
    private final TeacherPortraitService service;
    private final AccessGuard guard;
    @GetMapping("/options") public Result<Map<String,List<String>>> options(@RequestHeader(value="Authorization",required=false) String auth) { if (!guard.hasAnyRole(auth, Roles.STAFF_VIEW)) return Result.error(403,"无权访问教师字典"); return Result.success(service.options()); }
    @GetMapping("/accounts") public Result<List<Map<String,Object>>> accounts(@RequestHeader(value="Authorization",required=false) String auth) { if (!guard.hasAnyRole(auth, Roles.ADMIN)) return Result.error(403,"无权绑定教师账号"); return Result.success(service.accounts()); }
    @GetMapping("/{id}/portrait") public Result<TeacherPortraitDto> portrait(@PathVariable Long id,@RequestHeader(value="Authorization",required=false) String auth) { if (!guard.allowSelfRoleOrInternal(auth,null,Roles.STAFF_VIEW)) return Result.error(403,"无权查看教师画像"); return Result.success(service.portrait(id)); }
    @PostMapping("/{id}/records") public Result<TeacherRecord> addRecord(@PathVariable Long id,@RequestBody TeacherRecordRequest request,@RequestHeader(value="Authorization",required=false) String auth) { if (!guard.hasAnyRole(auth,Roles.ADMIN)) return Result.error(403,"无权修改教师成果"); return Result.success(service.saveRecord(id,null,request)); }
    @PutMapping("/{id}/records/{recordId}") public Result<TeacherRecord> updateRecord(@PathVariable Long id,@PathVariable Long recordId,@RequestBody TeacherRecordRequest request,@RequestHeader(value="Authorization",required=false) String auth) { if (!guard.hasAnyRole(auth,Roles.ADMIN)) return Result.error(403,"无权修改教师成果"); return Result.success(service.saveRecord(id,recordId,request)); }
    @DeleteMapping("/{id}/records/{recordId}") public Result<Void> deleteRecord(@PathVariable Long id,@PathVariable Long recordId,@RequestHeader(value="Authorization",required=false) String auth) { if (!guard.hasAnyRole(auth,Roles.ADMIN)) return Result.error(403,"无权修改教师成果"); service.deleteRecord(id,recordId); return Result.success(); }
    @PostMapping("/{id}/evaluations") public Result<TeacherEvaluation> addEvaluation(@PathVariable Long id,@RequestBody TeacherEvaluationRequest request,@RequestHeader(value="Authorization",required=false) String auth) { if (!guard.hasAnyRole(auth,Roles.ADMIN)) return Result.error(403,"无权录入教学评价"); return Result.success(service.saveEvaluation(id,null,request)); }
    @PutMapping("/{id}/evaluations/{recordId}") public Result<TeacherEvaluation> updateEvaluation(@PathVariable Long id,@PathVariable Long recordId,@RequestBody TeacherEvaluationRequest request,@RequestHeader(value="Authorization",required=false) String auth) { if (!guard.hasAnyRole(auth,Roles.ADMIN)) return Result.error(403,"无权录入教学评价"); return Result.success(service.saveEvaluation(id,recordId,request)); }
    @DeleteMapping("/{id}/evaluations/{recordId}") public Result<Void> deleteEvaluation(@PathVariable Long id,@PathVariable Long recordId,@RequestHeader(value="Authorization",required=false) String auth) { if (!guard.hasAnyRole(auth,Roles.ADMIN)) return Result.error(403,"无权录入教学评价"); service.deleteEvaluation(id,recordId); return Result.success(); }
}
