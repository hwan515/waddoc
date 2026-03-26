package com.waddoc.global.config;

import com.waddoc.global.security.RestAccessDeniedHandler;
import com.waddoc.global.security.RestAuthenticationEntryPoint;
import com.waddoc.global.security.jwt.JwtAuthenticationFilter;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
<<<<<<< HEAD
=======
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
>>>>>>> 910266df2b274cc347bea2b6dc1b06525dfa9b0a
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
<<<<<<< HEAD
=======
import org.springframework.core.annotation.Order;
>>>>>>> 910266df2b274cc347bea2b6dc1b06525dfa9b0a

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
<<<<<<< HEAD
=======
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    @Order(2)
>>>>>>> 910266df2b274cc347bea2b6dc1b06525dfa9b0a
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
<<<<<<< HEAD
=======
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
>>>>>>> 910266df2b274cc347bea2b6dc1b06525dfa9b0a
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/swagger/**").permitAll()
                        .requestMatchers("/api/v1/intake/**").permitAll()
                        .requestMatchers("/api/v1/terminal/bootstrap-token").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/guardians/signup").permitAll()
<<<<<<< HEAD
=======
                        .requestMatchers("/api/v1/admin/monitoring/authorize").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/monitoring/session").permitAll()
>>>>>>> 910266df2b274cc347bea2b6dc1b06525dfa9b0a
                        .requestMatchers("/api/v1/bookings/**").permitAll()
                        .requestMatchers("/api/v1/sessions/webhook/livekit").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/missions/*/telemetry").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
