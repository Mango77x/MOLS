# ================================================
# Stage 1: Build
# Uses Maven + JDK to compile and package the app
# ================================================
FROM eclipse-temurin:21-jdk AS builder

# Set working directory inside the container
WORKDIR /app

# Copy Maven wrapper and pom.xml first
# Docker caches this layer - dependencies only re-download when pom.xml changes
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn

# Windows checkouts often produce CRLF + non-executable mvnw; normalize for Linux Docker builds
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code and the React frontend (built by the frontend-maven-plugin
# during the Maven package, with its own pinned Node — no node image needed)
COPY src ./src
COPY frontend ./frontend

# Build the JAR, skipping tests (tests run in CI, not in Docker build)
RUN ./mvnw clean package -DskipTests

# ================================================
# Stage 2: Runtime
# Uses only JRE (smaller image, no build tools)
# ================================================
FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy only the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
