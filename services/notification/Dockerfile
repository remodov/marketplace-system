# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version

COPY src ./src
COPY migrations ./migrations
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd -r app && useradd -r -g app app
COPY --from=build /workspace/build/libs/*.jar /app/notification-service.jar

USER app
EXPOSE 8090

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/notification-service.jar"]
