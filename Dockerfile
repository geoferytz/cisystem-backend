# ==============================
# CISYSTEM SPRING BOOT DOCKERFILE
# ==============================

# ---------- STAGE 1 : BUILD ----------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# copy project files
COPY . .

# build jar
RUN mvn clean package -DskipTests


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
