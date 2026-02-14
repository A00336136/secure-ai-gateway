#!/bin/bash

##############################################################################
# Secure AI Gateway - Red Hat DevSecOps Setup Script
# This script sets up the complete DevSecOps software factory on OpenShift
##############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE_DEV="secure-ai-gateway-dev"
NAMESPACE_PROD="secure-ai-gateway-prod"
NAMESPACE_CICD="secure-ai-gateway-cicd"
QUAY_REGISTRY="quay.io/secure-ai"
IMAGE_NAME="secure-ai-gateway"
IMAGE_TAG="1.0.0"

# Functions
print_header() {
    echo -e "${GREEN}===================================================${NC}"
    echo -e "${GREEN}$1${NC}"
    echo -e "${GREEN}===================================================${NC}"
}

print_info() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    print_header "Checking Prerequisites"
    
    # Check if oc is installed
    if ! command -v oc &> /dev/null; then
        print_error "OpenShift CLI (oc) is not installed"
        exit 1
    fi
    print_success "OpenShift CLI found: $(oc version --client)"
    
    # Check if logged into OpenShift
    if ! oc whoami &> /dev/null; then
        print_error "Not logged into OpenShift cluster"
        print_info "Please run: oc login <your-cluster-url>"
        exit 1
    fi
    print_success "Logged in as: $(oc whoami)"
    
    # Check cluster connectivity
    print_info "Cluster: $(oc cluster-info | head -n 1)"
}

create_namespaces() {
    print_header "Creating Namespaces"
    
    for ns in $NAMESPACE_DEV $NAMESPACE_PROD $NAMESPACE_CICD; do
        if oc get namespace $ns &> /dev/null; then
            print_info "Namespace $ns already exists"
        else
            oc create namespace $ns
            print_success "Created namespace: $ns"
        fi
        
        # Label namespaces
        oc label namespace $ns \
            app=secure-ai-gateway \
            environment=$(echo $ns | sed 's/secure-ai-gateway-//') \
            --overwrite
    done
}

setup_rbac() {
    print_header "Setting Up RBAC"
    
    # Apply RBAC for each namespace
    for ns in $NAMESPACE_DEV $NAMESPACE_PROD; do
        oc apply -f kubernetes/rbac.yaml -n $ns
        print_success "Applied RBAC for $ns"
    done
    
    # Create service account for pipelines
    oc create serviceaccount pipeline -n $NAMESPACE_CICD || true
    
    # Grant pipeline SA permissions
    oc adm policy add-scc-to-user privileged \
        -z pipeline -n $NAMESPACE_CICD || true
    
    oc adm policy add-role-to-user edit \
        system:serviceaccount:$NAMESPACE_CICD:pipeline \
        -n $NAMESPACE_DEV
    
    oc adm policy add-role-to-user edit \
        system:serviceaccount:$NAMESPACE_CICD:pipeline \
        -n $NAMESPACE_PROD
    
    print_success "RBAC setup completed"
}

create_secrets() {
    print_header "Creating Secrets"
    
    print_info "Generating JWT secret..."
    JWT_SECRET=$(openssl rand -base64 32)
    
    for ns in $NAMESPACE_DEV $NAMESPACE_PROD; do
        # Create application secrets
        oc create secret generic secure-ai-gateway-secrets \
            --from-literal=ADMIN_USERNAME=admin \
            --from-literal=ADMIN_PASSWORD='SecurePassword123!' \
            --from-literal=JWT_SECRET="$JWT_SECRET" \
            --from-literal=DB_USERNAME=sa \
            --from-literal=DB_PASSWORD='' \
            -n $ns \
            --dry-run=client -o yaml | oc apply -f -
        
        print_success "Created secrets for $ns"
    done
    
    # Create Quay credentials (requires input)
    print_info "Creating Quay registry credentials..."
    read -p "Enter Quay username: " QUAY_USER
    read -sp "Enter Quay password: " QUAY_PASS
    echo ""
    
    oc create secret docker-registry quay-credentials \
        --docker-server=quay.io \
        --docker-username=$QUAY_USER \
        --docker-password=$QUAY_PASS \
        --docker-email=$QUAY_USER@example.com \
        -n $NAMESPACE_CICD \
        --dry-run=client -o yaml | oc apply -f -
    
    print_success "Quay credentials created"
}

apply_security_policies() {
    print_header "Applying Security Policies"
    
    # Apply Security Context Constraints
    oc apply -f security/scc.yaml
    
    # Grant SCC to service accounts
    for ns in $NAMESPACE_DEV $NAMESPACE_PROD; do
        oc adm policy add-scc-to-user secure-ai-gateway-scc \
            -z secure-ai-gateway-sa -n $ns
        print_success "Applied SCC to $ns"
    done
    
    # Apply Network Policies
    for ns in $NAMESPACE_DEV $NAMESPACE_PROD; do
        oc apply -f security/network-policy.yaml -n $ns
        print_success "Applied network policies to $ns"
    done
    
    # Apply Resource Quotas
    for ns in $NAMESPACE_DEV $NAMESPACE_PROD; do
        oc apply -f security/resource-limits.yaml -n $ns
        print_success "Applied resource quotas to $ns"
    done
}

deploy_application() {
    print_header "Deploying Application"
    
    local namespace=$1
    
    print_info "Deploying to $namespace..."
    
    # Apply ConfigMap
    oc apply -f kubernetes/configmap.yaml -n $namespace
    
    # Apply Service
    oc apply -f kubernetes/service.yaml -n $namespace
    
    # Apply Deployment
    oc apply -f kubernetes/deployment.yaml -n $namespace
    
    # Apply Route
    oc apply -f kubernetes/route.yaml -n $namespace
    
    # Wait for deployment
    print_info "Waiting for deployment to be ready..."
    oc rollout status deployment/secure-ai-gateway -n $namespace --timeout=5m
    
    print_success "Application deployed to $namespace"
    
    # Get route
    ROUTE=$(oc get route secure-ai-gateway -n $namespace -o jsonpath='{.spec.host}')
    print_success "Application URL: https://$ROUTE"
}

install_openshift_pipelines() {
    print_header "Installing OpenShift Pipelines Operator"
    
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
EOF
    
    print_info "Waiting for operator to be ready..."
    sleep 30
    
    print_success "OpenShift Pipelines installed"
}

install_openshift_gitops() {
    print_header "Installing OpenShift GitOps Operator"
    
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
EOF
    
    print_info "Waiting for operator to be ready..."
    sleep 30
    
    print_success "OpenShift GitOps installed"
}

setup_tekton_pipelines() {
    print_header "Setting Up Tekton Pipelines"
    
    # Apply tasks
    oc apply -f tekton/tasks.yaml -n $NAMESPACE_CICD
    
    # Apply pipeline
    oc apply -f tekton/pipeline.yaml -n $NAMESPACE_CICD
    
    # Apply triggers
    oc apply -f tekton/pipelinerun.yaml -n $NAMESPACE_CICD
    
    print_success "Tekton pipelines configured"
}

setup_gitops() {
    print_header "Setting Up GitOps (ArgoCD)"
    
    # Apply ArgoCD applications
    oc apply -f gitops/argocd-application.yaml
    
    # Get ArgoCD route
    ARGOCD_ROUTE=$(oc get route openshift-gitops-server -n openshift-gitops -o jsonpath='{.spec.host}' 2>/dev/null || echo "Not yet available")
    
    if [ "$ARGOCD_ROUTE" != "Not yet available" ]; then
        print_success "ArgoCD URL: https://$ARGOCD_ROUTE"
        
        # Get admin password
        ARGOCD_PASSWORD=$(oc extract secret/openshift-gitops-cluster -n openshift-gitops --to=- --keys=admin.password 2>/dev/null || echo "")
        if [ ! -z "$ARGOCD_PASSWORD" ]; then
            print_success "ArgoCD admin password: $ARGOCD_PASSWORD"
        fi
    fi
}

install_monitoring() {
    print_header "Setting Up Monitoring"
    
    # Create ServiceMonitor for Prometheus
    for ns in $NAMESPACE_DEV $NAMESPACE_PROD; do
        cat <<EOF | oc apply -f -
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: secure-ai-gateway
  namespace: $ns
spec:
  endpoints:
  - interval: 30s
    path: /actuator/prometheus
    port: http
  selector:
    matchLabels:
      app: secure-ai-gateway
EOF
        print_success "ServiceMonitor created for $ns"
    done
}

print_summary() {
    print_header "Setup Summary"
    
    echo ""
    echo "Namespaces created:"
    echo "  - $NAMESPACE_DEV (Development)"
    echo "  - $NAMESPACE_PROD (Production)"
    echo "  - $NAMESPACE_CICD (CI/CD)"
    echo ""
    
    echo "Application URLs:"
    DEV_ROUTE=$(oc get route secure-ai-gateway -n $NAMESPACE_DEV -o jsonpath='{.spec.host}' 2>/dev/null || echo "Not deployed")
    PROD_ROUTE=$(oc get route secure-ai-gateway -n $NAMESPACE_PROD -o jsonpath='{.spec.host}' 2>/dev/null || echo "Not deployed")
    echo "  - Dev:  https://$DEV_ROUTE"
    echo "  - Prod: https://$PROD_ROUTE"
    echo ""
    
    echo "Next steps:"
    echo "  1. Access ArgoCD for GitOps management"
    echo "  2. Trigger a pipeline run in OpenShift Pipelines"
    echo "  3. Monitor applications in OpenShift Console"
    echo "  4. Review security policies in Red Hat Advanced Cluster Security"
    echo ""
}

# Main execution
main() {
    print_header "Secure AI Gateway - DevSecOps Setup"
    
    check_prerequisites
    
    # Setup components
    create_namespaces
    setup_rbac
    create_secrets
    apply_security_policies
    
    # Install operators
    install_openshift_pipelines
    install_openshift_gitops
    
    # Deploy application
    deploy_application $NAMESPACE_DEV
    
    # Setup CI/CD
    setup_tekton_pipelines
    setup_gitops
    
    # Setup monitoring
    install_monitoring
    
    print_summary
    
    print_success "Setup completed successfully!"
}

# Run main function
main "$@"
