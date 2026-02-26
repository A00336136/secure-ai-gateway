# Secure AI Gateway — Complete Setup & Implementation Guide

> **Stack**: Spring Boot 3.2.12 · Ollama LLaMA 3.1 8B · PostgreSQL 16 · Kubernetes · Jenkins CI/CD  
> **Version**: 2.0.0 · **Last Updated**: 2025

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Quick Start (Docker Compose)](#2-quick-start-docker-compose)
3. [Project Structure Overview](#3-project-structure-overview)
4. [Detailed Local Development Setup](#4-detailed-local-development-setup)
5. [Ollama LLM Setup & Model Configuration](#5-ollama-llm-setup--model-configuration)
6. [Database Setup (PostgreSQL + H2)](#6-database-setup-postgresql--h2)
7. [Security Configuration (JWT, Secrets)](#7-security-configuration-jwt-secrets)
8. [Environment Profiles (dev / test / prod)](#8-environment-profiles-dev--test--prod)
9. [Kubernetes Deployment (Minikube & Production)](#9-kubernetes-deployment-minikube--production)
10. [Jenkins CI/CD Pipeline Setup](#10-jenkins-cicd-pipeline-setup)
11. [Monitoring (Prometheus + Grafana)](#11-monitoring-prometheus--grafana)
12. [API Reference & Testing](#12-api-reference--testing)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Prerequisites

### Required Tools & Versions

| Tool | Minimum Version | Purpose |
|------|----------------|---------|
| Java JDK | 17 (LTS) | Application runtime |
| Maven | 3.9.x | Build and dependency management |
| Docker | 24.x+ | Container runtime |
| Docker Compose | 2.x+ | Local multi-service orchestration |
| kubectl | 1.28+ | Kubernetes CLI |
| Minikube | 1.32+ | Local Kubernetes cluster |
| Git | 2.40+ | Source control |
| curl / wget | Any | API testing |

### System Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| RAM | 8 GB | 16 GB (for LLM) |
| CPU | 4 cores | 8 cores |
| Disk | 20 GB | 40 GB |
| OS | Linux / macOS / WSL2 | Ubuntu 22.04 LTS |

### Install Java 17

```bash
# Ubuntu / Debian
sudo apt update && sudo apt install -y openjdk-17-jdk
java -version  # Verify: openjdk 17.x

# macOS (Homebrew)
brew install openjdk@17
echo 'export JAVA_HOME=$(brew --prefix openjdk@17)' >> ~/.zshrc
source ~/.zshrc

# Verify
java -version
javac -version
```

### Install Maven 3.9

```bash
# Ubuntu / Debian
sudo apt install -y maven
mvn -version  # Verify: Apache Maven 3.x

# macOS
brew install maven

# Manual install (any OS)
curl -LO https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz
tar -xzf apache-maven-3.9.6-bin.tar.gz
sudo mv apache-maven-3.9.6 /opt/maven
echo 'export PATH=$PATH:/opt/maven/bin' >> ~/.bashrc
source ~/.bashrc
```

### Install Docker & Docker Compose

```bash
# Ubuntu
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker

# Verify
docker --version
docker compose version

# macOS — install Docker Desktop from https://www.docker.com/products/docker-desktop/
```

### Install kubectl & Minikube

```bash
# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl && sudo mv kubectl /usr/local/bin/
kubectl version --client

# Minikube
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
minikube version
```

---

## 2. Quick Start (Docker Compose)

The fastest way to run the complete stack locally — all services included.

```bash
# 1. Clone the repository
git clone https://github.com/your-org/secure-ai-gateway.git
cd secure-ai-gateway

# 2. Start all services (app + postgres + ollama + sonarqube + jenkins + prometheus + grafana)
docker compose up -d

# 3. Wait for services to be healthy (~2-3 minutes)
docker compose ps

# 4. Pull the LLaMA 3.1 model into Ollama (one-time, ~4.7 GB download)
docker exec -it secure-ai-ollama ollama pull llama3.1:8b

# 5. Verify the app is running
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# 6. Register a user and get a JWT token
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Test@1234","email":"test@example.com"}' | jq .

curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Test@1234"}' | jq .token

# 7. Send an AI query
TOKEN="<paste-token-from-above>"
curl -s -X POST http://localhost:8080/api/ai/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Explain what a firewall does in simple terms"}' | jq .
```

### Service URLs (Docker Compose)

| Service | URL | Default Credentials |
|---------|-----|-------------------|
| **Spring Boot API** | http://localhost:8080 | — |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | — |
| **Actuator Health** | http://localhost:8080/actuator/health | — |
| **PostgreSQL** | localhost:5432 | secureai_user / secureai_password |
| **Ollama LLM** | http://localhost:11434 | — |
| **SonarQube** | http://localhost:9000 | admin / admin |
| **Jenkins** | http://localhost:8090 | (setup on first run) |
| **Prometheus** | http://localhost:9090 | — |
| **Grafana** | http://localhost:3000 | admin / admin |

---

## 3. Project Structure Overview

```
secure-ai-gateway/
├── src/
│   ├── main/
│   │   ├── java/com/secureai/
│   │   │   ├── config/          # SecurityConfig, RateLimitConfig, OllamaConfig
│   │   │   ├── controller/      # AuthController, AiController, AgentController
│   │   │   ├── model/           # User, AuditLog, JwtRequest, JwtResponse
│   │   │   ├── security/        # JwtTokenProvider, JwtAuthFilter, UserDetailsService
│   │   │   ├── service/         # AiService, PiiRedactionService, RateLimitService
│   │   │   │                       AuditService, ReActAgentService
│   │   │   └── repository/      # UserRepository, AuditLogRepository
│   │   └── resources/
│   │       ├── application.yml          # Base config (all profiles)
│   │       ├── application-dev.yml      # Dev: H2 in-memory DB
│   │       ├── application-test.yml     # Test: H2 + mock LLM
│   │       ├── application-prod.yml     # Prod: PostgreSQL + TLS
│   │       ├── db/migration/            # Flyway SQL migration scripts
│   │       └── static/                  # Static resources
│   └── test/
│       └── java/com/secureai/           # 63+ JUnit 5 test cases (*Test.java) + 9 integration tests (*IT.java)
│           ├── controller/              # @WebMvcTest slice tests (*Test.java) + @SpringBootTest ITs (*IT.java)
│           ├── service/                 # Unit tests with Mockito
│           └── pii/                     # PII redaction unit tests
├── k8s/
│   ├── namespace.yaml           # secure-ai-dev + secure-ai-prod namespaces
│   ├── deployment.yaml          # Deployment + Service + HPA + Ingress
│   └── postgres/
│       └── postgres.yaml        # PostgreSQL StatefulSet + PVC
├── monitoring/
│   └── prometheus.yml           # Prometheus scrape config
├── Dockerfile                   # Multi-stage: JDK build → JRE runtime
├── docker-compose.yml           # Full local dev stack
├── Jenkinsfile                  # 13-stage DevSecOps pipeline
├── pom.xml                      # Maven dependencies
└── owasp-suppressions.xml       # OWASP false-positive suppressions
```

---

## 4. Detailed Local Development Setup

### Step 1: Clone and Build

```bash
git clone https://github.com/your-org/secure-ai-gateway.git
cd secure-ai-gateway

# Compile (skips tests for speed)
mvn clean compile -DskipTests

# Run unit tests only (*Test.java — fast, no external services)
mvn test -Dspring.profiles.active=test

# Run with dev profile (H2 in-memory DB — no external DB needed)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Step 2: IDE Setup (IntelliJ IDEA / VS Code)

**IntelliJ IDEA:**
```
1. File → Open → select secure-ai-gateway directory
2. Wait for Maven import to complete
3. Run → Edit Configurations → Add Application
   - Main class: com.secureai.SecureAiGatewayApplication
   - Active profiles: dev
4. Run the configuration
```

**VS Code:**
```bash
# Install extensions
code --install-extension vscjava.vscode-spring-boot-pack
code --install-extension vscjava.vscode-java-debug

# Open project
code .
# Then use Spring Boot Dashboard in the sidebar to run with 'dev' profile
```

### Step 3: Verify Application Startup

```bash
# Check app is running (dev profile uses H2, port 8080)
curl http://localhost:8080/actuator/health
# {"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"}}}

# View H2 console (dev profile only)
# Open browser: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:testdb
# Username: sa  |  Password: (empty)

# View API docs
# Open browser: http://localhost:8080/swagger-ui.html
```

### Step 4: Running Tests

```bash
# Unit tests only (*Test.java — Surefire, fast, no external services needed)
mvn test -Dspring.profiles.active=test

# With coverage report
mvn test jacoco:report -Dspring.profiles.active=test
# Report: target/site/jacoco/index.html

# Specific test class
mvn test -Dtest=AiServiceTest -Dspring.profiles.active=test

# Integration tests only (*IT.java — Failsafe, loads Spring context + H2 DB)
mvn failsafe:integration-test failsafe:verify -Dspring.profiles.active=test

# Full build with all checks — unit + integration tests (mirrors CI pipeline)
mvn clean verify -Dspring.profiles.active=test
```

### Step 5: Live Reload (Optional)

```bash
# Add spring-boot-devtools to pom.xml (already included in dev profile)
# Then run:
mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.jvmArguments="-Dspring.devtools.restart.enabled=true"
# Changes to .java files will trigger automatic restart
```

---

## 5. Ollama LLM Setup & Model Configuration

### Install Ollama (Native — Recommended for Development)

```bash
# Linux / macOS
curl -fsSL https://ollama.ai/install.sh | sh

# Start Ollama server
ollama serve &

# Verify
ollama list
curl http://localhost:11434/api/tags
```

### Pull LLaMA 3.1 8B Model

```bash
# Pull model (~4.7 GB — one-time download)
ollama pull llama3.1:8b

# Verify model is available
ollama list
# NAME              ID            SIZE    MODIFIED
# llama3.1:8b      xxx           4.7 GB  Just now

# Test the model directly
ollama run llama3.1:8b "Hello, how are you?"
```

### GPU Acceleration (Optional — Recommended)

```bash
# NVIDIA GPU — Ollama auto-detects CUDA
# Verify GPU is detected
ollama run llama3.1:8b "Test"
# Look for: "using NVIDIA GPU" in ollama serve output

# AMD GPU (ROCm)
HSA_OVERRIDE_GFX_VERSION=10.3.0 ollama serve

# Docker with GPU (edit docker-compose.yml — section is commented out)
# Uncomment the `deploy.resources` block under the ollama service
```

### Ollama Configuration in Application

```yaml
# application.yml (already configured)
ollama:
  base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
  model: ${OLLAMA_MODEL:llama3.1:8b}
  timeout-seconds: 120
  react:
    max-steps: 10   # ReAct agent: max Think→Act→Observe iterations
```

```bash
# Override for a different model at runtime
OLLAMA_MODEL=llama3.1:70b mvn spring-boot:run -Dspring-boot.run.profiles=dev

# List available models from the API
curl http://localhost:11434/api/tags | jq '.models[].name'
```

### Verify LLM Integration

```bash
# Start app with Ollama running, then test
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@1234"}' | jq -r .token)

# Simple AI query (direct)
curl -s -X POST http://localhost:8080/api/ai/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"What is rate limiting in APIs?"}' | jq .

# ReAct agent query (multi-step reasoning)
curl -s -X POST http://localhost:8080/api/agent/run \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"task":"Analyze the security implications of storing passwords in plain text"}' | jq .
```

---

## 6. Database Setup (PostgreSQL + H2)

### H2 In-Memory (Development & Testing — Zero Setup)

H2 is used automatically when running with the `dev` or `test` profile. No installation required.

```bash
# Runs on H2 automatically
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# H2 console available at:
# http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:testdb
# Username: sa  |  Password: (blank)
```

### PostgreSQL Setup (Production-Like Local Environment)

```bash
# Option A: Docker (easiest)
docker run -d \
  --name secure-ai-postgres \
  -e POSTGRES_DB=secureai \
  -e POSTGRES_USER=secureai_user \
  -e POSTGRES_PASSWORD=secureai_password \
  -p 5432:5432 \
  postgres:16-alpine

# Option B: Native install (Ubuntu)
sudo apt install -y postgresql postgresql-client
sudo -u postgres psql -c "CREATE USER secureai_user WITH PASSWORD 'secureai_password';"
sudo -u postgres psql -c "CREATE DATABASE secureai OWNER secureai_user;"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE secureai TO secureai_user;"

# Verify connection
psql -h localhost -U secureai_user -d secureai -c "SELECT version();"
```

### Running Flyway Migrations

```bash
# Migrations run automatically on startup via Spring Boot
# To run manually:
mvn flyway:migrate -Dspring.profiles.active=prod \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/secureai \
  -Dspring.datasource.username=secureai_user \
  -Dspring.datasource.password=secureai_password

# View migration history
mvn flyway:info -Dspring.profiles.active=prod

# Migration files location: src/main/resources/db/migration/
# Naming convention: V{version}__{description}.sql
# Example: V1__create_users_table.sql, V2__create_audit_log_table.sql
```

### Database Schema Overview

```sql
-- Key tables created by Flyway migrations

-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,  -- BCrypt cost=12
    role VARCHAR(20) DEFAULT 'USER',
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Audit log table (all AI requests logged here)
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    request_prompt TEXT,          -- PII-redacted before storage
    response_text TEXT,           -- PII-redacted before storage
    model_used VARCHAR(50),
    tokens_used INTEGER,
    response_time_ms BIGINT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT NOW()
);
```

---

## 7. Security Configuration (JWT, Secrets)

### JWT Configuration

```yaml
# application.yml
jwt:
  secret: ${JWT_SECRET:secure-ai-gateway-super-secret-key-minimum-32-chars-for-hs256}
  expiration-ms: 3600000   # 1 hour token expiry
```

```bash
# Generate a strong JWT secret (32+ characters, Base64-encoded)
openssl rand -base64 48
# Example output: xK9mP2vL8nQ5rT1wY7uI4oE6aS3dF0gH==

# Set as environment variable for production
export JWT_SECRET="xK9mP2vL8nQ5rT1wY7uI4oE6aS3dF0gH=="
```

### Creating Kubernetes Secrets

```bash
# Create JWT secret
kubectl create secret generic secure-ai-secrets \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
  --from-literal=DB_PASSWORD="$(openssl rand -base64 24)" \
  -n secure-ai-dev

# Create secret for prod namespace
kubectl create secret generic secure-ai-secrets \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
  --from-literal=DB_PASSWORD="your-secure-prod-db-password" \
  -n secure-ai-prod

# Verify secrets exist (values are base64-encoded, not shown)
kubectl get secrets -n secure-ai-dev
```

### Rate Limiting Configuration

```yaml
# application.yml — token bucket algorithm (Bucket4j)
rate-limit:
  capacity: 100             # Max requests per bucket
  refill-tokens: 100        # Tokens refilled per interval
  refill-duration-minutes: 60  # Refill every 60 minutes = 100 req/hr per user
```

```bash
# Test rate limiting
for i in {1..5}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://localhost:8080/api/ai/query \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"prompt":"test"}'
done
# All 5 return 200; after 100 requests within 1 hour → 429 Too Many Requests
```

### PII Redaction

```yaml
# application.yml
pii:
  redaction:
    enabled: true
    redact-in-audit-logs: true   # PII stripped before DB storage
```

The `PiiRedactionService` automatically strips:
- Email addresses → `[EMAIL_REDACTED]`
- Phone numbers → `[PHONE_REDACTED]`
- SSNs (XXX-XX-XXXX format) → `[SSN_REDACTED]`
- Credit card numbers → `[CARD_REDACTED]`

```bash
# Test PII redaction
curl -s -X POST http://localhost:8080/api/ai/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"My email is john@example.com and SSN is 123-45-6789"}' | jq .
# Response and audit log will have PII replaced with [*_REDACTED] tokens
```

### Security Headers

The `SecurityConfig` enforces these response headers on every request:

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000; includeSubDomains
Cache-Control: no-cache, no-store, must-revalidate
```

---

## 8. Environment Profiles (dev / test / prod)

### Profile Overview

| Profile | Database | LLM | Logging | Use Case |
|---------|----------|-----|---------|----------|
| `dev` | H2 in-memory | Ollama (localhost) | DEBUG | Local development |
| `test` | H2 in-memory | Mocked | INFO | Unit & integration tests |
| `prod` | PostgreSQL | Ollama (env var) | WARN | Production / Kubernetes |

### Activating a Profile

```bash
# Maven (local run)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# Environment variable
export SPRING_PROFILES_ACTIVE=prod
java -jar target/secure-ai-gateway.jar

# Docker / Docker Compose
docker run -e SPRING_PROFILES_ACTIVE=prod secure-ai-gateway:latest

# Kubernetes (set in deployment.yaml → envFrom configMapRef)
kubectl create configmap secure-ai-config \
  --from-literal=SPRING_PROFILES_ACTIVE=prod \
  --from-literal=DB_HOST=postgres-service \
  --from-literal=DB_PORT=5432 \
  --from-literal=DB_NAME=secureai \
  --from-literal=DB_USERNAME=secureai_user \
  --from-literal=OLLAMA_BASE_URL=http://ollama-service:11434 \
  --from-literal=OLLAMA_MODEL=llama3.1:8b \
  -n secure-ai-prod
```

### Production Environment Variables Reference

| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` |
| `DB_HOST` | PostgreSQL hostname | `postgres-service` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `secureai` |
| `DB_USERNAME` | DB user | `secureai_user` |
| `DB_PASSWORD` | DB password (Secret) | `(secret)` |
| `OLLAMA_BASE_URL` | Ollama server URL | `http://ollama:11434` |
| `OLLAMA_MODEL` | LLM model name | `llama3.1:8b` |
| `JWT_SECRET` | JWT signing key (Secret) | `(secret, 48+ chars)` |

---

## 9. Kubernetes Deployment (Minikube & Production)

### Step 1: Start Minikube

```bash
# Start with sufficient resources for LLM workload
minikube start \
  --cpus=4 \
  --memory=8192 \
  --disk-size=30g \
  --driver=docker

# Enable required addons
minikube addons enable ingress          # Nginx ingress
minikube addons enable metrics-server   # HPA support
minikube addons enable dashboard        # Kubernetes dashboard

# Verify cluster is running
kubectl get nodes
# NAME       STATUS   ROLES           AGE   VERSION
# minikube   Ready    control-plane   60s   v1.28.x
```

### Step 2: Create Namespaces

```bash
kubectl apply -f k8s/namespace.yaml
# Creates: secure-ai-dev and secure-ai-prod namespaces

kubectl get namespaces | grep secure-ai
```

### Step 3: Deploy PostgreSQL

```bash
kubectl apply -f k8s/postgres/postgres.yaml -n secure-ai-dev

# Wait for postgres to be ready
kubectl rollout status statefulset/postgres -n secure-ai-dev

# Verify postgres is running
kubectl get pods -n secure-ai-dev -l app=postgres
```

### Step 4: Create ConfigMap and Secrets

```bash
# ConfigMap (non-sensitive config)
kubectl create configmap secure-ai-config \
  --from-literal=SPRING_PROFILES_ACTIVE=prod \
  --from-literal=DB_HOST=postgres-service \
  --from-literal=DB_PORT=5432 \
  --from-literal=DB_NAME=secureai \
  --from-literal=DB_USERNAME=secureai_user \
  --from-literal=OLLAMA_BASE_URL=http://ollama-service:11434 \
  --from-literal=OLLAMA_MODEL=llama3.1:8b \
  -n secure-ai-dev

# Secrets (sensitive — never put in Git)
kubectl create secret generic secure-ai-secrets \
  --from-literal=DB_PASSWORD="secureai_password" \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
  -n secure-ai-dev
```

### Step 5: Build & Load Docker Image

```bash
# Point Docker to Minikube's Docker daemon
eval $(minikube docker-env)

# Build the image inside Minikube
docker build -t secure-ai-gateway:latest .

# Verify image is available
docker images | grep secure-ai-gateway
```

### Step 6: Deploy the Application

```bash
# Update the image name in deployment.yaml to use local image
# image: secure-ai-gateway:latest
# imagePullPolicy: Never  (for local Minikube image)

kubectl apply -f k8s/deployment.yaml -n secure-ai-dev

# Watch rollout progress
kubectl rollout status deployment/secure-ai-gateway -n secure-ai-dev

# Check all pods are running
kubectl get pods -n secure-ai-dev
# NAME                                  READY   STATUS    RESTARTS
# secure-ai-gateway-7d6f9b8c4-abc12    1/1     Running   0
# secure-ai-gateway-7d6f9b8c4-def34    1/1     Running   0
# secure-ai-gateway-7d6f9b8c4-ghi56    1/1     Running   0
```

### Step 7: Access the Application

```bash
# Get the Minikube ingress IP
minikube ip
# e.g., 192.168.49.2

# Add to /etc/hosts
echo "$(minikube ip) secure-ai.local" | sudo tee -a /etc/hosts

# Test access
curl http://secure-ai.local/actuator/health

# Or use port-forward for quick access
kubectl port-forward service/secure-ai-gateway-service 8080:80 -n secure-ai-dev &
curl http://localhost:8080/actuator/health
```

### Step 8: Verify HPA and Scaling

```bash
# View HPA
kubectl get hpa -n secure-ai-dev
# NAME                      REFERENCE               TARGETS         MINPODS   MAXPODS
# secure-ai-gateway-hpa     Deployment/...          <20%/70%>      2         10

# View resource usage
kubectl top pods -n secure-ai-dev
```

### Production Deployment Notes

```bash
# Production uses the same manifests with different namespace
# After pipeline stages 1-12 pass (unit tests, SAST, DAST, container scan):

# Manual approval required (Jenkins input step)
# Only deploys from 'main' branch

kubectl apply -f k8s/deployment.yaml -n secure-ai-prod
kubectl rollout status deployment/secure-ai-gateway -n secure-ai-prod --timeout=300s

# Rollback if needed
kubectl rollout undo deployment/secure-ai-gateway -n secure-ai-prod
kubectl rollout history deployment/secure-ai-gateway -n secure-ai-prod
```

---

## 10. Jenkins CI/CD Pipeline Setup

### Step 1: Start Jenkins

Jenkins is included in the Docker Compose stack on port 8090.

```bash
# Start Jenkins
docker compose up -d jenkins

# Get the initial admin password
docker exec secure-ai-jenkins cat /var/jenkins_home/secrets/initialAdminPassword

# Open browser: http://localhost:8090
# Enter the initial password and install suggested plugins
```

### Step 2: Install Required Plugins

Navigate to **Manage Jenkins → Manage Plugins → Available** and install:

- Git Integration
- Pipeline (already installed with suggested plugins)
- SonarQube Scanner
- OWASP Dependency-Check
- Jacoco
- HTML Publisher
- Kubernetes CLI (kubectl)
- Docker Pipeline
- Slack Notification (optional)
- AnsiColor
- Workspace Cleanup

### Step 3: Configure Credentials

Navigate to **Manage Jenkins → Credentials → System → Global credentials → Add Credential**:

| Credential ID | Type | Value |
|--------------|------|-------|
| `sonarqube-token` | Secret text | SonarQube API token |
| `dockerhub-credentials` | Username/Password | Docker Hub login |
| `kubeconfig` | Secret file | ~/.kube/config contents |
| `jwt-secret` | Secret text | Your JWT secret (32+ chars) |

```bash
# Create SonarQube token
# Login to http://localhost:9000 (admin/admin)
# My Account → Security → Generate Token → Copy token → paste as 'sonarqube-token'
```

### Step 4: Configure SonarQube Integration

Navigate to **Manage Jenkins → Configure System → SonarQube servers**:
- Name: `SonarQube`
- Server URL: `http://sonarqube:9000`
- Authentication token: `sonarqube-token`

### Step 5: Create the Pipeline Job

1. **New Item** → Enter name `secure-ai-gateway` → Select **Multibranch Pipeline** → OK
2. Under **Branch Sources** → Add Source → Git
3. Repository URL: `http://your-git-server/secure-ai-gateway.git`
4. Credentials: Add your Git credentials
5. **Build Configuration**: Mode = `by Jenkinsfile`, Script Path = `Jenkinsfile`
6. Click **Save** → Jenkins will scan and find branches

### Step 6: Pipeline Stage Summary

| Stage | What It Does | Failure Action |
|-------|-------------|----------------|
| 1. Checkout | Fetches source, sets build metadata (commit, author, branch) | Aborts build |
| 2. Compile | `mvn clean compile -DskipTests` | Aborts build |
| 3. Unit Tests | `mvn test` with JUnit 5 + publishes test reports | Aborts build |
| 4. JaCoCo Coverage | Enforces 70% line / 60% branch minimum | Aborts if below threshold |
| 5. SonarQube Analysis | Static code analysis for code smells + bugs | Reports issues |
| 6. Quality Gate | Waits for SonarQube quality gate result (10 min max) | Aborts if gate fails |
| 7. OWASP CVE Check | Scans dependencies; fails on CVSS ≥ 7.0 | Aborts + Slack alert |
| 8. SpotBugs | FindSecBugs security static analysis | Marks unstable if HIGH bugs found |
| 9. Build FAT JAR | `mvn package -DskipTests -Pprod` + archives artifact | Aborts build |
| 10. Docker Build & Push | Builds image tagged with Git commit SHA, pushes to registry | Aborts build |
| 11. Trivy Scan | Scans container for HIGH/CRITICAL CVEs | Aborts + Slack alert |
| 12. Deploy Dev | `kubectl set image` in `secure-ai-dev` namespace | Aborts + alerts |
| 13. Integration Tests | `mvn failsafe:integration-test` against dev environment | Reports failures |
| 14. Deploy Prod | Manual approval gate; `main` branch only → prod namespace | Requires approval |

### Step 7: Trigger a Build

```bash
# Commit and push to trigger a build
git add .
git commit -m "feat: add new security feature"
git push origin feature/my-feature

# Monitor in Jenkins UI: http://localhost:8090
# Or via CLI:
# (Install Jenkins CLI from http://localhost:8090/jnlpJars/jenkins-cli.jar)
java -jar jenkins-cli.jar -s http://localhost:8090 -auth admin:password \
  build secure-ai-gateway/main -f -v
```

---

## 11. Monitoring (Prometheus + Grafana)

### Prometheus Configuration

Prometheus scrapes metrics from the Spring Boot Actuator endpoint at `/actuator/prometheus`. Configuration is in `monitoring/prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'secure-ai-gateway'
    static_configs:
      - targets: ['app:8080']
    metrics_path: /actuator/prometheus
    scrape_interval: 15s
```

```bash
# Start Prometheus
docker compose up -d prometheus

# Verify scrape targets are healthy
# Open: http://localhost:9090/targets
# Status should be UP for secure-ai-gateway

# Sample queries (Prometheus expression browser)
# Request rate:
rate(http_server_requests_seconds_count{application="secure-ai-gateway"}[5m])

# Error rate:
rate(http_server_requests_seconds_count{status=~"5.."}[5m])

# JVM heap usage:
jvm_memory_used_bytes{area="heap"}

# Active database connections:
hikaricp_connections_active
```

### Grafana Setup

```bash
# Start Grafana
docker compose up -d grafana

# Open: http://localhost:3000
# Login: admin / admin (change on first login)

# Add Prometheus as data source:
# Configuration → Data Sources → Add data source → Prometheus
# URL: http://prometheus:9090
# Save & Test
```

**Recommended Grafana Dashboards:**

| Dashboard | Import ID | What it shows |
|-----------|-----------|---------------|
| Spring Boot Statistics | 12900 | JVM, HTTP, datasource metrics |
| JVM (Micrometer) | 4701 | Detailed JVM internals |
| PostgreSQL Database | 9628 | DB performance |

```bash
# Import via Grafana UI:
# Dashboards → Import → Enter ID → Load → Select Prometheus datasource → Import
```

### Application Metrics Exposed

The app exposes these custom metrics via Actuator:

```bash
# View all metrics
curl http://localhost:8080/actuator/metrics | jq '.names[]' | grep -i secure

# AI query count
curl http://localhost:8080/actuator/metrics/ai.queries.total

# Rate limit hits
curl http://localhost:8080/actuator/metrics/ratelimit.exceeded.total

# PII redaction count
curl http://localhost:8080/actuator/metrics/pii.redactions.total

# Full Prometheus format
curl http://localhost:8080/actuator/prometheus
```

---

## 12. API Reference & Testing

### Authentication Endpoints

```bash
# Register new user
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "developer",
    "password": "Dev@1234",
    "email": "dev@example.com"
  }' | jq .

# Login and get JWT token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"developer","password":"Dev@1234"}' | jq -r .token)

echo "Token: $TOKEN"

# Validate token (protected endpoint)
curl -s http://localhost:8080/api/auth/validate \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### AI Query Endpoints

```bash
# Direct AI query
curl -s -X POST http://localhost:8080/api/ai/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"What are the OWASP Top 10 vulnerabilities?"}' | jq .

# ReAct agent (multi-step reasoning — max 10 steps)
curl -s -X POST http://localhost:8080/api/agent/run \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"task":"Explain how JWT tokens work and why they are secure"}' | jq .

# Get audit log history (admin only)
curl -s http://localhost:8080/api/audit/logs \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### Response Format

```json
{
  "requestId": "a1b2c3d4-...",
  "prompt": "What are the OWASP Top 10?",
  "response": "The OWASP Top 10 are...",
  "modelUsed": "llama3.1:8b",
  "responseTimeMs": 2450,
  "piiRedacted": false,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created (registration) |
| 400 | Bad request / validation error |
| 401 | Missing or invalid JWT token |
| 403 | Forbidden (insufficient role) |
| 429 | Rate limit exceeded (100 req/hr) |
| 500 | Internal server error |
| 503 | Ollama LLM unavailable |

---

## 13. Troubleshooting

### App Won't Start

```bash
# Check logs
docker compose logs -f app
# Or for JAR:
java -jar target/secure-ai-gateway.jar 2>&1 | tail -50

# Common cause 1: DB not ready
# Fix: Ensure PostgreSQL is running and credentials are correct
docker compose up -d postgres
docker compose logs postgres | tail -20

# Common cause 2: Port 8080 already in use
lsof -i :8080
kill -9 <PID>

# Common cause 3: Wrong Java version
java -version  # Must be 17
update-alternatives --config java  # Select JDK 17
```

### Ollama / LLM Issues

```bash
# Model not found
ollama list  # Check model is downloaded
ollama pull llama3.1:8b  # Re-pull if missing

# Ollama not responding
curl http://localhost:11434/api/tags  # Should return JSON
systemctl restart ollama  # Linux service restart
# Or: kill $(pgrep ollama) && ollama serve &

# LLM timeout (slow machine)
# Increase in application.yml:
# ollama.timeout-seconds: 300

# Out of memory for model
free -h  # Check available RAM
# llama3.1:8b needs ~6 GB RAM
# Use a smaller model: ollama pull llama3.2:1b
# Then: OLLAMA_MODEL=llama3.2:1b mvn spring-boot:run
```

### JWT / Authentication Issues

```bash
# "401 Unauthorized" error
# Ensure token is not expired (1 hour TTL by default)
# Re-login to get a fresh token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"developer","password":"Dev@1234"}' | jq -r .token)

# Decode JWT to inspect claims
echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | jq .
# Check 'exp' field for expiry timestamp

# JWT secret mismatch (common in Kubernetes)
kubectl get secret secure-ai-secrets -n secure-ai-dev -o jsonpath='{.data.JWT_SECRET}' | base64 -d
# Ensure this matches the running app's JWT_SECRET
```

### Database Issues

```bash
# Flyway migration failure
docker compose logs app | grep -i flyway
# Common fix: clean and retry (DEVELOPMENT ONLY — destructive!)
mvn flyway:clean flyway:migrate -Dspring.profiles.active=dev

# PostgreSQL connection refused
docker compose ps postgres  # Is it running?
docker exec -it secure-ai-postgres pg_isready -U secureai_user
# Should output: /var/run/postgresql:5432 - accepting connections

# Check Hikari pool exhaustion (common in load tests)
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active | jq .
# If at max (20), scale up connections or investigate slow queries
```

### Kubernetes Issues

```bash
# Pod stuck in CrashLoopBackOff
kubectl describe pod <pod-name> -n secure-ai-dev
kubectl logs <pod-name> -n secure-ai-dev --previous

# ImagePullBackOff (Minikube)
eval $(minikube docker-env)  # Point Docker to Minikube
docker build -t secure-ai-gateway:latest .
# Update imagePullPolicy: Never in deployment.yaml

# Secrets not mounted
kubectl get events -n secure-ai-dev | grep -i secret
kubectl describe deployment secure-ai-gateway -n secure-ai-dev | grep -A5 Env

# HPA not scaling
kubectl describe hpa secure-ai-gateway-hpa -n secure-ai-dev
minikube addons enable metrics-server  # Ensure metrics-server is running
kubectl top pods -n secure-ai-dev  # Must show CPU/memory data
```

### Jenkins Pipeline Failures

```bash
# SonarQube quality gate timeout
# Jenkins waits up to 10 minutes for SonarQube analysis
# Check SonarQube at http://localhost:9000 → Projects → secure-ai-gateway

# OWASP check fails (CVE found)
# Check: target/dependency-check-report.html for details
# Add false positives to owasp-suppressions.xml

# Docker push fails
# Ensure dockerhub-credentials is configured correctly in Jenkins
# Check: docker login registry.hub.docker.com

# Trivy scan fails with network error
# Trivy needs internet access to update CVE database
# Pre-pull in offline mode: trivy image --download-db-only
```

### Rate Limiting (429 Too Many Requests)

```bash
# Check your remaining requests
curl -v http://localhost:8080/api/ai/query \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"prompt":"test"}' 2>&1 | grep -E "X-Rate|Retry|429"

# Response headers include:
# X-RateLimit-Remaining: 0
# Retry-After: 3600  (seconds until bucket refills)

# Temporarily increase limit (development)
# In application.yml:
# rate-limit.capacity: 1000
# rate-limit.refill-tokens: 1000
```

---

## Appendix: Useful Commands Reference

```bash
# Build everything
mvn clean package -DskipTests -Pprod

# Run tests with full coverage
mvn clean verify -Dspring.profiles.active=test

# Docker build & run
docker build -t secure-ai-gateway:latest .
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev secure-ai-gateway:latest

# Kubernetes quick deploy
kubectl apply -f k8s/ -n secure-ai-dev -R

# Watch all pods
kubectl get pods -n secure-ai-dev -w

# View app logs live
kubectl logs -f deployment/secure-ai-gateway -n secure-ai-dev

# Scale deployment
kubectl scale deployment secure-ai-gateway --replicas=5 -n secure-ai-dev

# Full stack reset (Docker Compose)
docker compose down -v && docker compose up -d
```

---

*Document Version: 2.0.0 | Based on actual project source code | Spring Boot 3.2.12 · Ollama LLaMA 3.1 8B · Kubernetes · Jenkins*
