package com.edu.student.dto;
import com.edu.common.exception.BusinessException;
import com.edu.student.entity.Student;
import lombok.Data;
@Data
public class StudentWriteRequest {
    private Long userId;
    private String studentId;
    private String name;
    private Integer gender;
    private java.time.LocalDate birthDate;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean birthDateProvided;
    public void setBirthDate(java.time.LocalDate birthDate) {
        this.birthDate = birthDate;
        this.birthDateProvided = true;
    }
    private String deptName;
    private String majorName;
    private String grade;
    private String className;
    private java.time.LocalDate enrollmentDate;
    private java.time.LocalDate expectedGraduation;
    private java.math.BigDecimal gpa;
    private Integer credits;
    private Integer status;
    public Student validatedEntity() {
        if (userId == null || userId <= 0) throw new BusinessException(400, "请选择有效的关联账号");
        if (gender != null && gender != 0 && gender != 1) throw new BusinessException(400, "性别值无效");
        if (studentId == null || studentId.isBlank()) throw new BusinessException(400, "studentId 不能为空");
        if (name == null || name.isBlank()) throw new BusinessException(400, "name 不能为空");
        if (studentId != null && studentId.length() > 50) throw new BusinessException(400, "studentId 长度不能超过 50");
        if (name != null && name.length() > 50) throw new BusinessException(400, "name 长度不能超过 50");
        if (deptName != null && deptName.length() > 100) throw new BusinessException(400, "deptName 长度不能超过 100");
        if (majorName != null && majorName.length() > 100) throw new BusinessException(400, "majorName 长度不能超过 100");
        if (grade != null && grade.length() > 20) throw new BusinessException(400, "grade 长度不能超过 20");
        if (className != null && className.length() > 50) throw new BusinessException(400, "className 长度不能超过 50");
        if (status == null) status = 1;
        if (status < 0 || status > 2) throw new BusinessException(400, "学生状态无效");
        if (gpa != null && (gpa.signum() < 0 || gpa.compareTo(new java.math.BigDecimal("4.00")) > 0 || gpa.scale() > 2)) throw new BusinessException(400, "GPA 必须为 0–4，最多两位小数");
        if (credits != null && credits < 0) throw new BusinessException(400, "学分不能为负数");
        if (enrollmentDate != null && expectedGraduation != null && expectedGraduation.isBefore(enrollmentDate)) throw new BusinessException(400, "毕业日期不能早于入学日期");
        Student value = new Student();
        value.setUserId(userId);
        value.setStudentId(studentId == null || studentId.isBlank() ? null : studentId.trim());
        value.setName(name == null || name.isBlank() ? null : name.trim());
        value.setGender(gender);
        value.setBirthDate(birthDate);
        value.setClearBirthDate(birthDateProvided && birthDate == null);
        value.setDeptName(deptName == null || deptName.isBlank() ? null : deptName.trim());
        value.setMajorName(majorName == null || majorName.isBlank() ? null : majorName.trim());
        value.setGrade(grade == null || grade.isBlank() ? null : grade.trim());
        value.setClassName(className == null || className.isBlank() ? null : className.trim());
        value.setEnrollmentDate(enrollmentDate);
        value.setExpectedGraduation(expectedGraduation);
        value.setGpa(gpa);
        value.setCredits(credits);
        value.setStatus(status);
        return value;
    }
}
