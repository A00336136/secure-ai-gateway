# ⚡ Secure AI Gateway v2.0

> **Enterprise-Grade Security Gateway for AI Model Interactions**  
> Spring Boot 3.2 · JWT · BCrypt · Bucket4j · Ollama LLaMA 3.1 · ReAct Agent · PostgreSQL · Jenkins CI/CD · Kubernetes · SonarQube · OWASP · Trivy

---

## 📋 Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Quick Start](#quick-start)
3. [CLI Guide (Step-by-Step)](#cli-guide)
4. [GUI Dashboard Guide](#gui-dashboard-guide)
5. [Components Deep Dive](#components)
6. [DevSecOps Pipeline](#devsecops-pipeline)
7. [Kubernetes Setup (Minikube)](#kubernetes-setup)
8. [SonarQube + Quality Gate](#sonarqube)
9. [API Reference](#api-reference)
10. [Test Scenarios](#test-scenarios)
11. [Security Compliance](#security-compliance)
12. [Troubleshooting](#troubleshooting)

---

## Architecture Overview

```
         ┌─────────────────────────────────────────┐
         │          CLIENT (Browser / CLI)          │
         └──────────────────┬──────────────────────┘
                            │ HTTPS + Bearer JWT
         ┌──────────────────▼──────────────────────┐
         │        ① JWT Filter (HMAC-SHA256)         │
         │  Validates token signature, expiry, role  │
         └──────────────────┬──────────────────────┘
                            │ Authenticated request
         ┌──────────────────▼──────────────────────┐
         │   ② Rate Limiter (Bucket4j Token Bucket)  │
         │   100 tokens/hr per user · HTTP 429 ↓    │
         └──────────────────┬──────────────────────┘
                            │ Rate OK
         ┌──────────────────▼──────────────────────┐
         │    ③ Controller / ReAct Agent Router      │
         │  Direct inference OR Think→Act→Observe    │
         └──────────────────┬──────────────────────┘
                            │
         ┌──────────────────▼──────────────────────┐
         │      Ollama Local LLM (LLaMA 3.1 8B)     │
         │  Zero cloud · Full privacy · Port 11434   │
         └──────────────────┬──────────────────────┘
                            │ Raw LLM response
         ┌──────────────────▼──────────────────────┐
         │    ④ PII Redaction Engine (10 patterns)   │
         │  Email·Phone·SSN·CC·IP·IBAN·DOB·Passport │
         └──────────────────┬──────────────────────┘
                            │ Redacted response
         ┌──────────────────▼──────────────────────┐
         │   ⑤ Audit Logger (Async → PostgreSQL)     │
         │   Immutable append-only compliance log    │
         └──────────────────┬──────────────────────┘
                            │
         ┌──────────────────▼──────────────────────┐
         │          Response + Security Headers      │
         │  X-Rate-Limit-Remaining · X-PII-Redacted  │
         └─────────────────────────────────────────┘
```

---

## Quick Start

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java JDK | 17+ | Runtime |
| Maven | 3.9+ | Build |
| Docker | 24+ | Containers |
| Minikube | Latest | Local K8s |
| Ollama | Latest | Local LLM |

---

## CLI Guide

### 1. Clone & Build

```bash
# Clone repository
git clone https://github.com/your-org/secure-ai-gateway.git
cd secure-ai-gateway

# Build (skip tests for speed)
mvn clean package -DskipTests

# Build with full test suite
mvn clean package
```

### 2. Start Ollama (Local LLM)

```bash
# Install Ollama
curl -fsSL https://ollama.ai/install.sh | sh

# Start Ollama server
ollama serve

# Pull LLaMA 3.1 8B model (in new terminal)
ollama pull llama3.1:8b

# Verify model is ready
ollama list
# Should show: llama3.1:8b
```

### 3. Start PostgreSQL

```bash
# Option A: Docker (recommended for dev)
docker run -d \
  --name secureai-postgres \
  -e POSTGRES_DB=secureai \
  -e POSTGRES_USER=secureai_user \
  -e POSTGRES_PASSWORD=secureai_password \
  -p 5432:5432 \
  postgres:16-alpine

# Option B: Homebrew (macOS)
brew install postgresql@16
brew services start postgresql@16
createdb secureai
```

### 4. Run the Application

```bash
# Development mode (H2 in-memory, no PostgreSQL needed)
mvn spring-boot:run -Dspring.profiles.active=dev

# Production mode (requires PostgreSQL)
mvn spring-boot:run -Dspring.profiles.active=prod \
  -Ddb.password=secureai_password \
  -Djwt.secret=your-secret-key-at-least-32-chars-long

# Or via environment variables:
export SPRING_PROFILES_ACTIVE=prod
export DB_PASSWORD=secureai_password
export JWT_SECRET="your-32-char-secret-key-here"
mvn spring-boot:run
```

### 5. Test via CLI (cURL)

```bash
BASE_URL="http://localhost:8080"

# ── Register a new user ──────────────────────────────
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"SecurePass123!","email":"alice@example.com"}' \
  | python3 -m json.tool

# ── Login and capture token ──────────────────────────
TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo "Token: $TOKEN"

# ── Send AI prompt ────────────────────────────────────
curl -s -X POST "$BASE_URL/api/ask" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Explain quantum computing in 2 sentences","useReActAgent":false}' \
  | python3 -m json.tool

# ── Use ReAct Agent mode ──────────────────────────────
curl -s -X POST "$BASE_URL/api/ask" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"What is 15 factorial? Show your reasoning.","useReActAgent":true}' \
  | python3 -m json.tool

# ── Check rate limit remaining ────────────────────────
curl -sI -X POST "$BASE_URL/api/ask" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Hello"}' \
  | grep -i "x-rate-limit"

# ── View API status ───────────────────────────────────
curl -s "$BASE_URL/api/status" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -m json.tool

# ── Admin: View dashboard stats ───────────────────────
curl -s "$BASE_URL/admin/dashboard" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -m json.tool

# ── Admin: View audit logs ────────────────────────────
curl -s "$BASE_URL/admin/audit?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -m json.tool

# ── Admin: View PII alerts ────────────────────────────
curl -s "$BASE_URL/admin/audit/pii-alerts" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -m json.tool

# ── Actuator health check ─────────────────────────────
curl -s "$BASE_URL/actuator/health" | python3 -m json.tool
```

### 6. Run Tests

```bash
# Unit tests only (*Test.java — runs under Maven Surefire, fast, no Spring context required for most)
mvn test

# Specific test class
mvn test -Dtest=PiiRedactionServiceTest

# Unit tests with coverage report
mvn test jacoco:report
# View: open target/site/jacoco/index.html

# Integration tests only (*IT.java — runs under Maven Failsafe, requires Spring context + H2 DB)
mvn failsafe:integration-test failsafe:verify

# Both unit + integration tests (runs Surefire then Failsafe)
mvn verify

# OWASP Dependency Check
mvn dependency-check:check

# SpotBugs analysis
mvn spotbugs:check

# SonarQube (requires SonarQube server at localhost:9000)
mvn sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=YOUR_SONAR_TOKEN
```

---

## GUI Dashboard Guide

1. **Open browser**: `http://localhost:8080`
2. **Login**: admin / Admin@123 (or your registered user)
3. **Chat Tab**: Send prompts, toggle ReAct mode, see PII badges
4. **Dashboard Tab**: View stats (total requests, PII detections, avg response time)
5. **Audit Logs Tab**: See all requests with PII flags (ADMIN only)
6. **DevSecOps Tab**: Pipeline stage status and Kubernetes status
7. **API Docs Tab**: REST endpoint reference + Swagger UI link

**Swagger UI**: `http://localhost:8080/swagger-ui.html`  
**H2 Console** (dev): `http://localhost:8080/h2-console`

---

## DevSecOps Pipeline

### Jenkins Setup (Local)

```bash
# Start Jenkins via Docker
docker run -d \
  --name jenkins \
  -p 8090:8080 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  jenkins/jenkins:lts-jdk17

# Get admin password
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

**Jenkins Configuration:**
1. Open `http://localhost:8090`
2. Install suggested plugins + Blue Ocean, SonarQube Scanner, Docker, Kubernetes
3. Add credentials:
   - `sonarqube-token` → Secret text
   - `dockerhub-credentials` → Username/Password
   - `kubeconfig` → Secret file
   - `jwt-secret` → Secret text
4. Create Multibranch Pipeline → GitHub repo URL
5. The `Jenkinsfile` auto-configures all 12 stages

### SonarQube Quality Gate

```bash
# Start SonarQube
docker run -d \
  --name sonarqube \
  -p 9000:9000 \
  sonarqube:community

# Open http://localhost:9000 (admin/admin)
# Create project: secure-ai-gateway
# Generate token and add to Jenkins credentials

# Run analysis directly
mvn sonar:sonar \
  -Dsonar.projectKey=secure-ai-gateway \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=YOUR_TOKEN
```

**Quality Gate thresholds (configured):**
- Line coverage: 70% minimum
- Duplicated lines: < 3%
- Maintainability rating: A
- Security rating: A
- Reliability rating: A

---

## Kubernetes Setup (Minikube)

```bash
# 1. Start Minikube
minikube start --memory=4096 --cpus=4 --disk-size=20g

# Enable Nginx Ingress
minikube addons enable ingress
minikube addons enable metrics-server

# 2. Create namespaces + config
kubectl apply -f k8s/namespace.yaml

# 3. Deploy PostgreSQL
kubectl apply -f k8s/postgres/postgres.yaml

# 4. Deploy SonarQube
kubectl apply -f k8s/sonarqube/sonarqube.yaml

# 5. Deploy Jenkins
kubectl apply -f k8s/jenkins/jenkins.yaml

# 6. Deploy Application
kubectl apply -f k8s/deployment.yaml

# 7. Verify all pods are running
kubectl get pods -n secure-ai-dev

# 8. Access the gateway
minikube service secure-ai-gateway-service -n secure-ai-dev

# OR via port-forward:
kubectl port-forward svc/secure-ai-gateway-service 8080:80 -n secure-ai-dev

# 9. Access Jenkins
minikube service jenkins-service -n secure-ai-dev

# 10. Access SonarQube
minikube service sonarqube-service -n secure-ai-dev

# Useful kubectl commands:
kubectl get all -n secure-ai-dev
kubectl logs -f deployment/secure-ai-gateway -n secure-ai-dev
kubectl describe pod -l app=secure-ai-gateway -n secure-ai-dev
kubectl rollout status deployment/secure-ai-gateway -n secure-ai-dev
```

---

## API Reference

### Authentication

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/auth/register` | POST | Public | Register new user |
| `/auth/login` | POST | Public | Login → get JWT |
| `/auth/health` | GET | Public | Health check |

### AI Gateway

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/ask` | POST | JWT | Send prompt (rate limited) |
| `/api/status` | GET | JWT | Ollama + rate limit status |

### Admin (ROLE_ADMIN only)

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/admin/dashboard` | GET | ADMIN | Aggregate statistics |
| `/admin/audit` | GET | ADMIN | Paginated audit logs |
| `/admin/audit/pii-alerts` | GET | ADMIN | PII-detected requests |
| `/admin/rate-limit/{user}` | DELETE | ADMIN | Reset user rate limit |

### Response Headers

```
X-Rate-Limit-Remaining: 98        ← Tokens left this hour
X-Rate-Limit-Capacity: 100        ← Max tokens per hour
X-PII-Redacted: true              ← Whether PII was found
X-Duration-Ms: 1247               ← Request processing time
```

---

## Test Scenarios

### Scenario 1: Happy Path — Basic AI Query
```
Given:  User is registered and logged in
When:   POST /api/ask {"prompt": "Hello, what can you do?"}
Then:   200 OK with non-empty response
        piiDetected = false
        X-Rate-Limit-Remaining = 99
```

### Scenario 2: PII Redaction — Email + SSN
```
Given:  LLM returns "Email john@corp.com, SSN 123-45-6789"
When:   Gateway processes the response
Then:   Response contains "[EMAIL_REDACTED]" and "[SSN_REDACTED]"
        piiDetected = true
        Original PII NOT in response or audit log
```

### Scenario 3: Rate Limiting
```
Given:  User has consumed 100 tokens this hour
When:   User sends another request
Then:   HTTP 429 Too Many Requests
        Header: Retry-After: 3600
        Header: X-Rate-Limit-Remaining: 0
        Audit log entry with rateLimited = true
```

### Scenario 4: JWT Authentication Failure
```
Given:  Request with invalid/expired JWT token
When:   Any protected endpoint is called
Then:   HTTP 403 Forbidden
        Body: {"status":403,"error":"Forbidden","message":"..."}
        No AI inference performed
```

### Scenario 5: ReAct Agent Multi-Step
```
Given:  User enables useReActAgent=true
When:   POST /api/ask {"prompt":"...", "useReActAgent":true}
Then:   200 OK with complete agent answer
        reactSteps > 0 in response
        Full audit trail of all steps
```

### Scenario 6: Admin Audit Log
```
Given:  Admin user with ROLE_ADMIN
When:   GET /admin/audit?page=0&size=20
Then:   200 OK with paginated audit entries
        All responses PII-redacted in audit
        Timestamps, usernames, durations present
```

### Scenario 7: Security — No Auth
```
Given:  Request with no Authorization header
When:   POST /api/ask
Then:   HTTP 403 Forbidden
        No LLM inference performed
        No rate limit consumption
```

---

## Security Compliance

| Control | Implementation | Standard |
|---------|---------------|----------|
| Authentication | JWT HMAC-SHA256, 1hr expiry | OAuth 2.0 |
| Password Storage | BCrypt cost=12 | NIST 800-63 |
| PII Protection | Regex redaction engine | GDPR Article 25 |
| Rate Limiting | Token bucket 100/hr | CWE-400 |
| Input Validation | Jakarta Bean Validation | OWASP A03 |
| Error Handling | No stack trace leakage | OWASP A05 |
| Security Headers | CSP, HSTS, X-Frame-Options | OWASP |
| CORS | Allowlist-based origins | OWASP |
| CVE Scanning | OWASP Dependency-Check | DevSecOps |
| Container Scanning | Trivy (CRITICAL/HIGH) | DevSecOps |
| Static Analysis | SpotBugs + FindSecBugs | DevSecOps |
| Code Quality | SonarQube Quality Gate | ISO 25010 |
| Audit Logging | Immutable PostgreSQL logs | ISO 27001 |

---

## Troubleshooting

**Ollama not responding:**
```bash
# Check if running
curl http://localhost:11434/api/tags
# Start: ollama serve
# Pull model: ollama pull llama3.1:8b
```

**JWT secret too short:**
```
IllegalStateException: JWT secret must be at least 32 bytes
Fix: export JWT_SECRET="a-much-longer-secret-key-here-32chars"
```

**PostgreSQL connection refused:**
```bash
docker ps | grep postgres
docker logs secureai-postgres
# Check: jdbc:postgresql://localhost:5432/secureai
```

**SonarQube Quality Gate failing:**
```bash
# Check coverage is sufficient
mvn test jacoco:report
open target/site/jacoco/index.html
```

**Minikube pods not starting:**
```bash
kubectl describe pod -l app=secure-ai-gateway -n secure-ai-dev
kubectl logs -f deployment/secure-ai-gateway -n secure-ai-dev
# Check resource limits: minikube start --memory=6144
```
