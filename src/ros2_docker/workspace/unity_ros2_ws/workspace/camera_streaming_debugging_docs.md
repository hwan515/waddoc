# Unity ROS2 카메라 영상 스트리밍 디버깅 과정

본 문서는 Unity 시뮬레이션 환경의 카메라 영상을 ROS2(Docker)를 거쳐 Windows 호스트의 웹 브라우저(MediaMTX)에서 실시간으로 스트리밍하기 위해 겪은 문제들과 해결 과정을 정리한 것입니다.

---

## 1. 초기 상황 및 목표
- **목표:** Unity 내의 차량 카메라 영상을 ROS2 토픽(`/camera/image_raw`)으로 발행하고, 이를 MediaMTX 서버를 통해 WebRTC/HLS/RTSP로 외부에서 시청.
- **환경:** Windows 10/11 호스트 → WSL2 → Docker 컨테이너 (`ros2_humble`, `network_mode: host`)
- **초기 상태:** `docker-compose.yml`에 MediaMTX 서버와 ROS2 컨테이너가 구성되어 있었으나, 이미지를 스트리밍하는 노드가 없었음.

---

## 2. 시도 및 문제 해결 과정

### 시도 1: GStreamer 플러그인을 이용한 스트리밍 노드 작성
가장 일반적인 방법인 `cv2.VideoWriter`와 GStreamer `appsrc` 파이프라인을 사용해 RTSP로 영상을 밀어넣는(push) 파이썬 노드를 작성했습니다.
- **결과:** 실패. `VideoWriter open failed` 에러 발생.
- **원인:** Docker 이미지 내에 `rtspclientsink` 등 필요한 GStreamer 플러그인이 부족하여 파이프라인 구성이 불가능했습니다.

### 시도 2: 서브프로세스로 `ffmpeg` 직접 호출 (stdin 파이프)
GStreamer 대신 스트리밍의 표준인 `ffmpeg`를 사용하기로 결정했습니다.
1. `apt-get install -y ffmpeg` 명령으로 컨테이너에 ffmpeg 설치.
2. OpenCV로 처리된 프레임을 `subprocess.PIPE`(`stdin`)를 통해 ffmpeg로 전달하도록 노드 재작성.
- **결과:** 실행은 되었으나 MediaMTX 스트림이 10초 만에 반복적으로 끊기며 무한 재시작.
- **디버깅 과정:** 
  - `ffmpeg`의 `stderr`를 캡처하여 출력하도록 ` threading `을 추가해 ROS2 로그에서 직접 확인.
  - 로그 상에는 `Stream mapping`까지 성공했으나 MediaMTX 측에서 `i/o timeout`이 발생.

### 시도 3: 백그라운드 실행 시의 stdin 증발 문제 (Named Pipe / FIFO 도입)
`docker exec ... nohup &` 방식으로 백그라운드 실행 시, 부모 쉘이 닫히면서 ffmpeg의 `stdin`이 `/dev/null`로 연결되어 프로세스가 즉시 종료(Broken Pipe)되는 문제를 발견했습니다.
- **해결책:** 파이썬 subprocess의 파이프 대신, 리눅스 공유 파일 시스템인 **Named Pipe (FIFO)** 커널 기능을 사용.
  - 파이썬 노드는 `/tmp/ros_camera_fifo` 경로에 파일을 쓰듯 이미지를 밀어넣음.
  - `ffmpeg`는 `-i /tmp/ros_camera_fifo` 인자를 통해 해당 파이프에서 프레임을 읽어감.

### 시도 4: 좀비 ffmpeg 프로세스 충돌 해결
수차례 테스트를 진행하면서 백그라운드에 생성된 이전 ffmpeg 프로세스들이 `8554` RTSP 포트를 점유(Zombie 프로세스)하고 있었습니다.
- **문제점:** 새 영상 스트림이 올라가도 기존 프로세스가 소켓을 가로채거나 막아버려 영상이 뚝뚝 끊기거나 에러 발생. `pkill -9 ffmpeg` 명령어조차 완벽히 작동하지 않음.
- **해결책:** 깔끔한 정리를 위해 **Docker 컨테이너를 통째로 재시작**(`docker restart ros2_dev_env`)하여 프로세스를 초기화.

### 시도 5: 유니티 프레임 레이트 불일치 및 네트워크 Timeout 해결
ffmpeg가 60프레임까지는 잘 전송하다가 `i/o timeout`을 띄우며 죽는 현상이 지속되었습니다.
- **원인 찾기:** `ros2 topic hz /camera/image_raw`를 측정해보니 **약 10Hz**로 발행되고 있었습니다. 하지만 ffmpeg 옵션은 `30fps`를 기대하고 있었고, 입력 데이터가 너무 느리게 들어오자 10초 타임아웃에 걸려 스스로 종료되는 것이었습니다.
- **해결책:** 
  - `camera_streamer.py`의 기본 `fps` 파라미터를 `10`으로 하향 조정.
  - ffmpeg 옵션에 `-stimeout 60000000` (소켓 타임아웃 60초) 추가.
  - Broken Pipe 발생 시 노드가 죽지 않고 ffmpeg를 자동 재시작(`self._start_ffmpeg()`)하도록 예외 처리.

### 시도 6: Windows - WSL2 간 포트포워딩
노드는 완벽하게 돌아가고 MediaMTX도 `[path unity_cam] stream is available` 로그를 띄웠음에도, Windows 웹 브라우저(`localhost:8889`)에서는 "스트림 없음" 에러가 발생했습니다.
- **원인:** WSL2의 네트워크 구조 특성 상, Docker 컨테이너가 `host` 모드라 하더라도 이는 **WSL2 내부의 호스트망(`172.17.88.241`)** 일 뿐, Windows OS의 `localhost`가 아니었습니다.
- **해결책:** Windows PowerShell을 관리자 권한으로 열어 **Port Proxy**와 **방화벽 허용 규칙**을 등록.
  ```powershell
  # WSL IP 확인 후 (ex. 172.17.88.241)
  netsh interface portproxy add v4tov4 listenport=8889 listenaddress=0.0.0.0 connectport=8889 connectaddress=172.17.88.241
  netsh advfirewall firewall add rule name="WSL2 WebRTC" dir=in action=allow protocol=TCP localport=8889
  ```

### 시도 7: 최종 원인 규명 — 유니티 'Run In Background' 설정
모든 코드가 정상화되었음에도 간헐적으로 스트림이 끊겼습니다.
- **결정적 단서:** 카메라 토픽의 Hz 통계가 `min: 0.089s max: 80.238s` 로 튀는 것을 발견. 유니티에서 80초 동안 프레임이 아예 나오지 않았음을 의미합니다.
- **원인:** 윈도우 환경에서 유니티 창이 포커스를 잃으면(브라우저를 클릭하는 순간) 엔진이 자동으로 연산을 멈추는(일시정지) 유니티 기본 동작 때문이었습니다.
- **최종 해결책:** Unity Editor → `Edit` → `Project Settings` → `Player` → `Resolution and Presentation`에서 **`Run In Background`** 옵션을 활성화. 이로써 브라우저로 클릭을 넘겨도 영상이 끊김 없이 발행되게 되었습니다.

---

## 3. 최종 설정 및 노드 구조 요약

1. **`camera_streamer.py` (핵심 구조)**
   - `/camera/image_raw` 토픽 구독 (10Hz)
   - OpenCV를 사용해 이미지를 읽고 리사이즈/포맷 변환 (BGR24)
   - `os.mkfifo()`를 사용해 `/tmp/ros_camera_fifo` 생성
   - `subprocess` 스레드로 `ffmpeg` 구동 (입력: `fifo경로`, 출력: `rtsp://...`)
   - `BrokenPipeError` 발생 시 프로세스 킬 후 다시 인스턴스화하는 자가 치유(Self-Healing) 구조 구현.

2. **접속 아키텍처**
   - Unity (Windows) → `ros_tcp_endpoint` (Docker/WSL2)
   - ROS2 Node: `/camera/image_raw` → `camera_streamer` Node (Python)
   - Node Pipeline: `FIFO` → `ffmpeg` 인코딩(libx264, ultrafast) → `MediaMTX` 서버 (Docker/WSL2)
   - Viewer: Windows 웹 브라우저(`localhost:8889`, WebRTC) ← (Windows Port Proxy) ← WSL2 망

## 4. 스트림 재실행 매뉴얼

앞으로 스트리밍을 켜야 할 때 사용하는 기본 명령어입니다:

1. 워크스페이스에서 빌드 및 소스 갱신 (변경사항 있을 때만)
```bash
cd /root/workspace
colcon build --packages-select my_ros2_basics
source install/setup.bash
```

2. 백그라운드로 ROS TCP 엔드포인트와 카메라 스트리머 동시 실행
```bash
nohup ros2 run ros_tcp_endpoint default_server_endpoint --ros-args -p ROS_IP:=0.0.0.0 > /tmp/tcp_endpoint.log 2>&1 &
nohup ros2 run my_ros2_basics camera_streamer > /tmp/camera_streamer.log 2>&1 &
```

3. 시청 주소
- WebRTC (추천): `http://localhost:8889/unity_cam`
- HLS: `http://localhost:8888/unity_cam`
- RTSP / VLC: `rtsp://localhost:8554/unity_cam`
