package com.edu.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserInfoResponse {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer userType;
    private String deptName;
    private List<String> roles;
    private List<String> permissions;
    private String bio;
}
