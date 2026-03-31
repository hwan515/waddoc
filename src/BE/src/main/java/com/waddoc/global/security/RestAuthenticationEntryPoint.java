package com.waddoc.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.error.ErrorResponse;
import com.waddoc.global.util.KstTime;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RestAuthenticationEntryPoint(
            ObjectMapper objectMapper,
            @Autowired(required = false) Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock != null ? clock : Clock.system(KstTime.ZONE);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(ErrorCode.AUTH_UNAUTHORIZED.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.AUTH_UNAUTHORIZED, clock));
    }
}
