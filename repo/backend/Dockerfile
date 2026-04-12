FROM maven:3.9.9-eclipse-temurin-21 AS base
WORKDIR /app
COPY backend ./backend
COPY tests/backend ./tests/backend

FROM base AS build
WORKDIR /app/backend
RUN mvn -B -DskipTests package

FROM base AS test
WORKDIR /app/backend
CMD ["mvn", "-B", "-Dspring.profiles.active=test", "test"]

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/backend/target/*.jar /app/app.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]
