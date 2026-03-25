from setuptools import find_packages, setup

package_name = 'lane_follow_pkg'

setup(
    name=package_name,
    version='0.0.0',
    packages=find_packages(exclude=['test']),
    data_files=[
        ('share/ament_index/resource_index/packages',
            ['resource/' + package_name]),
        ('share/' + package_name, ['package.xml', 'TopologicalMap.json']),
    ],
    install_requires=['setuptools'],
    zip_safe=True,
    maintainer='seungyeon9944',
    maintainer_email='you@example.com',
    description='Lane following package',
    license='TODO: License declaration',
    tests_require=['pytest'],
    entry_points={
        'console_scripts': [
            'vision_detect = lane_follow_pkg.vision_detect:main',
            'waypoint_commander = lane_follow_pkg.waypoint_commander:main',
        ],
    },
)
