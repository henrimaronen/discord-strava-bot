FROM gradle:8.12-jdk21 AS build
WORKDIR /workspace
COPY build.gradle settings.gradle ./
RUN gradle --no-daemon dependencies > /dev/null
COPY src ./src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
