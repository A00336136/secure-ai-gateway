# Secure AI Gateway - Complete Red Hat DevSecOps Setup Guide

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Detailed Setup Instructions](#detailed-setup-instructions)
4. [CLI Commands Reference](#cli-commands-reference)
5. [GUI Dashboard Configuration](#gui-dashboard-configuration)
6. [Security Configuration](#security-configuration)
7. [CI/CD Pipeline Setup](#cicd-pipeline-setup)
8. [Monitoring and Observability](#monitoring-and-observability)
9. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Tools
```bash
# OpenShift CLI
curl -LO https://mirror.openshift.com/pub/openshift-v4/clients/ocp/stable/openshift-client-linux.tar.gz
tar -xzf openshift-client-linux.tar.gz
sudo mv oc kubectl /usr/local/bin/

# Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Tekton CLI (tkn)
curl -LO https://github.com/tektoncd/cli/releases/download/v0.32.0/tkn_0.32.0_Linux_x86_64.tar.gz
tar xvzf tkn_0.32.0_Linux_x86_64.tar.gz
sudo mv tkn /usr/local/bin/

# ArgoCD CLI
curl -sSL -o argocd-linux-amd64 https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
sudo install -m 555 argocd-linux-amd64 /usr/local/bin/argocd
```

### OpenShift Cluster Requirements
- OpenShift 4.10 or later
- Cluster admin access
- Red Hat OpenShift Platform Plus subscription (or evaluation)
- Minimum 3 worker nodes with 8GB RAM each

### Red Hat Operators Required
- OpenShift Pipelines (Tekton)
- OpenShift GitOps (ArgoCD)
- Red Hat Advanced Cluster Security for Kubernetes (optional but recommended)
- Red Hat Quay (optional - can use quay.io)

---

## Quick Start

### Option 1: Automated Setup (Recommended)
```bash
# Clone the repository
git clone https://github.com/your-org/secure-ai-gateway.git
cd secure-ai-gateway

# Login to OpenShift
oc login https://api.your-cluster.example.com:6443

# Make setup script executable
chmod +x scripts/setup.sh

# Run complete setup
./scripts/setup.sh
```

### Option 2: Helm Deployment
```bash
# Login to OpenShift
oc login https://api.your-cluster.example.com:6443

# Create namespace
oc create namespace secure-ai-gateway

# Install with Helm
helm install secure-ai-gateway ./helm \
  --namespace secure-ai-gateway \
  --set secrets.jwtSecret="$(openssl rand -base64 32)" \
  --set secrets.adminPassword="YourSecurePassword123!"
```

---

## Detailed Setup Instructions

### Step 1: Login to OpenShift Cluster

```bash
# Login via CLI
oc login https://api.your-cluster.example.com:6443 \
  --username=your-username \
  --password=your-password

# Verify cluster access
oc whoami
oc cluster-info

# Check cluster version
oc get clusterversion
```

**GUI Access:**
1. Open browser to: `https://console-openshift-console.apps.your-cluster.example.com`
2. Login with your credentials
3. Verify you're in the Administrator perspective

---

### Step 2: Create Project Namespaces

#### CLI Method:
```bash
# Create development namespace
oc new-project secure-ai-gateway-dev \
  --display-name="Secure AI Gateway - Development" \
  --description="Development environment for Secure AI Gateway"

# Create production namespace
oc new-project secure-ai-gateway-prod \
  --display-name="Secure AI Gateway - Production" \
  --description="Production environment for Secure AI Gateway"

# Create CI/CD namespace
oc new-project secure-ai-gateway-cicd \
  --display-name="Secure AI Gateway - CI/CD" \
  --description="CI/CD pipelines for Secure AI Gateway"

# Label namespaces for monitoring
oc label namespace secure-ai-gateway-dev \
  app=secure-ai-gateway environment=dev

oc label namespace secure-ai-gateway-prod \
  app=secure-ai-gateway environment=prod

oc label namespace secure-ai-gateway-cicd \
  app=secure-ai-gateway environment=cicd
```

#### GUI Method:
1. Navigate to: **Home → Projects**
2. Click **Create Project**
3. Fill in details:
   - Name: `secure-ai-gateway-dev`
   - Display Name: `Secure AI Gateway - Development`
   - Description: `Development environment for Secure AI Gateway`
4. Click **Create**
5. Repeat for `secure-ai-gateway-prod` and `secure-ai-gateway-cicd`

---

### Step 3: Install Required Operators

#### Install OpenShift Pipelines (Tekton)

**CLI Method:**
```bash
cat <<EOF | oc apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: openshift-pipelines-operator
  namespace: openshift-operators
spec:
  channel: latest
  name: openshift-pipelines-operator-rh
  source: redhat-operators
  sourceNamespace: openshift-marketplace
  installPlanApproval: Automatic
EOF

# Wait for operator to be ready
oc get csv -n openshift-operators | grep openshift-pipelines

# Verify installation
tkn version
```

**GUI Method:**
1. Navigate to: **Operators → OperatorHub**
2. Search for "OpenShift Pipelines"
3. Click on "Red Hat OpenShift Pipelines"
4. Click **Install**
5. Select:
   - Update Channel: `latest`
   - Installation Mode: `All namespaces on the cluster`
   - Approval Strategy: `Automatic`
6. Click **Install**
7. Wait for installation to complete (Status: Succeeded)

#### Install OpenShift GitOps (ArgoCD)

**CLI Method:**
```bash
cat <<EOF | oc apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: openshift-gitops-operator
  namespace: openshift-operators
spec:
  channel: latest
  name: openshift-gitops-operator
  source: redhat-operators
  sourceNamespace: openshift-marketplace
  installPlanApproval: Automatic
EOF

# Wait for operator
sleep 60

# Get ArgoCD route
oc get route openshift-gitops-server -n openshift-gitops

# Get admin password
oc extract secret/openshift-gitops-cluster \
  -n openshift-gitops \
  --to=- \
  --keys=admin.password
```

**GUI Method:**
1. Navigate to: **Operators → OperatorHub**
2. Search for "OpenShift GitOps"
3. Click on "Red Hat OpenShift GitOps"
4. Click **Install**
5. Accept defaults and click **Install**
6. Wait for installation complete

**Access ArgoCD Dashboard:**
1. Navigate to: **Networking → Routes** (in openshift-gitops namespace)
2. Click on `openshift-gitops-server` route URL
3. Login with:
   - Username: `admin`
   - Password: (retrieve from secret as shown in CLI method)

#### Install Red Hat Advanced Cluster Security (Optional)

**CLI Method:**
```bash
# Install ACS Operator
cat <<EOF | oc apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: rhacs-operator
  namespace: openshift-operators
spec:
  channel: stable
  name: rhacs-operator
  source: redhat-operators
  sourceNamespace: openshift-marketplace
EOF

# Create namespace for ACS
oc new-project stackrox

# Create Central instance
cat <<EOF | oc apply -f -
apiVersion: platform.stackrox.io/v1alpha1
kind: Central
metadata:
  name: stackrox-central-services
  namespace: stackrox
spec:
  central:
    exposure:
      route:
        enabled: true
  egress:
    connectivityPolicy: Online
  scanner:
    analyzer:
      scaling:
        autoScaling: Enabled
        maxReplicas: 5
        minReplicas: 2
        replicas: 3
EOF
```

---

### Step 4: Create Secrets and ConfigMaps

#### Generate JWT Secret
```bash
# Generate secure JWT secret (256 bits)
JWT_SECRET=$(openssl rand -base64 32)
echo "Generated JWT Secret: $JWT_SECRET"
```

#### Create Application Secrets

**CLI Method:**
```bash
# For Development
oc create secret generic secure-ai-gateway-secrets \
  --from-literal=ADMIN_USERNAME=admin \
  --from-literal=ADMIN_PASSWORD='DevPassword123!' \
  --from-literal=JWT_SECRET="$JWT_SECRET" \
  --from-literal=DB_USERNAME=sa \
  --from-literal=DB_PASSWORD='' \
  -n secure-ai-gateway-dev

# For Production
oc create secret generic secure-ai-gateway-secrets \
  --from-literal=ADMIN_USERNAME=admin \
  --from-literal=ADMIN_PASSWORD='ProdPassword123!@#' \
  --from-literal=JWT_SECRET="$JWT_SECRET" \
  --from-literal=DB_USERNAME=sa \
  --from-literal=DB_PASSWORD='ProductionDBPassword' \
  -n secure-ai-gateway-prod
```

**GUI Method:**
1. Navigate to: **Workloads → Secrets** (in your namespace)
2. Click **Create → Key/value secret**
3. Fill in:
   - Secret name: `secure-ai-gateway-secrets`
   - Key: `ADMIN_USERNAME`, Value: `admin`
   - Add key: `ADMIN_PASSWORD`, Value: (your password)
   - Add key: `JWT_SECRET`, Value: (generated secret)
4. Click **Create**

#### Create Quay Registry Credentials

```bash
# Create docker-registry secret for Quay
oc create secret docker-registry quay-credentials \
  --docker-server=quay.io \
  --docker-username=your-quay-username \
  --docker-password=your-quay-password \
  --docker-email=your-email@example.com \
  -n secure-ai-gateway-cicd

# Link secret to pipeline service account
oc secrets link pipeline quay-credentials -n secure-ai-gateway-cicd
```

#### Apply ConfigMaps

```bash
# Apply ConfigMap
oc apply -f kubernetes/configmap.yaml -n secure-ai-gateway-dev
oc apply -f kubernetes/configmap.yaml -n secure-ai-gateway-prod
```

---

### Step 5: Apply Security Policies

#### Apply Security Context Constraints (SCC)

```bash
# Create custom SCC
oc apply -f security/scc.yaml

# Grant SCC to service account
oc adm policy add-scc-to-user secure-ai-gateway-scc \
  -z secure-ai-gateway-sa \
  -n secure-ai-gateway-dev

oc adm policy add-scc-to-user secure-ai-gateway-scc \
  -z secure-ai-gateway-sa \
  -n secure-ai-gateway-prod

# Verify SCC
oc get scc secure-ai-gateway-scc
```

#### Apply Network Policies

```bash
# Apply network policies
oc apply -f security/network-policy.yaml -n secure-ai-gateway-dev
oc apply -f security/network-policy.yaml -n secure-ai-gateway-prod

# Verify network policies
oc get networkpolicies -n secure-ai-gateway-dev
```

#### Apply Resource Quotas and Limits

```bash
# Apply resource quotas
oc apply -f security/resource-limits.yaml -n secure-ai-gateway-dev
oc apply -f security/resource-limits.yaml -n secure-ai-gateway-prod

# Verify quotas
oc get resourcequota -n secure-ai-gateway-dev
oc get limitrange -n secure-ai-gateway-dev
```

---

### Step 6: Build and Push Container Image

#### Build with Docker/Podman

```bash
# Build image
docker build -t quay.io/secure-ai/secure-ai-gateway:1.0.0 .

# Login to Quay
docker login quay.io

# Push image
docker push quay.io/secure-ai/secure-ai-gateway:1.0.0

# Tag as latest
docker tag quay.io/secure-ai/secure-ai-gateway:1.0.0 \
  quay.io/secure-ai/secure-ai-gateway:latest

docker push quay.io/secure-ai/secure-ai-gateway:latest
```

#### Build with OpenShift BuildConfig

```bash
# Create build config
oc new-build --name=secure-ai-gateway \
  --binary=true \
  --strategy=docker \
  -n secure-ai-gateway-cicd

# Start build
oc start-build secure-ai-gateway \
  --from-dir=. \
  --follow \
  -n secure-ai-gateway-cicd

# Tag and push to Quay (if needed)
```

---

### Step 7: Deploy Application

#### Deploy to Development

```bash
# Create RBAC
oc apply -f kubernetes/rbac.yaml -n secure-ai-gateway-dev

# Deploy application
oc apply -f kubernetes/deployment.yaml -n secure-ai-gateway-dev
oc apply -f kubernetes/service.yaml -n secure-ai-gateway-dev
oc apply -f kubernetes/route.yaml -n secure-ai-gateway-dev

# Wait for deployment
oc rollout status deployment/secure-ai-gateway -n secure-ai-gateway-dev

# Get application URL
oc get route secure-ai-gateway -n secure-ai-gateway-dev
```

**GUI Method:**
1. Navigate to: **Workloads → Deployments**
2. Click **Create Deployment**
3. Choose "From YAML" and paste deployment.yaml content
4. Click **Create**
5. Monitor rollout in **Workloads → Pods**

---

### Step 8: Setup Tekton CI/CD Pipeline

#### Install Pipeline Resources

```bash
# Apply custom tasks
oc apply -f tekton/tasks.yaml -n secure-ai-gateway-cicd

# Apply pipeline
oc apply -f tekton/pipeline.yaml -n secure-ai-gateway-cicd

# Create pipeline service account with permissions
oc create serviceaccount pipeline -n secure-ai-gateway-cicd

# Grant permissions
oc adm policy add-scc-to-user privileged \
  -z pipeline -n secure-ai-gateway-cicd

oc adm policy add-role-to-user edit \
  system:serviceaccount:secure-ai-gateway-cicd:pipeline \
  -n secure-ai-gateway-dev

oc adm policy add-role-to-user edit \
  system:serviceaccount:secure-ai-gateway-cicd:pipeline \
  -n secure-ai-gateway-prod
```

#### Run Pipeline Manually

```bash
# Create a pipeline run
tkn pipeline start secure-ai-gateway-pipeline \
  -n secure-ai-gateway-cicd \
  -p git-url=https://github.com/your-org/secure-ai-gateway.git \
  -p git-revision=main \
  -p image-name=quay.io/secure-ai/secure-ai-gateway \
  -p image-tag=1.0.0 \
  --use-param-defaults \
  --workspace name=shared-workspace,claimName=pipeline-pvc \
  --showlog

# Watch pipeline run
tkn pipelinerun logs -f -n secure-ai-gateway-cicd

# List pipeline runs
tkn pipelinerun list -n secure-ai-gateway-cicd
```

**GUI Method - OpenShift Pipelines:**
1. Navigate to: **Pipelines** (in secure-ai-gateway-cicd namespace)
2. Click on `secure-ai-gateway-pipeline`
3. Click **Actions → Start**
4. Fill in parameters:
   - git-url: your repository
   - git-revision: main
   - image-name: quay.io/secure-ai/secure-ai-gateway
   - image-tag: 1.0.0
5. Click **Start**
6. Monitor progress in **PipelineRuns** tab

#### Setup Webhook for Automatic Triggers

```bash
# Get webhook URL
WEBHOOK_URL=$(oc get route el-secure-ai-gateway-listener \
  -n secure-ai-gateway-cicd \
  -o jsonpath='{.spec.host}')

echo "Webhook URL: https://$WEBHOOK_URL"

# Create webhook secret
oc create secret generic github-webhook-secret \
  --from-literal=secret=$(openssl rand -hex 20) \
  -n secure-ai-gateway-cicd
```

**Configure GitHub Webhook:**
1. Go to your GitHub repository
2. Navigate to **Settings → Webhooks**
3. Click **Add webhook**
4. Fill in:
   - Payload URL: `https://<webhook-url-from-above>`
   - Content type: `application/json`
   - Secret: (value from github-webhook-secret)
   - Events: Just the push event
5. Click **Add webhook**

---

### Step 9: Setup GitOps with ArgoCD

#### Create ArgoCD Applications

```bash
# Apply ArgoCD application manifests
oc apply -f gitops/argocd-application.yaml

# Login to ArgoCD CLI
ARGOCD_ROUTE=$(oc get route openshift-gitops-server \
  -n openshift-gitops \
  -o jsonpath='{.spec.host}')

ARGOCD_PASSWORD=$(oc extract secret/openshift-gitops-cluster \
  -n openshift-gitops \
  --to=- \
  --keys=admin.password)

argocd login $ARGOCD_ROUTE \
  --username admin \
  --password $ARGOCD_PASSWORD \
  --insecure

# Verify applications
argocd app list
argocd app get secure-ai-gateway-dev
```

**GUI Method - ArgoCD Dashboard:**
1. Access ArgoCD URL (from Step 3)
2. Login with admin credentials
3. Click **+ NEW APP**
4. Fill in:
   - Application Name: `secure-ai-gateway-dev`
   - Project: `default`
   - Sync Policy: `Automatic`
   - Repository URL: `https://github.com/your-org/secure-ai-gateway.git`
   - Revision: `HEAD`
   - Path: `kubernetes`
   - Cluster: `in-cluster`
   - Namespace: `secure-ai-gateway-dev`
5. Click **CREATE**

---

### Step 10: Setup Monitoring and Observability

#### Create ServiceMonitor for Prometheus

```bash
# Create ServiceMonitor
cat <<EOF | oc apply -f -
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: secure-ai-gateway
  namespace: secure-ai-gateway-dev
spec:
  endpoints:
  - interval: 30s
    path: /actuator/prometheus
    port: http
    scheme: http
  selector:
    matchLabels:
      app: secure-ai-gateway
EOF

# Verify ServiceMonitor
oc get servicemonitor -n secure-ai-gateway-dev
```

**Access Prometheus:**
```bash
# Get Prometheus route
oc get route prometheus-k8s -n openshift-monitoring
```

**Access Grafana:**
```bash
# Create Grafana instance (if not already installed)
# Access via OpenShift Console → Observe → Metrics
```

**GUI Method - OpenShift Console:**
1. Navigate to: **Observe → Metrics**
2. Enter query: `up{job="secure-ai-gateway"}`
3. Click **Run Queries**
4. Visualize metrics

#### Access Application Logs

**CLI Method:**
```bash
# View logs
oc logs -f deployment/secure-ai-gateway -n secure-ai-gateway-dev

# View all pods logs
oc logs -l app=secure-ai-gateway -n secure-ai-gateway-dev --tail=100
```

**GUI Method:**
1. Navigate to: **Workloads → Pods**
2. Click on a pod name
3. Click **Logs** tab
4. View real-time logs

---

## CLI Commands Reference

### Namespace Management
```bash
# List all projects
oc projects

# Switch project
oc project secure-ai-gateway-dev

# Get current project
oc project
```

### Deployment Management
```bash
# List deployments
oc get deployments -n secure-ai-gateway-dev

# Scale deployment
oc scale deployment/secure-ai-gateway --replicas=5 -n secure-ai-gateway-dev

# Edit deployment
oc edit deployment/secure-ai-gateway -n secure-ai-gateway-dev

# Rollout restart
oc rollout restart deployment/secure-ai-gateway -n secure-ai-gateway-dev

# Check rollout status
oc rollout status deployment/secure-ai-gateway -n secure-ai-gateway-dev

# Rollback deployment
oc rollout undo deployment/secure-ai-gateway -n secure-ai-gateway-dev
```

### Pod Management
```bash
# List pods
oc get pods -n secure-ai-gateway-dev

# Describe pod
oc describe pod <pod-name> -n secure-ai-gateway-dev

# Execute command in pod
oc exec -it <pod-name> -n secure-ai-gateway-dev -- /bin/bash

# Port forward
oc port-forward <pod-name> 8080:8080 -n secure-ai-gateway-dev
```

### Service and Route Management
```bash
# List services
oc get svc -n secure-ai-gateway-dev

# Get route URL
oc get route secure-ai-gateway -n secure-ai-gateway-dev \
  -o jsonpath='{.spec.host}'

# Create route
oc expose service secure-ai-gateway -n secure-ai-gateway-dev
```

### Security Commands
```bash
# List SecurityContextConstraints
oc get scc

# View SCC details
oc describe scc secure-ai-gateway-scc

# List network policies
oc get networkpolicies -n secure-ai-gateway-dev

# View network policy
oc describe networkpolicy <policy-name> -n secure-ai-gateway-dev

# Check pod security
oc get pod <pod-name> -n secure-ai-gateway-dev -o yaml | grep -A 10 securityContext
```

### Pipeline Commands (Tekton)
```bash
# List pipelines
tkn pipeline list -n secure-ai-gateway-cicd

# Start pipeline
tkn pipeline start secure-ai-gateway-pipeline \
  -n secure-ai-gateway-cicd \
  --showlog

# List pipeline runs
tkn pipelinerun list -n secure-ai-gateway-cicd

# View pipeline run logs
tkn pipelinerun logs <run-name> -f -n secure-ai-gateway-cicd

# Delete pipeline run
tkn pipelinerun delete <run-name> -n secure-ai-gateway-cicd

# List tasks
tkn task list -n secure-ai-gateway-cicd
```

### GitOps/ArgoCD Commands
```bash
# Login to ArgoCD
argocd login <argocd-url> --username admin

# List applications
argocd app list

# Get application details
argocd app get secure-ai-gateway-dev

# Sync application
argocd app sync secure-ai-gateway-dev

# View application logs
argocd app logs secure-ai-gateway-dev

# Delete application
argocd app delete secure-ai-gateway-dev
```

### Monitoring Commands
```bash
# View metrics
oc get --raw /apis/metrics.k8s.io/v1beta1/namespaces/secure-ai-gateway-dev/pods

# Top nodes
oc adm top nodes

# Top pods
oc adm top pods -n secure-ai-gateway-dev

# View events
oc get events -n secure-ai-gateway-dev --sort-by='.lastTimestamp'
```

---

## GUI Dashboard Configuration

### OpenShift Console

#### Access the Console
1. URL: `https://console-openshift-console.apps.your-cluster.example.com`
2. Login with credentials
3. Switch between Administrator and Developer perspectives

#### Developer Dashboard Setup
1. Click **Developer** perspective (top-left)
2. Select project: `secure-ai-gateway-dev`
3. View:
   - **Topology**: Visual representation of deployed resources
   - **Builds**: CI/CD builds and image streams
   - **Pipelines**: Tekton pipelines and runs
   - **Helm**: Helm chart deployments
   - **Project**: Overall project metrics

#### Administrator Dashboard
1. Click **Administrator** perspective
2. Navigate to:
   - **Workloads**: Deployments, Pods, StatefulSets, etc.
   - **Networking**: Services, Routes, Network Policies
   - **Storage**: PVCs, Storage Classes
   - **Monitoring**: Dashboards, Alerts, Metrics
   - **User Management**: Projects, Roles, Service Accounts

### ArgoCD Dashboard

#### Access and Login
1. URL: `https://openshift-gitops-server-openshift-gitops.apps.your-cluster.example.com`
2. Username: `admin`
3. Password: Retrieved from secret (see Step 3)

#### Application Management
1. **Applications**: View all managed applications
2. **Click on application** to see:
   - Sync status
   - Health status
   - Resource tree
   - Last sync details
3. **Actions**:
   - Sync: Deploy latest changes
   - Refresh: Check for new changes
   - Delete: Remove application
   - App Diff: View differences

#### Create Application
1. Click **+ NEW APP**
2. Configure:
   - **General**: Name, Project, Sync Policy
   - **Source**: Git repo, revision, path
   - **Destination**: Cluster, Namespace
   - **Sync Options**: Auto-sync, self-heal, prune
3. Click **CREATE**

### Tekton Dashboard

#### Access
1. Via OpenShift Console: **Pipelines** menu
2. Or standalone: `https://tekton-dashboard.apps.your-cluster.example.com`

#### Pipeline Management
1. **Pipelines Tab**: List all pipelines
2. **PipelineRuns Tab**: View pipeline executions
3. **Tasks Tab**: View available tasks
4. **Resources**: View pipeline resources

#### Trigger Pipeline
1. Select pipeline
2. Click **Actions → Start**
3. Fill parameters
4. Monitor execution in real-time

### Red Hat Advanced Cluster Security Dashboard

#### Access
1. URL: Retrieved from Central route
2. Login with generated credentials

#### Security Views
1. **Dashboard**: Overall security posture
2. **Violations**: Policy violations
3. **Risk**: Risk assessment of deployments
4. **Compliance**: Compliance status
5. **Network Graph**: Network traffic visualization
6. **Vulnerabilities**: CVE scanning results

#### Key Features
- **Policy Management**: Create and manage security policies
- **Image Scanning**: Scan container images for vulnerabilities
- **Runtime Protection**: Monitor and protect running containers
- **Network Policies**: Visualize and manage network policies

### Prometheus/Grafana

#### Access Prometheus
1. OpenShift Console → **Observe → Metrics**
2. Enter PromQL queries
3. Visualize metrics

#### Sample Queries
```promql
# Application availability
up{job="secure-ai-gateway"}

# HTTP request rate
rate(http_server_requests_seconds_count{job="secure-ai-gateway"}[5m])

# Error rate
rate(http_server_requests_seconds_count{job="secure-ai-gateway",status="500"}[5m])

# Memory usage
container_memory_usage_bytes{pod=~"secure-ai-gateway.*"}

# CPU usage
rate(container_cpu_usage_seconds_total{pod=~"secure-ai-gateway.*"}[5m])
```

### Logging (EFK Stack)

#### Access Kibana
1. OpenShift Console → **Observe → Logs**
2. Or access Kibana directly if installed

#### Create Dashboard
1. Search for application logs:
   ```
   kubernetes.namespace_name: "secure-ai-gateway-dev"
   ```
2. Filter by severity, pod, etc.
3. Create visualizations
4. Save dashboard

---

## Security Configuration

### Network Policies

**View Network Policies:**
```bash
oc get networkpolicy -n secure-ai-gateway-dev
oc describe networkpolicy secure-ai-gateway-network-policy -n secure-ai-gateway-dev
```

**Test Network Connectivity:**
```bash
# From within a pod
oc run test-pod --image=busybox -it --rm -- sh
# Inside pod:
wget -O- http://secure-ai-gateway:8080/actuator/health
```

### Pod Security

**View Pod Security Context:**
```bash
oc get pod <pod-name> -n secure-ai-gateway-dev -o yaml | grep -A 20 securityContext
```

**Verify Running User:**
```bash
oc exec <pod-name> -n secure-ai-gateway-dev -- id
```

### Secrets Management

**View Secrets (without values):**
```bash
oc get secrets -n secure-ai-gateway-dev
```

**Decode Secret:**
```bash
oc get secret secure-ai-gateway-secrets -n secure-ai-gateway-dev -o jsonpath='{.data.JWT_SECRET}' | base64 -d
```

**Rotate Secrets:**
```bash
# Generate new JWT secret
NEW_JWT_SECRET=$(openssl rand -base64 32)

# Update secret
oc patch secret secure-ai-gateway-secrets \
  -n secure-ai-gateway-dev \
  -p "{\"data\":{\"JWT_SECRET\":\"$(echo -n $NEW_JWT_SECRET | base64)\"}}"

# Restart pods to use new secret
oc rollout restart deployment/secure-ai-gateway -n secure-ai-gateway-dev
```

---

## Monitoring and Observability

### Application Health Checks

**Check Health:**
```bash
# Get route
ROUTE=$(oc get route secure-ai-gateway -n secure-ai-gateway-dev -o jsonpath='{.spec.host}')

# Health check
curl https://$ROUTE/actuator/health

# Liveness
curl https://$ROUTE/actuator/health/liveness

# Readiness
curl https://$ROUTE/actuator/health/readiness
```

### Metrics Collection

**View Prometheus Metrics:**
```bash
curl https://$ROUTE/actuator/prometheus
```

**Create Custom Dashboard:**
1. OpenShift Console → **Observe → Dashboards**
2. Click **+ Add Dashboard**
3. Add panels with PromQL queries
4. Save dashboard

---

## Troubleshooting

### Pod Issues

**Pod Not Starting:**
```bash
# Check pod status
oc get pods -n secure-ai-gateway-dev

# Describe pod
oc describe pod <pod-name> -n secure-ai-gateway-dev

# Check logs
oc logs <pod-name> -n secure-ai-gateway-dev

# Check previous logs (if pod crashed)
oc logs <pod-name> -n secure-ai-gateway-dev --previous

# Check events
oc get events -n secure-ai-gateway-dev --sort-by='.lastTimestamp'
```

**Image Pull Issues:**
```bash
# Verify secret exists
oc get secret quay-credentials -n secure-ai-gateway-dev

# Check secret is linked to service account
oc get sa secure-ai-gateway-sa -n secure-ai-gateway-dev -o yaml

# Verify image exists in registry
# Check ImagePullBackOff events
```

### Network Issues

**Cannot Access Application:**
```bash
# Check route
oc get route secure-ai-gateway -n secure-ai-gateway-dev

# Check service
oc get svc secure-ai-gateway -n secure-ai-gateway-dev

# Check endpoints
oc get endpoints secure-ai-gateway -n secure-ai-gateway-dev

# Test service from within cluster
oc run test --image=curlimages/curl -it --rm -- \
  curl http://secure-ai-gateway:8080/actuator/health
```

**Network Policy Issues:**
```bash
# Temporarily disable network policy for testing
oc delete networkpolicy deny-all-default -n secure-ai-gateway-dev

# Re-apply after testing
oc apply -f security/network-policy.yaml -n secure-ai-gateway-dev
```

### Pipeline Issues

**Pipeline Failing:**
```bash
# View pipeline run details
tkn pipelinerun describe <run-name> -n secure-ai-gateway-cicd

# View logs of failed task
tkn pipelinerun logs <run-name> -n secure-ai-gateway-cicd

# Check task status
tkn taskrun list -n secure-ai-gateway-cicd

# View task logs
tkn taskrun logs <task-run-name> -n secure-ai-gateway-cicd
```

### Permission Issues

**RBAC Errors:**
```bash
# Check service account
oc get sa secure-ai-gateway-sa -n secure-ai-gateway-dev

# Check role bindings
oc get rolebinding -n secure-ai-gateway-dev

# Check SCC
oc get scc secure-ai-gateway-scc

# Verify SCC is assigned
oc describe scc secure-ai-gateway-scc | grep Users
```

### Resource Issues

**Out of Memory:**
```bash
# Check resource usage
oc adm top pods -n secure-ai-gateway-dev

# Increase memory limit
oc set resources deployment/secure-ai-gateway \
  --limits=memory=2Gi \
  --requests=memory=1Gi \
  -n secure-ai-gateway-dev
```

**CPU Throttling:**
```bash
# Check CPU usage
oc adm top pods -n secure-ai-gateway-dev

# Increase CPU limit
oc set resources deployment/secure-ai-gateway \
  --limits=cpu=2 \
  --requests=cpu=500m \
  -n secure-ai-gateway-dev
```

---

## Additional Resources

### Documentation Links
- [Red Hat OpenShift Documentation](https://docs.openshift.com/)
- [OpenShift Pipelines (Tekton)](https://docs.openshift.com/container-platform/latest/cicd/pipelines/understanding-openshift-pipelines.html)
- [OpenShift GitOps (ArgoCD)](https://docs.openshift.com/container-platform/latest/cicd/gitops/understanding-openshift-gitops.html)
- [Red Hat Advanced Cluster Security](https://docs.openshift.com/acs/welcome/index.html)

### Support
- Red Hat Support Portal: https://access.redhat.com/
- OpenShift Community: https://community.openshift.com/
- GitHub Issues: https://github.com/your-org/secure-ai-gateway/issues

---

**Version:** 1.0.0  
**Last Updated:** 2024-02-15  
**Maintained By:** Security Engineering Team
