// ═══════════════════════════════════════════════════════════════════════════
// Jenkinsfile — Secure AI Gateway
// Multi-Branch Pipeline · 12-Stage DevSecOps CI/CD
//
// Stages:
//  1.  Checkout         — Fetch source, set build metadata
//  2.  Compile          — Maven compile + validate
//  3.  Unit Tests       — JUnit 5 via maven-surefire
//  4.  JaCoCo Coverage  — Code coverage report (70% minimum)
//  5.  SonarQube        — Static analysis + Quality Gate
//  6.  OWASP CVE        — Dependency vulnerability scan (fail on HIGH)
//  7.  SpotBugs         — FindSecBugs security static analysis
//  8.  FAT JAR Build    — spring-boot:repackage
//  9.  Docker Build     — Multi-stage Dockerfile
// 10.  Trivy Scan       — Container image CVE scan
// 11.  Deploy Dev       — kubectl apply to dev namespace
// 12.  Integration Test — Smoke tests against dev
// 13.  Deploy Prod      — kubectl apply to prod (main branch only)
// ═══════════════════════════════════════════════════════════════════════════

pipeline {
    agent {
        docker {
            image 'maven:3.9.6-eclipse-temurin-17'
            args '-v /root/.m2:/root/.m2 --network host'
        }
    }

    environment {
        APP_NAME        = 'secure-ai-gateway'
        APP_VERSION     = "${env.BUILD_NUMBER}"
        DOCKER_IMAGE    = "your-dockerhub-username/${APP_NAME}"
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
        // STAGE 2: Compile
        // ────────────────────────────────────────────────────
        stage('Compile') {
            steps {
                sh 'mvn -B clean compile -DskipTests'
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 3: Unit Tests
        // ────────────────────────────────────────────────────
        stage('Unit Tests') {
            steps {
                sh 'mvn -B test -Dspring.profiles.active=test'
            }
            post {
                always {
                    junit 'target/surefire-reports/**/*.xml'
                    publishHTML(target: [
                        reportDir: 'target/surefire-reports',
                        reportFiles: 'index.html',
                        reportName: 'Unit Test Report'
                    ])
                }
                failure {
                    slackSend(color: 'danger',
                        message: "❌ Unit tests failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}")
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 4: JaCoCo Coverage
        // ────────────────────────────────────────────────────
        stage('JaCoCo Coverage') {
            steps {
                sh 'mvn -B jacoco:report'
            }
            post {
                always {
                    jacoco(
                        execPattern: 'target/jacoco.exec',
                        classPattern: 'target/classes',
                        sourcePattern: 'src/main/java',
                        exclusionPattern: '**/model/**,**/config/**',
                        minimumInstructionCoverage: '70',
                        minimumBranchCoverage: '60',
                        minimumLineCoverage: '70'
                    )
                    publishHTML(target: [
                        reportDir: 'target/site/jacoco',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Coverage Report'
                    ])
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 5: SonarQube Analysis
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
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                            -Dsonar.java.binaries=target/classes \
                            -Dsonar.sources=src/main/java \
                            -Dsonar.tests=src/test/java
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
                        message: "❌ SonarQube Quality Gate FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}")
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 7: OWASP Dependency-Check
        // ────────────────────────────────────────────────────
        stage('OWASP CVE Check') {
            steps {
                sh """
                    mvn -B dependency-check:check \
                        -Ddependency-check.failBuildOnCVSS=7 \
                        -Ddependency-check.format=ALL
                """
            }
            post {
                always {
                    dependencyCheckPublisher pattern: 'target/dependency-check-report.xml'
                    publishHTML(target: [
                        reportDir: 'target',
                        reportFiles: 'dependency-check-report.html',
                        reportName: 'OWASP Dependency Check'
                    ])
                }
                failure {
                    slackSend(color: 'danger',
                        message: "🚨 OWASP CVE scan FAILED (HIGH severity CVEs found): ${env.JOB_NAME}")
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 8: SpotBugs
        // ────────────────────────────────────────────────────
        stage('SpotBugs Analysis') {
            steps {
                sh 'mvn -B spotbugs:check'
            }
            post {
                always {
                    recordIssues(
                        tools: [spotBugs(pattern: 'target/spotbugsXml.xml')],
                        qualityGates: [[threshold: 1, type: 'TOTAL_HIGH', unstable: true]]
                    )
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 9: Build FAT JAR
        // ────────────────────────────────────────────────────
        stage('Build FAT JAR') {
            steps {
                sh 'mvn -B package -DskipTests -Pprod'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 10: Docker Build & Push
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
        // STAGE 11: Trivy Container Scan
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
                        message: "🚨 Trivy found CRITICAL container vulnerabilities: ${env.JOB_NAME}")
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 12: Deploy to Dev
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
        // STAGE 13: Integration Tests (Dev)
        // ────────────────────────────────────────────────────
        stage('Integration Tests') {
            steps {
                sh """
                    mvn -B failsafe:integration-test failsafe:verify \
                        -Dspring.profiles.active=test \
                        -Dintegration.base-url=http://secure-ai-gateway.secure-ai-dev.svc.cluster.local:8080
                """
            }
            post {
                always {
                    junit 'target/failsafe-reports/**/*.xml'
                }
            }
        }

        // ────────────────────────────────────────────────────
        // STAGE 14: Deploy Prod (main branch only)
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
                        message: "✅ ${APP_NAME} v${DOCKER_TAG} deployed to PRODUCTION successfully!")
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
                message: "✅ Pipeline PASSED: ${env.JOB_NAME} #${env.BUILD_NUMBER} (${env.BRANCH_NAME})")
        }
        failure {
            slackSend(color: 'danger',
                message: "❌ Pipeline FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER} (${env.BRANCH_NAME})")
            emailext(
                subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: "Pipeline failed. Check: ${env.BUILD_URL}",
                recipientProviders: [[$class: 'DevelopersRecipientProvider']]
            )
        }
        unstable {
            slackSend(color: 'warning',
                message: "⚠️ Pipeline UNSTABLE: ${env.JOB_NAME} #${env.BUILD_NUMBER}")
        }
    }
}
