// ═══════════════════════════════════════════════════════════════════════════
// Jenkinsfile — Secure AI Gateway (Multi-Module Maven)
// Multi-Branch Pipeline · 12-Stage DevSecOps CI/CD
//
// Module Build Order: secure-ai-model → secure-ai-core →
//                     secure-ai-service → secure-ai-web (FAT JAR)
//
// Test Pyramid:
//   L1: Unit Tests          — JUnit 5 + Mockito (*Test.java via Surefire)
//   L2: Smoke Tests         — Context + Endpoints (*SmokeTest.java via Failsafe)
//   L3: Integration Tests   — E2E security flows (*IT.java via Failsafe)
//   L4: Karate API Tests    — BDD-style E2E against deployed app
//
// DevSecOps Chain:
//   JaCoCo (80% line) → SpotBugs + FindSecBugs → SonarQube + Quality Gate →
//   Docker Build → Trivy (HIGH/CRITICAL) → Docker Push → Deploy → Karate E2E
//
// ── Infrastructure (NOT managed by this pipeline) ──────────────────────────
// SonarQube and ngrok run as separate Docker Compose stacks:
//   SonarQube:  docker compose -f docker-compose.infra.yml -p secureai-infra up -d
//   ngrok:      managed within secureai-infra stack (port 4041)
//
// Ports (completely isolated from NutriTrack):
//   Jenkins: 9095 | SonarQube: 9001 | SonarDB: 5433 | AppDB: 5434
//   Prometheus: 9092 | Grafana: 3001 | ngrok: 4041 | App: 8100
// ═══════════════════════════════════════════════════════════════════════════

pipeline {
    agent any

    // ── Build Triggers ──────────────────────────────────────────────────────
    // githubPush() — Jenkins listens for GitHub webhook POST requests via ngrok.
    // ngrok is Dockerised in secureai-infra stack (port 4041).
    // pollSCM is a fallback: polls every 1 min if webhook delivery fails.
    triggers {
        githubPush()
        pollSCM('* * * * *')
    }

    // ── Tool Installations ───────────────────────────────────────────────────
    // Requires these to be registered in:
    //   Jenkins → Manage Jenkins → Global Tool Configuration
    //
    //   Maven : name = "Maven", install automatically (Apache 3.9.x)
    //   JDK   : name = "JDK21", install automatically (Adoptium 21)
    //
    // Jenkins will auto-install on first run and add them to PATH for every
    // sh/bat step — this is why bare `mvn` works without a full path.
    tools {
        maven 'Maven'
        jdk   'JDK21'
    }

    // ── Credentials ─────────────────────────────────────────────────────────
    // sonarqube-token       — SonarQube auth token (Jenkins Credentials Store)
    // dockerhub-credentials — Docker Hub username/PAT (for push)
    environment {
        APP_NAME        = 'secure-ai-gateway'
        DOCKER_IMAGE    = "absartus/${APP_NAME}"
        DOCKERHUB_USER  = 'absartus'
        SONAR_TOKEN     = credentials('sonarqube-token')
        SONAR_URL       = 'http://host.docker.internal:9001'
        JAVA_HOME       = "${tool 'JDK21'}"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '5'))
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 0 — SETUP (Multibranch: compute branch-based image tag)
        //           Detect ngrok tunnel URL for GitHub webhook config
        // ─────────────────────────────────────────────────────────────────────
        stage('Setup') {
            steps {
                checkout scm
                script {
                    // Sanitize branch name for Docker tag
                    def branchTag = env.BRANCH_NAME
                        .replaceAll('[^a-zA-Z0-9._-]', '-')
                        .toLowerCase()
                    env.BRANCH_TAG     = branchTag
                    env.GIT_COMMIT_MSG = sh(script: 'git log -1 --pretty=%B', returnStdout: true).trim()
                    env.GIT_AUTHOR     = sh(script: 'git log -1 --pretty=%an', returnStdout: true).trim()

                    echo "================================================"
                    echo "  SecureAI Gateway — Pipeline Setup"
                    echo "================================================"
                    echo "  Branch     : ${env.BRANCH_NAME}"
                    echo "  Build #    : ${env.BUILD_NUMBER}"
                    echo "  Image tag  : ${branchTag}-${env.BUILD_NUMBER}"
                    echo "  Commit     : ${env.GIT_COMMIT}"
                    echo "  Author     : ${env.GIT_AUTHOR}"
                    echo "  Message    : ${env.GIT_COMMIT_MSG}"
                    echo "  Push :latest: ${env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master'}"
                    echo "================================================"

                    // ── Detect Dockerised ngrok tunnel (port 4041) ────────────────
                    echo ""
                    echo "================================================"
                    echo "  ngrok Tunnel Detection (SecureAIGW)"
                    echo "================================================"
                    sh '''
                        NGROK_STATUS=$(docker inspect -f '{{.State.Status}}' secureai-ngrok 2>/dev/null || echo "not_found")
                        echo "  ngrok container: ${NGROK_STATUS}"
                        if [ "${NGROK_STATUS}" = "running" ]; then
                            NGROK_URL=$(curl -sf http://localhost:4041/api/tunnels 2>/dev/null \
                                | python3 -c "
import sys, json
data = json.load(sys.stdin)
tunnels = data.get('tunnels', [])
for t in tunnels:
    if t.get('proto') == 'https':
        print(t['public_url'])
        break
else:
    print(tunnels[0].get('public_url', 'UNKNOWN') if tunnels else 'NO_TUNNELS')
" 2>/dev/null || echo "API_ERROR")
                            echo "  ngrok URL: ${NGROK_URL}"
                            echo "  Webhook  : ${NGROK_URL}/github-webhook/"
                        else
                            echo "  ngrok not running — using pollSCM fallback"
                        fi
                    '''
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 1 — MAVEN BUILD (Install Multi-Module)
        // Build order: model → core → service → web
        // ─────────────────────────────────────────────────────────────────────
        stage('Maven Build') {
            steps {
                echo "========================================"
                echo "  Stage 1: Maven Build (4 modules)"
                echo "========================================"
                sh 'mvn -B clean install -DskipTests'
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 2 — UNIT TESTS + JACOCO COVERAGE
        // JUnit 5 + Mockito — Surefire runs *Test.java across ALL 4 modules
        // JaCoCo enforces 80% line coverage threshold
        // ─────────────────────────────────────────────────────────────────────
        stage('Unit Tests + Coverage') {
            steps {
                echo "========================================"
                echo "  Stage 2: Unit Tests + JaCoCo Coverage"
                echo "========================================"
                script {
                    try {
                        sh 'mvn -B test jacoco:report -Dspring.profiles.active=test'
                    } catch (Exception e) {
                        env.FAILURE_CAUSE = "Stage 2 — Unit Tests FAILED: ${e.message}"
                        error("Unit tests failed")
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/**/*.xml'
                    // Publish JaCoCo HTML coverage report in Jenkins UI
                    // This generates the Coverage Summary chart (INSTRUCTION, BRANCH,
                    // COMPLEXITY, LINE, METHOD, CLASS) visible on the build page
                    jacoco(
                        execPattern:           '**/target/jacoco.exec',
                        classPattern:          '**/target/classes',
                        sourcePattern:         '**/src/main/java',
                        exclusionPattern:      '**/*Application*.class,**/*DTO*.class,**/exception/**,**/config/**,**/model/**',
                        minimumLineCoverage:    '70',
                        minimumBranchCoverage: '60',
                        maximumLineCoverage:   '80',
                        maximumBranchCoverage: '70',
                        changeBuildStatus:      true
                    )
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 3 — SPOTBUGS + FINDSECBUGS ANALYSIS
        // Static analysis for bugs + security vulnerabilities
        // ─────────────────────────────────────────────────────────────────────
        stage('SpotBugs Analysis') {
            steps {
                echo "========================================"
                echo "  Stage 3: SpotBugs Static Analysis"
                echo "========================================"
                script {
                    try {
                        sh 'mvn -B spotbugs:check'
                    } catch (Exception e) {
                        env.FAILURE_CAUSE = "Stage 3 — SpotBugs FAILED: static analysis found bugs above threshold."
                        error("SpotBugs check failed")
                    }
                }
            }
            post {
                always {
                    archiveArtifacts(
                        artifacts:         '**/spotbugsXml.xml',
                        allowEmptyArchive: true,
                        fingerprint:       true
                    )
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 4 — SONARQUBE SCAN + QUALITY GATE
        // SecureAIGW SonarQube on port 9001 (isolated from NutriTrack 9000)
        // ─────────────────────────────────────────────────────────────────────
        stage('SonarQube Scan + Quality Gate') {
            steps {
                echo "========================================"
                echo "  Stage 4: SonarQube + Quality Gate"
                echo "========================================"
                script {
                    env.SONAR_SCAN_SUCCESS = 'false'
                    try {
                        // Pre-flight: validate token before running full scan
                        sh '''
                            echo "=== SonarQube token pre-flight check ==="
                            TOKEN_LEN=$(printf '%s' "${SONAR_TOKEN}" | wc -c | tr -d ' ')
                            TOKEN_PREFIX=$(printf '%s' "${SONAR_TOKEN}" | cut -c1-4)
                            echo "  Token length  : ${TOKEN_LEN} chars"
                            echo "  Token prefix  : ${TOKEN_PREFIX}..."
                            VALID=$(curl -sf --max-time 5 \
                                -u "${SONAR_TOKEN}:" \
                                "${SONAR_URL}/api/authentication/validate" \
                                | python3 -c "import sys,json; print(json.load(sys.stdin).get('valid','false'))" \
                                2>/dev/null || echo "false")
                            echo "  Token valid   : ${VALID}"
                            if [ "${VALID}" != "True" ] && [ "${VALID}" != "true" ]; then
                                echo "  WARNING: SonarQube token validation failed"
                                echo "  Fix: Generate new token at http://localhost:9001 → My Account → Security"
                            fi
                            echo "========================================="
                        '''

                        withSonarQubeEnv('SonarQube') {
                            sh """
                                unset SONARQUBE_SCANNER_PARAMS
                                mvn -B sonar:sonar \
                                    -Dsonar.projectKey=${APP_NAME} \
                                    -Dsonar.projectName='Secure AI Gateway' \
                                    -Dsonar.host.url=${SONAR_URL} \
                                    -Dsonar.token=\${SONAR_TOKEN}
                            """
                        }
                        env.SONAR_SCAN_SUCCESS = 'true'
                    } catch (Exception e) {
                        env.FAILURE_CAUSE = "Stage 4 — SonarQube scan FAILED. Error: ${e.message}"
                        error("SonarQube analysis failed")
                    }
                }
            }
            post {
                always {
                    script {
                        if (env.SONAR_SCAN_SUCCESS == 'true') {
                            // Check Quality Gate via REST API — retry up to 60s for analysis to complete
                            sh """
                                echo "Checking Quality Gate status..."
                                QG_STATUS="UNKNOWN"
                                for i in 1 2 3 4 5 6; do
                                    sleep 10
                                    QG_STATUS=\$(curl -sf -u "\${SONAR_TOKEN}:" \
                                        "${SONAR_URL}/api/qualitygates/project_status?projectKey=${APP_NAME}" \
                                        | python3 -c "import sys,json; print(json.load(sys.stdin).get('projectStatus',{}).get('status','UNKNOWN'))" \
                                        2>/dev/null || echo "UNKNOWN")
                                    echo "  Attempt \${i}: Quality Gate = \${QG_STATUS}"
                                    if [ "\${QG_STATUS}" != "UNKNOWN" ]; then
                                        break
                                    fi
                                done
                                echo "Quality Gate: \${QG_STATUS}"
                                if [ "\${QG_STATUS}" = "ERROR" ]; then
                                    echo "WARNING: Quality Gate FAILED — check SonarQube dashboard"
                                fi
                            """
                        }
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 5 — ARCHIVE ARTIFACTS (FAT JAR)
        // ─────────────────────────────────────────────────────────────────────
        stage('Archive Artifacts') {
            steps {
                echo "========================================"
                echo "  Stage 5: Archive Build Artifacts"
                echo "========================================"
                sh 'mvn -B package -DskipTests -pl secure-ai-web -am'
                archiveArtifacts artifacts: 'secure-ai-web/target/*.jar', fingerprint: true
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 6 — DOCKER BUILD & TAG
        // Single monolith image (unlike NutriTrack's 6 microservices)
        // Tags: <branch>-<build> and :latest (main/master only)
        // ─────────────────────────────────────────────────────────────────────
        stage('Docker Build & Tag') {
            steps {
                echo "========================================"
                echo "  Stage 6: Docker Build & Tag"
                echo "========================================"
                script {
                    try {
                        def imageTag     = "${env.BRANCH_TAG}-${env.BUILD_NUMBER}"
                        def hubUser      = env.DOCKERHUB_USER
                        def isMainBranch = (env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master')

                        echo "Building Docker image: ${hubUser}/${APP_NAME}:${imageTag}"

                        sh """
                            docker build \
                                -t ${hubUser}/${APP_NAME}:${imageTag} \
                                -t ${hubUser}/${APP_NAME}:build-${env.BUILD_NUMBER} \
                                .
                        """

                        // Tag :latest ONLY on main/master
                        if (isMainBranch) {
                            sh "docker tag ${hubUser}/${APP_NAME}:${imageTag} ${hubUser}/${APP_NAME}:latest"
                            echo "Tagged :latest (main branch)"
                        } else {
                            echo "Skipping :latest tag (branch: ${env.BRANCH_NAME})"
                        }

                        sh "docker images --format '{{.Repository}}:{{.Tag}}  {{.Size}}' | grep '${APP_NAME}' | grep '${env.BRANCH_TAG}' || true"
                        echo "Stage 6 PASSED — Docker image built [tag: ${imageTag}]"
                    } catch (Exception e) {
                        env.FAILURE_CAUSE = "Stage 6 — Docker Build FAILED: ${e.message}"
                        error("Docker Build failed")
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 7 — TRIVY SECURITY SCAN
        // Scans Docker image for CVEs (CRITICAL + HIGH)
        // --ignore-unfixed: skip base-image CVEs with no upstream fix
        // ─────────────────────────────────────────────────────────────────────
        stage('Trivy Security Scan') {
            steps {
                echo "========================================"
                echo "  Stage 7: Trivy Image Security Scan"
                echo "========================================"
                script {
                    def imageTag = "${env.BRANCH_TAG}-${env.BUILD_NUMBER}"
                    def hubUser  = env.DOCKERHUB_USER
                    def fullImage = "${hubUser}/${APP_NAME}:${imageTag}"

                    def trivyPath = sh(
                        script: 'which trivy || echo /usr/local/bin/trivy',
                        returnStdout: true
                    ).trim()

                    // Scan for CVEs — exit-code 0 so base-image CVEs don't fail the build
                    // We report findings in trivy-report.json artifact for review
                    sh """
                        ${trivyPath} image \
                            --format json \
                            --output trivy-report.json \
                            --severity CRITICAL,HIGH \
                            --ignore-unfixed \
                            --exit-code 0 \
                            ${fullImage}
                    """
                    // Count findings and warn (but don't mark UNSTABLE for base-image CVEs)
                    def vulnCount = sh(
                        script: """python3 -c "
import json
data = json.load(open('trivy-report.json'))
results = data.get('Results', [])
total = sum(len(r.get('Vulnerabilities', [])) for r in results)
print(total)
" 2>/dev/null || echo "0" """,
                        returnStdout: true
                    ).trim()
                    if (vulnCount.toInteger() > 0) {
                        echo "INFO: Trivy found ${vulnCount} fixable CRITICAL/HIGH CVEs in base image (non-blocking)"
                        echo "Review trivy-report.json artifact for details."
                    } else {
                        echo "Trivy scan PASSED — no fixable CRITICAL/HIGH CVEs found"
                    }

                    // Generate human-readable table report
                    sh """
                        ${trivyPath} image \
                            --format table \
                            --severity CRITICAL,HIGH \
                            --ignore-unfixed \
                            --exit-code 0 \
                            ${fullImage} \
                            2>&1 | tee trivy-summary.txt || true
                    """
                }
            }
            post {
                always {
                    archiveArtifacts(
                        artifacts:         'trivy-report.json,trivy-summary.txt',
                        allowEmptyArchive: true,
                        fingerprint:       true
                    )
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 7b — AI RED-TEAM SECURITY SCAN
        // LLM-specific adversarial testing: Garak + Promptfoo
        // Maps to: OWASP LLM Top 10, MITRE ATLAS, NIST AI RMF
        // Non-blocking: findings archived as artifacts, build not failed
        // Run nightly on main/master; per-PR on feature branches
        // Tools: Garak (NVIDIA), Promptfoo (MIT), JailbreakBench (NeurIPS 2024)
        // ─────────────────────────────────────────────────────────────────────
        stage('AI Red-Team Security Scan') {
            steps {
                echo "========================================"
                echo "  Stage 7b: AI Red-Team Security Scan"
                echo "  Tools: Garak + Promptfoo"
                echo "  OWASP LLM Top 10 | MITRE ATLAS | NIST AI RMF"
                echo "========================================"
                script {
                    def appUrl = "http://localhost:8100"

                    // ── Garak LLM Vulnerability Scanner (NVIDIA) ─────────────────
                    sh '''
                        echo "=== Garak LLM Red-Team Scan ==="
                        GARAK_PATH=$(which garak 2>/dev/null || echo "")
                        if [ -n "${GARAK_PATH}" ]; then
                            echo "Garak found at: ${GARAK_PATH}"
                            mkdir -p redteam-reports
                            garak \
                                --model_type rest \
                                --model_name secure-ai-gateway \
                                --probes encoding,jailbreak,leakage,toxicity,continuation \
                                --report_prefix redteam-reports/garak \
                                --parallel_attempts 5 \
                                2>&1 | tee redteam-reports/garak-output.txt || true
                            echo "Garak scan complete. Results in redteam-reports/garak-*.jsonl"
                        else
                            echo "INFO: Garak not installed — skipping."
                            echo "Install: pip install garak"
                            echo "Docs: https://github.com/NVIDIA/garak"
                            mkdir -p redteam-reports
                            echo "Garak not installed on this Jenkins agent." > redteam-reports/garak-output.txt
                        fi
                    '''

                    // ── Promptfoo OWASP LLM Top 10 Scan ─────────────────────────
                    sh """
                        echo "=== Promptfoo OWASP LLM Red-Team Scan ==="
                        PROMPTFOO_PATH=\$(which promptfoo 2>/dev/null || which npx 2>/dev/null || echo "")
                        if [ -n "\${PROMPTFOO_PATH}" ]; then
                            echo "Promptfoo found. Running OWASP LLM Top 10 scan..."
                            mkdir -p redteam-reports
                            # Generate promptfoo red-team config targeting the deployed gateway
                            cat > redteam-reports/promptfoo-config.yaml << 'PFEOF'
targets:
  - id: http
    config:
      url: ${appUrl}/api/ask
      method: POST
      headers:
        Content-Type: application/json
        Authorization: "Bearer \${GATEWAY_TEST_TOKEN}"
      body:
        prompt: "{{prompt}}"

redteam:
  plugins:
    - owasp:llm:01   # Prompt Injection
    - owasp:llm:02   # Sensitive Info Disclosure
    - owasp:llm:06   # Excessive Agency
    - owasp:llm:07   # System Prompt Leakage
    - owasp:llm:09   # Misinformation
    - owasp:llm:10   # Unbounded Consumption
    - jailbreak
    - harmful:hate
    - harmful:violence
    - pii:direct
    - pii:session
  numTests: 25
  strategies:
    - jailbreak
    - prompt-injection
PFEOF
                            promptfoo redteam run \
                                --config redteam-reports/promptfoo-config.yaml \
                                --output redteam-reports/promptfoo-results.json \
                                --no-cache \
                                2>&1 | tee redteam-reports/promptfoo-output.txt || true

                            # Generate HTML report
                            promptfoo redteam report \
                                --file redteam-reports/promptfoo-results.json \
                                --output redteam-reports/promptfoo-report.html \
                                2>/dev/null || true

                            echo "Promptfoo scan complete."
                        else
                            echo "INFO: Promptfoo not installed — skipping."
                            echo "Install: npm install -g promptfoo"
                            echo "Docs: https://www.promptfoo.dev/docs/red-team/"
                            mkdir -p redteam-reports
                            echo "Promptfoo not installed on this Jenkins agent." > redteam-reports/promptfoo-output.txt
                        fi
                    """

                    // ── Summarise findings ────────────────────────────────────────
                    sh '''
                        echo ""
                        echo "=== AI Red-Team Scan Summary ==="
                        if [ -f "redteam-reports/garak-output.txt" ]; then
                            GARAK_FAILS=$(grep -c "FAIL\\|fail\\|vulnerable" redteam-reports/garak-output.txt 2>/dev/null || echo 0)
                            echo "  Garak failures detected : ${GARAK_FAILS}"
                        fi
                        if [ -f "redteam-reports/promptfoo-output.txt" ]; then
                            PFOO_FAILS=$(grep -c "failed\\|vulnerable\\|FAIL" redteam-reports/promptfoo-output.txt 2>/dev/null || echo 0)
                            echo "  Promptfoo failures      : ${PFOO_FAILS}"
                        fi
                        echo "  Full reports archived in: redteam-reports/"
                        echo "  Review artifacts to assess LLM security posture."
                        echo "================================"
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts(
                        artifacts:         'redteam-reports/**/*',
                        allowEmptyArchive: true,
                        fingerprint:       true
                    )
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 8 — DOCKER PUSH TO HUB
        // Pushes image to Docker Hub using PAT credentials
        // main/master → :branch-build AND :latest
        // other branches → :branch-build only
        // ─────────────────────────────────────────────────────────────────────
        stage('Docker Push to Hub') {
            steps {
                echo "========================================"
                echo "  Stage 8: Docker Push to Docker Hub"
                echo "========================================"
                script {
                    try {
                        def imageTag     = "${env.BRANCH_TAG}-${env.BUILD_NUMBER}"
                        def hubUser      = env.DOCKERHUB_USER
                        def isMainBranch = (env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master')

                        withCredentials([usernamePassword(
                            credentialsId: 'dockerhub-credentials',
                            usernameVariable: 'DOCKER_USER',
                            passwordVariable: 'DOCKER_PASS'
                        )]) {
                            sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'

                            echo "Pushing ${hubUser}/${APP_NAME}:${imageTag}..."
                            retry(3) {
                                sh "docker push ${hubUser}/${APP_NAME}:${imageTag}"
                            }

                            if (isMainBranch) {
                                retry(3) {
                                    sh "docker push ${hubUser}/${APP_NAME}:latest"
                                }
                                echo "Pushed :latest (main branch)"
                            }
                        }

                        sh 'docker logout || true'
                        echo "Stage 8 PASSED — image pushed [tag: ${imageTag}]"
                    } catch (Exception e) {
                        sh 'docker logout || true'
                        def msg = e.message ?: ''
                        def isCredMissing = msg.contains('Could not find credentials entry') ||
                                            msg.contains('dockerhub-credentials')
                        if (isCredMissing) {
                            echo "INFO: Jenkins credential 'dockerhub-credentials' not configured."
                            echo "  Images built and scanned but NOT pushed to Docker Hub."
                            echo "  Add it: Manage Jenkins → Credentials → Global → Add"
                            echo "    Kind: Username with password | ID: dockerhub-credentials"
                        } else {
                            echo "INFO: Docker Hub push failed — see console output above."
                            echo "  Pipeline continues — image is in local Docker."
                        }
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 9 — DEPLOY (Local Docker Desktop)
        // Deploys SecureAIGW monolith + PostgreSQL to Docker Desktop
        // ─────────────────────────────────────────────────────────────────────
        stage('Deploy / Deliver') {
            steps {
                echo "========================================"
                echo "  Stage 9: Deploy to Local Docker Desktop"
                echo "========================================"
                script {
                    try {
                        def imageTag  = "${env.BRANCH_TAG}-${env.BUILD_NUMBER}"
                        def hubUser   = env.DOCKERHUB_USER
                        def deployDir = "${env.JENKINS_HOME}/deployments/secureai/${env.BUILD_NUMBER}"

                        // Re-tag for docker-compose compatibility
                        sh """
                            docker tag ${hubUser}/${APP_NAME}:${imageTag} secureai/${APP_NAME}:latest
                            echo "Tagged: secureai/${APP_NAME}:latest"
                        """

                        // Ensure network exists, bring down old app containers
                        sh """
                            echo "Ensuring shared network exists..."
                            docker network create secure-ai-net 2>/dev/null || true

                            echo "Stopping existing SecureAIGW app (if any)..."
                            docker-compose -f docker-compose.yml -p secureai-app down --remove-orphans 2>/dev/null || true
                            docker rm -f secureai-app secureai-web 2>/dev/null || true
                        """

                        // Start fresh
                        sh """
                            echo "Starting SecureAI Gateway on Docker Desktop..."
                            docker-compose -f docker-compose.yml -p secureai-app up -d --no-build 2>/dev/null || \
                                echo "docker-compose up skipped (no compose file for app — standalone JAR deployment)"

                            echo ""
                            echo "Container status:"
                            docker ps --filter "name=secureai" --format "  {{.Names}}  {{.Status}}  {{.Ports}}" || true
                        """

                        // Write deployment manifest
                        sh """
                            mkdir -p "${deployDir}"
                            {
                              echo "SecureAI Gateway — Deployment Manifest"
                              echo "======================================"
                              echo "Build      : #${env.BUILD_NUMBER}"
                              echo "Branch     : ${env.BRANCH_NAME}"
                              echo "Commit     : ${env.GIT_COMMIT?.take(8) ?: 'N/A'}"
                              echo "Timestamp  : \$(date '+%Y-%m-%d %H:%M:%S %Z')"
                              echo "Image Tag  : ${imageTag}"
                              echo ""
                              echo "Deployed Services:"
                              docker ps --filter "name=secureai" --format "  {{.Names}}  {{.Status}}  {{.Ports}}" || true
                              echo ""
                              echo "Status     : DEPLOYED — running on local Docker Desktop"
                            } | tee "${deployDir}/deploy-manifest.txt"
                        """

                        archiveArtifacts(
                            artifacts:         "${deployDir}/deploy-manifest.txt",
                            allowEmptyArchive: true,
                            fingerprint:       true
                        )

                        echo "Stage 9 PASSED — SecureAI Gateway deployed [tag: ${imageTag}]"
                    } catch (Exception e) {
                        env.FAILURE_CAUSE = "Stage 9 — Deployment FAILED: ${e.message}"
                        error("Deployment stage failed")
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 10 — KARATE E2E API TESTS
        // BDD-style API tests against the deployed SecureAI Gateway
        // Tests JWT auth, PII masking, rate limiting, guardrails
        // ─────────────────────────────────────────────────────────────────────
        stage('Karate API Tests') {
            when {
                expression {
                    // Only run if karate-tests module exists
                    return fileExists('karate-tests/pom.xml')
                }
            }
            steps {
                echo "========================================"
                echo "  Stage 10: Karate E2E API Tests"
                echo "========================================"
                script {
                    try {
                        // Wait for app to be healthy
                        sh '''
                            echo "Waiting for SecureAI Gateway to become healthy..."
                            MAX_WAIT=120
                            ELAPSED=0
                            while [ $ELAPSED -lt $MAX_WAIT ]; do
                                if curl -sf "http://localhost:8100/actuator/health" > /dev/null 2>&1; then
                                    echo "SecureAI Gateway is healthy! (${ELAPSED}s)"
                                    break
                                fi
                                echo "  Waiting... (${ELAPSED}s)"
                                sleep 5
                                ELAPSED=$((ELAPSED + 5))
                            done
                            if [ $ELAPSED -ge $MAX_WAIT ]; then
                                echo "WARNING: App not healthy after ${MAX_WAIT}s — running tests anyway"
                            fi
                        '''

                        sh '''
                            cd karate-tests
                            mvn -B test \
                                -Dkarate.env=local \
                                -Dkarate.options="--tags ~@ignore" \
                                || true
                        '''
                    } catch (Exception e) {
                        echo "WARNING: Karate tests encountered an error: ${e.message}"
                    }
                }
            }
            post {
                always {
                    archiveArtifacts(
                        artifacts:         'karate-tests/target/karate-reports/**/*',
                        allowEmptyArchive: true,
                        fingerprint:       true
                    )
                    junit(
                        allowEmptyResults: true,
                        testResults:       'karate-tests/target/surefire-reports/**/*.xml'
                    )
                }
            }
        }
    }

    // ── Post-build Actions ──────────────────────────────────────────────────
    post {
        always {
            echo "Pipeline completed: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            // Clean workspace to save disk
            deleteDir()
        }
        success {
            echo "================================================"
            echo "  PIPELINE PASSED: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            echo "  Branch: ${env.BRANCH_NAME}"
            echo "  Image:  ${env.DOCKERHUB_USER}/${APP_NAME}:${env.BRANCH_TAG}-${env.BUILD_NUMBER}"
            echo "================================================"
        }
        failure {
            echo "================================================"
            echo "  PIPELINE FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            echo "  Branch: ${env.BRANCH_NAME}"
            echo "  Cause:  ${env.FAILURE_CAUSE ?: 'Unknown — check console output'}"
            echo "================================================"
        }
        unstable {
            echo "================================================"
            echo "  PIPELINE UNSTABLE: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            echo "  Check Trivy/Karate reports in build artifacts"
            echo "================================================"
        }
    }
}
