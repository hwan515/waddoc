# 베이스 이미지
FROM osrf/ros:humble-desktop

# 컨테이너 내 모든 ROS 2 프로세스가 CycloneDDS를 사용하도록 강제 고정
ENV RMW_IMPLEMENTATION=rmw_cyclonedds_cpp

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

# 2. Gazebo 및 ROS2 컨트롤러 패키지, CycloneDDS 설치
RUN apt-get update && apt-get install -y \
    ros-humble-ros-gz-sim ros-humble-ros-gz-bridge \
    ros-humble-robot-state-publisher ros-humble-xacro \
    ros-humble-ign-ros2-control ros-humble-rmw-cyclonedds-cpp \
    ros-humble-joint-state-broadcaster \
    ros-humble-joint-trajectory-controller \
    ros-humble-ros2-control ros-humble-ros2-controllers \
    && rm -rf /var/lib/apt/lists/*

# 3. PyTorch 및 비전 AI 설치 
RUN pip3 install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu128
RUN pip3 install ultralytics opencv-python "numpy<2"

# 4. FFmpeg 시스템 패키지 설치 (OpenCV는 위에서 pip로 설치함)
RUN apt-get update && apt-get install -y \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# 5. ROS2 환경변수 및 폴더 숨기기 (.hidden) 적용
RUN echo "source /opt/ros/humble/setup.bash" >> /root/.bashrc && \
    echo "if [ -f /root/workspace/install/setup.bash ]; then" >> /root/.bashrc && \
    echo "  source /root/workspace/install/setup.bash" >> /root/.bashrc && \
    echo "fi" >> /root/.bashrc
RUN echo -e "Downloads\nPictures\nTemplates\nDocuments\nMusic\nPublic\nVideos\nsnap\nDesktop" > /root/.hidden

# 6. 자동 실행 스크립트 작성 (TCP Endpoint & Camera Streamer & BioSignal Publisher)
RUN echo '#!/bin/bash\n\
    source /opt/ros/humble/setup.bash\n\
    if [ -f /root/workspace/install/setup.bash ]; then\n\
    source /root/workspace/install/setup.bash\n\
    fi\n\
    ros2 run ros_tcp_endpoint default_server_endpoint --ros-args -p ROS_IP:=0.0.0.0 &\n\
    ros2 run my_ros2_basics camera_streamer &\n\
    ros2 run my_ros2_basics biosignal_publisher &\n\
    exec "$@"\n'\
    > /custom_entrypoint.sh && chmod +x /custom_entrypoint.sh

WORKDIR /root/workspace
ENTRYPOINT ["/custom_entrypoint.sh"]