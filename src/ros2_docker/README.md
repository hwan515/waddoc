# ROS 2 Humble & PyTorch Docker Environment

이 디렉토리는 ROS 2 Humble 기반의 로봇 시뮬레이션 및 딥러닝(PyTorch) 환경을 구축하기 위한 Docker 환경 설정 파일들을 포함하고 있습니다. 호스트 PC의 디스플레이, GPU, 네트워크 자원을 컨테이너에서 쉽게 사용할 수 있도록 구성되어 있습니다.

## 폴더 구조

- `Dockerfile`: ROS 2 데스크탑 환경을 기반으로 시뮬레이션, 제어, 딥러닝 환경을 추가로 세팅하는 이미지 빌드 파일입니다.
- `docker-compose.yml`: 컨테이너 실행 옵션(GUI, GPU 설정, 호스트 네트워크 활성화, 디렉토리 마운트 등)을 정의하는 명세서입니다.
- `workspace/`: 컨테이너 내부의 `/root/workspace` 디렉토리와 연결(마운트)되는 호스트의 작업 공간입니다. 이곳에 작성한 코드는 컨테이너 종료 후에도 안전하게 유지됩니다.

## 주요 기능 및 설치되는 패키지

### 1. 베이스 환경
- **Docker Image**: `osrf/ros:humble-desktop` (Ubuntu 22.04 + ROS 2 Humble Desktop 풀버전)
- **기본 도구**: Terminator 터미널, GUI 지원용 X11 패키지(`dbus-x11`), Python 패키지 관리를 위한 `python3-pip`
- **언어 설정**: 다국어 지원을 위한 `en_US.UTF-8`

### 2. 시뮬레이션 및 로봇 제어 (Gazebo & ROS 2 Control)
- **Gazebo 시뮬레이터**: `ros-humble-ros-gz-sim`, `ros-humble-ros-gz-bridge`
- **로봇 모델 파싱 및 렌더링**: `ros-humble-xacro`, `ros-humble-robot-state-publisher`
- **로봇 제어 프레임워크**: `ros-humble-ros2-control`, `ros-humble-ros2-controllers`, `ros-humble-ign-ros2-control`, `ros-humble-joint-state-broadcaster`, `ros-humble-joint-trajectory-controller`
- **통신 미들웨어**: 통신 성능 향상을 위한 `ros-humble-rmw-cyclonedds-cpp`

### 3. 딥러닝 프레임워크 (PyTorch / CUDA 12.8)
- GPU를 지원하는 최신 PyTorch 환경(`torch`, `torchvision`, `torchaudio`)을 설치합니다.
- `docker-compose.yml` 리소스 예약(`deploy.resources.reservations.devices.capabilities: [gpu]`)을 통해 NVIDIA GPU 가속을 지원합니다. 컴퓨팅 자원을 최대한으로 활용하여 딥러닝 훈련과 추론을 수행할 수 있습니다.

## 환경 실행 및 접속 방법

### 필수 조건 ( Ubuntu 기준)

컨테이너를 원활하게 실행하기 위해 호스트 PC(Ubuntu)에 다음 항목들을 순서대로 설치 및 설정해야 합니다.

1. **Docker 및 Docker Compose 설치**
   - 시스템에 Docker가 없다면 [공식 문서 가이드](https://docs.docker.com/engine/install/ubuntu/) 등을 참고하여 설치해 주세요. (`docker`, `docker compose` 명령어 필수)
2. **NVIDIA 그래픽 드라이버 및 [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html) 설치 (GPU 사용 시)**
   - 컨테이너 내부에서 호스트의 GPU 리소스를 사용하기 위해 필수적입니다.
   - **(⚠️주의)** 만약 GPU가 없는 가상환경 등이라면, `docker-compose.yml` 파일 하단의 `deploy:` 부터 끝까지의 GPU 할당 부분을 반드시 주석 처리하거나 삭제한 뒤 실행해야 컨테이너가 켜집니다.
3. **GUI 화면 표시 권한 허용 (X11)**
    - 터미널에서 아래 명령어를 실행하여 Docker 컨테이너가 호스트의 화면을 띄울 수 있도록 권한을 부여해야 합니다. (재부팅 시 매번 혹은 컨테이너 실행 전 최초 1회 입력 필요)
    ```bash
    xhost +local:docker
    ```
### 자동 초기 환경 세팅 스크립트 (추천)
우분투를 갓 설치했거나(순정) 필수 패키지 설치가 번거롭다면, 함께 제공되는 쉘 스크립트를 통해 한 번에 세팅할 수 있습니다.

```bash
chmod +x setup.sh  # (이미 실행 권한이 있다면 생략 가능)
./setup.sh
```
> *해당 스크립트는 Docker 설치, GUI 접근 권한 허용, GPU 유무에 따른 `docker-compose.yml` 자동 주석처리 등을 도와줍니다.*

### 컨테이너 빌드 및 실행
터미널에서 현재 디렉토리(`/home/bkg/ros2_docker`)로 이동한 후, 다음 명령어를 실행하여 컨테이너를 구동합니다:

```bash
cp .env.example .env  # 최초 1회
docker compose up -d
```
> *최초 실행 시 이미지를 다운로드하고 패키지를 설치하므로 일정 시간이 소요될 수 있습니다.*
> *이 환경은 CycloneDDS discovery 충돌을 피하기 위해 `ROS_LOCALHOST_ONLY=1` 로 동작합니다. Unity는 ROS TCP(10000)로 붙기 때문에 이 설정과 충돌하지 않습니다.*

### 로컬 카메라 스트림 확인

- 기본값은 **EC2 MediaMTX relay로 publish 하는 설정**이다.
- `.env.example` 기본값:
  - `UNITY_CAM_RTSP_URL=rtsp://www.waddoc.site:8554/unity_cam`
  - `MEDIAMTX_WEBRTC_PUBLIC_HOST=127.0.0.1`
- 웹 프론트 로컬 compose는 기본적으로 EC2 공개 endpoint 기준 `http://www.waddoc.site:8889/unity_cam` 로 MediaMTX를 프록시한다.
- 따라서 로컬 개발에서도 기본 경로는 EC2 relay를 보는 구조다.
- 같은 PC의 로컬 MediaMTX로 직접 확인하고 싶다면 `.env`에서 `UNITY_CAM_RTSP_URL=rtsp://127.0.0.1:8554/unity_cam` 으로 바꾸고, 필요 시 `MEDIAMTX_WEBRTC_PUBLIC_HOST=127.0.0.1` 또는 개발 PC LAN IP로 조정한다.
### YOLO 사용 시 추가 설치
`vision_detect.py` 의 사람/경운기 ROI e-stop 기능은 `ultralytics` 와 PyTorch가 필요합니다. 최신 `Dockerfile`로 이미지를 다시 빌드하면 함께 설치됩니다. 이미 실행 중인 컨테이너에서 바로 테스트하려면 아래를 실행하세요.

```bash
pip3 install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu128
pip3 install ultralytics opencv-python "numpy<2"
```

설치 후에는 컨테이너를 재시작하거나 새 셸에서 다시 `ros2 run lane_follow_pkg vision_detect ...` 를 실행해 주세요.

X11 권한이 없어서 OpenCV 창이 죽는 환경이면 `--ros-args -p show_debug_windows:=false` 로 실행하세요. 디버그 창이 꼭 필요할 때만 `true` 로 켜는 것을 권장합니다.

### 컨테이너 내부 접속 (터미널)
실행된 컨테이너 내부로 들어가 작업하려면 다음 명령어를 사용합니다:

```bash
docker exec -it ros2_dev_env bash
```
> *컨테이너에 접속하면 자동으로 ROS 2 환경(`source /opt/ros/humble/setup.bash`)이 로드되며, 작업 디렉토리(`/root/workspace`)에서 바로 시작할 수 있습니다.*
> *워크스페이스를 `colcon build` 한 뒤 새 터미널을 열었는데 `ros2 topic list` 에서 기대한 토픽이 안 보이면, 먼저 `source /root/workspace/install/setup.bash`, `export ROS_DOMAIN_ID=0`, `export ROS_LOCALHOST_ONLY=1`, `ros2 daemon stop && ros2 daemon start` 를 순서대로 실행해 같은 그래프를 보게 맞춰 주세요.*

### 컨테이너 종료
작업을 마치고 컨테이너를 중지하려면 다음 명령어를 입력합니다. (파일은 `workspace/`에 그대로 남습니다.)

```bash
docker compose down
```
