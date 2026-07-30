# -------- Build stage --------
FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress clean verify

# -------- Run stage --------
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --gid 1000 app \
    && useradd --uid 1000 --gid app --home-dir /app --shell /usr/sbin/nologin app
COPY --from=build --chown=app:app /app/target/authdemo-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

USER app

# Nechá dost paměti pro nativní části JVM, Tomcat, JSch a databázový ovladač.
# Při neobnovitelném OOM nechá Render proces čistě restartovat.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
