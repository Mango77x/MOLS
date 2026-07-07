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

# Run as a dedicated non-root user instead of the image's default root —
# a compromised JVM process shouldn't have root inside the container.
RUN groupadd --system mols && useradd --system --gid mols --home /app mols
COPY --from=builder --chown=mols:mols /app/target/*.jar app.jar
USER mols

# Expose application port
EXPOSE 8080

# Container-level liveness check against the public actuator endpoint
# (SecurityConfig permits /actuator/health without auth). start-period
# gives Flyway migrations + Spring context startup time to finish.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl --fail http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
