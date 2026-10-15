package com.edu.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.auth.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    // Same protected row used by user/role authorization mutations, always acquired before user locks.
    @Select("SELECT id FROM sys_role WHERE code='admin' AND deleted=0 FOR UPDATE")
    List<Long> lockIdentityAdminRole();

    @Select("SELECT r.* FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0 AND r.status = 1")
    List<Role> selectRolesByUserId(Long userId);

    @Select("SELECT DISTINCT p.code FROM sys_permission p " +
            "JOIN sys_role_permission rp ON rp.permission_id=p.id " +
            "JOIN sys_role r ON r.id=rp.role_id AND r.deleted=0 AND r.status=1 " +
            "JOIN sys_user_role ur ON ur.role_id=r.id " +
            "WHERE ur.user_id=#{userId} AND p.deleted=0 AND p.status=1 ORDER BY p.code")
    List<String> selectPermissionsByUserId(Long userId);
}
