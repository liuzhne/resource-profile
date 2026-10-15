package com.edu.user.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class UserWriteRequest {
    @NotBlank @Pattern(regexp="[A-Za-z0-9_.@-]{3,50}") private String username;
    @Size(max=72) private String password;
    @Size(max=50) private String nickname;
    @Email @Size(max=100) private String email;
    @Pattern(regexp="^$|[+0-9() .-]{5,20}") private String phone;
    @Size(max=100) private String deptName;
    @Size(max=1000) private String bio;
    @NotNull @Min(0) @Max(2) private Integer userType;
    @NotNull @Min(0) @Max(1) private Integer status;
    @NotEmpty @Size(max=6) private List<@Positive Long> roleIds;
}
