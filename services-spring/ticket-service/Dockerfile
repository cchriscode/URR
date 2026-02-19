FROM eclipse-temurin:21-jdk AS build

# Copy shared library (required by Gradle composite build)
COPY urr-common /workspace/urr-common

WORKDIR /workspace/ticket-service

COPY ticket-service/gradlew ticket-service/gradlew.bat ./
COPY ticket-service/gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY ticket-service/settings.gradle ticket-service/build.gradle ./
RUN ./gradlew --no-daemon dependencies || true

COPY ticket-service/src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/ticket-service/build/libs/*.jar app.jar

RUN addgroup --system --gid 1001 app && adduser --system --uid 1001 --ingroup app app
USER app

EXPOSE 3002
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=50.0", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
