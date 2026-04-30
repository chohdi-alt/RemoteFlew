# ---------- BUILD STAGE ----------
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 1️⃣ Copy ONLY pom first (cache dependencies)
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

RUN ./mvnw -B -q dependency:go-offline

# 2️⃣ Copy source
COPY src ./src
RUN chmod +x mvnw

RUN ./mvnw -B clean package -DskipTests

# ---------- RUNTIME ----------
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java","-jar","app.jar"]