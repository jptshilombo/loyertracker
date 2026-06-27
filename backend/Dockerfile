# syntax=docker/dockerfile:1
# Image API multi-stage. Le durcissement/scan (Trivy) est ajouté à l'étape 07 (CI/CD).

# ---- Étape build : compile + package (tests exécutés en CI, pas ici) ----
FROM maven:3.9-eclipse-temurin-21@sha256:d7e7f57407437c014571f1ad5a9955f03fc3edcb1d964067ef351fa38e798665 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- Étape run : JRE minimal, utilisateur non-root ----
FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c
# Patche les paquets OS au-delà de l'image de base (ex. CVE-2026-45447 openssl 3.5.7-r0,
# gate Trivy HIGH/CRITICAL) sans attendre la reconstruction upstream d'eclipse-temurin.
RUN apk -U upgrade --no-cache
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
