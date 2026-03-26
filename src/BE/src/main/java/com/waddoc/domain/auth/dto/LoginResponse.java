package com.waddoc.domain.auth.dto;

import com.waddoc.domain.user.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String accessToken;
    private int expiresIn;
    private UserInfo user;

    @Getter
    @Builder
    public static class UserInfo {
        private String userId;
        private String name;
        private Role role;
    }
}