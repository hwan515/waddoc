# BE Local Run Guide

`src/BE` 폴더만으로도 백엔드 서버를 바로 띄울 수 있게 로컬 실행 경로 

기본 포트:

- Spring API: `8080`
- PostgreSQL: `5432`
- Redis: `6379`
- Kafka: `9092`

## 1. Docker로 한 번에 실행

```bash
cd src/BE
docker compose up --build
```

접속:

- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger/spring`

중지:

```bash
docker compose down
```

데이터까지 비우려면:

```bash
docker compose down -v
```

## 2. IDE에서 Spring만 실행

DB, Redis, Kafka만 먼저 띄운 뒤, Spring Boot는 IDE 또는 Gradle로 실행하는 방식

```bash
cd src/BE
docker compose up -d postgres redis zookeeper kafka
./scripts/run-local.sh
```

이 스크립트는 Gradle 캐시를 프로젝트 내부의 `.gradle-local`에 두고 `bootRun`을 실행

## 3. 로컬 기본값

- Spring profile: `local`
- DB 계정: `waddoc / waddoc_dev`
- 더미 데이터 seed: 기본 활성화
- 더미 슬롯 seed: 기본 시간대는 `09:00`~`23:00` 30분 단위이며, 오늘 날짜는 현재 시각 이후 슬롯만 유지하고 이미 지난 오늘 슬롯은 정리
- 더미 예약 seed: 당일 활성 비대면 예약은 생성하지 않음
- 파일 업로드 경로: `src/BE/local-storage/uploads`

## 4. 시간 처리 규칙

- 서버가 생성하는 기준 시각은 전부 KST(`Asia/Seoul`)다.
- 공용 유틸은 `KstTime`을 사용한다. 시간대 변환 상수는 `KstTime.ZONE`, KST 현재 시각이 즉시 필요하면 `KstTime.now()`를 사용한다.
- 서비스 계층에서 현재 시각이 필요한 경우 `Clock` Bean을 주입받고 `LocalDate.now(clock)`, `LocalTime.now(clock)`, `LocalDateTime.now(clock)`를 사용한다.
- JPA auditing(`@CreatedDate`, `@LastModifiedDate`)도 `JpaConfig`의 `DateTimeProvider`를 통해 KST로 고정된다.
- DTO에서 `OffsetDateTime` 변환이 필요한 경우만 `KstTime.ZONE`으로 zone 변환한다.

## 5. 참고

- LiveKit, AI IDV 서버 주소는 기본값이 들어가 있어 서버 기동 자체는 가능하고, 관련 API를 실제 호출할 때만 연결이 필요
- 첫 실행 시 Docker image pull, Gradle dependency download 때문에 시간이 걸릴 수 있음
