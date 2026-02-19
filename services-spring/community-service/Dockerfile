FROM eclipse-temurin:21-jdk AS build

# Copy shared library (required by Gradle composite build)
COPY urr-common /workspace/urr-common

WORKDIR /workspace/community-service

COPY community-service/gradlew community-service/gradlew.bat ./
COPY community-service/gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY community-service/settings.gradle community-service/build.gradle ./
RUN ./gradlew --no-daemon dependencies || true

COPY community-service/src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/community-service/build/libs/*.jar app.jar

RUN addgroup --system --gid 1001 app && adduser --system --uid 1001 --ingroup app app
USER app

EXPOSE 3008
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=50.0", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
