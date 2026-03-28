#!/bin/bash

echo "========================================================"
echo "ROS 2 Humble & PyTorch Docker Environment Setup Script"
echo "========================================================"

# 운영체제 확인
if [ -f /etc/os-release ]; then
    . /etc/os-release
    if [ "$ID" != "ubuntu" ]; then
        echo "경고: 이 스크립트는 Ubuntu 환경용으로 작성되었습니다. 현재 OS: $ID"
        echo "계속 진행하시겠습니까? (y/n)"
        read -r response
        if [[ ! "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
            exit 1
        fi
    fi
else
    echo "운영체제 정보를 확인할 수 없습니다. 스크립트를 중단합니다."
    exit 1
fi

echo -e "\n[1/4] 시스템 업데이트 및 필수 패키지 설치 중..."
sudo apt-get update && sudo apt-get install -y ca-certificates curl gnupg

echo -e "\n[2/4] Docker 엔진 및 Docker Compose 설치 확인..."
if ! command -v docker &> /dev/null; then
    echo "Docker가 설치되어 있지 않습니다. 설치를 진행합니다..."
    
    # Docker 공식 GPG 키 추가
    sudo install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    sudo chmod a+r /etc/apt/keyrings/docker.gpg

    # Docker 저장소 설정
    echo \
      "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      "$(. /etc/os-release && echo "$VERSION_CODENAME")" stable" | \
      sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

    # Docker 엔진 설치
    sudo apt-get update
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    
    # 현재 사용자를 docker 그룹에 추가 (sudo 없이 docker 사용)
    sudo usermod -aG docker $USER
    echo "Docker 설치가 완료되었습니다. 변경사항을 적용하려면 시스템을 재부팅하거나 로그아웃 후 다시 로그인해야 할 수 있습니다."
else
    echo "Docker가 이미 설치되어 있습니다."
fi

echo -e "\n[3/4] X11 GUI 권한 설정..."
# 호스트에서 xhost 명령어를 사용할 수 있는지 확인하고 패키지 설치
if ! command -v xhost &> /dev/null; then
    sudo apt-get install -y x11-xserver-utils
fi
# Docker 컨테이너의 로컬 X server 접속 허용
xhost +local:docker

echo -e "\n[4/4] NVIDIA GPU 및 Container Toolkit 확인..."
if command -v nvidia-smi &> /dev/null; then
    echo "NVIDIA GPU가 감지되었습니다. NVIDIA Container Toolkit 설치 여부를 확인합니다."
    if ! command -v nvidia-ctk &> /dev/null; then
        echo "NVIDIA Container Toolkit이 설치되어 있지 않습니다. 공식 가이드를 참조하여 설치해 주세요."
        echo "링크: https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html"
        echo "설치 후 반드시 도커 데몬을 재시작해야 합니다: sudo systemctl restart docker"
    else
        echo "NVIDIA Container Toolkit이 설치되어 있습니다."
    fi
else
    echo "NVIDIA GPU 스마일 명령어(nvidia-smi)를 찾을 수 없거나 GPU가 없습니다."
    echo "가상환경(VM)이거나 GPU가 없는 경우, docker-compose.yml 파일에 있는 GPU 할당 부분(deploy: 이하)을 주석 처리해야 컨테이너가 실행됩니다."
    echo "자동으로 주석 처리하시겠습니까? (y/n)"
    read -r response
    if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
        if [ -f "docker-compose.yml" ]; then
            # 원본 백업
            cp docker-compose.yml docker-compose.yml.bak
            # deploy: 부터 끝까지 주석 처리 (sed 사용)
            sed -i '/deploy:/,$s/^/#/' docker-compose.yml
            echo "docker-compose.yml 파일의 GPU 기능이 비활성화되었습니다. (원본은 docker-compose.yml.bak에 백업됨)"
        else
            echo "docker-compose.yml 파일을 찾을 수 없습니다."
        fi
    fi
fi

echo -e "\n========================================================"
echo "초기 환경 설정이 완료되었습니다!"
echo ""
echo "다음 명령어로 컨테이너를 실행할 수 있습니다:"
echo "  docker compose up -d"
echo ""
echo "실행된 컨테이너에 접속하려면:"
echo "  docker exec -it ros2_dev_env bash"
echo "========================================================"
