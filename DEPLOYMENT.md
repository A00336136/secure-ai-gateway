# Deployment Strategy - Secure AI Gateway

## 1. Local Development (Laptop)
Keep the following components local for fast development iterations:
- **Java Application**: Run via Maven (`mvn spring-boot:run` in `secure-ai-web`) or IDE.
- **H2 Database**: Use in-memory H2 for unit and integration tests.
- **Mocked Services**: Use Mockito for all external AI/Guardrails dependencies during local development.

## 2. Docker (Containerized Services)
Run heavy dependencies in Docker to ensure environment consistency without cluttering the local OS:
- **PostgreSQL**: Persist audit logs in a local Docker container (`docker-compose up -d postgres`).
- **Ollama**: Run the AI engine in Docker (`ollama/ollama`) to offload computation.
- **Redis**: If used for distributed rate limiting or caching.

## 3. Kubernetes (Production/Staging)
Deploy the core gateway and its dependencies to K8s for scalability and high availability:
- **Secure AI Gateway**: Multi-instance deployment with LoadBalancer.
- **ConfigMaps/Secrets**: Manage JWT secrets and API endpoints.
- **Guardrails Servers**: Deploy NeMo, LlamaGuard, and Presidio as separate microservices in the same cluster.
- **Monitoring**: Prometheus + Grafana for tracking latency and block rates.

# Jenkins CI/CD Pipeline Configuration

## Multi-Module Maven Build
The pipeline is configured to build the parent project, which automatically builds all child modules in the correct dependency order.

## JaCoCo & SonarQube Integration
- **JaCoCo**: Generates XML coverage reports for each module.
- **SonarQube**: Aggregates reports and enforces **Quality Gates**:
    - **Code Coverage**: Min 80%
    - **Bugs/Vulnerabilities**: Zero allowed (Critical/Blocker)
    - **Maintainability**: Rating A

## Pipeline Stages
1. **Checkout**: Pull source from Git.
2. **Build & Test**: `mvn clean install` - executes all JUnit 5 + Mockito tests.
3. **Sonar Analysis**: `mvn sonar:sonar` - uploads results to SonarQube server.
4. **Quality Gate**: Waits for SonarQube callback; fails the build if Gate is not met.
5. **Docker Build**: Build the `secure-ai-web` module as a Docker image.
6. **Deploy**: Push to Registry and update K8s manifests.
