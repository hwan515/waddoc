FROM eclipse-temurin:17-jdk AS builder
ARG MODULE_NAME=core-app

WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./
COPY shared-kernel/ shared-kernel/
COPY core-app/ core-app/
COPY edge-bff/ edge-bff/
COPY notification-service/ notification-service/
COPY robot-gateway/ robot-gateway/

RUN chmod +x gradlew
RUN ./gradlew :${MODULE_NAME}:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
ARG MODULE_NAME=core-app

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/${MODULE_NAME}/build/libs/*.jar app.jar
RUN mkdir -p /data/uploads

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
