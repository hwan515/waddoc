# subscriber/Dockerfile
# Zenoh 구독자 애플리케이션을 컨테이너로 패키지하기 위한 Dockerfile

# 경량 Python 베이스 이미지
FROM python:3.9-slim         
 

 # 작업 디렉터리를 /app으로 설정
WORKDIR /app                

# eclipse-zenoh는 Rust 확장을 빌드하므로 C 컴파일러가 필요하다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential \
    && rm -rf /var/lib/apt/lists/*

# Zenoh Python 바인딩 설치
RUN pip install --no-cache-dir eclipse-zenoh

# 구독자 스크립트 복사
COPY router_app.py .

# 컨테이너 시작 시 구독자 스크립트를 실행
CMD ["python", "-u", "router_app.py"]
