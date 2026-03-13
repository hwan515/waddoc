package com.example.reservation.chat.service;

import com.example.reservation.chat.domain.ChatSessionState;
import com.example.reservation.chat.dto.ChatMessageView;
import com.example.reservation.chat.dto.ConfirmReservationRequest;
import com.example.reservation.chat.dto.ConfirmReservationResponse;
import com.example.reservation.chat.dto.CreateSessionRequest;
import com.example.reservation.chat.dto.CreateSessionResponse;
import com.example.reservation.chat.dto.MessageHistoryResponse;
import com.example.reservation.chat.dto.SttConnectionInfo;
import com.example.reservation.chat.dto.SubmitMessageRequest;
import com.example.reservation.chat.dto.SubmitMessageResponse;
import com.example.reservation.triage.client.AiServerClient;
import com.example.reservation.triage.dto.TriageRecommendationRequest;
import com.example.reservation.triage.dto.TriageRecommendationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatSessionService {

    private final Map<String, ChatSessionState> sessions = new ConcurrentHashMap<>();
    private final Map<String, SubmitMessageResponse> responsesByTurn = new ConcurrentHashMap<>();
    private final AiServerClient aiServerClient;
    private final String sttWsBaseUrl;

    public ChatSessionService(
            AiServerClient aiServerClient,
            @Value("${app.stt.ws-base-url}") String sttWsBaseUrl
    ) {
        this.aiServerClient = aiServerClient;
        this.sttWsBaseUrl = sttWsBaseUrl;
    }

    public CreateSessionResponse createSession(CreateSessionRequest request) {
        String sessionId = "cs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String locale = request.locale() == null || request.locale().isBlank() ? "ko-KR" : request.locale();
        List<ChatMessageView> initialMessages = Collections.synchronizedList(new ArrayList<>());
        initialMessages.add(new ChatMessageView(
                "msg_assistant_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                "ASSISTANT",
                "증상을 말씀해주세요."
        ));

        sessions.put(sessionId, new ChatSessionState(
                sessionId,
                locale,
                initialMessages
        ));

        return new CreateSessionResponse(
                sessionId,
                new SttConnectionInfo(
                        sttWsBaseUrl + "/api/v1/stt/streams/" + sessionId,
                        "stt_" + sessionId,
                        16000,
                        200,
                        "pcm_s16le"
                )
        );
    }

    public MessageHistoryResponse getMessages(String sessionId) {
        ChatSessionState session = getRequiredSession(sessionId);
        return new MessageHistoryResponse(sessionId, List.copyOf(session.messages()));
    }

    public SubmitMessageResponse submitMessage(String sessionId, SubmitMessageRequest request) {
        ChatSessionState session = getRequiredSession(sessionId);
        String turnKey = sessionId + ":" + request.turnId();

        SubmitMessageResponse existingResponse = responsesByTurn.get(turnKey);
        if (existingResponse != null) {
            return existingResponse;
        }

        List<ChatMessageView> priorMessages = List.copyOf(session.messages());

        ChatMessageView userMessage = new ChatMessageView(
                "msg_user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                "USER",
                request.text()
        );
        session.messages().add(userMessage);

        TriageRecommendationResponse recommendation = aiServerClient.recommend(
                new TriageRecommendationRequest(
                        sessionId,
                        request.turnId(),
                        request.text(),
                        priorMessages
                )
        );

        ChatMessageView assistantMessage = new ChatMessageView(
                "msg_assistant_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                "ASSISTANT",
                recommendation.assistantMessage()
        );
        session.messages().add(assistantMessage);

        SubmitMessageResponse response = new SubmitMessageResponse(
                sessionId,
                userMessage,
                assistantMessage,
                recommendation,
                recommendation.ttsText()
        );

        responsesByTurn.put(turnKey, response);
        return response;
    }

    public ConfirmReservationResponse confirmReservation(
            String sessionId,
            ConfirmReservationRequest request
    ) {
        getRequiredSession(sessionId);

        return new ConfirmReservationResponse(
                sessionId,
                "PENDING_RESERVATION",
                request.departmentCode()
        );
    }

    private ChatSessionState getRequiredSession(String sessionId) {
        ChatSessionState session = sessions.get(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found.");
        }

        return session;
    }
}
