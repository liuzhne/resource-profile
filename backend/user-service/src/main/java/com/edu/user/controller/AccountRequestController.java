package com.edu.user.controller;
import com.edu.common.security.*;
import com.edu.common.exception.BusinessException;
import com.edu.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/user/support") @RequiredArgsConstructor
public class AccountRequestController {
    private final JdbcTemplate db;
    private final AccessGuard guard;
    @Data public static class Resolution {
        @NotBlank @Pattern(regexp="RESOLVED|REJECTED") private String status;
        @NotBlank @Size(min=5,max=1000) private String resolution;
    }
    private void require(String auth){if(!guard.hasAnyRole(auth,Roles.ADMIN)) throw new BusinessException(403,"无权查看账号申请");}
    @GetMapping public Result<List<Map<String,Object>>> list(@RequestHeader("Authorization") String auth,@RequestParam(defaultValue="OPEN") String status){
        require(auth);if(!Set.of("OPEN","RESOLVED","REJECTED").contains(status))throw new BusinessException(400,"状态无效");
        return Result.success(db.queryForList("SELECT id,type,username,contact,reason,status,resolution,create_time AS createTime FROM sys_account_request WHERE status=? ORDER BY id DESC LIMIT 100",status));
    }
    @PutMapping("/{id}") public Result<Void> resolve(@RequestHeader("Authorization") String auth,@PathVariable Long id,@Valid @RequestBody Resolution r){
        require(auth);if(db.update("UPDATE sys_account_request SET status=?,resolution=?,handled_by=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND status='OPEN'",r.status,r.resolution,guard.currentUserId(auth),id)!=1)throw new BusinessException(409,"申请不存在或已处理");
        return Result.success();
    }
}
