"""
camera_streamer.py
ROS2 /camera/image_raw → Named FIFO → ffmpeg → MediaMTX(RTSP) 실시간 스트리밍 노드

ffmpeg가 stdin 대신 Named Pipe(FIFO)에서 프레임을 읽어서 nohup & 환경에서도 동작합니다.
"""

import subprocess
import threading
import os
import rclpy
from rclpy.node import Node
from sensor_msgs.msg import Image
import numpy as np
import cv2

FIFO_PATH = '/tmp/ros_camera_fifo'


class CameraStreamer(Node):
    def __init__(self):
        super().__init__('camera_streamer')

        self.declare_parameter('rtsp_url', 'rtsp://127.0.0.1:8554/unity_cam')
        self.declare_parameter('width', 640)
        self.declare_parameter('height', 480)
        self.declare_parameter('fps', 10)   # Unity 카메라가 ~10Hz이므로 맞춤

        rtsp_url    = self.get_parameter('rtsp_url').get_parameter_value().string_value
        self.width  = self.get_parameter('width').get_parameter_value().integer_value
        self.height = self.get_parameter('height').get_parameter_value().integer_value
        fps         = self.get_parameter('fps').get_parameter_value().integer_value

        self.get_logger().info(f'RTSP 스트리밍 시작 → {rtsp_url}  ({self.width}x{self.height}@{fps}fps)')

        # FIFO 생성
        if os.path.exists(FIFO_PATH):
            os.remove(FIFO_PATH)
        os.mkfifo(FIFO_PATH)
        self.get_logger().info(f'FIFO 생성: {FIFO_PATH}')
        
        # ffmpeg 실행 시작
        self._start_ffmpeg()

        self.frame_count = 0
        self.subscription = self.create_subscription(
            Image,
            '/camera/image_raw',
            self.image_callback,
            10
        )

    def _start_ffmpeg(self):
        fps = self.get_parameter('fps').get_parameter_value().integer_value
        rtsp_url = self.get_parameter('rtsp_url').get_parameter_value().string_value
        
        # ffmpeg를 FIFO에서 읽도록 실행 (nohup 환경에서도 안전)
        ffmpeg_cmd = [
            'ffmpeg',
            '-y',
            '-f', 'rawvideo',
            '-vcodec', 'rawvideo',
            '-pix_fmt', 'bgr24',
            '-s', f'{self.width}x{self.height}',
            '-r', str(fps),
            '-i', FIFO_PATH,           # FIFO(Named Pipe)에서 읽기
            '-vcodec', 'libx264',
            '-preset', 'ultrafast',
            '-tune', 'zerolatency',
            '-g', str(fps * 2),        # keyframe 간격
            '-pix_fmt', 'yuv420p',
            '-f', 'rtsp',
            '-rtsp_transport', 'tcp',
            '-max_muxing_queue_size', '1024',
            '-stimeout', '60000000',   # RTSP 소켓 타임아웃 60초 (마이크로초)
            rtsp_url
        ]

        self.ffmpeg = subprocess.Popen(
            ffmpeg_cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE
        )
        self.get_logger().info('ffmpeg 프로세스 (재)시작 완료')

        # stderr 로그 스레드
        self._stderr_thread = threading.Thread(target=self._read_stderr, daemon=True)
        self._stderr_thread.start()
        
        # FIFO 연결 (다시 열기)
        if hasattr(self, 'fifo_fd') and self.fifo_fd is not None:
            try:
                self.fifo_fd.close()
            except Exception:
                pass
        self.fifo_fd = open(FIFO_PATH, 'wb')
        self.get_logger().info('FIFO 쓰기 연결 완료')

    def image_callback(self, msg: Image):
        try:
            dtype = np.uint8

            if msg.encoding == 'rgb8':
                img = np.frombuffer(msg.data, dtype=dtype).reshape(msg.height, msg.width, 3)
                img = cv2.cvtColor(img, cv2.COLOR_RGB2BGR)
            elif msg.encoding == 'bgr8':
                img = np.frombuffer(msg.data, dtype=dtype).reshape(msg.height, msg.width, 3)
            elif msg.encoding in ('rgba8', 'bgra8'):
                img = np.frombuffer(msg.data, dtype=dtype).reshape(msg.height, msg.width, 4)
                code = cv2.COLOR_RGBA2BGR if msg.encoding == 'rgba8' else cv2.COLOR_BGRA2BGR
                img = cv2.cvtColor(img, code)
            else:
                self.get_logger().warn(f'지원하지 않는 인코딩: {msg.encoding}', throttle_duration_sec=5.0)
                return

            # 해상도 불일치 시 리사이즈
            if img.shape[1] != self.width or img.shape[0] != self.height:
                img = cv2.resize(img, (self.width, self.height))

            # FIFO에 raw 프레임 쓰기
            self.fifo_fd.write(img.tobytes())
            self.fifo_fd.flush()
            self.frame_count += 1

            if self.frame_count % 60 == 0:
                self.get_logger().info(f'[{self.frame_count}프레임] 스트리밍 중...')

        except BrokenPipeError:
            self.get_logger().error('FIFO 파이프 끊김! ffmpeg가 종료됐습니다. 다시 시작을 시도합니다.')
            self._start_ffmpeg()
        except Exception as e:
            self.get_logger().error(f'이미지 처리 오류: {e}')

    def _read_stderr(self):
        """ffmpeg stderr를 실시간으로 읽어서 ROS2 로그로 출력"""
        for line in self.ffmpeg.stderr:
            try:
                decoded = line.decode('utf-8', errors='replace').rstrip()
                if decoded:
                    # ffmpeg progress 줄은 warn 레벨로 출력
                    if 'frame=' in decoded and 'fps=' in decoded:
                        self.get_logger().info(f'[ffmpeg] {decoded}')
                    else:
                        self.get_logger().info(f'[ffmpeg] {decoded}')
            except Exception:
                pass

    def destroy_node(self):
        try:
            self.fifo_fd.close()
        except Exception:
            pass
        try:
            self.ffmpeg.terminate()
            self.ffmpeg.wait(timeout=3)
        except Exception:
            pass
        if os.path.exists(FIFO_PATH):
            os.remove(FIFO_PATH)
        super().destroy_node()


def main(args=None):
    rclpy.init(args=args)
    try:
        node = CameraStreamer()
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        if rclpy.ok():
            rclpy.shutdown()


if __name__ == '__main__':
    main()
