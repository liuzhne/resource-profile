package com.edu.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordChangeRequest {
    @NotBlank @Size(max = 72) private String oldPassword;
    @NotBlank @Size(min = 12, max = 72, message = "新密码长度须为12至72个字符") private String newPassword;
}
