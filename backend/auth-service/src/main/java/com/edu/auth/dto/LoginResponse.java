package com.edu.auth.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class LoginResponse {

    private String token;
    @JsonIgnore
    private String refreshToken;
    private Long expiresIn;
}
