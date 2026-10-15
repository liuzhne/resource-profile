package com.edu.user.service.impl;

import com.edu.common.config.JwtProperties;
import com.edu.common.security.AccessGuard;
import com.edu.common.security.InternalCallCredential;
import com.edu.common.util.JwtUtil;
import com.edu.user.service.RoleDirectoryService;
import com.edu.user.service.UserAdministrationService;
import com.edu.user.dto.UserWriteRequest;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.*;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual proxied role-edit transactions against separate connections; login uses auth's locking lookup/claim SQL contract. */
class RoleSessionConcurrencyTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate db; StringRedisTemplate redis; RoleDirectoryService roles; UserAdministrationService users;
    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext(Config.class);db=context.getBean(JdbcTemplate.class);redis=context.getBean(StringRedisTemplate.class);roles=context.getBean(RoleDirectoryService.class);users=context.getBean(UserAdministrationService.class);
        db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY AUTO_INCREMENT,username VARCHAR(50) UNIQUE,password VARCHAR(100),nickname VARCHAR(50),email VARCHAR(100),phone VARCHAR(20),dept_name VARCHAR(100),bio VARCHAR(1000),user_type INT,status INT,deleted INT,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,code VARCHAR(50),status INT,deleted INT,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT)");
        db.execute("CREATE TABLE sys_permission(id BIGINT PRIMARY KEY,name VARCHAR(50),code VARCHAR(50),parent_id BIGINT DEFAULT 0,status INT,deleted INT,sort_order INT DEFAULT 0)");
        db.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_id BIGINT)");
        db.execute("CREATE TABLE student_info(user_id BIGINT,deleted INT)");db.execute("CREATE TABLE teacher_info(user_id BIGINT,deleted INT)");
        db.update("INSERT INTO sys_user(id,username,password,user_type,status,deleted) VALUES(1,'synthetic-admin','hash',0,1,0),(2,'synthetic-teacher','hash',2,1,0)");
        db.update("INSERT INTO sys_role(id,code,status,deleted) VALUES(1,'admin',1,0),(2,'teacher',1,0),(3,'student',1,0)");
        db.update("INSERT INTO sys_user_role VALUES(1,1),(2,2)");
        db.update("INSERT INTO sys_permission(id,code,status,deleted) VALUES(7,'student:read',1,0),(8,'student:write',1,0)");
        db.update("INSERT INTO sys_role_permission VALUES(2,7),(2,8)");
    }
    @Test void accountDeletionWaitsForPendingProfileBindingAndRejectsAfterItsCommit() throws Exception {
        var bound=new CountDownLatch(1);var finishBinding=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);Future<?> binding=null;Future<?> deletion=null;
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            binding=executor.submit(()->tx.execute(status->{
                // Profile creation takes this valid-account lock before its insert.
                db.queryForList("SELECT id FROM sys_user WHERE id=2 AND status=1 AND deleted=0 AND user_type=2 FOR UPDATE",Long.class);
                db.update("INSERT INTO student_info VALUES(2,0)");bound.countDown();
                await(finishBinding);return null;
            }));
            assertThat(bound.await(2,TimeUnit.SECONDS)).isTrue();
            deletion=executor.submit(()->users.delete(2L,1L));Future<?> pendingDeletion=deletion;
            assertThatThrownBy(()->pendingDeletion.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            finishBinding.countDown();binding.get(2,TimeUnit.SECONDS);
            assertThatThrownBy(()->pendingDeletion.get(2,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(com.edu.common.exception.BusinessException.class).hasRootCauseMessage("账号仍绑定师生档案，请先解除绑定或停用账号");
            assertThat(db.queryForObject("SELECT deleted FROM sys_user WHERE id=2",Integer.class)).isZero();
        } finally {
            finishBinding.countDown();if(binding!=null)binding.get(3,TimeUnit.SECONDS);
            if(deletion!=null)try{deletion.get(3,TimeUnit.SECONDS);}catch(ExecutionException expected){/* checked above */}
            executor.shutdownNow();
        }
    }
    @Test void assigningRoleWaitsForDeletionAndCannotBindDeletedRoleFromOldSnapshot() throws Exception {
        var deleted=new CountDownLatch(1);var finishDeletion=new CountDownLatch(1);
        doAnswer(call->{Object count=call.callRealMethod();deleted.countDown();await(finishDeletion);return count;})
                .when(db).update(eq("UPDATE sys_role SET deleted=1,status=0,update_time=CURRENT_TIMESTAMP WHERE id=?"),eq(3L));
        var executor=Executors.newFixedThreadPool(2);Future<?> deletion=null;Future<?> assignment=null;
        try {
            deletion=executor.submit(()->roles.delete(3L));assertThat(deleted.await(2,TimeUnit.SECONDS)).isTrue();
            var request=new UserWriteRequest();request.setUsername("synthetic-teacher");request.setPassword("");request.setUserType(2);request.setStatus(1);request.setRoleIds(List.of(3L));
            assignment=executor.submit(()->users.save(2L,1L,request));Future<?> pendingAssignment=assignment;
            assertThatThrownBy(()->pendingAssignment.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            finishDeletion.countDown();deletion.get(2,TimeUnit.SECONDS);
            assertThatThrownBy(()->pendingAssignment.get(2,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasRootCauseMessage("角色不存在或已停用");
            assertThat(users.roleIds(2L)).containsExactly(2L);
        } finally {
            finishDeletion.countDown();if(deletion!=null)deletion.get(3,TimeUnit.SECONDS);
            if(assignment!=null)try{assignment.get(3,TimeUnit.SECONDS);}catch(ExecutionException expected){/* checked above */}
            executor.shutdownNow();
        }
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(3,TimeUnit.SECONDS))throw new IllegalStateException("transaction release timeout"); }
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
    }
    @AfterEach void cleanup(){RequestContextHolder.resetRequestAttributes();context.close();}
    @Test void concurrentLoginCannotReissueOldPermissionsAfterRevocationBeforeRoleCommit() throws Exception {
        var revoked=new CountDownLatch(1);var finishRole=new CountDownLatch(1);
        var liveToken=new AtomicReference<String>();
        doAnswer(call->{liveToken.set(null);revoked.countDown();if(!finishRole.await(3,TimeUnit.SECONDS))throw new IllegalStateException("transaction release timeout");return 1L;}).when(redis).delete(anyCollection());
        var props=new JwtProperties();props.setSecret("synthetic-only-role-session-test-secret-32");var jwt=new JwtUtil(props);jwt.init();
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        var executor=Executors.newFixedThreadPool(2);
        Future<?> edit=null;Future<String> login=null;
        try {
            edit=executor.submit(()->roles.setPermissions(2L,List.of(7L)));
            assertThat(revoked.await(2,TimeUnit.SECONDS)).isTrue();
            login=executor.submit(()->tx.execute(status->{
                // Same coordinator -> account -> current claims order as the login contract.
                db.queryForList("SELECT id FROM sys_role WHERE code='admin' AND deleted=0 FOR UPDATE",Long.class);
                db.queryForList("SELECT id FROM sys_user WHERE username='synthetic-teacher' AND deleted=0 FOR UPDATE");
                var permissions=db.queryForList("SELECT DISTINCT p.code FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id JOIN sys_role r ON r.id=rp.role_id AND r.deleted=0 AND r.status=1 JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=2 AND p.deleted=0 AND p.status=1 ORDER BY p.code",String.class);
                String token=jwt.generateAccessToken("2",Map.of("roles",List.of("teacher"),"permissions",permissions));liveToken.set(token);return token;
            }));
            // Permission writer must retain login's identity coordinator until commit.
            Future<String> pendingLogin=login;
            assertThatThrownBy(()->pendingLogin.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            finishRole.countDown();edit.get(2,TimeUnit.SECONDS);
            String token=login.get(2,TimeUnit.SECONDS);assertThat(liveToken.get()).isEqualTo(token);
            var claims=jwt.parseToken(token);assertThat(((Collection<?>)claims.get("permissions")).stream().map(Object::toString).toList()).doesNotContain("student:write");
            var request=new MockHttpServletRequest("PUT","/student/1");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            assertThat(new AccessGuard(jwt,new InternalCallCredential("synthetic-internal-unused-secret-32")).hasAnyRole("Bearer "+token,"teacher")).isFalse();
        } finally {
            finishRole.countDown();
            if(edit!=null)edit.get(3,TimeUnit.SECONDS);
            if(login!=null)login.get(3,TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:role_session_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");}
        @Bean JdbcTemplate jdbc(DataSource source){return spy(new JdbcTemplate(source));}
        @Bean PlatformTransactionManager tx(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean StringRedisTemplate redis(){return mock(StringRedisTemplate.class);}
        @Bean UserAdministrationService users(JdbcTemplate db,StringRedisTemplate redis){return new UserAdministrationService(db,redis);}
        @Bean RoleDirectoryService roles(JdbcTemplate db,UserAdministrationService users){return new RoleDirectoryService(db,users);}
    }
}
