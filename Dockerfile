FROM gradle:8.10.2-jdk17 AS build

WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :application:bootJar --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /workspace/application/build/libs/sigep-backend.jar /app/sigep-backend.jar

ENV SPRING_PROFILES_ACTIVE=qa
ENV JAVA_TOOL_OPTIONS="-Xms48m -Xmx192m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/sigep-backend.jar"]
