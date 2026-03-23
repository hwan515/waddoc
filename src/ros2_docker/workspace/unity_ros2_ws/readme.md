


1. 아래 디렉토리에 맞게 구성
2. 쉘 명령어 실행 
 

```bash

docker compsoe down
docker compsoe build
docker compsoe up

docker exec -it ros2_dev_env bash

```

docker쉘로 접속 후

```

source /opt/ros/humble/setup.bash
colcon build --symlink-install
source install/setup.bash
ros2 run waypoint_follow_pkg waypoint_follow

```

하이브리드 주행 패키지 실행:

```bash
ros2 run hybrid_follow_pkg hybrid_follow
```

터미널 입력에서 원하는 웨이포인트 입력<br>
ex)<br>
10<br>



![alt text](image.png)


