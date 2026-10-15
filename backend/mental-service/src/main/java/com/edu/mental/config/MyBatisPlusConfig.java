package com.edu.mental.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                log.debug("start insert fill ....");
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                log.debug("start update fill ....");
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            }
        };
    }
}
