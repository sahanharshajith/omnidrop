# Stage 1: Build Java Application
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

# Stage 2: Minimal Runtime Container
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080 9090 8888/udp

RUN mkdir -p /app/downloads /app/checkpoints

ENTRYPOINT ["java", "-jar", "app.jar"]
