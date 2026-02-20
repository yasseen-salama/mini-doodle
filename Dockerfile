FROM gradle:8-jdk21-alpine AS builder
WORKDIR /app

COPY build.gradle settings.gradle ./
COPY src ./src

RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN addgroup --system doodle && adduser --system --ingroup doodle doodle
COPY --from=builder --chown=doodle:doodle /app/build/libs/*.jar /app/app.jar

USER doodle
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
