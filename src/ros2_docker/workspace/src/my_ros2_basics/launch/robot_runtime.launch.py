from launch import LaunchDescription
from launch_ros.actions import Node


def generate_launch_description():
    return LaunchDescription([
        Node(
            package='lane_follow_pkg',
            executable='vision_detect',
            name='vision_detect',
            output='screen'
        ),
        Node(
            package='my_ros2_basics',
            executable='mqtt_bridge',
            name='mqtt_bridge',
            output='screen'
        ),
    ])
