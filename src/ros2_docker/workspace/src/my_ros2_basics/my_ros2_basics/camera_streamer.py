"""
camera_streamer.py
ROS2 /camera/image_raw -> FFmpeg subprocess (stdin) -> h264_nvenc (GPU 하드웨어 가속) -> MediaMTX(RTSP)
"""

import subprocess
import threading
import rclpy
from rclpy.node import Node
from sensor_msgs.msg import Image
from std_msgs.msg import String
import numpy as np
import cv2

class CameraStreamer(Node):
    def __init__(self):
        super().__init__('camera_streamer')
        
        self.declare_parameter('rtsp_url', 'rtsp://127.0.0.1:8554/unity_cam')
        self.declare_parameter('width', 640)
        self.declare_parameter('height', 480)
        self.declare_parameter('fps', 10)
        
        self.drive_state = 0 
        
        rtsp_url    = self.get_parameter('rtsp_url').get_parameter_value().string_value
        self.width  = self.get_parameter('width').get_parameter_value().integer_value
        self.height = self.get_parameter('height').get_parameter_value().integer_value
        fps         = self.get_parameter('fps').get_parameter_value().integer_value

        self.get_logger().info(f'FFmpeg(NVENC GPU가속) 스트리밍 시작 -> {rtsp_url} ({self.width}x{self.height}@{fps}fps)')

        # 프레임 버퍼 초기화 (검은 화면)
        self.current_frame = np.zeros((self.height, self.width, 3), dtype=np.uint8)
        self.frame_count = 0
        
        self._start_encoder(rtsp_url, fps)

        self.sub_state_drive = self.create_subscription(
            String, '/state', self.state_callback, 1
        )

        self.sub_front = self.create_subscription(
            Image, '/camera/image_raw', self.front_image_callback, 10
        )

        self.sub_inner = self.create_subscription(
            Image, '/camera/image_raw_inner', self.inner_image_callback, 10
        )

        # 30Hz 주기로 FFmpeg에 프레임을 쏘는 독립 타이머 생성d
        self.timer = self.create_timer(1.0 / fps, self.stream_timer_callback)

    def _start_encoder(self, rtsp_url, fps):
        """FFmpeg 서브프로세스를 실행하고 stdin 파이프를 엽니다."""
        ffmpeg_cmd = [
            'ffmpeg',
            '-y',
            '-f', 'rawvideo',
            '-vcodec', 'rawvideo',
            '-pix_fmt', 'bgr24',
            '-s', f'{self.width}x{self.height}',
            '-r', str(fps),
            '-i', '-',  
            '-c:v', 'h264_nvenc',       
            '-preset', 'llhq',          
            '-tune', 'ull',             
            '-zerolatency', '1',
            '-profile:v', 'baseline',
            '-bf', '0',
            '-pix_fmt', 'yuv420p',
            '-b:v', '2000k',
            '-g', str(fps * 2),
            '-f', 'rtsp',
            '-rtsp_transport', 'tcp',
            rtsp_url
        ]

        self.encoder_process = subprocess.Popen(
            ffmpeg_cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE
        )
        self.get_logger().info('FFmpeg 프로세스 시작 및 stdin 파이프 연결 완료')

        self._stderr_thread = threading.Thread(target=self._read_stderr, daemon=True)
        self._stderr_thread.start()

    def _read_stderr(self):
        for line in self.encoder_process.stderr:
            try:
                decoded = line.decode('utf-8', errors='replace').rstrip()
                if decoded and ('frame=' in decoded or 'Error' in decoded):
                    self.get_logger().info(f'[FFmpeg] {decoded}')
            except Exception:
                pass

    def state_callback(self, msg: String):
        state_text = msg.data.strip()
        # '주행 중' 또는 '출발' 상태일 때 전방 카메라, 나머지는 내부 카메라
        new_state = 1 if state_text in ('주행 중', '출발') else 0
        if new_state != self.drive_state:
            if new_state == 1:
                self.get_logger().info(f'카메라 전환: [전방 카메라] (state={state_text!r})')
            else:
                self.get_logger().info(f'카메라 전환: [내부 카메라] (state={state_text!r})')
            self.drive_state = new_state

    def front_image_callback(self, msg: Image):
        if self.drive_state == 1:
            self.process_image(msg)

    def inner_image_callback(self, msg: Image):
        if self.drive_state == 0:
            self.process_image(msg)

    def process_image(self, msg: Image):
        """이미지를 변환하여 current_frame 버퍼만 갱신합니다."""
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

            # shape 인덱싱 버그 수정
            if img.shape[1] != self.width or img.shape[0] != self.height:
                img = cv2.resize(img, (self.width, self.height))

            # 최신 프레임 버퍼 업데이트
            self.current_frame = img

        except Exception as e:
            self.get_logger().error(f'이미지 처리 오류: {e}')

    def stream_timer_callback(self):
        """정확한 fps 주기로 버퍼의 이미지를 FFmpeg에 밀어넣습니다."""
        if hasattr(self, 'encoder_process') and self.encoder_process.poll() is None:
            try:
                self.encoder_process.stdin.write(self.current_frame.tobytes())
                self.encoder_process.stdin.flush()
                self.frame_count += 1

                if self.frame_count % 60 == 0:
                    self.get_logger().info(f'[{self.frame_count}프레임] GPU 스트리밍 중... (State: {self.drive_state})')
            except BrokenPipeError:
                self.get_logger().error('FFmpeg 파이프라인 BrokenPipeError')
        else:
            self.get_logger().error('FFmpeg 프로세스가 죽었습니다.', throttle_duration_sec=5.0)

    def destroy_node(self):
        if hasattr(self, 'encoder_process') and self.encoder_process.poll() is None:
            self.encoder_process.stdin.close()
            self.encoder_process.terminate()
            self.encoder_process.wait(timeout=3)
            self.get_logger().info("FFmpeg 프로세스 종료 완료")
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
