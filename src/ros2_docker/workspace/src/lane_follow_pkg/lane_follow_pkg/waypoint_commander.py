import argparse
import json
import sys
import time
from pathlib import Path

import rclpy
from rclpy.node import Node
from rclpy.utilities import remove_ros_args
from std_msgs.msg import Int32

try:
    from ament_index_python.packages import get_package_share_directory
except ImportError:
    get_package_share_directory = None


def resolve_map_path():
    candidates = []

    if get_package_share_directory is not None:
        try:
            candidates.append(
                Path(get_package_share_directory('lane_follow_pkg')) / 'TopologicalMap.json'
            )
        except Exception:
            pass

    candidates.append(Path(__file__).resolve().parents[1] / 'TopologicalMap.json')

    for candidate in candidates:
        if candidate.exists():
            return candidate

    return candidates[-1]


def parse_waypoint_value(raw_value):
    text = str(raw_value).strip()
    if not text:
        raise ValueError('빈 waypoint 값은 사용할 수 없습니다.')

    if text.lower().startswith('waypoint_'):
        text = text.split('_')[-1].strip()

    return int(text)


def load_waypoint_catalog(map_path):
    map_file = Path(map_path)
    if not map_file.exists():
        raise FileNotFoundError(f'맵 파일을 찾을 수 없습니다: {map_file}')

    with map_file.open('r', encoding='utf-8') as handle:
        payload = json.load(handle)

    catalog = []
    for waypoint in payload.get('waypoints', []):
        waypoint_id = str(waypoint.get('id', '')).strip()
        if not waypoint_id:
            continue

        try:
            value = parse_waypoint_value(waypoint_id)
        except ValueError:
            continue

        catalog.append((value, waypoint_id))

    catalog.sort(key=lambda item: item[0])
    return catalog


class WaypointCommander(Node):
    def __init__(self, topic_name):
        super().__init__('waypoint_commander')
        self.topic_name = topic_name
        self.publisher = self.create_publisher(Int32, topic_name, 10)

    def wait_for_subscribers(self, timeout_sec):
        if timeout_sec <= 0.0:
            return self.publisher.get_subscription_count()

        deadline = time.monotonic() + timeout_sec
        while time.monotonic() < deadline:
            subscriber_count = self.publisher.get_subscription_count()
            if subscriber_count > 0:
                return subscriber_count
            rclpy.spin_once(self, timeout_sec=0.1)

        return self.publisher.get_subscription_count()

    def publish_waypoint(self, waypoint_value, repeat_count, interval_sec):
        message = Int32()
        message.data = int(waypoint_value)

        for index in range(repeat_count):
            self.publisher.publish(message)
            self.get_logger().info(
                f'Waypoint {message.data} -> {self.topic_name} '
                f'({index + 1}/{repeat_count})'
            )
            if index + 1 < repeat_count:
                rclpy.spin_once(self, timeout_sec=0.0)
                time.sleep(interval_sec)


def build_parser():
    parser = argparse.ArgumentParser(
        description='lane_follow_pkg 목표 waypoint를 명령어로 퍼블리시합니다.',
    )
    parser.add_argument(
        'waypoint',
        nargs='?',
        help='보낼 waypoint 번호 또는 Waypoint_<번호> 형식',
    )
    parser.add_argument(
        '--interactive',
        action='store_true',
        help='여러 waypoint를 순서대로 입력할 수 있는 프롬프트를 엽니다.',
    )
    parser.add_argument(
        '--list',
        action='store_true',
        help='맵에 등록된 waypoint 목록을 출력합니다.',
    )
    parser.add_argument(
        '--map',
        default=str(resolve_map_path()),
        help='검증에 사용할 TopologicalMap.json 경로',
    )
    parser.add_argument(
        '--topic',
        default='/ec2_cmd/target_waypoint',
        help='waypoint를 퍼블리시할 ROS 2 토픽',
    )
    parser.add_argument(
        '--repeat',
        type=int,
        default=3,
        help='같은 waypoint 메시지를 반복 전송할 횟수',
    )
    parser.add_argument(
        '--interval',
        type=float,
        default=0.15,
        help='반복 전송 간격(초)',
    )
    parser.add_argument(
        '--wait-for-subscriber',
        type=float,
        default=2.0,
        help='vision_detect 구독자 연결을 기다릴 최대 시간(초)',
    )
    return parser


def normalize_main_args(args):
    if args is None:
        return list(sys.argv)

    raw_args = list(args)
    if not raw_args:
        return ['waypoint_commander']

    first_arg = str(raw_args[0])
    if first_arg.startswith('-') or first_arg.isdigit() or first_arg.lower().startswith('waypoint_'):
        return ['waypoint_commander', *raw_args]

    return raw_args


def print_waypoint_catalog(catalog):
    if not catalog:
        print('등록된 waypoint가 없습니다.')
        return

    print('사용 가능한 waypoint 목록:')
    for value, waypoint_id in catalog:
        print(f'  {value:>3}  ({waypoint_id})')


def resolve_target_waypoint(raw_value, available_waypoints):
    waypoint_value = parse_waypoint_value(raw_value)
    if available_waypoints and waypoint_value not in available_waypoints:
        raise ValueError(f'맵에 없는 waypoint입니다: {waypoint_value}')
    return waypoint_value


def interactive_loop(commander, available_waypoints, repeat_count, interval_sec):
    print("interactive 모드입니다. waypoint 번호를 입력하세요. 종료는 'q' 입니다.")
    while True:
        try:
            raw_value = input('waypoint> ').strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return
        if raw_value.lower() in {'q', 'quit', 'exit'}:
            return
        if not raw_value:
            continue

        try:
            waypoint_value = resolve_target_waypoint(raw_value, available_waypoints)
        except ValueError as error:
            print(error)
            continue

        commander.publish_waypoint(waypoint_value, repeat_count, interval_sec)


def main(args=None):
    raw_args = normalize_main_args(args)
    cli_args = remove_ros_args(args=raw_args)
    parser = build_parser()
    options = parser.parse_args(cli_args[1:])

    try:
        catalog = load_waypoint_catalog(options.map)
    except Exception as error:
        catalog = []
        print(f'waypoint 맵 로드 실패: {error}')

    available_waypoints = {value for value, _ in catalog}

    if options.list:
        print_waypoint_catalog(catalog)
        if not options.interactive and not options.waypoint:
            return

    if not options.interactive and not options.waypoint:
        parser.error('waypoint 번호를 지정하거나 --interactive 를 사용하세요.')

    if options.repeat <= 0:
        parser.error('--repeat 는 1 이상이어야 합니다.')
    if options.interval < 0.0:
        parser.error('--interval 은 0 이상이어야 합니다.')
    if options.wait_for_subscriber < 0.0:
        parser.error('--wait-for-subscriber 는 0 이상이어야 합니다.')

    requested_waypoint = None
    if options.waypoint:
        try:
            requested_waypoint = resolve_target_waypoint(
                options.waypoint,
                available_waypoints,
            )
        except ValueError as error:
            parser.error(str(error))

    rclpy.init(args=raw_args)
    commander = WaypointCommander(options.topic)

    try:
        subscriber_count = commander.wait_for_subscribers(options.wait_for_subscriber)
        if subscriber_count <= 0:
            commander.get_logger().warn(
                '구독자를 찾지 못했지만 waypoint를 전송합니다. '
                'vision_detect 노드가 실행 중인지 확인하세요.'
            )
        else:
            commander.get_logger().info(
                f'{options.topic} 구독자 {subscriber_count}개 확인'
            )

        if options.interactive:
            interactive_loop(
                commander,
                available_waypoints,
                options.repeat,
                options.interval,
            )
        else:
            commander.publish_waypoint(
                requested_waypoint,
                options.repeat,
                options.interval,
            )
    finally:
        commander.destroy_node()
        rclpy.shutdown()


if __name__ == '__main__':
    main()
