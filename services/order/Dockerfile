# syntax=docker/dockerfile:1.7

# ---- build stage --------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Кэшируем dependencies — копируем только wrapper и build-файлы
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version

# Модульные build.gradle.kts отдельно
COPY core/build.gradle.kts core/build.gradle.kts
COPY adapter-in-rest/build.gradle.kts adapter-in-rest/build.gradle.kts
COPY adapter-in-kafka/build.gradle.kts adapter-in-kafka/build.gradle.kts
COPY adapter-out-postgres/build.gradle.kts adapter-out-postgres/build.gradle.kts
COPY adapter-out-catalog/build.gradle.kts adapter-out-catalog/build.gradle.kts
COPY adapter-out-payment/build.gradle.kts adapter-out-payment/build.gradle.kts
COPY bootstrap/build.gradle.kts bootstrap/build.gradle.kts
COPY test-utils/build.gradle.kts test-utils/build.gradle.kts

# Собственно исходники
COPY core/src core/src
COPY adapter-in-rest/src adapter-in-rest/src
COPY adapter-in-kafka/src adapter-in-kafka/src
COPY adapter-out-postgres/src adapter-out-postgres/src
COPY adapter-out-catalog/src adapter-out-catalog/src
COPY adapter-out-payment/src adapter-out-payment/src
COPY bootstrap/src bootstrap/src
COPY test-utils/src test-utils/src
COPY migrations migrations

RUN ./gradlew :bootstrap:bootJar -x test --no-daemon

# ---- runtime stage ------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd -r app && useradd -r -g app app

COPY --from=build /workspace/bootstrap/build/libs/*.jar /app/order-service.jar
COPY --from=build /workspace/migrations /app/migrations

USER app
EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/order-service.jar"]
