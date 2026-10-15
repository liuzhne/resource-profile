package com.edu.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    @Size(max = 50) private String nickname;
    @Email @Size(max = 100) private String email;
    @Pattern(regexp = "^$|[+0-9() .-]{5,20}", message = "电话格式无效") private String phone;
    @Size(max = 1000) private String bio;
}
