FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src

RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system safetransfer && adduser --system --ingroup safetransfer safetransfer

COPY --from=build /workspace/build/libs/*.jar app.jar

USER safetransfer

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
