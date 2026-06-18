FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -B
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn clean package -B -DskipTests=true

FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S app -g 1001 && adduser -S app -u 1001 -G app

COPY --from=builder --chown=app:app /build/target/xiaozhi-esp32-api.jar /app/app.jar

USER app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=prod

EXPOSE 8002

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8002/xiaozhi/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]