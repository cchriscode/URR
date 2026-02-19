FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/gateway-service

COPY gateway-service/gradlew gateway-service/gradlew.bat ./
COPY gateway-service/gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY gateway-service/settings.gradle gateway-service/build.gradle ./
RUN ./gradlew --no-daemon dependencies || true

COPY gateway-service/src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/gateway-service/build/libs/*.jar app.jar

RUN addgroup --system --gid 1001 app && adduser --system --uid 1001 --ingroup app app
USER app

EXPOSE 3001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=50.0", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
