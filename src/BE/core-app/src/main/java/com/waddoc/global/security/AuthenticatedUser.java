package com.waddoc.global.security;

import com.waddoc.domain.user.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * 일반 사용자 JWT에서 복원한 최소 인증 주체 정보다.
 */
public record AuthenticatedUser(String userId, Role role) {

    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
