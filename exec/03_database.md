# 데이터베이스 문서

## 1. 데이터 저장소

| 시스템 | 역할 | 근거 |
| --- | --- | --- |
| PostgreSQL | 메인 애플리케이션의 주 관계형 DB | Spring datasource 설정, Flyway migration, Docker Compose |
| Redis | 캐시, pub/sub, 분산락, 알림 fan-out | Redis 설정, notification 서비스, distributed lock 사용 코드 |

## 2. PostgreSQL 연결 설정

### 2.1 메인 애플리케이션 로컬 profile

`src/BE/src/main/resources/application-local.yml` 기준:

- JDBC URL: `jdbc:postgresql://${DB_HOST:localhost}:5432/waddoc`
- Username: `${DB_USER:waddoc}`
- Password: `${DB_PASSWORD:waddoc_dev}`

`infra/docker-compose.yml` 기준 로컬 DB:

- database: `waddoc`
- user: `waddoc`
- password: `waddoc_dev`
- host 노출 포트: `5432`

### 2.2 메인 애플리케이션 운영 profile

`src/BE/src/main/resources/application-prod.yml`, `infra/docker-compose.prod.yml` 기준:

- JDBC URL: `jdbc:postgresql://${DB_HOST}:5432/waddoc`
- Username: `${DB_USER:${POSTGRES_USER}}`
- Password: `${DB_PASSWORD}`

운영형 Compose는 Postgres

- `127.0.0.1:5432:5432`

### 2.3 Redis 설정

로컬:

- host 기본값: `localhost`
- port 기본값: `6379`
- 비밀번호 기본값 없음

운영:

- host: `REDIS_HOST`
- port: `REDIS_PORT`
- password: `REDIS_PASSWORD`

## 3. 스키마 관리 방식

### 3.1 Migration 도구

확인된 도구: Flyway

확인된 동작:

- `spring.jpa.hibernate.ddl-auto=validate`
- `spring.flyway.enabled=true`
- migration source of truth: `src/BE/src/main/resources/db/migration`

즉, 스키마 생성과 변경은 Hibernate 자동 생성이 아니라 SQL migration 파일이 기준

### 3.2 확인된 migration 파일

실제 소스 migration:

- `V1__baseline_current_schema.sql`
- `V13__update_guardian_approval_schema.sql`
- `V14__add_mission_schedule_columns.sql`
- `V15__add_mission_previous_phase.sql`
- `V16__add_mission_telemetry_tracking_columns.sql`
- `V17__add_patient_gender.sql`
- `V18__backfill_seed_patient_gender.sql`
- `V19__create_vehicle_table.sql`
- `V20__create_dispatch_outbox_table.sql`
- `V21__add_dispatch_outbox_retry_pending_index.sql`
- `V22__create_vital_measurement_table.sql`
- `V23__add_mission_target_waypoint_number.sql`

`src/BE/build/resources/...`, `src/BE/bin/main/...` 아래에도 복사본이 존재하지만, 이들은 빌드 산출물

## 4. 도메인별 스키마 사용

### 4.1 사용자 및 신원 정보

기본 테이블:

- `user`
- `doctor_profile`
- `patient`
- `patient_guardian_link`

사용처:

- guardian/admin/doctor 로그인 및 권한
- 보호자 승인 워크플로우
- 의사 프로필 및 진료과 매핑
- 환자 기준 이미지 경로 관리

### 4.2 스케줄 및 접수

확인된 테이블:

- `schedule_slot`
- `intake_session`
- `booking`
- `care_case`

사용처:

- 휴대폰 시뮬레이터가 intake session 생성
- 추천 진료과와 제안 슬롯을 바탕으로 booking 생성
- booking 생성과 동시에 care case 생성

### 4.3 미션 및 원격진료

확인된 테이블:

- `mission`
- `consultation_session`
- `consultation_summary`
- `vital_measurement`

사용처:

- 미션 생성 및 phase 전이
- 본인확인 통과 후 환자 토큰 발급
- LiveKit consultation session 관리
- 의사 진료 소견 저장
- 차량/로봇 단말의 vital 저장

### 4.4 배차 및 차량

확인된 테이블:

- `vehicle`
- `dispatch_outbox`

사용처:

- 차량 운영 상태 관리
- 지역 기반 배차 대상 선택
- 가용 차량이 없을 때 retry 처리
- Outbox 패턴으로 DB 반영 후 Kafka publish

## 5. 시드 데이터와 데모 데이터

### 5.1 애플리케이션 시더

메인 백엔드에는 `LocalDummyDataSeeder`가 있으며, 아래 조건에서 활성화

- `app.seed.enabled=true`
- `app.seed.default-password` 설정됨

시더가 다루는 범위:

- 관리자 계정
- 의사 계정/프로필
- 차량
- 환자
- 보호자 및 보호자-환자 링크
- 과거/다가오는 예약
- care case
- mission
- consultation session
- dispatch outbox

중요:

- 현재 시더 코드 기준 계정명은 `seed_prod_*` 패턴

### 5.2 SQL 시드 스냅샷

확인된 SQL 파일:

- `infra/sql/prod_dummy_seed.sql`

파일 내부에서 확인된 내용:

- 기존 데모용 row 정리
- admin/doctor/guardian user insert
- vehicle insert
- patient insert
- schedule slot insert
- intake session, booking, care case, mission 등 insert

이 파일은 자체 주석에서 다음 용도라고 설명한다.

- 현재 로컬 더미 DB 기준 auto-generated snapshot
- demo/prod-like seed 용도

## 6. 발견된 dump / backup 산출물

발견됨:

- `infra/sql/prod_dummy_seed.sql`

## 7. 초기화 및 복원 명령

### 7.1 권장 스키마 초기화

PostgreSQL을 띄운 뒤 Spring 애플리케이션을 띄우면 Flyway가 migration을 자동 적용한다.

예시:

```bash
cd infra
docker compose up -d postgres
docker compose up -d spring-api
```

### 7.2 `psql`로 데모 시드 SQL 반영

`infra/docker-compose.yml` 기본값 기준:

```bash
psql -h localhost -U waddoc -d waddoc -f infra/sql/prod_dummy_seed.sql
```

운영형 Compose 값 기준 예시:

```bash
psql -h 127.0.0.1 -U <POSTGRES_USER> -d <POSTGRES_DB> -f infra/sql/prod_dummy_seed.sql
```

Postgres 컨테이너 내부로 직접 넣는 방식:

```bash
cd infra
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" < sql/prod_dummy_seed.sql
```

### 7.3 백엔드 전용 Compose 기준 복원

`src/BE/docker-compose.yml` 기본값도 로컬과 동일하게 `waddoc / waddoc` 조합이다.

```bash
psql -h localhost -U waddoc -d waddoc -f infra/sql/prod_dummy_seed.sql
```

### 7.4 업로드/기준 이미지 파일

DB만 복원해도 모든 데모가 완성되지는 않는다.

본인확인 흐름은 `reference_image_path`가 가리키는 실제 파일도 필요하다.

확인된 저장 위치:

- 백엔드 단독 로컬 실행: `src/BE/local-storage/uploads`
- infra 로컬 실행: `infra/local-storage/uploads`
- 운영형 Compose: `/data/uploads`에 매핑된 `uploads` volume

해석:

- DB row에는 기준 이미지 경로가 있는데 실제 파일이 `FILE_STORAGE_ROOT` 아래 없으면, AI-IDV 검증은 실패하거나 기준 이미지 없이 fallback 경로로 동작할 수 있다.

## 8. Redis 데이터 사용 방식

Redis는 주 데이터 저장소가 아니라 운영 보조 저장소이다.

확인된 역할:

- outbox relay용 분산락
- doctor notification fan-out
- key expiration / keyspace event 처리

운영상 의미:

- 비즈니스 원본 데이터는 PostgreSQL에 있다.
- Redis는 캐시/메시지 fan-out/운영 보조용으로 봐야 한다.

## 9. 운영 메모

- 메인 업무 데이터의 authoritative source는 PostgreSQL이다.
- 다중 인스턴스 알림과 분산락을 위해 Redis는 사실상 필수 운영 구성이다.
- 스키마 기준 파일은 `src/BE/src/main/resources/db/migration`이다.
- 저장소에 포함된 SQL은 full backup이 아니라 demo/prod-like seed 스냅샷이다.

