FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Layer 1: Gradle wrapper (rarely changes)
COPY gradlew gradlew.bat ./
COPY gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Layer 2: Build config + dependency download (changes only when deps change)
COPY settings.gradle build.gradle ./
RUN ./gradlew --no-daemon dependencies || true

# Layer 3: Source compile (changes on every code edit)
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

RUN addgroup --system --gid 1001 app && adduser --system --uid 1001 --ingroup app app
USER app

EXPOSE 3001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=50.0", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
