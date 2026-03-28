#!/bin/bash

set -eo pipefail

RESPAWN_DELAY="${RESPAWN_DELAY:-2}"
declare -a CHILD_PIDS=()

source /opt/ros/humble/setup.bash
if [ -f /root/workspace/install/setup.bash ]; then
    source /root/workspace/install/setup.bash
fi

start_service() {
    local name="$1"
    shift

    (
        while true; do
            echo "[entrypoint] starting ${name}"
            if "$@"; then
                exit_code=0
            else
                exit_code=$?
            fi
            echo "[entrypoint] ${name} exited with code ${exit_code}; restarting in ${RESPAWN_DELAY}s"
            sleep "${RESPAWN_DELAY}"
        done
    ) &

    CHILD_PIDS+=("$!")
}

cleanup() {
    trap - EXIT INT TERM
    for pid in "${CHILD_PIDS[@]:-}"; do
        kill "${pid}" 2>/dev/null || true
    done
    wait || true
}

trap cleanup EXIT INT TERM

start_service "ros_tcp_endpoint" ros2 run ros_tcp_endpoint default_server_endpoint --ros-args -p ROS_IP:=0.0.0.0
start_service "camera_streamer" ros2 run my_ros2_basics camera_streamer
start_service "biosignal_publisher" ros2 run my_ros2_basics biosignal_publisher
start_service "mqtt_bridge" ros2 run my_ros2_basics mqtt_bridge

if [ "$#" -eq 0 ]; then
    set -- tail -f /dev/null
fi

echo "[entrypoint] starting foreground command: $*"
"$@" &
foreground_pid=$!
CHILD_PIDS+=("${foreground_pid}")

wait "${foreground_pid}"
