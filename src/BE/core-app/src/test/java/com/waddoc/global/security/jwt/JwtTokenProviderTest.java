package com.waddoc.global.security.jwt;

import com.waddoc.domain.user.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    @Test
    void createAccessTokenProducesImmediatelyValidToken() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", "waddoc-dev-secret-key-must-be-at-least-32-chars-long");
        ReflectionTestUtils.setField(provider, "accessTokenExpiry", 900L);
        ReflectionTestUtils.setField(provider, "refreshTokenExpiry", 604800L);
        provider.init();

        String token = provider.createAccessToken("usr_admin", Role.ADMIN);

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo("usr_admin");
        assertThat(provider.getRole(token)).isEqualTo(Role.ADMIN);
    }
}
