import rclpy
from rclpy.node import Node
from std_msgs.msg import String
import random
import json
import requests

class BioSignalPublisher(Node):
    def __init__(self):
        super().__init__('biosignal_publisher')
        self.publisher_ = self.create_publisher(String, 'biosignals', 10)
        self.subscriber_ = self.create_subscription(String, 'check_biosignal', self.subscriber_callback, 10)

        
        self.base_url = "http://3.34.123.145"

    
    def subscriber_callback(self, msg):
        # 1. 수신된 메시지에서 mission_id와 token 파싱
        try:
            req_data = json.loads(msg.data)
            mission_id = req_data['mission_id']
            token = req_data['token']
        except (json.JSONDecodeError, KeyError) as e:
            self.get_logger().error(f"메시지 파싱 실패. JSON 형식을 확인하세요: {e}")
            return

      
        api_url = f"{self.base_url}/api/v1/missions/{mission_id}/vitals"
        headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        }

        # 3. 생체 신호 랜덤 생성
        payload = {
            "temperature": round(random.uniform(36.0, 37.8), 1),
            "bloodPressureSys": random.randint(110, 140),
            "bloodPressureDia": random.randint(70, 90),
            "heartRate": random.randint(60, 100),
            "spO2": random.randint(95, 100),
            "ecgWaveform": [round(random.uniform(-0.5, 1.2), 2) for _ in range(6)],
            "ecgSamplingHz": 25,
            "ecgDurationSeconds": 8
        }
        
        self.get_logger().info(f"데이터 전송 시도: {api_url}")
        
        try:
            
            response = requests.put(
                api_url,
                json=payload,
                headers=headers,
                timeout=3.0
            )
            
            if response.status_code == 200:
                self.get_logger().info(f"PUT 성공: {response.json().get('caseId')}")
            else:
                self.get_logger().error(f"PUT 실패 ({response.status_code}): {response.text}")
                
        except requests.exceptions.RequestException as e:
            self.get_logger().error(f"HTTP 통신 에러 발생: {e}")

def main(args=None):
    rclpy.init(args=args)
    node = BioSignalPublisher()
    
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()

if __name__ == '__main__':
    main()