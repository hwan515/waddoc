FROM ros:humble-ros-base

WORKDIR /app

# 시스템 업데이트 및 CycloneDDS 패키지 추가 설치
RUN apt-get update && apt-get install -y \
    python3-pip \
    ros-humble-rmw-cyclonedds-cpp \
    && rm -rf /var/lib/apt/lists/*
RUN apt-get update && apt-get install -y python3-pip
RUN pip3 install fastapi uvicorn
COPY router_app.py /app/

CMD ["/bin/bash", "-c", "source /opt/ros/humble/setup.bash && python3 /app/router_app.py"]