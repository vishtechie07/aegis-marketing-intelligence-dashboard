# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S aegis && adduser -S aegis -G aegis \
    && apk add --no-cache wget
COPY --from=builder /app/target/*.jar app.jar
USER aegis
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
