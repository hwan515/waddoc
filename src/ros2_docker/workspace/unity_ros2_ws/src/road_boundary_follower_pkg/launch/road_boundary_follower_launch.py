from launch import LaunchDescription
from launch_ros.actions import Node


def generate_launch_description():
    return LaunchDescription([
        Node(
            package='road_boundary_follower_pkg',
            executable='road_boundary_follower',
            name='road_boundary_follower',
            output='screen'
        )
    ])