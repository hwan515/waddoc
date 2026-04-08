# BE Local Run Guide

`src/BE`는 이제 Gradle multi-project workspace다. 로컬 기본 런타임은 아래 4개다.

- `core-app`: auth, patient, intake, booking, consultation, mission, dispatch, vehicle, vital을 소유하는 모듈러 모놀리스
- `edge-bff`: 외부 `/api/**` 진입점
- `notification-service`: 의사 알림 SSE / SMS owner
- `robot-gateway`: 로봇 SSE / command / MQTT owner

기본 포트:

- Public API (`edge-bff`): `8080`
- core DB: `5432`
- notification DB: `5433`
- core Redis: `6379`
- notification Redis: `6380`
- robot Redis: `6381`
- Kafka: `9092`
- Mosquitto WS: `9001`

## 1. 권장 로컬 실행

```bash
cd infra
docker compose --env-file .env.local -f docker-compose.yml up -d --build
```

접속:

- Public API: `http://localhost:8080`
- core-app Swagger: `http://localhost:8080/swagger/spring`

중지:

```bash
docker compose --env-file .env.local -f docker-compose.yml down
```

데이터까지 비우려면:

```bash
docker compose --env-file .env.local -f docker-compose.yml down -v
```

`src/BE/docker-compose.yml`은 제거했다. 로컬 compose 진입점은 `infra/docker-compose.yml` 하나로 통일한다.

## 2. 개별 모듈 실행

```bash
cd src/BE
./gradlew :core-app:bootRun
./gradlew :edge-bff:bootRun
./gradlew :notification-service:bootRun
./gradlew :robot-gateway:bootRun
```

host JVM으로 개별 실행할 때 Redis 포트는 서비스별로 다르다.

- `core-app`: `REDIS_PORT=6379`
- `notification-service`: `REDIS_PORT=6380`
- `robot-gateway`: `REDIS_PORT=6381`

Docker 이미지도 모듈별로 같은 Dockerfile을 사용한다.

```bash
docker build --build-arg MODULE_NAME=edge-bff -t waddoc-edge-bff .
docker build --build-arg MODULE_NAME=notification-service -t waddoc-notification .
docker build --build-arg MODULE_NAME=robot-gateway -t waddoc-robot-gateway .
docker build --build-arg MODULE_NAME=core-app -t waddoc-core-app .
```

## 3. 현재 경계

- `core-app`은 auth, patient, intake, booking, consultation, mission, dispatch, vehicle, vital을 계속 소유한다.
- `notification-service`는 `booking.confirmed.v1`, `booking.cancelled.v1`, `dispatch.assigned.v1`, `dispatch.delayed.v1`만 소비한다.
- `robot-gateway`만 MQTT broker에 연결한다.
- `edge-bff`는 BFF-owned read API만 직접 인증하고, 나머지 proxied API는 Authorization/Cookie/correlation ID를 그대로 전달한다.

## 4. 검증 명령

```bash
./gradlew :shared-kernel:compileJava :core-app:compileJava :notification-service:compileJava :robot-gateway:compileJava :edge-bff:compileJava
./gradlew :core-app:compileTestJava :notification-service:compileTestJava :robot-gateway:compileTestJava :edge-bff:compileTestJava
```

대표 단위 테스트:

```bash
./gradlew :core-app:test --tests "com.waddoc.domain.booking.service.BookingServiceTest" --tests "com.waddoc.domain.dispatch.service.DispatchConsumerTest"
./gradlew :notification-service:test --tests "com.waddoc.domain.notification.controller.DoctorNotificationStreamControllerTest" --tests "com.waddoc.domain.notification.service.DoctorNotificationConsumerTest" --tests "com.waddoc.domain.notification.service.DoctorNotificationSseServiceTest" --tests "com.waddoc.domain.notification.service.SmsConsumerTest"
./gradlew :robot-gateway:test --tests "com.waddoc.domain.robot.service.RobotCommandPublisherTest" --tests "com.waddoc.domain.robot.service.RobotSseServiceTest"
```
