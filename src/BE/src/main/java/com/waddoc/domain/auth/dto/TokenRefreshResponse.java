package com.waddoc.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenRefreshResponse {

    private String accessToken;
    private int expiresIn;
    private LoginResponse.UserInfo user;
}
