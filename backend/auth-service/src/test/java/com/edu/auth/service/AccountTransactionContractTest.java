package com.edu.auth.service;

import com.edu.auth.dto.LoginRequest;
import com.edu.auth.dto.PasswordChangeRequest;
import com.edu.auth.entity.User;
import com.edu.auth.entity.Role;
import com.edu.auth.mapper.UserMapper;
import com.edu.auth.mapper.RoleMapper;
import com.edu.auth.service.impl.AuthServiceImpl;
import com.edu.common.exception.BusinessException;
import com.edu.common.util.JwtUtil;
import com.edu.common.config.JwtProperties;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real Spring transaction advice + real SQL row locks, using synthetic in-memory H2, never production. */
class AccountTransactionContractTest {
    private JdbcTemplate db;
    private DataSourceTransactionManager manager;
    private UserMapper users;
    private StringRedisTemplate redis;
    private BCryptPasswordEncoder encoder;
    private String oldHash;
    private AccountService accounts;
    private String changeSql;

    @BeforeEach void setup() throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:auth-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        db=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
        db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,username VARCHAR(50) UNIQUE,password VARCHAR(100),status INT,deleted INT,update_time TIMESTAMP)");
        db.execute("CREATE TABLE sys_login_log(user_id BIGINT,username VARCHAR(50),role VARCHAR(100),ip VARCHAR(50),login_time TIMESTAMP,status INT)");
        db.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,code VARCHAR(50) UNIQUE,status INT,deleted INT)");
        db.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT)");
        db.execute("CREATE TABLE sys_permission(id BIGINT PRIMARY KEY,code VARCHAR(50),status INT,deleted INT)");
        db.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_id BIGINT)");
        db.update("INSERT INTO sys_role VALUES(1,'admin',1,0),(2,'teacher',1,0)");
        encoder=new BCryptPasswordEncoder(4);oldHash=encoder.encode("synthetic-old-password");
        db.update("INSERT INTO sys_user VALUES(42,'synthetic',?,1,0,CURRENT_TIMESTAMP)",oldHash);
        users=mock(UserMapper.class);redis=mock(StringRedisTemplate.class);
        when(users.selectById(42L)).thenAnswer(inv -> db.queryForObject("SELECT * FROM sys_user WHERE id=42",(row,n)-> {
            var user=new User();user.setId(row.getLong("id"));user.setUsername(row.getString("username"));user.setPassword(row.getString("password"));user.setStatus(row.getInt("status"));return user;
        }));
        changeSql=UserMapper.class.getMethod("changePassword",Long.class,String.class,String.class).getAnnotation(Update.class).value()[0]
                .replace("#{password}","?").replace("#{id}","?").replace("#{oldHash}","?");
        when(users.changePassword(eq(42L),anyString(),anyString())).thenAnswer(inv -> db.update(changeSql,inv.getArgument(2),42L,inv.getArgument(1)));
        accounts=transactional(new AccountService(users,encoder,redis));
    }

    private RoleMapper identityMappers() throws Exception {
        String loginSql=UserMapper.class.getMethod("selectByUsername",String.class).getAnnotation(Select.class).value()[0].replace("#{username}","?");
        when(users.selectByUsername(anyString())).thenAnswer(inv -> db.queryForObject(loginSql,(row,n) -> {
            var user=new User();user.setId(row.getLong("id"));user.setUsername(row.getString("username"));user.setPassword(row.getString("password"));user.setStatus(row.getInt("status"));return user;
        },(Object)inv.getArgument(0)));
        String lockedUserSql=UserMapper.class.getMethod("selectByIdForUpdate",Long.class).getAnnotation(Select.class).value()[0].replace("#{id}","?");
        when(users.selectByIdForUpdate(anyLong())).thenAnswer(inv -> db.queryForObject(lockedUserSql,(row,n) -> {
            var user=new User();user.setId(row.getLong("id"));user.setUsername(row.getString("username"));user.setPassword(row.getString("password"));user.setStatus(row.getInt("status"));return user;
        },(Object)inv.getArgument(0)));
        String auditSql=UserMapper.class.getMethod("recordLogin",Long.class,String.class,String.class,String.class,int.class).getAnnotation(Insert.class).value()[0].replaceAll("#\\{[^}]+}","?");
        doAnswer(inv -> db.update(auditSql,inv.getArgument(0),inv.getArgument(1),inv.getArgument(2),inv.getArgument(3),inv.getArgument(4))).when(users).recordLogin(anyLong(),anyString(),anyString(),anyString(),anyInt());
        var roles=mock(RoleMapper.class);
        String coordinatorSql=RoleMapper.class.getMethod("lockIdentityAdminRole").getAnnotation(Select.class).value()[0];
        when(roles.lockIdentityAdminRole()).thenAnswer(inv -> db.queryForList(coordinatorSql,Long.class));
        String roleSql=RoleMapper.class.getMethod("selectRolesByUserId",Long.class).getAnnotation(Select.class).value()[0].replace("#{userId}","?");
        when(roles.selectRolesByUserId(anyLong())).thenAnswer(inv -> db.query(roleSql,(row,n) -> {
            var role=new Role();role.setId(row.getLong("id"));role.setCode(row.getString("code"));return role;
        },(Object)inv.getArgument(0)));
        String permissionSql=RoleMapper.class.getMethod("selectPermissionsByUserId",Long.class).getAnnotation(Select.class).value()[0].replace("#{userId}","?");
        when(roles.selectPermissionsByUserId(anyLong())).thenAnswer(inv -> db.queryForList(permissionSql,String.class,(Object)inv.getArgument(0)));
        return roles;
    }

    @SuppressWarnings("unchecked") private <T>T transactional(T target) {
        var factory=new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        return (T)factory.getProxy();
    }
    private PasswordChangeRequest request() {var request=new PasswordChangeRequest();request.setOldPassword("synthetic-old-password");request.setNewPassword("synthetic-new-password");return request;}
    private String storedHash() {return db.queryForObject("SELECT password FROM sys_user WHERE id=42",String.class);}

    @Test void missingRevocationConfirmationRollsBackActualPasswordUpdate() {
        when(redis.delete(List.of("token:42","refresh:42"))).thenReturn(null);
        assertThatThrownBy(() -> accounts.changePassword(42L,request())).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.getCode()).isEqualTo(503));
        assertThat(storedHash()).isEqualTo(oldHash);
    }

    @Test void redisExceptionRollsBackActualPasswordUpdate() {
        when(redis.delete(List.of("token:42","refresh:42"))).thenThrow(new IllegalStateException("synthetic Redis failure"));
        assertThatThrownBy(() -> accounts.changePassword(42L,request())).isInstanceOf(IllegalStateException.class);
        assertThat(storedHash()).isEqualTo(oldHash);
    }

    @Test void confirmedOrAlreadyRevokedSessionsAllowPasswordCommit() {
        when(redis.delete(List.of("token:42","refresh:42"))).thenReturn(0L);
        accounts.changePassword(42L,request());
        assertThat(storedHash()).isNotEqualTo(oldHash);
        assertThat(encoder.matches("synthetic-new-password",storedHash())).isTrue();
        verify(redis).delete(List.of("token:42","refresh:42"));
    }

    @Test void rowLockPreventsDelayedOldPasswordLoginFromSurvivingPasswordChangeAndFailedLoginAuditCommits() throws Exception {
        var roles=identityMappers();
        var jwt=mock(JwtUtil.class);
        var verified=new CountDownLatch(1);var issue=new CountDownLatch(1);var changing=new CountDownLatch(1);
        Map<String,String> sessions=new ConcurrentHashMap<>();
        when(jwt.generateAccessToken(eq("42"),any())).thenAnswer(inv -> {
            verified.countDown(); if(!issue.await(5,TimeUnit.SECONDS))throw new IllegalStateException("synthetic lock test timed out");return "synthetic-access";
        });
        when(redis.execute(any(RedisScript.class),eq(List.of("token:42","refresh:42")),eq("synthetic-access"),eq("")))
                .thenAnswer(inv -> {sessions.put("token:42","synthetic-access");return 1L;});
        when(redis.delete(List.of("token:42","refresh:42"))).thenAnswer(inv -> {sessions.clear();return 1L;});
        when(users.changePassword(eq(42L),anyString(),anyString())).thenAnswer(inv -> {changing.countDown();return db.update(changeSql,inv.getArgument(2),42L,inv.getArgument(1));});
        var auth=transactional(new AuthServiceImpl(users,roles,redis,encoder,jwt));
        var login=new LoginRequest();login.setUsername("synthetic");login.setPassword("synthetic-old-password");login.setRemember(false);
        try(var threads=Executors.newFixedThreadPool(2)) {
            var loggedIn=threads.submit(() -> auth.login(login));
            if (!verified.await(5,TimeUnit.SECONDS)) loggedIn.get(1,TimeUnit.SECONDS);
            assertThat(verified.getCount()).isZero();
            var changed=threads.submit(() -> accounts.changePassword(42L,request()));
            assertThat(changing.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> changed.get(150,TimeUnit.MILLISECONDS))
                    .as("password UPDATE waits for login's FOR UPDATE lock").isInstanceOf(TimeoutException.class);
            issue.countDown();
            assertThat(loggedIn.get(5,TimeUnit.SECONDS).getToken()).isEqualTo("synthetic-access");
            changed.get(5,TimeUnit.SECONDS);
            assertThat(sessions).isEmpty();
            assertThat(encoder.matches("synthetic-new-password",storedHash())).isTrue();
            assertThatThrownBy(() -> auth.login(login)).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.getCode()).isEqualTo(401));
            assertThat(db.queryForObject("SELECT COUNT(*) FROM sys_login_log WHERE status=0",Integer.class)).isEqualTo(1);
        } finally {issue.countDown();}
    }

    @Test void roleMutationCommitIsVisibleToWaitingLoginWithoutHoldingCoordinatorDuringBcrypt() throws Exception {
        var roles=identityMappers();
        db.update("INSERT INTO sys_user_role VALUES(42,2)");
        db.update("INSERT INTO sys_permission VALUES(10,'synthetic:old',1,0),(11,'synthetic:new',1,0)");
        db.update("INSERT INTO sys_role_permission VALUES(2,10)");
        var properties=new JwtProperties();properties.setSecret("synthetic-identity-coordinator-0123456789abcdef");
        var jwt=new JwtUtil(properties);jwt.init();
        var checked=new CountDownLatch(1);var mutationLocked=new CountDownLatch(1);var commit=new CountDownLatch(1);
        var loginEncoder=spy(encoder);
        doAnswer(inv -> {boolean matches=encoder.matches(inv.getArgument(0),inv.getArgument(1));checked.countDown();return matches;})
                .when(loginEncoder).matches(any(),anyString());
        Map<String,String> sessions=new ConcurrentHashMap<>();
        when(redis.execute(any(RedisScript.class),eq(List.of("token:42","refresh:42")),anyString(),eq("")))
                .thenAnswer(inv -> {sessions.put("token:42",inv.getArgument(2));return 1L;});
        var auth=transactional(new AuthServiceImpl(users,roles,redis,loginEncoder,jwt));
        var login=new LoginRequest();login.setUsername("synthetic");login.setPassword("synthetic-old-password");login.setRemember(false);
        String coordinatorSql=RoleMapper.class.getMethod("lockIdentityAdminRole").getAnnotation(Select.class).value()[0];
        try(var threads=Executors.newFixedThreadPool(2)) {
            var mutation=threads.submit(() -> new TransactionTemplate(manager).execute(status -> {
                db.queryForList(coordinatorSql,Long.class);
                db.update("DELETE FROM sys_role_permission WHERE role_id=2");
                db.update("INSERT INTO sys_role_permission VALUES(2,11)");
                sessions.clear(); // Simulates role mutation's revocation before commit.
                mutationLocked.countDown();
                try {if(!commit.await(5,TimeUnit.SECONDS))throw new IllegalStateException("synthetic mutation timed out");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return null;
            }));
            assertThat(mutationLocked.await(5,TimeUnit.SECONDS)).isTrue();
            var loggedIn=threads.submit(() -> auth.login(login));
            if (!checked.await(5,TimeUnit.SECONDS)) loggedIn.get(1,TimeUnit.SECONDS);
            assertThat(checked.getCount()).as("BCrypt completes while another transaction holds coordinator").isZero();
            assertThatThrownBy(() -> loggedIn.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            commit.countDown();mutation.get(5,TimeUnit.SECONDS);
            String token=loggedIn.get(5,TimeUnit.SECONDS).getToken();
            assertThat((Object)jwt.getClaim(token,"permissions")).isEqualTo(List.of("synthetic:new"));
            assertThat(sessions.get("token:42")).isEqualTo(token);
        } finally {commit.countDown();}
    }

    @Test void passwordCommitBetweenInitialCheckAndCoordinatorPreventsStaleSessionIssuance() throws Exception {
        var roles=identityMappers();var jwt=mock(JwtUtil.class);
        var verified=new CountDownLatch(1);var continueLogin=new CountDownLatch(1);
        var loginEncoder=spy(encoder);
        doAnswer(inv -> {
            boolean matches=encoder.matches(inv.getArgument(0),inv.getArgument(1));verified.countDown();
            if(!continueLogin.await(5,TimeUnit.SECONDS))throw new IllegalStateException("synthetic password race timed out");
            return matches;
        }).when(loginEncoder).matches(any(),anyString());
        var auth=transactional(new AuthServiceImpl(users,roles,redis,loginEncoder,jwt));
        var login=new LoginRequest();login.setUsername("synthetic");login.setPassword("synthetic-old-password");login.setRemember(false);
        when(redis.delete(List.of("token:42","refresh:42"))).thenReturn(0L);
        try(var threads=Executors.newSingleThreadExecutor()) {
            var loggedIn=threads.submit(() -> auth.login(login));
            if(!verified.await(5,TimeUnit.SECONDS))loggedIn.get(1,TimeUnit.SECONDS);
            assertThat(verified.getCount()).isZero();
            accounts.changePassword(42L,request());
            continueLogin.countDown();
            assertThatThrownBy(() -> loggedIn.get(5,TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(BusinessException.class);
            assertThat(encoder.matches("synthetic-new-password",storedHash())).isTrue();
            verify(jwt,never()).generateAccessToken(anyString(),any());
            verify(redis,never()).execute(any(RedisScript.class),anyList(),any(Object[].class));
        } finally {continueLogin.countDown();}
    }
}
