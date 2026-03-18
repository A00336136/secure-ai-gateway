// ═══════════════════════════════════════════════════════════════════════════
// Jenkinsfile — Secure AI Gateway (Multi-Module Maven)
// Multi-Branch Pipeline · 15-Stage DevSecOps CI/CD
//
// Module Build Order: secure-ai-model → secure-ai-core →
//                     secure-ai-service → secure-ai-web (FAT JAR)
//
// Test Pyramid:
//   L1: Unit Tests          — JUnit 5 + Mockito (*Test.java via Surefire)
//   L2: Smoke Tests         — Context + Endpoints (*SmokeTest.java via Failsafe)
//   L3: Performance Tests   — JWT/PII/RateLimiter throughput (*PerfTest.java)
//   L4: Integration Tests   — E2E security flows (*IT.java via Failsafe)
//
// DevSecOps Chain:
//   JaCoCo (80% line) → SonarQube + Quality Gate → OWASP (CVSS 7+) →
//   SpotBugs + FindSecBugs → Trivy (HIGH/CRITICAL) → Alpine Linux
// ═══════════════════════════════════════════════════════════════════════════

pipeline {
    agent {
        docker {
            image 'maven:3.9.9-eclipse-temurin-21-alpine'
            args '-v /root/.m2:/root/.m2 --network host'
        }
    }

    environment {
        APP_NAME        = 'secure-ai-gateway'
        APP_VERSION     = "${env.BUILD_NUMBER}"
        DOCKER_IMAGE    = "a00336136/${APP_NAME}"
        DOCKER_TAG      = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"
        SONAR_URL       = 'http://sonarqube:9000'
        SONAR_TOKEN     = credentials('sonarqube-token')
        DOCKER_CREDS    = credentials('dockerhub-credentials')
        KUBE_CONFIG     = credentials('kubeconfig')
        JWT_SECRET      = credentials('jwt-secret')
    }

    options {
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        ansiColor('xterm')
    }

    stages {

        // ────────────────────────────────────────────────────
        // STAGE 1: Checkout
        // ────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_MSG  = sh(script: 'git log -1 --pretty=%B', returnStdout: true).trim()
                    env.GIT_AUTHOR      = sh(script: 'git log -1 --pretty=%an', returnStdout: true).trim()
                    env.BRANCH_NAME_ENV = env.BRANCH_NAME ?: 'unknown'
                }
                echo "Branch: ${env.BRANCH_NAME_ENV}"
                echo "Commit: ${env.GIT_COMMIT}"
                echo "Author: ${env.GIT_AUTHOR}"
                echo "Message: ${env.GIT_COMMIT_MSG}"
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 2: Install Multi-Module (Parent + 4 Children)
        // Build order: model → core → service → web
        // ────────────────────────────────────────────────────
        stage('Install Modules') {
            steps {
                sh 'mvn -B clean install -DskipTests'
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 3: Unit Tests (Test Pyramid — Layer 1)
        // Surefire runs *Test.java across ALL 4 modules
        // ────────────────────────────────────────────────────
        stage('Unit Tests') {
            steps {
                sh 'mvn -B test -Dspring.profiles.active=test'
            }
            post {
                always {
                    junit '**/target/surefire-reports/**/*.xml'
                }
                failure {
                    slackSend(color: 'danger',
                        message: "Unit tests failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}")
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 4: JaCoCo Code Coverage (80% line, 70% branch)
        // Aggregate report from all 4 modules
        // ────────────────────────────────────────────────────
        stage('JaCoCo Coverage') {
            steps {
                sh 'mvn -B jacoco:report -pl secure-ai-web jacoco:report-aggregate'
            }
            post {
                always {
                    jacoco(
                        execPattern: '**/target/jacoco.exec',
                        classPattern: '**/target/classes',
                        sourcePattern: '**/src/main/java',
                        exclusionPattern: '**/model/**,**/config/**',
                        minimumInstructionCoverage: '80',
                        minimumBranchCoverage: '70',
                        minimumLineCoverage: '80'
                    )
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 5: SonarQube Analysis (multi-module)
        // ────────────────────────────────────────────────────
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh """
                        mvn -B sonar:sonar \
                            -Dsonar.projectKey=${APP_NAME} \
                            -Dsonar.projectName='Secure AI Gateway' \
                            -Dsonar.host.url=${SONAR_URL} \
                            -Dsonar.token=${SONAR_TOKEN} \
                            -Dsonar.coverage.jacoco.xmlReportPaths=secure-ai-web/target/site/jacoco-aggregate/jacoco.xml
                    """
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 6: SonarQube Quality Gate
        // ────────────────────────────────────────────────────
        stage('Quality Gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
            post {
                failure {
                    slackSend(color: 'danger',
                        message: "SonarQube Quality Gate FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}")
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 7: OWASP Dependency-Check (CVE CVSS >= 7)
        // ────────────────────────────────────────────────────
        stage('OWASP CVE Check') {
            steps {
                sh """
                    mvn -B dependency-check:aggregate \
                        -Ddependency-check.failBuildOnCVSS=7 \
                        -Ddependency-check.format=ALL
                """
            }
            post {
                always {
                    dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
                    publishHTML(target: [
                        reportDir: 'target',
                        reportFiles: 'dependency-check-report.html',
                        reportName: 'OWASP Dependency Check'
                    ])
                }
                failure {
                    slackSend(color: 'danger',
                        message: "OWASP CVE scan FAILED (HIGH severity CVEs found): ${env.JOB_NAME}")
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 8: SpotBugs + FindSecBugs (SAST)
        // ────────────────────────────────────────────────────
        stage('SpotBugs Analysis') {
            steps {
                sh 'mvn -B spotbugs:check -PdevSecOps'
            }
            post {
                always {
                    recordIssues(
                        tools: [spotBugs(pattern: '**/spotbugsXml.xml')],
                        qualityGates: [[threshold: 1, type: 'TOTAL_HIGH', unstable: true]]
                    )
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 9: Smoke Tests (Test Pyramid — Layer 2)
        // Failsafe runs *SmokeTest.java in secure-ai-web
        // ────────────────────────────────────────────────────
        stage('Smoke Tests') {
            steps {
                sh 'mvn -B failsafe:integration-test -pl secure-ai-web -Dgroups=smoke -Dspring.profiles.active=test'
            }
            post {
                always {
                    junit '**/target/failsafe-reports/**/*.xml'
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 10: Build FAT JAR (secure-ai-web module)
        // ────────────────────────────────────────────────────
        stage('Build FAT JAR') {
            steps {
                sh 'mvn -B package -DskipTests -pl secure-ai-web -am'
                archiveArtifacts artifacts: 'secure-ai-web/target/*.jar', fingerprint: true
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 11: Docker Build & Push (Alpine Linux)
        // ────────────────────────────────────────────────────
        stage('Docker Build & Push') {
            agent { label 'docker' }
            steps {
                script {
                    docker.withRegistry('https://registry.hub.docker.com', 'dockerhub-credentials') {
                        def image = docker.build("${DOCKER_IMAGE}:${DOCKER_TAG}")
                        image.push()
                        image.push('latest')
                        if (env.BRANCH_NAME == 'main') {
                            image.push("stable")
                        }
                    }
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 12: Trivy Container Scan (Alpine Linux)
        // Exit code 1 on HIGH/CRITICAL CVEs
        // ────────────────────────────────────────────────────
        stage('Trivy Container Scan') {
            agent { label 'docker' }
            steps {
                sh """
                    trivy image \
                        --exit-code 1 \
                        --severity HIGH,CRITICAL \
                        --format sarif \
                        --output trivy-results.sarif \
                        ${DOCKER_IMAGE}:${DOCKER_TAG}
                """
            }
            post {
                always {
                    recordIssues(tools: [sarif(pattern: 'trivy-results.sarif')])
                    publishHTML(target: [
                        reportDir: '.',
                        reportFiles: 'trivy-results.sarif',
                        reportName: 'Trivy Security Report'
                    ])
                }
                failure {
                    slackSend(color: 'danger',
                        message: "Trivy found CRITICAL container vulnerabilities: ${env.JOB_NAME}")
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 13: Deploy to Dev (Kubernetes/Minikube)
        // ────────────────────────────────────────────────────
        stage('Deploy Dev') {
            steps {
                withKubeConfig([credentialsId: 'kubeconfig']) {
                    sh """
                        kubectl set image deployment/${APP_NAME} \
                            ${APP_NAME}=${DOCKER_IMAGE}:${DOCKER_TAG} \
                            -n secure-ai-dev
                        kubectl rollout status deployment/${APP_NAME} \
                            -n secure-ai-dev --timeout=120s
                    """
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 14: Integration Tests (Test Pyramid — Layer 4)
        // Failsafe runs *IT.java against Dev deployment
        // ────────────────────────────────────────────────────
        stage('Integration Tests') {
            steps {
                sh """
                    mvn -B failsafe:integration-test failsafe:verify \
                        -pl secure-ai-web \
                        -Dspring.profiles.active=test
                """
            }
            post {
                always {
                    junit '**/target/failsafe-reports/**/*.xml'
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 15: Deploy Prod (main branch only + approval)
        // ────────────────────────────────────────────────────
        stage('Deploy Prod') {
            when {
                branch 'main'
            }
            input {
                message "Deploy to production?"
                ok "Deploy"
                submitter "admin,devops-lead"
            }
            steps {
                withKubeConfig([credentialsId: 'kubeconfig']) {
                    sh """
                        kubectl set image deployment/${APP_NAME} \
                            ${APP_NAME}=${DOCKER_IMAGE}:${DOCKER_TAG} \
                            -n secure-ai-prod
                        kubectl rollout status deployment/${APP_NAME} \
                            -n secure-ai-prod --timeout=300s
                    """
                }
            }
            post {
                success {
                    slackSend(color: 'good',
                        message: "${APP_NAME} v${DOCKER_TAG} deployed to PRODUCTION successfully!")
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            slackSend(color: 'good',
                message: "Pipeline PASSED: ${env.JOB_NAME} #${env.BUILD_NUMBER} (${env.BRANCH_NAME})")
        }
        failure {
            slackSend(color: 'danger',
                message: "Pipeline FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER} (${env.BRANCH_NAME})")
            emailext(
                subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: "Pipeline failed. Check: ${env.BUILD_URL}",
                recipientProviders: [[$class: 'DevelopersRecipientProvider']]
            )
        }
        unstable {
            slackSend(color: 'warning',
                message: "Pipeline UNSTABLE: ${env.JOB_NAME} #${env.BUILD_NUMBER}")
        }
    }
}
