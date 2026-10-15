package com.edu.student.dto;
import com.edu.common.exception.BusinessException;
import com.edu.student.entity.StudentQualityRecord;
import lombok.Data;
@Data
public class StudentQualityRecordRequest {
    private String kind;
    private String title;
    private java.time.LocalDate eventDate;
    private String description;
    public StudentQualityRecord validatedEntity() {
        if (title == null || title.isBlank() || title.length() > 200) throw new BusinessException(400, "标题不能为空且最多 200 字");
        if (eventDate == null) throw new BusinessException(400, "请选择记录日期");
        if (!java.util.Set.of("COMPETITION", "HONOR", "VOLUNTEER").contains(kind == null ? "" : kind)) throw new BusinessException(400, "记录类型无效");
        if (description != null && description.length() > 2000) throw new BusinessException(400, "说明最多 2000 字");
        StudentQualityRecord value = new StudentQualityRecord();
        value.setKind(kind);
        value.setTitle(title);
        value.setEventDate(eventDate);
        value.setDescription(description);
        value.setTitle(title.trim());
        return value;
    }
}
