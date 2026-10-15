package com.edu.user.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class RoleWriteRequest {
    @NotBlank @Size(max=50) private String name;
    @NotBlank @Pattern(regexp="admin|teacher|student|counselor|academic_advisor|psychologist") private String code;
    @Size(max=255) private String description;
    @NotNull @Min(0) @Max(1) private Integer status;
}
