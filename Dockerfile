# ==============================
# CISYSTEM SPRING BOOT DOCKERFILE
# ==============================

# ---------- STAGE 1 : BUILD ----------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

ENV MAVEN_OPTS="-Dmaven.wagon.httpconnectionManager.ttlSeconds=120 -Dmaven.wagon.http.retryHandler.count=3 -Dmaven.wagon.http.connectionTimeout=60000 -Dmaven.wagon.http.readTimeout=600000"

# copy pom first to leverage Docker layer cache
COPY pom.xml .

# download dependencies (more resilient in flaky networks)
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests dependency:go-offline

# copy project files
COPY . .

# build jar
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests


# ---------- STAGE 2 : RUNTIME ----------
FROM eclipse-temurin:21-jdk-jammy

# create app folder
WORKDIR /app

# copy jar from builder
COPY --from=builder /build/target/*.jar app.jar

# expose spring port
EXPOSE 8080

# JVM optimization for small servers (AWS t2.micro/t3.micro)
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]
