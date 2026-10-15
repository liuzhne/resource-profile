package com.edu.user.service;

import com.edu.user.dto.UserWriteRequest;
import com.edu.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserAdministrationService {
    private final JdbcTemplate db;
    private final StringRedisTemplate redis;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public List<Long> roleIds(Long id) {
        return db.queryForList("SELECT role_id FROM sys_user_role WHERE user_id=? ORDER BY role_id", Long.class, id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long save(Long id, Long actor, UserWriteRequest request) {
        lockIdentityChanges();
        String existingHash = id == null ? null : requireUserLocked(id);
        if (id != null) validateBoundType(id, request.getUserType());
        var roles = request.getRoleIds().stream().distinct().toList();
        List<String> codes = new ArrayList<>();
        for (Long roleId : roles) {
            var result = db.queryForList("SELECT code FROM sys_role WHERE id=? AND status=1 AND deleted=0 FOR UPDATE", String.class, roleId);
            if (result.size()!=1) throw new BusinessException(400, "角色不存在或已停用");
            codes.add(result.getFirst());
        }
        String password = request.getPassword();
        if (id==null && (password==null || password.isBlank())) throw new BusinessException(400, "新增账号必须设置密码");
        if (password!=null && !password.isBlank() && (password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72))
            throw new BusinessException(400, "密码至少12个字符且UTF-8长度不超过72字节");
        Long count = db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username=? AND (? IS NULL OR id<>?)", Long.class, request.getUsername(), id, id);
        if (count!=null && count>0) throw new BusinessException(409, "用户名已存在");
        if (id!=null) {
            if(request.getStatus()==1 && existingHash!=null && existingHash.startsWith("!disabled!") && (password==null || password.isBlank()))
                throw new BusinessException(400,"开通待初始化账号必须设置新密码");
            protectAdmin(id, actor, request.getStatus()!=1 || !codes.contains("admin"));
            db.update("UPDATE sys_user SET username=?,nickname=?,email=?,phone=?,dept_name=?,bio=?,user_type=?,status=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND deleted=0",
                    request.getUsername(),request.getNickname(),request.getEmail(),request.getPhone(),request.getDeptName(),request.getBio(),request.getUserType(),request.getStatus(),id);
            if (password!=null && !password.isBlank()) db.update("UPDATE sys_user SET password=? WHERE id=?", encoder.encode(password),id);
            db.update("DELETE FROM sys_user_role WHERE user_id=?",id);
        } else {
            var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
            db.update(connection -> {
                var ps=connection.prepareStatement("INSERT INTO sys_user(username,password,nickname,email,phone,dept_name,bio,user_type,status,deleted) VALUES(?,?,?,?,?,?,?,?,?,0)", new String[]{"id"});
                Object[] args={request.getUsername(),encoder.encode(password),request.getNickname(),request.getEmail(),request.getPhone(),request.getDeptName(),request.getBio(),request.getUserType(),request.getStatus()};
                for(int i=0;i<args.length;i++) ps.setObject(i+1,args[i]);
                return ps;
            },key);
            id=Objects.requireNonNull(key.getKey()).longValue();
        }
        for (Long roleId : roles) db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",id,roleId);
        revoke(id);
        return id;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id, Long actor) {
        lockIdentityChanges();
        requireUserLocked(id);
        protectAdmin(id,actor,true);
        Long bound=db.queryForObject("SELECT (SELECT COUNT(*) FROM student_info WHERE user_id=? AND deleted=0)+(SELECT COUNT(*) FROM teacher_info WHERE user_id=? AND deleted=0)",Long.class,id,id);
        if(bound!=null && bound>0) throw new BusinessException(409,"账号仍绑定师生档案，请先解除绑定或停用账号");
        db.update("UPDATE sys_user SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);
        revoke(id);
    }

    /** Identity writers and login hold this non-removable role row until commit, before locking accounts. */
    public void lockIdentityChanges() {
        var coordinator = db.queryForList("SELECT id FROM sys_role WHERE code='admin' AND deleted=0 FOR UPDATE", Long.class);
        if (coordinator.size() != 1) throw new BusinessException(503, "管理员角色配置异常，请先修复授权配置");
    }
    private String requireUserLocked(Long id) {
        var rows = db.query("SELECT password FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE", (rs, row) -> rs.getString("password"), id);
        if (rows.size() != 1) throw new BusinessException(404,"账号不存在");
        return rows.getFirst();
    }
    private void validateBoundType(Long id, Integer type) {
        Long students = db.queryForObject("SELECT COUNT(*) FROM student_info WHERE user_id=? AND deleted=0", Long.class, id);
        Long teachers = db.queryForObject("SELECT COUNT(*) FROM teacher_info WHERE user_id=? AND deleted=0", Long.class, id);
        if ((students != null && students > 0 && !Integer.valueOf(2).equals(type)) ||
                (teachers != null && teachers > 0 && !Integer.valueOf(1).equals(type)))
            throw new BusinessException(409, "账号类型与已绑定师生档案不一致，请先校正绑定");
    }
    private void protectAdmin(Long id, Long actor, boolean removing) {
        if (!removing) return;
        if(id.equals(actor)) throw new BusinessException(409,"不能停用、删除或移除自己的管理员权限");
        Integer own=db.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='admin' AND r.deleted=0",Integer.class,id);
        Integer remaining=db.queryForObject("SELECT COUNT(DISTINCT u.id) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE r.code='admin' AND r.status=1 AND r.deleted=0 AND u.status=1 AND u.deleted=0 AND u.id<>?",Integer.class,id);
        if(own!=null && own>0 && (remaining==null || remaining==0)) throw new BusinessException(409,"必须保留至少一个有效管理员");
    }
    public void revoke(Long id) { redis.delete(List.of("token:"+id,"refresh:"+id)); }
}
