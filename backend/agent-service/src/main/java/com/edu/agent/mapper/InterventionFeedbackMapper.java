package com.edu.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.agent.entity.InterventionFeedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * I-5：干预反馈 Mapper。无 XML，全部走 MyBatis-Plus wrapper。
 */
@Mapper
public interface InterventionFeedbackMapper extends BaseMapper<InterventionFeedback> {
}
