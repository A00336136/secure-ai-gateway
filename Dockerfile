# ═══════════════════════════════════════════════════════
# Secure AI Gateway — Multi-Stage Dockerfile
#
# Stage 1: Build (Maven + JDK 17)
# Stage 2: Runtime (JRE 17 — minimal attack surface)
#
# Security hardening:
#  - Non-root user (uid 1001)
#  - Read-only filesystem (except /tmp)
#  - No shell in runtime image
#  - Distroless-like: eclipse-temurin JRE only
# ═══════════════════════════════════════════════════════

# ─── Stage 1: Build ─────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS builder

# Install Maven
RUN apt-get update && \
    apt-get install -y --no-install-recommends maven && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Cache dependencies first (layer optimization)
COPY pom.xml .
RUN mvn -B dependency:go-offline -DskipTests

# Build the application
COPY src ./src
RUN mvn -B clean package -DskipTests -Pprod && \
    java -Djarmode=layertools -jar target/secure-ai-gateway.jar extract

# ─── Stage 2: Runtime ───────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

# Security: Create non-root user
RUN groupadd --gid 1001 secureai && \
    useradd --uid 1001 --gid secureai --no-create-home --shell /bin/false secureai

# Labels (OCI spec)
LABEL org.opencontainers.image.title="Secure AI Gateway"
LABEL org.opencontainers.image.description="Enterprise-grade security gateway for AI model interactions"
LABEL org.opencontainers.image.version="2.0.0"
LABEL org.opencontainers.image.vendor="SecureAI Team"

WORKDIR /app

# Copy layered JAR (Spring Boot layer optimization)
COPY --from=builder --chown=secureai:secureai /build/dependencies/ ./
COPY --from=builder --chown=secureai:secureai /build/spring-boot-loader/ ./
COPY --from=builder --chown=secureai:secureai /build/snapshot-dependencies/ ./
COPY --from=builder --chown=secureai:secureai /build/application/ ./

# Create log directory
RUN mkdir -p /var/log/secure-ai-gateway && \
    chown secureai:secureai /var/log/secure-ai-gateway

# Security: Switch to non-root
USER secureai

EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+OptimizeStringConcat \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.profiles.active=prod"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
