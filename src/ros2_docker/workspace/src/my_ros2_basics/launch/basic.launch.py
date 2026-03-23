import os
from ament_index_python.packages import get_package_share_directory
from launch import LaunchDescription
from launch_ros.actions import Node

def generate_launch_description():
    return LaunchDescription([
        Node(
            package='my_ros2_basics',
            executable='talker',
            name='talker_node',
            output='screen',
            parameters=[
                {'interval': 2.5} # 기본값 1.0에서 0.5초로 변경 테스트
            ]
        ),
        Node(
            package='my_ros2_basics',
            executable='listener',
            name='listener_node',
            output='screen'
        )
    ])
