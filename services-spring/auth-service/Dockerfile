FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/auth-service

COPY auth-service/gradlew auth-service/gradlew.bat ./
COPY auth-service/gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY auth-service/settings.gradle auth-service/build.gradle ./
RUN ./gradlew --no-daemon dependencies || true

COPY auth-service/src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/auth-service/build/libs/*.jar app.jar

RUN addgroup --system --gid 1001 app && adduser --system --uid 1001 --ingroup app app
USER app

EXPOSE 3005
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=50.0", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
