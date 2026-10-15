package com.edu.mental.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.edu.common.exception.BusinessException;
import com.edu.mental.dto.WarningActionRequest;
import com.edu.mental.entity.MentalAssessment;
import com.edu.mental.mapper.MentalAssessmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class MentalManagementService {
    private final MentalAssessmentMapper mapper;
    public Page<Map<String,Object>> warnings(int page,int size,String status) {
        if(page<1 || size<1 || size>100) throw new BusinessException(400,"分页参数无效");
        if(status!=null && !Set.of("OPEN","IN_PROGRESS","RESOLVED").contains(status)) throw new BusinessException(400,"预警状态无效");
        return mapper.selectWarnings(new Page<>(page,size),status);
    }
    @Transactional public void handleWarning(Long id,WarningActionRequest action,Long actor) {
        if(action==null || action.getStatus()==null || !Set.of("OPEN","IN_PROGRESS","RESOLVED").contains(action.getStatus()))
            throw new BusinessException(400,"预警状态无效");
        if(actor==null) throw new BusinessException(403,"处置需要登录身份");
        String note=action.getNote()==null?"":action.getNote().trim();
        if(note.length()>1000) throw new BusinessException(400,"处置说明不超过1000字");
        if(!"OPEN".equals(action.getStatus()) && note.isBlank()) throw new BusinessException(400,"请填写处置说明");
        MentalAssessment assessment=mapper.selectById(id);
        if(assessment==null) throw new BusinessException(404,"预警不存在");
        if(assessment.getLevel()==null || !Set.of("中度","重度","高危").contains(assessment.getLevel())) throw new BusinessException(400,"该记录不是心理预警");
        var update=new LambdaUpdateWrapper<MentalAssessment>().eq(MentalAssessment::getId,id)
                .set(MentalAssessment::getWarningStatus,action.getStatus()).set(MentalAssessment::getWarningNote,note)
                .set(MentalAssessment::getWarningHandledBy,actor)
                .set(MentalAssessment::getWarningHandledAt,LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        if(mapper.update(null,update)!=1) throw new BusinessException(409,"预警已变化，请刷新");
    }
    public byte[] exportAnalysis() {
        StringBuilder csv=new StringBuilder("\uFEFF维度,分组,良好记录数,关注记录数,干预记录数,比例/人数\r\n");
        for(var row:mapper.selectDeptDistribution()) csv.append(line("学院",row.get("dept"),row.get("good"),row.get("attention"),row.get("intervention"),""));
        for(var row:mapper.selectGradeComparison()) csv.append(line("年级良好率",row.get("grade"),"","","",row.get("rate")));
        for(var row:mapper.selectGenderAnalysis()) csv.append(line("性别",row.get("gender"),row.get("good"),row.get("attention"),row.get("intervention"),""));
        for(var row:mapper.selectFocusGroups()) csv.append(line("重点人群",row.get("group"),"","","",row.get("count")));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static String line(Object... values) {
        StringJoiner out=new StringJoiner(",");
        for(Object value:values) {
            String text=value==null?"":String.valueOf(value);
            String trimmed=text.stripLeading();
            if(!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0))>=0) text="'"+text;
            out.add("\""+text.replace("\"","\"\"")+"\"");
        }
        return out+"\r\n";
    }
}
