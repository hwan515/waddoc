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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * 인증은 되었지만 권한이 부족한 요청을 공통 JSON 에러 형식으로 응답한다.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RestAccessDeniedHandler(
            ObjectMapper objectMapper,
            @Autowired(required = false) Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock != null ? clock : Clock.system(KstTime.ZONE);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(ErrorCode.AUTH_FORBIDDEN.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.AUTH_FORBIDDEN, clock));
    }
}
