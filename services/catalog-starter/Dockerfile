# TODO шаг 15: образ сервиса.
# Сейчас всё в один слой и от root — в образ уедут gradle, исходники и кэш,
# а контейнер побежит с лишними правами.
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon bootJar -x test
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/build/libs/app.jar"]
