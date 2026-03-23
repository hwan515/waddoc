import os
from glob import glob
from setuptools import find_packages, setup

package_name = 'my_ros2_basics'

setup(
    name=package_name,
    version='0.0.0',
    packages=find_packages(exclude=['test']),
    data_files=[
        ('share/ament_index/resource_index/packages',
            ['resource/' + package_name]),
        ('share/' + package_name, ['package.xml']),
        # 런치 파일 설치 설정 추가
        (os.path.join('share', package_name, 'launch'), glob(os.path.join('launch', '*launch.[pxy][yma]*')))
    ],
    install_requires=['setuptools'],
    zip_safe=True,
    maintainer='root',
    maintainer_email='root@todo.todo',
    description='ROS 2 Basics Pub/Sub package',
    license='TODO: License declaration',
    extras_require={
        'test': [
            'pytest',
        ],
    },
    entry_points={
        'console_scripts': [
            'talker = my_ros2_basics.talker:main',
            'listener = my_ros2_basics.listener:main',
            'camera_streamer = my_ros2_basics.camera_streamer:main',
        ],
    },
)
