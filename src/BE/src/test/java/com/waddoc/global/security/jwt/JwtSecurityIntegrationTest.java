package com.waddoc.global.security.jwt;

import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.user.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.auto-create=false",
                "app.seed.default-password=ci-seed-password"
        }
)
class JwtSecurityIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MissionRepository missionRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${telemetry.api-key}")
    private String telemetryApiKey;

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
        String missionId = seededDashboardMission().getPublicId();

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
    void telemetryApiKeyCanPostMissionTelemetry() {
        Mission mission = seededDashboardMission();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", telemetryApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {
                  "source": "ROS2",
                  "sourceEventId": "ros2_msg_abc123",
                  "seqNo": 42,
                  "vehicleId": "%s",
                  "phase": "%s",
                  "latitude": %s,
                  "longitude": %s,
                  "speed": 30.5,
                  "heading": 180,
                  "timestamp": "2026-03-11T09:15:00+09:00",
                  "metadata": {}
                }
                """.formatted(
                mission.getVehicleId(),
                mission.getPhase().name(),
                "36.1395",
                "128.1136"
        );

        ResponseEntity<Void> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/missions/" + mission.getPublicId() + "/telemetry",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Void.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(202);
    }

    @Test
    void swaggerApiDocsIsAccessibleWithoutAuthentication() {
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/swagger/spring/openapi.json",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"openapi\"");
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

    @Test
    void adminAccessTokenCanIssueMonitoringSessionCookie() {
        String accessToken = jwtTokenProvider.createAccessToken("usr_admin", Role.ADMIN);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Void> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/monitoring/session",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Void.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("monitoring_access=");
    }

    @Test
    void monitoringAuthorizeAcceptsIssuedCookie() {
        String accessToken = jwtTokenProvider.createAccessToken("usr_admin", Role.ADMIN);
        HttpHeaders bootstrapHeaders = new HttpHeaders();
        bootstrapHeaders.setBearerAuth(accessToken);

        ResponseEntity<Void> bootstrapResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/monitoring/session",
                HttpMethod.POST,
                new HttpEntity<>(bootstrapHeaders),
                Void.class
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, extractCookieValue(bootstrapResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE)));

        ResponseEntity<Void> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/monitoring/authorize",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Void.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void monitoringAuthorizeRejectsMissingCookie() {
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/monitoring/authorize",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).contains("AUTH_UNAUTHORIZED");
    }

    @Test
    void prometheusEndpointIsAccessibleWithoutAuthentication() {
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/actuator/prometheus",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }

    private String extractCookieValue(String setCookie) {
        assertThat(setCookie).isNotBlank();
        return setCookie.split(";", 2)[0];
    }

    private Mission seededDashboardMission() {
        return missionRepository.findAllForAdminDashboard().stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected at least one seeded dashboard mission for JwtSecurityIntegrationTest"));
    }
}
