# 베이스 이미지
FROM osrf/ros:humble-desktop

# 1. 기본 환경, 로케일, pip 설치
RUN apt-get update && apt-get install -y \
    locales \
    terminator \
    dbus-x11 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

RUN locale-gen en_US.UTF-8
ENV LANG=en_US.UTF-8
ENV LC_ALL=en_US.UTF-8

# 2. Gazebo 및 ROS2 컨트롤러 패키지 설치
RUN apt-get update && apt-get install -y \
    ros-humble-ros-gz-sim ros-humble-ros-gz-bridge \
    ros-humble-robot-state-publisher ros-humble-xacro \
    ros-humble-ign-ros2-control ros-humble-rmw-cyclonedds-cpp \
    ros-humble-joint-state-broadcaster \
    ros-humble-joint-trajectory-controller \
    ros-humble-ros2-control ros-humble-ros2-controllers \
    && rm -rf /var/lib/apt/lists/*

# 3. PyTorch 설치 (CUDA 12.8 대응 문서 내용 반영)
RUN pip3 install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu128

# 4. ROS2 환경변수 및 폴더 숨기기 (.hidden) 적용
RUN echo "source /opt/ros/humble/setup.bash" >> /root/.bashrc
RUN cat > /root/.hidden << 'EOF'
Downloads
Pictures
Templates
Documents
Music
Public
Videos
snap
Desktop
EOF

WORKDIR /root/workspace