# 대용량 트래픽 처리 설계 문서

- 작성 기준: 2026-03-23 저장소 스냅샷
- 범위: `src/BE`, `infra`, `src/AI-IDV`
- 관점: "현재 코드가 트래픽을 어떻게 흡수하고, 어떤 부하를 어디로 분산시키는가"

## 1. 결론

현재 구조는 모든 요청을 Spring Boot와 DB가 동기 처리하는 형태가 아니다. 구현 기준으로 보면 아래와 같이 역할이 분리되어 있다.

- `Spring Boot`는 인증, 업무 규칙, 상태 전이 같은 제어 plane을 담당한다.
- `Kafka`는 배차, SMS, 의사 알림, 텔레메트리 같은 burst 성격의 이벤트를 완충하는 비동기 버퍼 역할을 한다.
- `Redis`는 refresh token, disconnect timer, webhook idempotency, 본인 확인 TTL 상태처럼 "공유가 필요하지만 영속 DB까지는 필요 없는 상태"를 저장한다.
- `LiveKit`는 화상 진료의 미디어 plane을 분리해서 Spring 애플리케이션이 영상/음성 트래픽을 직접 처리하지 않게 한다.
- `AI-IDV 서버`는 OCR, 얼굴 인식 같은 GPU/CPU 집중 워크로드를 별도 프로세스로 격리한다.

즉, 이 저장소의 대용량 대응 전략은 "단일 서버 성능 극대화"보다는 "동기 요청 경로를 짧게 유지하고, 무거운 작업을 외부 컴포넌트로 분리하며, Redis/Kafka로 다중 인스턴스 친화적으로 만드는 구조"에 가깝다.

다만 현재 구현은 "초대규모 트래픽 완성형"이라기보다 "버스트 흡수와 scale-out 친화성을 확보한 1단계 구조"다. 운영 파일과 일부 조회 API는 추가 보완이 필요하다.

## 2. 트래픽 유형별 설계

### 2.1 예약 생성과 배차는 Outbox + Kafka로 분리

예약 생성 시 `BookingService`는 `booking`, `care_case`, `dispatch_outbox`를 한 트랜잭션 안에서 먼저 저장한다. 배차 요청을 바로 외부로 보내지 않고 DB outbox에 적재한 뒤 별도 relay가 Kafka로 전송한다.

- 근거 코드
  - `src/BE/src/main/java/com/waddoc/domain/booking/service/BookingService.java`
  - `src/BE/src/main/java/com/waddoc/domain/dispatch/service/DispatchOutboxRelay.java`
  - `src/BE/src/main/java/com/waddoc/domain/dispatch/service/DispatchConsumer.java`
  - `src/BE/src/main/resources/db/migration/V20__create_dispatch_outbox_table.sql`
  - `src/BE/src/main/resources/db/migration/V21__add_dispatch_outbox_retry_pending_index.sql`

핵심 포인트는 다음과 같다.

- 예약 확정과 배차 이벤트 발행을 직접 묶지 않고 `dispatch_outbox` 테이블로 느슨하게 연결한다.
- relay는 1초마다 polling 하되, `RedisDistributedLock`으로 락을 잡은 인스턴스만 relay를 수행한다.
- Kafka publish는 `.get()`으로 broker ack를 받은 뒤에만 `PUBLISHED`로 바꾼다.
- 실제 배차 소비자는 `COMPLETED` 여부, 케이스 취소 여부, 기존 mission 존재 여부를 다시 확인해서 멱등하게 종료한다.
- 가용 차량이 없으면 `RETRY_PENDING`으로 상태를 바꾸고, 차량이 복구되면 retry 이벤트를 다시 흘려 재평가한다.

이 설계의 효과는 "예약 생성 API 응답 지연"과 "배차 처리 실패가 예약 트랜잭션을 망가뜨리는 문제"를 분리하는 데 있다. 또한 다중 인스턴스 환경에서도 relay 중복 실행을 막도록 설계되어 있다.

### 2.2 외부 부작용은 트랜잭션 커밋 이후 Kafka로 넘김

SMS 발송과 의사 알림은 예약 생성 트랜잭션 안에서 직접 외부 I/O를 호출하지 않는다. `afterCommit`에서 Kafka 메시지를 발행하고, 실제 발송/전달은 consumer가 담당한다.

- 근거 코드
  - `src/BE/src/main/java/com/waddoc/domain/booking/service/BookingService.java`
  - `src/BE/src/main/java/com/waddoc/domain/notification/service/SmsConsumer.java`
  - `src/BE/src/main/java/com/waddoc/global/config/KafkaConfig.java`
  - `src/BE/src/main/resources/application.yml`

구현상 특징은 다음과 같다.

- 예약 트랜잭션이 커밋된 뒤에만 SMS와 의사 알림을 발행한다.
- Kafka consumer는 `enable-auto-commit: false`, `ack-mode: record`로 동작해서 실패 레코드만 재처리할 수 있다.
- SMS는 재시도 후에도 실패하면 DLT(`sms.requests.dlt`)로 보내서 본 업무 흐름과 분리한다.

이 방식은 외부 SMS 벤더 지연이나 실패가 사용자 요청 경로를 직접 막지 않게 해 준다.

### 2.3 의사 실시간 알림은 Kafka -> Redis Pub/Sub -> SSE fan-out

의사 알림은 단순 SSE 단일 서버 구조가 아니라, Kafka와 Redis Pub/Sub를 사이에 둔 다단 fan-out 구조다.

- 근거 코드
  - `src/BE/src/main/java/com/waddoc/domain/notification/service/DoctorNotificationConsumer.java`
  - `src/BE/src/main/java/com/waddoc/domain/notification/service/DoctorNotificationRedisPublisher.java`
  - `src/BE/src/main/java/com/waddoc/domain/notification/service/DoctorNotificationRedisSubscriber.java`
  - `src/BE/src/main/java/com/waddoc/domain/notification/service/DoctorNotificationSseService.java`
  - `src/BE/src/main/java/com/waddoc/global/config/RedisConfig.java`
  - `infra/nginx/prod.conf`

동작 방식은 다음과 같다.

- 예약 알림 이벤트는 Kafka 토픽 `doctor.notifications`로 들어간다.
- consumer는 이를 Redis Pub/Sub 채널 `doctor:notifications`에 publish 한다.
- 각 Spring 인스턴스는 Redis subscriber로 메시지를 받고, 자기 JVM 안에 열려 있는 SSE 연결이 있으면 그 연결에만 전달한다.
- SSE 연결은 `ConcurrentHashMap`으로 인메모리 관리되며, heartbeat를 주기적으로 보내 끊어진 연결을 정리한다.
- Nginx는 해당 SSE endpoint에 대해 `proxy_buffering off`, 긴 read timeout, `X-Accel-Buffering no`를 설정해 스트림이 중간에 막히지 않도록 했다.

즉, SSE 연결 자체는 각 인스턴스 로컬 메모리에 있지만, 이벤트 fan-out은 Redis Pub/Sub로 공유되므로 다중 인스턴스 환경에서도 특정 인스턴스에 붙은 의사 브라우저에게 알림을 전달할 수 있다.

### 2.4 텔레메트리 burst는 202 Accepted + Kafka buffer로 완충

차량/미션 텔레메트리 수집 API는 요청을 즉시 DB에 반영하지 않는다. API 진입점에서 API Key만 검증하고 Kafka에 넣은 뒤 `202 Accepted`를 반환한다.

- 근거 코드
  - `src/BE/src/main/java/com/waddoc/domain/mission/controller/MissionTelemetryController.java`
  - `src/BE/src/main/java/com/waddoc/domain/mission/service/MissionTelemetryConsumer.java`
  - `src/BE/src/main/java/com/waddoc/domain/mission/service/MissionTelemetryService.java`
  - `src/BE/src/main/java/com/waddoc/domain/mission/entity/Mission.java`
  - `src/BE/src/main/resources/db/migration/V16__add_mission_telemetry_tracking_columns.sql`

구현상 트래픽 대응 포인트는 다음과 같다.

- HTTP 수집 API는 빠르게 `202`를 반환하고, 실제 처리 부담은 Kafka consumer로 넘긴다.
- `sourceEventId`, `seqNo`, `timestamp`를 이용해 중복 이벤트와 역순 이벤트를 버린다.
- 최신 위치와 phase만 mission 엔티티에 반영하고, 이전 텔레메트리 전체를 별도 적재하지 않는다.

이 구조는 텔레메트리 burst가 들어와도 API thread가 DB update에 오래 묶이지 않게 만들고, 순서 뒤섞임과 중복 수신을 애플리케이션 레벨에서 흡수한다.

### 2.5 화상 진료는 LiveKit으로 미디어 plane을 분리

영상/음성 자체는 Spring 서버가 직접 중계하지 않는다. Spring은 room 생성과 participant token 발급, webhook 처리만 담당하고 실제 미디어는 LiveKit이 처리한다.

- 근거 코드
  - `src/BE/src/main/java/com/waddoc/domain/consultation/service/ConsultationLiveKitService.java`
  - `src/BE/src/main/java/com/waddoc/domain/consultation/service/ConsultationWebhookService.java`
  - `infra/docker-compose.prod.yml`
  - `infra/nginx/prod.conf`
  - `infra/livekit/livekit.yaml`

트래픽 관점에서 중요한 점은 다음과 같다.

- WebRTC signaling은 `/livekit/`으로 프록시되고, 미디어 포트는 LiveKit 컨테이너가 직접 받는다.
- Spring은 토큰 발급과 webhook 기반 세션 상태 전이에만 관여한다.
- webhook는 Redis `SETNX` 기반 5분 TTL idempotency 키로 중복 이벤트를 무시한다.
- 참가자 이탈 후 reconnect grace period는 Redis TTL 키로 관리하고, 키 만료 이벤트 리스너가 timeout 발생을 감지해 audit log를 남긴다.

이 구조 덕분에 대역폭이 큰 실시간 미디어 트래픽이 Spring Boot와 DB를 직접 압박하지 않는다.

### 2.6 인증과 세션은 무상태 access token + Redis refresh token

애플리케이션은 서버 메모리 세션을 두지 않는다. access token은 JWT로 검증하고, refresh token만 Redis에 저장한다.

- 근거 코드
  - `src/BE/src/main/java/com/waddoc/global/config/SecurityConfig.java`
  - `src/BE/src/main/java/com/waddoc/domain/auth/service/AuthService.java`
  - `src/BE/src/main/java/com/waddoc/domain/auth/service/RefreshTokenService.java`
  - `src/BE/src/main/java/com/waddoc/global/security/jwt/JwtTokenProvider.java`

구현상 특징은 다음과 같다.

- `SessionCreationPolicy.STATELESS`로 서버 세션을 사용하지 않는다.
- refresh token은 Redis에 평문이 아니라 SHA-256 해시 키로 저장한다.
- 사용자별 인덱스 set을 따로 두어 특정 사용자 세션 전체 강제 로그아웃이 가능하다.

즉, 인증 계층은 scale-out에 유리한 무상태 구조이며, 서버 인스턴스를 늘려도 세션 동기화 비용이 크지 않다.

### 2.7 Redis는 일반 캐시보다 "공유 휘발성 상태 저장소"로 사용

현재 Redis 사용 방식은 조회 캐시보다는 여러 인스턴스가 함께 봐야 하는 짧은 수명의 상태 저장에 집중돼 있다.

- 근거 코드
  - `src/BE/src/main/java/com/waddoc/domain/auth/service/RefreshTokenService.java`
  - `src/BE/src/main/java/com/waddoc/domain/mission/service/MissionIdentityCheckCacheService.java`
  - `src/BE/src/main/java/com/waddoc/domain/consultation/service/DisconnectTimerService.java`
  - `src/BE/src/main/java/com/waddoc/domain/consultation/service/RedisKeyExpirationListener.java`
  - `src/BE/src/main/java/com/waddoc/domain/consultation/service/ConsultationWebhookService.java`
  - `infra/docker-compose.prod.yml`

대표 사례는 다음과 같다.

- refresh token 저장
- 본인 확인 성공 상태 TTL 캐시
- LiveKit reconnect timeout 타이머
- webhook idempotency 키
- distributed lock

이 설계는 영속 DB write를 줄이고, 여러 인스턴스 사이에서 짧은 수명의 상태를 일관되게 공유하는 데 유리하다.

### 2.8 DB 접근은 open-in-view 비활성화 + fetch join/entity graph + pagination 위주

DB 병목을 줄이기 위해 읽기 경로를 꽤 의식해서 작성해 두었다.

- 근거 코드
  - `src/BE/src/main/resources/application.yml`
  - `src/BE/src/main/java/com/waddoc/domain/consultation/repository/ConsultationSessionRepository.java`
  - `src/BE/src/main/java/com/waddoc/domain/carecase/repository/CareCaseRepository.java`
  - `src/BE/src/main/java/com/waddoc/domain/patient/repository/PatientRepository.java`
  - `src/BE/src/main/java/com/waddoc/domain/carecase/service/CareCaseQueryService.java`
  - `src/BE/src/main/java/com/waddoc/domain/admin/service/AdminService.java`

확인된 포인트는 다음과 같다.

- `open-in-view: false`로 트랜잭션 밖 lazy loading에 의존하지 않는다.
- 상세/목록 응답에서 필요한 연관 엔티티를 fetch join 또는 `@EntityGraph`로 묶어 조회한다.
- 관리자 환자/케이스/세션 조회는 `Pageable` 기반 페이지네이션을 사용한다.
- 의사 담당 케이스 목록은 케이스별 미션을 반복 조회하지 않고, 한 번에 읽어서 map으로 조립한다.

즉, 조회 API 다수는 N+1과 대량 전체 조회를 피하려는 방향으로 작성되어 있다.

### 2.9 동시성 충돌은 DB 제약과 멱등 처리로 방어

특히 예약과 배차는 동시에 많이 들어올 수 있으므로, 애플리케이션 로직만이 아니라 DB 제약까지 같이 사용하고 있다.

- 근거 코드
  - `src/BE/src/main/resources/db/migration/V1__baseline_current_schema.sql`
  - `src/BE/src/main/java/com/waddoc/domain/booking/service/BookingService.java`
  - `src/BE/src/main/java/com/waddoc/domain/dispatch/service/DispatchConsumer.java`

확인된 방어 장치는 다음과 같다.

- `booking(slot_id)`에 대해 `status <> 'CANCELLED'` 조건의 partial unique index가 있다.
- 따라서 같은 슬롯에 대한 동시 예약 경쟁은 DB가 최종적으로 막고, 서비스는 `DataIntegrityViolationException`을 conflict로 변환한다.
- 배차 소비자는 중복 소비 시에도 `COMPLETED` 여부, 기존 mission 존재 여부를 다시 검사해 멱등하게 종료한다.

### 2.10 AI/GPU 워크로드는 별도 서비스로 격리

신분 확인(IDV)은 본체 Spring 애플리케이션 안에 직접 들어 있지 않다.

- 근거 코드
  - `src/AI-IDV/app/main.py`
  - `src/AI-IDV/app/services/idv_model_registry.py`
  - `src/BE/src/main/java/com/waddoc/domain/consultation/service/ConsultationIdentityVerificationClient.java`

구현상 특징은 다음과 같다.

- IDV 서버는 시작 시 `warmup()`으로 모델을 미리 올리고, registry 내부 lock으로 중복 로드를 막는다.
- Spring은 IDV 서버를 직접 포함하지 않고 별도 HTTP 호출로 사용하며 timeout도 둔다.
- 운영 compose도 `AI_IDV_URL` 환경변수로 외부 GPU 서버를 바라보도록 되어 있어 배치 원칙 자체가 분리형이다.

이 구조는 GPU/CPU 집약 작업이 Spring request thread, JPA transaction, DB connection pool을 직접 잠식하지 않게 해 준다.

## 3. 이 설계가 실제로 버티는 부하 유형

현재 코드 기준으로 특히 잘 대응하는 부하는 다음과 같다.

- 예약 확정 직후에 SMS, 의사 알림, 배차 준비가 한 번에 몰리는 burst
- 차량 텔레메트리가 짧은 시간에 연속 유입되는 burst
- 의사 브라우저가 여러 탭 또는 여러 인스턴스에 걸쳐 SSE로 연결되는 상황
- 화상 진료 시작/종료 시 webhook 중복 호출과 재접속 타이머 관리
- refresh token 검증, 본인 확인 성공 상태 같은 짧은 수명 상태 공유

반대로, 현재 구조는 "영상 자체를 API 서버가 직접 중계하는 부하"나 "AI 모델 추론(IDV 등)이 애플리케이션 JVM 안에서 같이 도는 부하"를 피하도록 설계되어 있다.

## 4. 현재 구조의 한계와 병목 가능성

아래 항목들은 "설계가 없다"는 뜻이 아니라, 현재 구현 상태에서 대규모 운영으로 가려면 추가 보완이 필요한 지점이다.

### 4.1 배포 스펙은 아직 단일 replica 중심

- `infra/docker-compose.prod.yml`에는 `spring-api`, `redis`, `kafka`, `livekit` 등이 정의되어 있지만 replica 수, autoscaling, resource request/limit는 없다.
- Nginx는 Docker DNS resolver를 써서 scale-out 친화적으로 작성돼 있지만, compose 자체가 자동 scale 전략을 제공하지는 않는다.
- 추후 쿠버네티스 환경을 사용한다면 HPA를 통해 자동 확장이 가능할 것이다.

### 4.2 Kafka 토픽 파티션은 3개지만 consumer 동시성은 별도 설정 없음

- `KafkaConfig`는 토픽을 3 partition으로 만들지만 `setConcurrency(...)` 설정은 없다.
- 따라서 단일 인스턴스 내부에서 listener 병렬도를 적극적으로 올린 구조는 아니다.
- 현재 설계는 "인스턴스 확장 + 파티션 분산"을 염두에 둔 형태에 더 가깝다.

### 4.3 Outbox relay는 배치 크기 제한 없이 전체 scan

- `DispatchOutboxRelay.fetchByStatus()`는 `PENDING`, `RETRY_PENDING` 전체를 정렬 조회한다.
- backlog가 매우 커지면 한 번의 relay 사이클에서 조회량과 처리 시간이 커질 수 있다.
- relay publish가 Kafka broker ack를 `.get()`으로 직렬 대기하므로 backlog가 길어질수록 전송 throughput 상한이 낮아질 수 있다.
- 즉, outbox 패턴은 도입되어 있지만, 대량 backlog 전용 batch chunking까지는 아직 구현되지 않았다.

### 4.4 일부 조회 API는 아직 unpaged `List` 기반

- `MissionQueryService` / `MissionRepository`의 관리자 미션 대시보드 조회는 `List` 기반이다.
- `CareCaseQueryService.getAssignedCases()`도 의사 담당 케이스를 페이지네이션 없이 읽는다.
- 데이터가 커지면 응답 크기와 DB/GC 부담이 증가할 수 있다.

### 4.5 연결 풀과 자원 튜닝은 아직 명시적이지 않음

- `application.yml`에 Hikari pool, Redis client pool, Kafka listener concurrency 같은 운영 튜닝 값이 별도로 보이지 않는다.
- 지금 구조는 아키텍처 레벨 분리는 잘 되어 있지만, 세부 capacity tuning은 아직 기본값 의존이 많다.

### 4.6 Redis Pub/Sub 알림은 durable queue가 아님

- 의사 알림은 연결이 살아 있는 인스턴스에만 전달된다.
- 연결이 없는 동안의 알림을 재전송하거나 적재하는 durable inbox 구조는 현재 코드상 보이지 않는다.
- 즉, 이 경로는 "실시간 push" 최적화이지 "반드시 저장되는 알림함" 구조는 아니다.

## 5. 보완 우선순위 제안

현재 구조를 유지하면서 대용량 대응력을 더 높이려면 우선순위는 아래 순서가 적절하다.

1. `dispatch_outbox` relay에 batch size / limit / cursor 기반 chunk 처리 추가
2. Kafka consumer concurrency와 topic partition 수를 운영 부하에 맞게 조정
3. 관리자 미션/의사 케이스 조회를 `Pageable` 기반으로 전환
4. Hikari, Redis, Kafka listener 관련 운영 튜닝 값 명시
5. Docker Compose 수준을 넘어 실제 replica 운영 전략이나 Kubernetes 스펙으로 확장
6. 실시간 알림 외에 "읽지 않은 알림" 적재가 필요하다면 durable notification store 추가

## 6. 최종 정리

현재 코드베이스는 대용량 트래픽을 위해 다음 원칙을 구현하고 있다.

- 동기 요청 경로를 짧게 유지한다.
- 외부 부작용은 Kafka로 비동기 분리한다.
- 인스턴스 간 공유가 필요한 짧은 상태는 Redis로 뺀다.
- 영상/음성/AI 같은 무거운 부하는 별도 컴포넌트로 분리한다.
- DB 조회는 fetch join, 페이지네이션, 제약 조건으로 병목과 충돌을 줄인다.

즉, "Spring 서버 한 대가 모든 것을 처리한다"는 구조가 아니라, "제어 plane은 가볍게 유지하고 무거운 부하를 옆으로 분리하는 구조"로 설계되어 있다. 현재 시점의 한계는 주로 운영 튜닝과 일부 미완료 조회 경로에 있으며, 핵심 방향성 자체는 대용량/버스트 트래픽 대응에 맞게 잡혀 있다.
