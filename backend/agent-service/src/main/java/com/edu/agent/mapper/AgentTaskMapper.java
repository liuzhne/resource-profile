package com.edu.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.agent.entity.AgentTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;


public interface AgentTaskMapper extends BaseMapper<AgentTask> {

    /**
     * 乐观锁状态流转：只有当前状态=oldStatus时才更新
     */
    @Update("UPDATE agent_task SET status = #{newStatus}, updated_at = UTC_TIMESTAMP(), " +
            "completed_at = CASE WHEN #{newStatus} IN ('COMPLETED','FAILED','REJECTED') " +
            "THEN COALESCE(completed_at,UTC_TIMESTAMP()) ELSE completed_at END " +
            "WHERE id = #{id} AND status = #{oldStatus} AND deleted = 0")
    int updateStatus(@Param("id") Long id,
                     @Param("oldStatus") String oldStatus,
                     @Param("newStatus") String newStatus);
    @Update("UPDATE agent_task SET completed_at=UTC_TIMESTAMP() WHERE id=#{id} AND deleted=0 " +
            "AND status IN ('COMPLETED','REJECTED','FAILED') AND completed_at IS NULL")
    int markTerminalTime(@Param("id") Long id);
}
