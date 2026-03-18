package com.waddoc.global.security.jwt;

import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.user.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtSecurityIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MissionRepository missionRepository;

    @LocalServerPort
    private int port;

    @Test
    void bearerAccessTokenAuthenticatesProtectedRequest() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("usr_admin", Role.ADMIN);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Void> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Void.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void bearerAccessTokenCanReadMissionDashboardWithoutDateFilter() {
        String accessToken = jwtTokenProvider.createAccessToken("usr_admin", Role.ADMIN);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/missions",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("missions");
    }

    @Test
    void bearerAccessTokenCanReadMissionDetail() {
        String missionId = missionRepository.findAllForAdminDashboard().stream()
                .findFirst()
                .orElseThrow()
                .getPublicId();

        String accessToken = jwtTokenProvider.createAccessToken("usr_admin", Role.ADMIN);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/missions/" + missionId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"missionId\":\"" + missionId + "\"");
    }

    @Test
    void bearerAccessTokenCanReadAdminPatientsWithoutFilters() {
        String accessToken = jwtTokenProvider.createAccessToken("usr_admin", Role.ADMIN);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/patients",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("patients");
    }
}
