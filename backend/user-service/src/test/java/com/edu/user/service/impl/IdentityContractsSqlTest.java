package com.edu.user.service.impl;

import com.edu.user.service.UserAdministrationService;
import com.edu.user.service.RoleDirectoryService;
import com.edu.user.dto.UserWriteRequest;
import com.edu.user.dto.RoleWriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class IdentityContractsSqlTest {
    JdbcTemplate db;
    StringRedisTemplate redis;
    UserAdministrationService users;
    RoleDirectoryService roles;
    @BeforeEach void setUp() {
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:identity_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY AUTO_INCREMENT,username VARCHAR(50) UNIQUE,password VARCHAR(100),nickname VARCHAR(50),email VARCHAR(100),phone VARCHAR(20),dept_name VARCHAR(100),bio VARCHAR(1000),user_type INT,status INT,deleted INT,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY AUTO_INCREMENT,name VARCHAR(50),code VARCHAR(50) UNIQUE,description VARCHAR(255),status INT,deleted INT,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT,UNIQUE(user_id,role_id))");
        db.execute("CREATE TABLE sys_permission(id BIGINT PRIMARY KEY,name VARCHAR(50),code VARCHAR(100),parent_id BIGINT,status INT,deleted INT,sort_order INT)");
        db.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_id BIGINT,UNIQUE(role_id,permission_id))");
        db.execute("CREATE TABLE student_info(user_id BIGINT,deleted INT)");db.execute("CREATE TABLE teacher_info(user_id BIGINT,deleted INT)");
        db.update("INSERT INTO sys_role(id,name,code,status,deleted) VALUES(1,'Admin','admin',1,0),(2,'Teacher','teacher',1,0),(3,'Student','student',1,0),(4,'Psychologist','psychologist',1,0)");
        db.update("INSERT INTO sys_user(id,username,password,user_type,status,deleted) VALUES(1,'admin-test','hash',0,1,0)");
        db.update("INSERT INTO sys_user_role VALUES(1,1)");
        db.update("INSERT INTO sys_permission VALUES(7,'Student read','student:read',0,1,0,1),(8,'Student write','student:write',0,1,0,2),(9,'Teacher write','teacher:write',0,1,0,3),(10,'Mental read','mental:read',0,1,0,4),(11,'Mental write','mental:write',0,1,0,5),(12,'User read','user:read',0,1,0,6),(13,'Agent write','agent:write',0,1,0,7)");
        redis=mock(StringRedisTemplate.class);users=new UserAdministrationService(db,redis);roles=new RoleDirectoryService(db,users);
    }
    UserWriteRequest request(String username) {
        var r=new UserWriteRequest();r.setUsername(username);r.setPassword("a-synthetic-password-123");r.setUserType(2);r.setStatus(1);r.setRoleIds(List.of(3L));return r;
    }
    @Test void userCrudPersistsHashProfileRolesAndRevokesSessions() {
        var r=request("synthetic-student");r.setEmail("student@example.test");r.setDeptName("Synthetic Department");
        Long id=users.save(null,1L,r);
        String hash=db.queryForObject("SELECT password FROM sys_user WHERE id=?",String.class,id);
        assertThat(hash).isNotEqualTo(r.getPassword());assertThat(new BCryptPasswordEncoder().matches(r.getPassword(),hash)).isTrue();
        assertThat(users.roleIds(id)).containsExactly(3L);
        r.setPassword("");r.setEmail(null);r.setRoleIds(List.of(2L));r.setStatus(0);users.save(id,1L,r);
        assertThat(db.queryForObject("SELECT password FROM sys_user WHERE id=?",String.class,id)).isEqualTo(hash);
        assertThat(db.queryForObject("SELECT email FROM sys_user WHERE id=?",String.class,id)).isNull();
        assertThat(users.roleIds(id)).containsExactly(2L);
        users.delete(id,1L);assertThat(db.queryForObject("SELECT deleted FROM sys_user WHERE id=?",Integer.class,id)).isEqualTo(1);
        verify(redis,atLeastOnce()).delete(List.of("token:"+id,"refresh:"+id));
    }
    @Test void rejectsDuplicateUnknownRoleWeakPasswordAndBoundDeletion() {
        var r=request("synthetic-test");Long id=users.save(null,1L,r);
        assertThatThrownBy(()->users.save(null,1L,r)).hasMessage("用户名已存在");
        r.setUsername("synthetic-other");r.setRoleIds(List.of(99L));assertThatThrownBy(()->users.save(null,1L,r)).hasMessage("角色不存在或已停用");
        r.setRoleIds(List.of(3L));r.setPassword("weak");assertThatThrownBy(()->users.save(null,1L,r)).hasMessageContaining("密码至少12");
        db.update("INSERT INTO student_info VALUES(?,0)",id);assertThatThrownBy(()->users.delete(id,1L)).hasMessageContaining("仍绑定");
    }
    @Test void preservesSelfAndLastAdministrator() {
        var r=request("admin-test");r.setPassword("");r.setStatus(0);r.setRoleIds(List.of(1L));
        assertThatThrownBy(()->users.save(1L,1L,r)).hasMessageContaining("自己的管理员");
        assertThatThrownBy(()->users.save(1L,99L,r)).hasMessageContaining("至少一个有效管理员");
    }
    @Test void boundTypeChangesRejectIncompatibleTypesButPermitAccountDeactivation() {
        var student=request("bound-student");Long id=users.save(null,1L,student);db.update("INSERT INTO student_info VALUES(?,0)",id);
        student.setPassword("");student.setUserType(1);
        assertThatThrownBy(()->users.save(id,1L,student)).hasMessageContaining("账号类型与已绑定");
        student.setUserType(2);student.setStatus(0);users.save(id,1L,student);
        assertThat(db.queryForObject("SELECT status FROM sys_user WHERE id=?",Integer.class,id)).isZero();
        var teacher=request("bound-teacher");teacher.setUserType(1);teacher.setRoleIds(List.of(2L));Long teacherId=users.save(null,1L,teacher);db.update("INSERT INTO teacher_info VALUES(?,0)",teacherId);
        teacher.setPassword("");teacher.setUserType(2);
        assertThatThrownBy(()->users.save(teacherId,1L,teacher)).hasMessageContaining("账号类型与已绑定");
    }
    @Test void missingAdministratorCoordinatorFailsClosedBeforeIdentityMutation() {
        db.update("UPDATE sys_role SET deleted=1 WHERE code='admin'");
        assertThatThrownBy(()->users.save(null,1L,request("cannot-create"))).hasMessageContaining("管理员角色配置异常");
        assertThatThrownBy(()->roles.setPermissions(2L,List.of(7L))).hasMessageContaining("管理员角色配置异常");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sys_user",Integer.class)).isEqualTo(1);
    }
    @Test void roleCountsPermissionSelectionAndRevocationUseRealTables() {
        var r=request("synthetic-teacher");r.setRoleIds(List.of(2L));Long id=users.save(null,1L,r);
        var teacher=roles.list().stream().filter(row->row.get("code").equals("teacher")).findFirst().orElseThrow();
        assertThat(((Number)teacher.get("userCount")).intValue()).isEqualTo(1);
        roles.setPermissions(2L,List.of(7L,8L,7L));assertThat(roles.selected(2L)).containsExactlyInAnyOrder(7L,8L);
        roles.setPermissions(2L,List.of(7L));assertThat(roles.selected(2L)).containsExactly(7L);
        verify(redis,atLeastOnce()).delete(List.of("token:"+id,"refresh:"+id));
        assertThatThrownBy(()->roles.setPermissions(2L,List.of(999L))).hasMessageContaining("权限不存在");
        assertThatThrownBy(()->roles.delete(2L)).hasMessageContaining("仍有账号使用");
        assertThatThrownBy(()->roles.delete(1L)).hasMessageContaining("不可删除");
    }
    @Test void deletedUnusedCanonicalRoleCanBeRestoredWithoutOldPermissions() {
        roles.delete(3L);var r=new RoleWriteRequest();r.setName("Student restored");r.setCode("student");r.setStatus(1);roles.save(null,r);
        assertThat(roles.list()).hasSize(4);assertThat(roles.selected(3L)).isEmpty();
        r.setCode("teacher");assertThatThrownBy(()->roles.save(3L,r)).hasMessageContaining("编码不可修改");
    }
    @Test void catalogExplainsCanonicalRoleUpperBoundsAndStudentSelfServiceIsSeparate() {
        var catalog=roles.permissions();
        var teacherWrite=catalog.stream().filter(p->p.get("code").equals("teacher:write")).findFirst().orElseThrow();
        assertThat(((Collection<?>)teacherWrite.get("allowedRoles")).stream().map(Object::toString).toList()).containsExactly("admin");
        var studentWrite=catalog.stream().filter(p->p.get("code").equals("student:write")).findFirst().orElseThrow();
        assertThat(((Collection<?>)studentWrite.get("allowedRoles")).stream().map(Object::toString).toList()).containsExactly("admin","teacher");
        assertThat(catalog).allSatisfy(p->assertThat(((Collection<?>)p.get("allowedRoles")).stream().map(Object::toString).toList()).doesNotContain("student"));
    }
    @Test void unsupportedRolePermissionsFailBeforeAnyPartialWriteOrRevocation() {
        db.update("INSERT INTO sys_user_role VALUES(1,2),(1,4)");
        roles.setPermissions(2L,List.of(7L,8L));roles.setPermissions(4L,List.of(10L,11L));clearInvocations(redis);
        assertThatThrownBy(()->roles.setPermissions(2L,List.of(7L,9L))).hasMessageContaining("超出角色");
        assertThat(roles.selected(2L)).containsExactlyInAnyOrder(7L,8L);
        assertThatThrownBy(()->roles.setPermissions(4L,List.of(10L,8L))).hasMessageContaining("超出角色");
        assertThat(roles.selected(4L)).containsExactlyInAnyOrder(10L,11L);
        assertThatThrownBy(()->roles.setPermissions(3L,List.of(7L))).hasMessageContaining("学生本人能力");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE role_id=3",Integer.class)).isZero();
        verifyNoInteractions(redis);
    }
    @Test void supportedTeacherStudentWritePersistsAndRevokesActualMembers() {
        db.update("INSERT INTO sys_user_role VALUES(1,2)");roles.setPermissions(2L,List.of(8L));
        assertThat(roles.selected(2L)).containsExactly(8L);
        assertThat(db.queryForObject("SELECT permission_id FROM sys_role_permission WHERE role_id=2",Long.class)).isEqualTo(8L);
        verify(redis).delete(List.of("token:1","refresh:1"));
    }
    @Test void administratorHasFixedEffectiveCatalogAndCannotSaveInertCheckboxes() {
        assertThat(roles.selected(1L)).containsExactly(7L,8L,9L,10L,11L,12L,13L);
        assertThatThrownBy(()->roles.setPermissions(1L,List.of(7L))).hasMessageContaining("管理员固定保留");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE role_id=1",Integer.class)).isZero();
        verifyNoInteractions(redis);
    }
    @Test void initAndMigrationDefaultGrantsStayInsideControllerRoleBounds() throws Exception {
        var root=java.nio.file.Path.of("").toAbsolutePath();
        while(!java.nio.file.Files.exists(root.resolve("sql/init/09_identity_contract.sql"))) root=root.getParent();
        String init=java.nio.file.Files.readString(root.resolve("sql/init/09_identity_contract.sql"));
        assertThat(java.nio.file.Files.readString(root.resolve("sql/migrations/20261007_04_identity_contract.sql"))).isEqualTo(init);
        var seed=java.util.regex.Pattern.compile("INSERT IGNORE INTO sys_role_permission\\(role_id,permission_id\\)\\s+SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p.*?;",java.util.regex.Pattern.DOTALL).matcher(init);
        assertThat(seed.find()).isTrue();db.execute(seed.group());
        assertThat(roles.selected(2L)).contains(8L,13L).doesNotContain(9L);
        assertThat(roles.selected(4L)).contains(11L).doesNotContain(8L);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE role_id=3",Integer.class)).isZero();
        var catalog=roles.permissions();
        for(var row:db.queryForList("SELECT r.code AS roleCode,p.code AS permissionCode FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id")) {
            var permission=catalog.stream().filter(p->p.get("code").equals(row.get("permissionCode"))).findFirst().orElseThrow();
            assertThat(((Collection<?>)permission.get("allowedRoles")).stream().map(Object::toString).toList()).contains((String)row.get("roleCode"));
        }
    }
}
