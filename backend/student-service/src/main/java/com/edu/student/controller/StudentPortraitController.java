package com.edu.student.controller;
import com.edu.common.result.Result;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.Roles;
import com.edu.student.dto.*;
import com.edu.student.entity.*;
import com.edu.student.service.StudentPortraitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentPortraitController {
    private final StudentPortraitService service;
    private final AccessGuard guard;
    private boolean readable(Long id,String auth) { return guard.allowSelfRoleOrInternal(auth,service.requireStudent(id).getUserId(),Roles.STAFF_VIEW); }
    @GetMapping("/options") public Result<Map<String,List<String>>> options(@RequestHeader(value="Authorization",required=false) String auth) { if(!guard.hasAnyRole(auth,Roles.STAFF_VIEW)) return Result.error(403,"无权访问学生字典"); return Result.success(service.options()); }
    @GetMapping("/accounts") public Result<List<Map<String,Object>>> accounts(@RequestHeader(value="Authorization",required=false) String auth) { if(!guard.hasAnyRole(auth,Roles.STUDENT_WRITE)) return Result.error(403,"无权绑定学生账号"); return Result.success(service.accounts()); }
    @GetMapping("/{id}/rank") public Result<StudentRankDto> rank(@PathVariable Long id,@RequestHeader(value="Authorization",required=false) String auth) { if(!readable(id,auth)) return Result.error(403,"无权查看该学生排名"); return Result.success(service.rank(id)); }
    @GetMapping("/{id}/quality") public Result<List<StudentQualityRecord>> records(@PathVariable Long id,@RequestHeader(value="Authorization",required=false) String auth) { if(!readable(id,auth)) return Result.error(403,"无权查看该学生素质记录"); return Result.success(service.records(id)); }
    @PostMapping("/{id}/quality") public Result<StudentQualityRecord> addRecord(@PathVariable Long id,@RequestBody StudentQualityRecordRequest request,@RequestHeader(value="Authorization",required=false) String auth) { if(!guard.hasAnyRole(auth,Roles.STUDENT_WRITE)) return Result.error(403,"无权修改学生素质记录"); return Result.success(service.saveRecord(id,null,request)); }
    @PutMapping("/{id}/quality/{recordId}") public Result<StudentQualityRecord> updateRecord(@PathVariable Long id,@PathVariable Long recordId,@RequestBody StudentQualityRecordRequest request,@RequestHeader(value="Authorization",required=false) String auth) { if(!guard.hasAnyRole(auth,Roles.STUDENT_WRITE)) return Result.error(403,"无权修改学生素质记录"); return Result.success(service.saveRecord(id,recordId,request)); }
    @DeleteMapping("/{id}/quality/{recordId}") public Result<Void> deleteRecord(@PathVariable Long id,@PathVariable Long recordId,@RequestHeader(value="Authorization",required=false) String auth) { if(!guard.hasAnyRole(auth,Roles.STUDENT_WRITE)) return Result.error(403,"无权修改学生素质记录"); service.deleteRecord(id,recordId); return Result.success(); }
}
