import zenoh
import time
import struct  

class ZenohRouter:
    def __init__(self):
        conf = zenoh.Config()
        conf.insert_json5("mode", '"client"')
        conf.insert_json5("connect/endpoints", '["tcp/zenoh:8081"]')
        
        print("Zenoh 라우터에 연결 시도 중...", flush=True)
        self.session = zenoh.open(conf)
        print("연결 성공!", flush=True)
        
        self.publishers = {}
        self.subscribers = {}

    def add_subscriber(self, topic):
       
        if topic not in self.subscribers:
            self.subscribers[topic] = self.session.declare_subscriber(topic, self.listener)
            print(f" [구독 시작] 토픽: {topic}", flush=True)

    def add_publisher(self, topic):
        """특정 토픽의 퍼블리셔를 생성해 딕셔너리에 저장합니다."""
        if topic not in self.publishers:
            self.publishers[topic] = self.session.declare_publisher(topic)
            print(f" [발행 준비 완료] 토픽: {topic}", flush=True)

    def create_ros2_string_payload(self, text):
        header = b'\x00\x01\x00\x00'
        text_bytes = text.encode('utf-8')
        length = len(text_bytes) + 1
        length_bytes = struct.pack('<I', length)
        return header + length_bytes + text_bytes + b'\x00'

    def listener(self, sample):
        raw_data = sample.payload.to_bytes()

        try:
            decoded_msg = raw_data[8:].decode('utf-8').replace('\x00', '')
            print(f" [데이터 도착] 토픽: {sample.key_expr}")
            print(f" [해독 완료]: {decoded_msg}", flush=True)
        except Exception as e:
            print(f"❌ 해독 실패 (데이터 포맷 불일치): {e}", flush=True)

   
    def publish_message(self, topic, msg): # 토픽,메세지로 엣지에 전달
        """지정된 토픽으로 메시지 발행"""
        if topic not in self.publishers:
            print(f"❌ 오류: '{topic}' 토픽의 퍼블리셔가 없습니다. add_publisher()를 먼저 호출하세요.", flush=True)
            return
            
        payload = self.create_ros2_string_payload(msg)

        self.publishers[topic].put(payload)

        print(f" [{topic} 발행]: {msg}", flush=True)

if __name__ == "__main__":
    router_instance = ZenohRouter()
    
    
    router_instance.add_subscriber('ros1')
    router_instance.add_subscriber('ros2')
    router_instance.add_subscriber('ros3')
    
    
    router_instance.add_publisher('ec2_1')
    router_instance.add_publisher('ec2_2')
    router_instance.add_publisher('ec2_3')
    
    try:
        while True:
            time.sleep(3)
            
    except KeyboardInterrupt:
        print("\n정지합니다.")
        router_instance.session.close()