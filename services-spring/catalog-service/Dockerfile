FROM eclipse-temurin:21-jdk AS build

# Copy shared library (required by Gradle composite build)
COPY urr-common /workspace/urr-common

WORKDIR /workspace/catalog-service

COPY catalog-service/gradlew catalog-service/gradlew.bat ./
COPY catalog-service/gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY catalog-service/settings.gradle catalog-service/build.gradle ./
RUN ./gradlew --no-daemon dependencies || true

COPY catalog-service/src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/catalog-service/build/libs/*.jar app.jar

RUN addgroup --system --gid 1001 app && adduser --system --uid 1001 --ingroup app app
USER app

EXPOSE 3009
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=50.0", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
