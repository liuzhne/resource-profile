package com.edu.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * F-1：PDF 报告异步导出任务。
 * status：PENDING / PROCESSING / DONE / FAILED。
 * 用 String 而非枚举，避免再为 4 个值引入枚举类——状态机简单到可直接字面量比较。
 */
@Data
@TableName("agent_export_task")
public class AgentExportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long userId;

    private String status;

    private String filePath;

    private Long fileSize;

    private String errMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime finishedAt;

    @TableLogic
    private Integer deleted;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";
}
