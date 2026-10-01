# ---------------------------------------------------
# STAGE 1: Build the JAR using Maven + JDK 21
# ---------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy the Maven wrapper and pom.xml first
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make the wrapper executable and download dependencies.
# Docker caches this layer, so rebuilds are fast unless pom.xml changes!
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Copy the source code and build the JAR
COPY src src
RUN ./mvnw package -DskipTests -B

# ---------------------------------------------------
# STAGE 2: Run with a slim JRE-only image
# ---------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy ONLY the final compiled JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]