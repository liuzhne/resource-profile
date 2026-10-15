package com.edu.auth.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class AccountRequest {
    @NotBlank @Pattern(regexp="OPEN_ACCOUNT|PASSWORD_RECOVERY") private String type;
    @Size(max=50) private String username;
    @NotBlank @Size(max=100) private String contact;
    @NotBlank @Size(min=10,max=1000) private String reason;
}
