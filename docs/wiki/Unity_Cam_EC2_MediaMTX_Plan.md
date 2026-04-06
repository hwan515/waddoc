# Unity Camera EC2 MediaMTX 전환 플랜

## 목표

- 차량 로컬 ROS Compose 내부의 MediaMTX 의존성을 제거하고, EC2에서 `unity_cam` 스트림을 수신/중계한다.
- 차량 쪽 `camera_streamer.py`는 EC2의 RTSP ingest endpoint로 송출한다.
- 웹 브라우저는 계속 `https://www.waddoc.site/unity_cam/` 만 호출한다.
- 프론트엔드 React 컴포넌트는 가능한 한 변경하지 않고, 프록시와 인프라 설정만 교체한다.

## 현재 구조

코드 기준 현재 경로는 아래와 같다.

- 차량 송출 기본값: `src/ros2_docker/workspace/src/my_ros2_basics/my_ros2_basics/camera_streamer.py`
  - `rtsp://127.0.0.1:8554/unity_cam`
- 차량 로컬 MediaMTX: `src/ros2_docker/docker-compose.yml`
  - `mediamtx` 서비스가 같은 compose에 존재
- 프론트 프록시: `src/FE/nginx.conf`
  - `/unity_cam/` 요청을 Tailscale IP `http://100.89.189.27:8889/unity_cam/` 로 전달
- 브라우저 호출 경로: `src/FE/src/components/operator/MapMonitoring.jsx`
  - iframe `src="/unity_cam/"`

즉, 현재도 브라우저 계약은 `/unity_cam/` 하나로 고정되어 있고, 실제 스트림 소스만 뒤에서 바꾸는 구조다.

## 목표 구조

```text
차량 ROS2 camera_streamer
  -> RTSP publish (tcp)
  -> rtsp://<EC2 public host>:8554/unity_cam

EC2 MediaMTX
  -> 내부 네트워크에서 frontend nginx가 참조
  -> http://mediamtx:8889/unity_cam

frontend nginx
  -> /unity_cam/ 를 MediaMTX로 reverse proxy

브라우저
  -> https://www.waddoc.site/unity_cam/
```

## 설계 결정

### 1. 브라우저 URL은 유지

- `MapMonitoring.jsx`는 변경하지 않는다.
- 운영자 브라우저와 프론트 라우팅 계약은 `/unity_cam/` 그대로 유지한다.

### 2. EC2 주소는 소스에 하드코딩하지 않음

- `camera_streamer.py`의 소스 기본값은 로컬 fallback으로 유지하는 것을 권장한다.
- 실제 운영 RTSP 목적지는 환경변수 또는 ROS parameter로 주입한다.
- 이유:
  - 로컬 개발/복구 시 소스 수정 없이 기존 동작 복원 가능
  - 환경별 주소 변경 시 재빌드 범위를 줄일 수 있음
  - 하드코딩된 IP 변경 누락 리스크를 줄일 수 있음

### 3. 프론트도 하드코딩 대신 env 기반 프록시로 전환

- 현재 `src/FE/nginx.conf`의 Tailscale IP 하드코딩을 제거한다.
- `UNITY_CAM_PROXY_TARGET=http://mediamtx:8889/unity_cam` 같은 환경변수 기반으로 Nginx 템플릿을 렌더링한다.
- 최종 location은 `/unity_cam/` 서브패스 프록시 규칙을 사용한다.
- 핵심은 upstream 값에 `unity_cam` path를 포함해 `/unity_cam/` prefix strip 이후에도 MediaMTX stream path가 유지되게 하는 것이다.

### 4. EC2 MediaMTX는 frontend와 같은 Docker 네트워크에 둠

- `infra/docker-compose.prod.yml` 안에 `mediamtx` 서비스를 추가한다.
- `frontend` 컨테이너가 `mediamtx` 서비스에 내부 접근하고, 프록시 대상은 `http://mediamtx:8889/unity_cam` 로 둔다.
- `8889`는 가능하면 외부에 직접 publish하지 않고, 컨테이너 내부 통신으로만 사용한다.

## 단계별 구현 플랜

## Phase 1. EC2 MediaMTX 서비스 추가

대상 파일:

- `infra/docker-compose.prod.yml`
- `infra/.env.example`
- `docs/wiki/Infrastructure_Setup.md`

작업:

- `mediamtx` 서비스를 운영 compose에 추가한다.
- `frontend`, `nginx`와 같은 `waddoc-net` 네트워크에 붙인다.
- RTSP ingest 용 `8554/tcp` 를 host publish 한다.
- WebRTC/HTTP 제공을 위해 MediaMTX 내부 HTTP 리스너 `8889`를 사용한다.
- 운영 환경변수를 정의한다.

권장 환경변수 예시:

```env
UNITY_CAM_PROXY_TARGET=http://mediamtx:8889/unity_cam
UNITY_CAM_RTSP_URL=rtsp://<EC2_PUBLIC_HOST>:8554/unity_cam
MEDIAMTX_WEBRTC_PUBLIC_HOST=<EC2_PUBLIC_HOST 또는 www.waddoc.site>
```

MediaMTX 예시 설정 방향:

```yaml
mediamtx:
  image: bluenviron/mediamtx:latest
  expose:
    - "8889"
  ports:
    - "8554:8554"
    - "8189:8189/udp"
  environment:
    - MTX_RTSPTRANSPORTS=tcp
    - MTX_WEBRTCADDITIONALHOSTS=${MEDIAMTX_WEBRTC_PUBLIC_HOST}
  restart: unless-stopped
  networks:
    - waddoc-net
```

주의:

- `8554/tcp`는 차량 publish 용이다.
- 현재 `/unity_cam/`는 MediaMTX의 WebRTC 페이지(`8889`)를 프록시하는 구조로 추정된다.
- 따라서 보안그룹을 `8554/tcp`만 여는 것으로 끝나지 않을 수 있다.
- MediaMTX 공식 문서 기준으로 WebRTC 연결은 `8189/udp` 라우팅과 `webrtcAdditionalHosts` 설정이 필요하다.
- 이 부분은 현재 구조상 `viewer` 트래픽 요구사항이므로, 운영 보안그룹 설계 시 별도로 반영해야 한다.

## Phase 2. 프론트 Nginx를 env 기반으로 전환

대상 파일:

- `src/FE/nginx.conf.template`
- `src/FE/Dockerfile`
- `src/FE/docker-entrypoint.d/40-runtime-config.sh`
- 필요 시 `src/FE/docker-entrypoint.d/30-nginx-env.sh` 신규 추가
- `infra/docker-compose.prod.yml`
- `infra/docker-compose.yml`

작업:

- `src/FE/nginx.conf.template`를 기반으로 실제 Nginx 설정을 렌더링하도록 바꾼다.
- 컨테이너 시작 시 `UNITY_CAM_PROXY_TARGET` 값을 사용해 실제 Nginx 설정을 생성한다.
- `/unity_cam/` reverse proxy는 MediaMTX 공식 subfolder 가이드에 맞춰 구성한다.
- `envsubst`는 전체 환경변수를 치환하지 않고 `UNITY_CAM_PROXY_TARGET`만 명시적으로 치환한다.

권장 프록시 규칙:

```nginx
location /unity_cam/ {
    proxy_pass ${UNITY_CAM_PROXY_TARGET}/;
    proxy_redirect / /unity_cam/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

설명:

- `location /unity_cam/` + `proxy_pass http://mediamtx:8889/;` 조합은 `/unity_cam/` prefix를 strip 하므로 잘못된 경로로 전달된다.
- 따라서 `UNITY_CAM_PROXY_TARGET`은 반드시 `http://mediamtx:8889/unity_cam` 처럼 stream path를 포함해야 한다.
- `proxy_redirect`를 함께 넣어 MediaMTX 응답의 `Location` 헤더가 `/unity_cam/` 하위로 유지되게 한다.
- `envsubst`는 `${UNITY_CAM_PROXY_TARGET}`만 치환 대상으로 제한해야 `$http_upgrade`, `$host` 같은 nginx 내장 변수가 손상되지 않는다.

권장 템플릿 렌더링 예시:

```sh
envsubst '${UNITY_CAM_PROXY_TARGET}' \
  < /etc/nginx/templates/default.conf.template \
  > /etc/nginx/conf.d/default.conf
```

## Phase 3. 차량 RTSP publish endpoint를 env/parameter 기반으로 변경

대상 파일:

- `src/ros2_docker/custom_entrypoint.sh`
- `src/ros2_docker/docker-compose.yml`

작업:

- `camera_streamer.py`는 이미 `declare_parameter('rtsp_url', ...)`로 RTSP 목적지 override를 지원한다.
- 따라서 애플리케이션 코드 추가보다 `custom_entrypoint.sh`에서 ROS parameter를 주입하는 방식으로 처리한다.
- 차량 compose에 `UNITY_CAM_RTSP_URL` 환경변수를 추가한다.

권장 실행 형태:

```bash
ros2 run my_ros2_basics camera_streamer --ros-args -p rtsp_url:=${UNITY_CAM_RTSP_URL}
```

권장 기본값:

```env
UNITY_CAM_RTSP_URL=rtsp://127.0.0.1:8554/unity_cam
```

운영 차량 값:

```env
UNITY_CAM_RTSP_URL=rtsp://<EC2_PUBLIC_HOST>:8554/unity_cam
```

이 방식으로 가면:

- 로컬 단독 테스트 가능
- 운영 전환 시 코드 수정 없이 환경값만 변경
- 장애 시 즉시 로컬 MediaMTX fallback 가능

## Phase 4. 로컬 MediaMTX 의존성 정리

대상 파일:

- `src/ros2_docker/docker-compose.yml`
- 관련 실행 문서

선택지:

- A안: 로컬 `mediamtx` 서비스를 완전히 제거
- B안: 로컬 개발용 fallback profile로 분리

권장:

- 당장 운영 전환이 목표라면 B안을 권장한다.
- 이유:
  - 네트워크 장애 시 로컬 smoke test 경로 보존 가능
  - 차량 개발자가 EC2 없이도 영상 경로 자체를 검증 가능

예시:

- 기본 실행은 EC2 publish
- `--profile local-stream` 일 때만 로컬 `mediamtx` 활성화

## Phase 5. 보안그룹/네트워크 설정

인프라 작업:

- EC2 inbound 허용:
  - `8554/tcp` from 차량 출발지
  - `8189/udp` from viewer network if MediaMTX WebRTC direct ICE 필요
- 불필요한 외부 노출 차단:
  - `8889/tcp`는 가능하면 외부 공개하지 않고 프록시 내부 통신만 사용

주의:

- "`8554/tcp`만 열면 충분하다"는 가정은 ingest 경로에 대해서만 성립한다.
- 현재 `/unity_cam/`가 MediaMTX WebRTC endpoint를 프록시하는 구조라면, viewer용 ICE 포트 요구사항을 따로 검토해야 한다.
- 만약 외부 네트워크 정책상 UDP 개방이 어렵다면:
  - MediaMTX TCP WebRTC listener 활성화
  - 또는 TURN/coturn 경유
  - 또는 HLS/MSE 기반 소비 방식으로 변경

이 항목은 구현 전에 실제 브라우저 소비 방식으로 최종 확정해야 한다.

## Phase 6. 배포 및 전환 순서

1. EC2에 `mediamtx` 서비스만 먼저 올린다.
2. EC2 내부에서 `mediamtx:8889` 와 host `:8554` 가 정상인지 확인한다.
3. 프론트 nginx를 env 기반 프록시로 바꿔 배포한다.
4. 브라우저에서 `/unity_cam/` 경로가 MediaMTX 페이지로 응답하는지 확인한다.
5. 마지막으로 차량 `UNITY_CAM_RTSP_URL` 값을 EC2로 전환한다.
6. 실제 카메라 publish가 들어오는지 확인한다.

이 순서를 지키면 브라우저 경로, 프론트, 차량 publish 전환을 분리해서 검증할 수 있다.

## 검증 계획

### 1. 인프라 단위 검증

- EC2에서 `docker compose ps` 로 `mediamtx` 기동 확인
- EC2에서 `curl http://mediamtx:8889/unity_cam/` 응답 확인
- EC2에서 `ss -lntup` 또는 컨테이너 로그로 `8554`, `8189` 리스너 확인

### 2. 프론트 단위 검증

- `https://www.waddoc.site/unity_cam/`가 502 없이 응답하는지 확인
- 브라우저 devtools network에서 `/unity_cam/` 관련 리소스가 모두 같은 origin 하위로 들어오는지 확인
- 필요 시 WebSocket / WHEP 요청이 프록시를 통과하는지 확인

### 3. 차량 publish 검증

- `camera_streamer` 로그에 EC2 RTSP URL이 찍히는지 확인
- FFmpeg subprocess가 `BrokenPipeError` 없이 유지되는지 확인
- MediaMTX 로그에 `unity_cam` publisher 접속 로그가 찍히는지 확인

### 4. 사용자 시나리오 검증

- 운영자 화면에서 기존 iframe 경로 변경 없이 영상이 표시되는지 확인
- 차량 상태 전환에 따라 내부/외부 카메라 스위칭이 그대로 동작하는지 확인

## 변경 영향 체크리스트

- API specification:
  - 변경 없음
- Database schema / migration:
  - 변경 없음
- Environment variables / config:
  - 변경 필요
  - `UNITY_CAM_PROXY_TARGET`
  - `UNITY_CAM_RTSP_URL`
  - `MEDIAMTX_WEBRTC_PUBLIC_HOST`
- Deployment manifests / CI-CD:
  - 변경 필요
  - `infra/docker-compose.prod.yml`
  - 필요 시 Jenkins 배포 변수 반영
- User-facing / developer-facing docs:
  - 변경 필요
  - 인프라 설정 문서
  - ROS 실행 가이드

## 리스크와 대응

### 리스크 1. WebRTC ICE 설정 누락

- 증상:
  - `/unity_cam/` 페이지는 열리지만 영상이 안 나옴
- 대응:
  - `MTX_WEBRTCADDITIONALHOSTS`
  - `8189/udp` 라우팅
  - 필요 시 `webrtcLocalTCPAddress` 또는 TURN 적용

### 리스크 2. 차량 NAT/방화벽으로 인한 RTSP publish 실패

- 증상:
  - `camera_streamer`에서 FFmpeg 오류 또는 MediaMTX publish 미수신
- 대응:
  - `-rtsp_transport tcp` 유지
  - EC2 `8554/tcp` 보안그룹 확인
  - 차량 outbound 정책 확인

### 리스크 3. FE nginx 하드코딩 제거 중 프록시 경로 회귀

- 증상:
  - `/unity_cam/` 404 또는 redirect loop
- 대응:
  - `UNITY_CAM_PROXY_TARGET=http://mediamtx:8889/unity_cam` 사용
  - subfolder reverse proxy 규칙 적용
  - `proxy_redirect` 포함
  - `envsubst '${UNITY_CAM_PROXY_TARGET}'`처럼 치환 대상을 제한
  - 배포 전 로컬 컨테이너 수준 검증

## 수용 기준

- 운영자 브라우저는 계속 `https://www.waddoc.site/unity_cam/` 만 사용한다.
- 차량 스트림은 EC2 MediaMTX의 `rtsp://<host>:8554/unity_cam` 으로 publish 된다.
- FE는 Tailscale IP가 아닌 `UNITY_CAM_PROXY_TARGET` 기반으로 MediaMTX를 본다.
- 코드에 환경별 EC2 IP 하드코딩이 남지 않는다.
- Tailscale 없이도 운영 경로가 동작한다.

## 권장 구현 순서 요약

1. `infra/docker-compose.prod.yml` 에 `mediamtx` 서비스 추가
2. `src/FE` nginx 설정을 env 템플릿으로 전환
3. `src/ros2_docker` 에 `UNITY_CAM_RTSP_URL` 주입 경로 추가
4. 보안그룹과 MediaMTX WebRTC public host 설정 반영
5. 문서 업데이트 후 단계적 배포

## 참고 자료

- MediaMTX WebRTC clients: https://mediamtx.org/docs/read/webrtc
- MediaMTX install / 기본 포트: https://mediamtx.org/docs/kickoff/install
- MediaMTX WebRTC connectivity issues: https://mediamtx.org/docs/usage/webrtc-specific-features
- MediaMTX subfolder reverse proxy: https://mediamtx.org/docs/usage/expose-the-server-in-a-subfolder
