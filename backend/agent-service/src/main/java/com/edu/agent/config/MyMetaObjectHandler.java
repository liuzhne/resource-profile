package com.edu.agent.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("开始插入填充...");
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now(java.time.ZoneOffset.UTC));
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now(java.time.ZoneOffset.UTC));
        // completedAt 只在完成时填充，插入时不填（可为 null）
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("开始更新填充...");
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now(java.time.ZoneOffset.UTC));
    }
}