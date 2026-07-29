# -------- Build stage --------
FROM maven:3.8.4-openjdk-17 AS build


# Nastav pracovní adresář v kontejneru
WORKDIR /app

# Zkopíruj pom.xml a stáhni závislosti (cache pro rychlejší build)
COPY pom.xml .
RUN mvn dependency:go-offline

# Zkopíruj zdrojové soubory
COPY src ./src

# Builduj projekt (skip testy, pokud nechceš, aby se spouštěly testy)
RUN mvn clean package -DskipTests

# -------- Run stage --------
FROM eclipse-temurin:17-jdk-focal

# Nastav pracovní adresář
WORKDIR /app

# Zkopíruj JAR z build stage
COPY --from=build /app/target/authdemo-0.0.1-SNAPSHOT.jar .


# Expose port (ten, který používá Spring Boot, defaultně 8080)
EXPOSE 8080

# Spusť aplikaci
ENTRYPOINT ["java", "-jar", "/app/authdemo-0.0.1-SNAPSHOT.jar"]
