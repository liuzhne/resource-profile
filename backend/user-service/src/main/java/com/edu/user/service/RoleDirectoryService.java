package com.edu.user.service;

import com.edu.user.dto.RoleWriteRequest;
import com.edu.common.exception.BusinessException;
import com.edu.common.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RoleDirectoryService {
    private static final String CATALOG = "('teacher:read','teacher:write','student:read','student:write','mental:read','mental:write','data:read','agent:read','agent:write','user:read','user:write')";
    private final JdbcTemplate db;
    private final UserAdministrationService users;
    public List<Map<String,Object>> list() {
        return db.queryForList("SELECT r.id,r.name,r.code,r.description,r.status,r.create_time AS createTime,(SELECT COUNT(DISTINCT u.id) FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id WHERE ur.role_id=r.id AND u.deleted=0) AS userCount FROM sys_role r WHERE r.deleted=0 ORDER BY r.id");
    }
    public List<Map<String,Object>> permissions() {
        var rows=db.queryForList("SELECT id,name,code,parent_id AS parentId FROM sys_permission WHERE deleted=0 AND status=1 AND code IN " + CATALOG + " ORDER BY sort_order,id");
        for(var row:rows) row.put("allowedRoles",allowedRoles((String)row.get("code")));
        return rows;
    }
    public List<Long> selected(Long id) {
        String role=(String)require(id).get("code");
        // Admin is a fixed maintenance identity, not a configurable database permission subset.
        var supported=permissions().stream().filter(p->allowedRoles((String)p.get("code")).contains(role))
                .map(p->((Number)p.get("id")).longValue()).toList();
        if(Roles.ADMIN.equals(role)) return supported;
        return db.queryForList("SELECT rp.permission_id FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.role_id=? AND p.status=1 AND p.deleted=0 AND p.code IN " + CATALOG,Long.class,id)
                .stream().filter(supported::contains).toList();
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void save(Long id, RoleWriteRequest r) {
        users.lockIdentityChanges();
        if(id!=null) {
            Map<String,Object> old=require(id);
            if (!old.get("code").equals(r.getCode())) throw new BusinessException(400,"角色编码不可修改，避免改变授权身份");
            if("admin".equals(r.getCode()) && r.getStatus()!=1) throw new BusinessException(409,"系统管理员角色不可停用");
            if(r.getStatus()==0 && assigned(id)>0) throw new BusinessException(409,"角色仍有账号使用，请先调整账号角色");
            db.update("UPDATE sys_role SET name=?,description=?,status=?,update_time=CURRENT_TIMESTAMP WHERE id=?",r.getName(),r.getDescription(),r.getStatus(),id);
            revoke(id);
        } else {
            var existing=db.queryForList("SELECT id,deleted FROM sys_role WHERE code=? FOR UPDATE",r.getCode());
            if(!existing.isEmpty()) {
                if(((Number)existing.getFirst().get("deleted")).intValue()!=1) throw new BusinessException(409,"该角色编码已存在");
                Long restored=((Number)existing.getFirst().get("id")).longValue();
                db.update("UPDATE sys_role SET name=?,description=?,status=?,deleted=0,update_time=CURRENT_TIMESTAMP WHERE id=?",r.getName(),r.getDescription(),r.getStatus(),restored);
                db.update("DELETE FROM sys_role_permission WHERE role_id=?",restored);
            } else db.update("INSERT INTO sys_role(name,code,description,status,deleted) VALUES(?,?,?,?,0)",r.getName(),r.getCode(),r.getDescription(),r.getStatus());
        }
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id) {
        users.lockIdentityChanges();
        if("admin".equals(require(id).get("code"))) throw new BusinessException(409,"系统管理员角色不可删除");
        if(assigned(id)>0) throw new BusinessException(409,"角色仍有账号使用，请先调整账号角色");
        db.update("UPDATE sys_role SET deleted=1,status=0,update_time=CURRENT_TIMESTAMP WHERE id=?",id);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void setPermissions(Long id, List<Long> ids) {
        users.lockIdentityChanges();
        String role=(String)require(id).get("code");
        if(Roles.ADMIN.equals(role)) throw new BusinessException(409,"管理员固定保留全部维护权限，不可按目录缩减");
        if(ids==null || ids.size()>100 || ids.stream().anyMatch(Objects::isNull)) throw new BusinessException(400,"权限列表无效");
        var catalog=permissions();
        Set<Long> valid=new HashSet<>();
        for(var permission:catalog) valid.add(((Number)permission.get("id")).longValue());
        if(!valid.containsAll(ids)) throw new BusinessException(400,"权限不存在或不可分配");
        Set<Long> supported=new HashSet<>();
        for(var permission:catalog) if(allowedRoles((String)permission.get("code")).contains(role)) supported.add(((Number)permission.get("id")).longValue());
        if(!supported.containsAll(ids)) throw new BusinessException(400,"所选权限超出角色可执行范围；学生本人能力由身份与本人校验控制");
        db.update("DELETE FROM sys_role_permission WHERE role_id=?",id);
        for(Long permission: new LinkedHashSet<>(ids)) db.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(?,?)",id,permission);
        revoke(id);
    }
    private Map<String,Object> require(Long id) {
        var rows=db.queryForList("SELECT code FROM sys_role WHERE id=? AND deleted=0 FOR UPDATE",id);
        if(rows.size()!=1) throw new BusinessException(404,"角色不存在"); return rows.getFirst();
    }
    private long assigned(Long id) { return db.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id WHERE ur.role_id=? AND u.deleted=0",Long.class,id); }
    private void revoke(Long id) { for(Long user:db.queryForList("SELECT user_id FROM sys_user_role WHERE role_id=?",Long.class,id)) users.revoke(user); }
    /** This directory restricts management endpoints. Self-service endpoints keep their owner checks. */
    private static List<String> allowedRoles(String permission) {
        return switch(permission) {
            case "teacher:read","student:read","mental:read","data:read","agent:read","agent:write" -> List.of(Roles.STAFF_VIEW);
            case "student:write" -> List.of(Roles.STUDENT_WRITE);
            case "mental:write" -> List.of(Roles.MENTAL_WRITE);
            case "teacher:write","user:read","user:write" -> List.of(Roles.ADMIN);
            default -> List.of();
        };
    }
}
