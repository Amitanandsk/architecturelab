FROM gradle:9.4.0-jdk17 AS build

WORKDIR /workspace

COPY . .

RUN ./gradlew :services:order-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/services/order-service/build/libs/order-service-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
