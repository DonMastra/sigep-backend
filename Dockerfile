FROM gradle:8.10.2-jdk17 AS build

WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :application:bootJar --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /workspace/application/build/libs/sigep-backend.jar /app/sigep-backend.jar

ENV SPRING_PROFILES_ACTIVE=qa
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/sigep-backend.jar"]
