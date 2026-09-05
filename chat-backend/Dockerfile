# Build stage uses a plain JDK image plus the Gradle wrapper committed by Initializr, rather than
# an official `gradle:*` base image -- Java 25 is very recent, so pinning to a `gradle:*-jdk25`
# tag risked that image not existing/being current yet. The wrapper downloads the exact matching
# Gradle version itself, so a plain JDK image is all the build stage actually needs.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
# Dependencies resolved before the source is copied so editing application code doesn't bust the
# Docker layer cache for the (much slower) dependency download step.
RUN ./gradlew --no-daemon dependencies || true

COPY src ./src
RUN ./gradlew --no-daemon build -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# H2 file-mode DB lives here; docker-compose mounts a named volume at this path so message/user
# history survives the container being recreated, not just a process restart inside it.
ENV CHAT_DB_PATH=/data/chat
VOLUME /data

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
