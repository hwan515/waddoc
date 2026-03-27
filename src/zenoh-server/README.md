# ROS2 -> Zenoh -> FastAPI -> FE Data Flow Guide

> Legacy notice
> 이 문서는 Zenoh/FastAPI 기반 `GET /api/minimap` 흐름을 정리한 레거시 문서입니다.
> 2026-03-27 기준 운영 관제 화면은 `robot/minimap`, `robot/odom`, `robot/state`, `robot/status` MQTT 토픽을 Spring SSE `/api/v1/robots/stream`으로 소비합니다.
> 따라서 `/api/minimap` 관련 내용은 현재 운영 경로가 아니라 debug 또는 과거 구조 참고용으로만 봐야 합니다.

이 문서는 현재 프로젝트에서 아래 흐름을 수정하거나 점검할 때 어디를 봐야 하는지 한 번에 찾기 위한 문서

- ROS2 `lane_follow_pkg` 에서 어떤 topic을 publish / subscribe 하는지
- 그 topic이 zenoh bridge를 통해 `zenoh-server` 의 FastAPI로 어떻게 들어오는지
- FastAPI가 어떤 JSON payload를 만들어 FE로 내보내는지
- FE `operator/control` 이 어떤 endpoint와 필드명을 실제로 읽는지
- 다른 곳에서 위치/상태를 영속 저장하려면 Spring BE의 어느 API를 써야 하는지

이 문서는 "어디 파일을 수정해야 하는지", "변수명과 설정값이 무엇인지", "topic / endpoint / payload field가 무엇인지"를 중심으로 적음

## 1. 전체 흐름 요약

현재 데이터 흐름은 크게 2개입니다.

### A. Legacy 실시간 차량 상태 / minimap 표시 흐름

1. ROS2 `lane_follow_pkg` 가 아래 topic을 publish 합니다.
   - `/odom`
   - `/state`
   - `/ec2_state/minimap_route`
2. 로컬 `zenoh-bridge-ros2dds` 가 ROS2 topic을 zenoh 네트워크로 전달합니다.
3. EC2 쪽 `zenoh_bridge_ec2` + `ros2_app_ec2` 가 같은 ROS2 graph에서 topic을 받습니다.
4. `router_app.py` 가 topic을 subscribe 해서 메모리 state를 갱신합니다.
5. FastAPI가 아래 endpoint로 FE에 JSON을 반환합니다.
   - `GET /api/minimap`
   - `GET /api/odom`
6. 과거 FE `operator/control` 은 `/api/minimap` 을 polling 해서 지도를 그렸습니다.

### B. 차량 명령 흐름

1. FE 또는 외부에서 FastAPI로 아래 요청을 보냅니다.
   - `POST /api/cmd/waypoint/{target}`
   - `POST /api/cmd/estop/{state}`
2. `router_app.py` 가 ROS2 command topic으로 다시 publish 합니다.
   - `/ec2_cmd/target_waypoint`
   - `/ec2_cmd/e_stop`
3. `vision_detect.py` 가 해당 command topic을 subscribe 해서 실제 주행 동작에 반영합니다.

### C. Spring BE 위치 저장 흐름

이건 minimap GET과 별도입니다.

1. Spring BE는 `/api/v1/missions/{missionId}/telemetry` 로 telemetry를 받아야 합니다.
2. 이 API가 들어와야 `MISSION.latitude`, `MISSION.longitude` 가 DB에 반영됩니다.
3. 운영 차량 리스트 초기값은 Spring mission API의 `currentLocation` 도 같이 사용합니다.

즉:

- legacy minimap debug를 보려면 `GET /api/minimap` 흐름이 중요합니다.
- DB에 위치를 남기려면 `POST /api/v1/missions/{missionId}/telemetry` 도 별도로 연결해야 합니다.

## 2. ROS2 쪽 수정 포인트

### 파일

- `src/ros2_docker/workspace/src/lane_follow_pkg/lane_follow_pkg/vision_detect.py`
- `src/ros2_docker/workspace/src/lane_follow_pkg/lane_follow_pkg/exporters.py`

### `vision_detect.py` 에서 보는 핵심 변수 / topic

`HybridDijkstraVisionFollower.__init__()` 안에 현재 실시간 topic wiring이 있습니다.

#### 입력 topic

- `self.odom_sub`
  - topic: `/odom`
  - message: `nav_msgs/msg/Odometry`
- `self.image_sub`
  - topic: `/camera/image_raw`
  - message: `sensor_msgs/msg/Image`
- `self.estop_sub`
  - topic: `/ec2_cmd/e_stop`
  - message: `std_msgs/msg/Bool`
- `self.target_sub`
  - topic: `/ec2_cmd/target_waypoint`
  - message: `std_msgs/msg/Int32`

#### 출력 topic

- `self.cmd_pub`
  - topic: `/cmd_vel`
  - message: `geometry_msgs/msg/Twist`
- `self.estop_pub`
  - topic: `/ec2_cmd/e_stop`
  - message: `std_msgs/msg/Bool`
- `self.pub_state_stop`
  - topic: `/cmd_state/e_stop`
- `self.pub_state_waypoint`
  - topic: `/cmd_state/target_waypoint`
- `self.state_pub`
  - topic: `/state`
  - message: `std_msgs/msg/String`
- `self.pub_minimap_route`
  - topic: `/ec2_state/minimap_route`
  - message: `std_msgs/msg/String`

### `vision_detect.py` 에서 topic 이름을 바꾸고 싶으면

아래 publish / subscribe 선언부를 먼저 바꾸고, 반드시 `router_app.py` 쪽 동일 topic도 같이 바꿔야 합니다.

- `/ec2_state/minimap_route`
- `/state`
- `/ec2_cmd/e_stop`
- `/ec2_cmd/target_waypoint`
- `/odom`

한쪽만 바꾸면 FastAPI가 값을 못 받습니다.

### `vision_detect.py` 주요 parameter

현재 선언된 주요 parameter는 다음입니다.

- `waypoint_json_path`
- `goal_waypoint_id`
- `show_debug_windows`
- `route_export_path`
- `enable_yolo_estop`
- `yolo_model`
- `yolo_device`
- `yolo_confidence`
- `yolo_input_size`
- `yolo_hazard_labels`

수정 위치는 `HybridDijkstraVisionFollower.__init__()` 안 `self.declare_parameter(...)` 부분입니다.

## 3. minimap payload 생성 위치

### 파일

- `src/ros2_docker/workspace/src/lane_follow_pkg/lane_follow_pkg/exporters.py`

### 클래스

- `MinimapRouteExporter`

이 클래스가 minimap JSON payload를 만들고 아래 2가지를 모두 담당합니다.

- ROS2 publish
- 파일 persist

### 실제 publish 함수

- `publish_minimap_route_payload(payload)`
- `persist_minimap_route_payload(payload)`
- `publish_cleared_route(...)`
- `export_route(...)`
- `publish_live_snapshot(...)`

### payload 수정 포인트

#### 실시간 공통 필드

`apply_runtime_payload_fields()` 에서 현재 매 publish 시점마다 붙는 값:

- `battery_soc`

#### route snapshot 기본 구조

`build_route_snapshot()` 과 `build_empty_route_snapshot()` 에서 현재 포함되는 대표 필드:

- `route_version`
- `generated_at`
- `source_node`
- `planner_type`
- `start_waypoint_id`
- `goal_waypoint_id`
- `target_waypoint_value`
- `current_pose`
- `speed`
- `speedMs`
- `speedKmh`
- `start_waypoint`
- `goal_waypoint`
- `battery_soc`
- `path_waypoint_ids`
- `path_waypoints`
- `trajectory_point_count`
- `trajectory`
- `bounds`
- `cleared`
- `clear_reason`

### 현재 표시용 trajectory 수정 위치

closest waypoint 포함 로직은 아래 메서드에 있습니다.

- `build_display_pose_and_path(...)`

이 로직은:

1. `path_ids` 기반 waypoint 목록 생성
2. `current_pose` 와 각 waypoint 거리 계산
3. 가장 가까운 waypoint index 선택
4. 그 index부터 minimap용 `path_waypoints`, `trajectory` 를 다시 구성

중요:

- 이건 minimap 표시용 payload만 바꿉니다.
- planner / follower / control trajectory는 직접 건드리지 않습니다.

### 배터리 값 수정 위치

`MinimapRouteExporter` 내부 상태:

- `self.current_battery_soc`

관련 메서드:

- `sanitize_battery_soc(value, default=100.0)`
- `get_battery_soc()`
- `update_battery_soc(value)`

외부 callback에서 battery를 갱신하고 싶으면 같은 exporter 인스턴스에 대해:

```python
self.route_exporter.update_battery_soc(new_value)
```

형태로 넣으면 됩니다.

## 4. Zenoh / FastAPI 쪽 수정 포인트

### 파일

- `src/zenoh-server/router_app/router_app.py`
- `src/zenoh-server/docker-compose.yml`

### `router_app.py` 역할

이 파일은 2가지를 동시에 합니다.

1. ROS2 subscriber / publisher
2. FastAPI HTTP endpoint 제공

즉 "bridge 이후 FastAPI가 실제로 뭘 받는지 / 뭘 내보내는지"를 바꾸려면 대부분 이 파일을 수정합니다.

### 현재 subscribe 하는 ROS2 topic

`Ec2ControlNode.__init__()` 안:

- `/odom`
  - `Odometry`
- `/ec2_state/minimap_route`
  - `String`
- `/state`
  - `String`

### 현재 publish 하는 ROS2 command topic

- `/ec2_cmd/e_stop`
  - `Bool`
- `/ec2_cmd/target_waypoint`
  - `Int32`

### Legacy FastAPI endpoint 목록

현재 실제로 서비스에 포함된 endpoint:

- `GET /api/odom`
- `GET /api/minimap`
- `POST /api/cmd/estop/{state}`
- `POST /api/cmd/waypoint/{target}`

Swagger 경로:

- `/swagger/fastapi`

### `current_state` 내부 key

`router_app.py` 상단 `current_state` 가 FastAPI 응답의 원본 메모리 상태입니다.

주요 key:

- `odom`
  - `x`
  - `y`
  - `z`
  - `speed_ms`
  - `speed_kmh`
- `minimap_pose`
  - `x`
  - `z`
  - `yaw`
- `route_snapshot`
  - `route_version`
  - `planner_type`
  - `goal_waypoint_id`
  - `generated_at`
  - `source_node`
  - `status`
  - `trajectory`
  - `path_waypoints`
  - `current_pose`
  - `speed_ms`
  - `speed_kmh`
  - `battery_soc`
  - `cleared`
  - `clear_reason`
- `has_odom`
- `robot_state`

### Legacy `GET /api/minimap` 응답 구조

현재 대표 응답 필드는 다음과 같습니다.

- `vehiclePose`
- `current_pose`
- `currentPose`
- `pathPoints`
- `trajectory`
- `currentLocation`
- `vehicleLocation`
- `location`
- `latitude`
- `longitude`
- `routeVersion`
- `plannerType`
- `goalWaypointId`
- `updatedAt`
- `sourceNode`
- `status`
- `state`
- `speed`
- `speedMs`
- `speedKmh`
- `vehicleSpeedMs`
- `vehicleSpeedKmh`
- `battery_soc`
- `batterySoc`
- `hasOdom`

주의:

- 현재 FE가 `latitude/longitude` 키를 보기 때문에, GPS가 없을 때는 `x/z` minimap 좌표를 alias로 내려주고 있습니다.
- 즉 지금 `latitude`, `longitude` 는 실제 위경도라기보다 "FE 좌표 칸에 보여주기 위한 minimap 좌표 alias" 입니다.

### `GET /api/minimap` 응답을 바꾸고 싶으면

주로 아래 함수들을 수정합니다.

- `sanitize_pose`
- `sanitize_path_points`
- `build_location_payload`
- `get_minimap_state`
- `route_snapshot_callback`

### `POST /api/cmd/*` 가 실제로 하는 일

- `trigger_estop(state)`
  - `send_emergency_stop()` 호출
  - `/ec2_cmd/e_stop` publish
- `trigger_waypoint(target)`
  - `send_waypoint()` 호출
  - `/ec2_cmd/target_waypoint` publish

즉 API path를 바꾸고 싶으면 FastAPI handler와 FE command URL 둘 다 같이 수정해야 합니다.

## 5. Zenoh / Docker 환경 변수

### 로컬 ROS2 쪽

파일:

- `src/ros2_docker/docker-compose.yml`

현재 핵심 설정:

- `ROS_DOMAIN_ID=0`
- `ROS_LOCALHOST_ONLY=1`
- `RMW_IMPLEMENTATION=rmw_cyclonedds_cpp`
- 로컬 bridge endpoint: `tcp/zenoh.waddoc.site:8081`

### EC2 zenoh-server 쪽

파일:

- `src/zenoh-server/docker-compose.yml`

현재 핵심 설정:

- `ROS_DISTRO=humble`
- `ROS_DOMAIN_ID=0`
- `ROS_LOCALHOST_ONLY=1`
- `RMW_IMPLEMENTATION=rmw_cyclonedds_cpp`
- `CYCLONEDDS_URI=...NetworkInterfaceAddress>lo...`
- bridge listen endpoint: `tcp/0.0.0.0:8081`

### FastAPI host / port 환경 변수

`router_app.py` 하단에서 읽는 값:

- `ROBOT_API_HOST`
- `ROBOT_API_PORT`

기본값:

- host: `0.0.0.0`
- port: `8000`

## 6. FE 쪽 수정 포인트

### 파일

- `src/FE/src/utils/runtimeConfig.js`
- `src/FE/vite.config.js`
- `src/FE/src/pages/Operator/ControlCenter.jsx`
- `src/FE/src/components/operator/MapMonitoring.jsx`
- `src/FE/src/components/operator/MinimapPanel.jsx`

### FE가 실제로 어떤 endpoint를 쓰는지

#### robot API URL 후보 생성

파일:

- `src/FE/src/utils/runtimeConfig.js`

핵심 함수:

- `getRobotApiUrlCandidates(path)`
- `getRobotCommandUrlCandidates(path)`

사용 환경변수:

- `VITE_ROBOT_API_BASE_URL`
- `VITE_ROBOT_TERMINAL_ID`
- `VITE_ROBOT_TERMINAL_KEY`

런타임 주입:

- `window.__APP_CONFIG__`

즉 FE를 운영 / 로컬 / 다른 FastAPI 주소로 돌리고 싶으면 이 파일과 runtime config를 먼저 봅니다.

#### dev proxy 설정

파일:

- `src/FE/vite.config.js`

현재 dev 서버 proxy:

- `/api/odom` -> robot API target
- `/api/cmd` -> robot API target
- `/api` -> Spring `http://localhost:8080`

즉 dev에서는 "robot API"와 "Spring API"가 다른 origin으로 갈 수 있습니다.

### operator/control 화면 진입점

파일:

- `src/FE/src/pages/Operator/ControlCenter.jsx`

현재 핵심 상수:

- `MINIMAP_POLL_INTERVAL_MS = 100`
- `ACTIVE_OPERATOR_VEHICLE_ID = 'veh_GIMCHEON_01'`

중요:

- 현재 주 차량 선택 기준이 이 vehicle ID에 묶여 있습니다.
- 다른 차량 ID를 기준으로 보고 싶으면 이 상수나 선택 로직을 바꿔야 합니다.

### FE가 `/api/minimap` 에서 읽는 필드

`ControlCenter.jsx` 안 polling 로직에서 현재 아래 필드를 순서대로 봅니다.

#### pose

- `vehiclePose`
- `current_pose`
- `currentPose`

#### path

- `pathPoints`
- `trajectory`

#### state

- `state`
- `vehicleState`
- `missionState`
- `status`

#### speed

- `speedKmh`
- `vehicleSpeedKmh`
- `speedMs`
- `vehicleSpeedMs`
- `speed`

#### location

- `currentLocation`
- `vehicleLocation`
- `location`
- `latitude`
- `longitude`
- 마지막 fallback으로 `posePayload.x / posePayload.z`

#### battery

- `battery_soc`
- `batterySoc`

즉 FastAPI 응답 field를 바꾸면 최소 `ControlCenter.jsx` 를 같이 확인해야 합니다.

### FE 렌더링 위치

#### command 전송

파일:

- `src/FE/src/components/operator/MapMonitoring.jsx`

현재 E-Stop 버튼은:

- `POST /api/cmd/estop/1`

를 호출합니다.

#### minimap 렌더

파일:

- `src/FE/src/components/operator/MinimapPanel.jsx`

현재 이 컴포넌트가 실제로 쓰는 값:

- `vehiclePose`
- `pathPoints`
- `vehicleState`
- `vehicleSpeed`
- `vehicleLocation`

중요한 렌더 조건:

- path는 `minimapPathPoints.length > 1` 일 때만 선을 그립니다.

즉 FastAPI가 path를 1점만 내려주면 차량은 떠도 경로는 안 보일 수 있습니다.

## 7. Spring BE 쪽 별도 위치 저장 API

### 파일

- `src/BE/src/main/java/com/waddoc/domain/mission/controller/MissionTelemetryController.java`
- `src/BE/src/main/java/com/waddoc/domain/mission/dto/MissionTelemetryRequest.java`

### endpoint

- `POST /api/v1/missions/{missionId}/telemetry`

### header

- `X-API-Key`

### body 필드

- `source`
- `sourceEventId`
- `seqNo`
- `vehicleId`
- `phase`
- `latitude`
- `longitude`
- `speed`
- `heading`
- `timestamp`
- `metadata`

이 endpoint는 "실시간 minimap 표시"와는 별개입니다.

이걸 연결해야 하는 경우:

- 운영 차량 리스트의 Spring mission `currentLocation`
- DB에 누적되는 미션 위치
- telemetry 기반 후속 처리

## 8. 어디를 수정해야 하는지 빠른 가이드

### 8-1. ROS topic 이름을 바꾸고 싶다

수정 파일:

- `vision_detect.py`
- `router_app.py`
- 필요시 FE command path 호출부

같이 봐야 하는 이름:

- `/ec2_state/minimap_route`
- `/state`
- `/odom`
- `/ec2_cmd/e_stop`
- `/ec2_cmd/target_waypoint`

### 8-2. minimap JSON payload field를 바꾸고 싶다

수정 파일:

- `exporters.py`
- `router_app.py`
- `ControlCenter.jsx`

### 8-3. FE에서 다른 field 이름으로 읽게 하고 싶다

수정 파일:

- `ControlCenter.jsx`
- `MapMonitoring.jsx`
- `MinimapPanel.jsx`

### 8-4. FastAPI endpoint path를 바꾸고 싶다

수정 파일:

- `router_app.py`
- `runtimeConfig.js`
- `vite.config.js`
- command 호출 FE 코드

### 8-5. 로컬 / 운영 API 주소를 바꾸고 싶다

수정 파일:

- `runtimeConfig.js`
- `vite.config.js`
- `FE/public/runtime-config.js`
- 배포 환경변수

### 8-6. 운영 차량 리스트 vehicleId 기준을 바꾸고 싶다

수정 파일:

- `ControlCenter.jsx`

현재 고정값:

- `ACTIVE_OPERATOR_VEHICLE_ID = 'veh_GIMCHEON_01'`

## 9. 점검용 터미널 명령

### ROS publish 확인

```bash
docker exec -it ros2_dev_env bash
cd /root/workspace
source /opt/ros/humble/setup.bash
source /root/workspace/install/setup.bash
ros2 topic echo /ec2_state/minimap_route --once
ros2 topic echo /state --once
ros2 topic echo /odom --once
```

### FastAPI 응답 확인

로컬:

```bash
curl -i http://localhost:8000/api/minimap
curl -i http://localhost:8000/api/odom
```

운영:

```bash
curl -k -i https://www.waddoc.site/api/minimap
curl -k -i https://www.waddoc.site/api/odom
```

### command 확인

```bash
curl -k -i -X POST https://www.waddoc.site/api/cmd/waypoint/221
curl -k -i -X POST https://www.waddoc.site/api/cmd/estop/1
```

## 10. 현재 자주 헷갈리는 포인트

- `POST /api/cmd/waypoint/{target}` 이 200이라고 해서 minimap GET이 정상이라는 뜻은 아닙니다.
- `GET /api/minimap` 은 Spring mission API와 별개입니다.
- Spring mission `currentLocation` 은 telemetry API를 별도로 쳐야 DB에 반영됩니다.
- FE는 `vehiclePose.x/z`만 보지 않고, `currentLocation` / `latitude` / `longitude` 도 적극적으로 봅니다.
- path는 2점 이상이어야 선으로 보입니다.
- 현재 `latitude`, `longitude` 는 실 GPS가 아니라 minimap 좌표 alias일 수 있습니다.

## 11. 추천 수정 순서

새 기능을 붙일 때는 보통 아래 순서가 가장 안전합니다.

1. `vision_detect.py` 에서 publish topic / state / payload source 수정
2. `exporters.py` 에서 minimap payload field 추가
3. `router_app.py` 에서 route snapshot 수신 및 `/api/minimap` 응답 반영
4. `runtimeConfig.js` / `vite.config.js` 에서 FE endpoint 경로 확인
5. `ControlCenter.jsx` 에서 field binding 추가
6. `MapMonitoring.jsx`, `MinimapPanel.jsx` 에서 렌더 조건 확인
7. DB 저장이 필요하면 Spring telemetry API 별도 연결

## 12. 한 줄 결론

현재 구조에서 가장 중요한 파일은 아래 4개입니다.

- ROS publish / command 수신: `vision_detect.py`
- minimap payload 생성: `exporters.py`
- FastAPI / ROS bridge / API 응답: `router_app.py`
- FE polling / field binding: `ControlCenter.jsx`

이 4개만 정확히 이해하면 "ROS2 topic이 zenoh-server로 넘어가고, FastAPI를 거쳐 FE로 보이는 흐름"은 거의 다 따라갈 수 있습니다.
