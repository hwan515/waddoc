FROM gradle:8.14.3-jdk17

WORKDIR /app

COPY build.gradle settings.gradle ./

RUN gradle --no-daemon dependencies || true

COPY src ./src

EXPOSE 8080

CMD ["gradle", "bootRun", "--no-daemon"]
