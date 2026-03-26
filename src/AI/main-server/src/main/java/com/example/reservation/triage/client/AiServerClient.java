package com.example.reservation.triage.client;

import com.example.reservation.triage.dto.TriageRecommendationRequest;
import com.example.reservation.triage.dto.TriageRecommendationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class AiServerClient {

    private final WebClient webClient;

    public AiServerClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.ai-server.base-url}") String aiServerBaseUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(aiServerBaseUrl).build();
    }

    public TriageRecommendationResponse recommend(TriageRecommendationRequest request) {
        try {
            TriageRecommendationResponse response = webClient.post()
                    .uri("/api/v1/triage/recommendations")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(TriageRecommendationResponse.class)
                    .block();

            if (response != null) {
                return response;
            }
        } catch (Exception ignored) {
            // Fallback keeps the public API stable while the AI server is offline during early setup.
        }

        return new TriageRecommendationResponse(
                "GENERAL",
                "일반내과",
                "정확한 분류가 어려워 일반내과 진료를 먼저 추천합니다. 예약하시겠습니까?",
                "정확한 분류가 어려워 일반내과 진료를 먼저 추천합니다. 예약하시겠습니까?",
                0.0,
                "AI 서버에 연결할 수 없어 메인 서버의 기본 응답을 반환했습니다."
        );
    }
}
