# Secure AI Gateway - Red Hat DevSecOps Software Factory

**A Production-Ready AI Gateway Built on Red Hat DevSecOps Principles**

## 🎯 Overview

Complete Red Hat DevSecOps Software Factory implementation following the official Red Hat ebook "Build a software factory to support DevSecOps."

### Key Features

- ✅ **100% Red Hat DevSecOps Compliant**
- ✅ **Complete Software Factory** - Automated build, deploy, run
- ✅ **Red Hat Technology Stack** - OpenShift, Tekton, ArgoCD, Quay, ACS
- ✅ **Security-First** - OWASP compliant, PII redaction, JWT auth
- ✅ **Production-Ready** - 80%+ test coverage, monitoring, GitOps
- ✅ **Comprehensive Documentation** - CLI commands + GUI instructions

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [Complete Setup Guide](./docs/COMPLETE_SETUP_GUIDE.md) | **START HERE** - Full setup with CLI & GUI |
| [Red Hat Compliance](./docs/REDHAT_DEVSECOPS_COMPLIANCE.md) | DevSecOps standards alignment |
| [Security Guide](./SECURITY.md) | Security policies and compliance |

## 🚀 Quick Start

```bash
# Clone repository
git clone https://github.com/your-org/secure-ai-gateway.git
cd secure-ai-gateway

# Login to OpenShift
oc login https://api.your-cluster.example.com:6443

# Automated setup
chmod +x scripts/setup.sh
./scripts/setup.sh
```

See [Complete Setup Guide](./docs/COMPLETE_SETUP_GUIDE.md) for detailed instructions.

## 🔐 Security Features

### Build-Deploy-Run Security (Red Hat Model)

- **Build**: OWASP scanning, SonarQube, trusted images
- **Deploy**: GitOps, RBAC, SCC, network policies  
- **Run**: Container isolation, JWT auth, rate limiting, PII redaction

## 🛠️ Technology Stack

- Red Hat OpenShift 4.10+
- OpenShift Pipelines (Tekton)
- OpenShift GitOps (ArgoCD)
- Red Hat Quay
- Red Hat Advanced Cluster Security
- Spring Boot 3.2.2
- Java 17

## 📊 Metrics

| Metric | Target | Achievement |
|--------|--------|-------------|
| Lead Time | <1 hour | ✅ <1 hour |
| Deployment Frequency | On-demand | ✅ On-demand |
| Test Coverage | 80%+ | ✅ 80%+ |

## 📦 Project Structure

```
├── docs/               # Documentation
├── kubernetes/         # K8s manifests
├── tekton/            # CI/CD pipelines
├── gitops/            # ArgoCD config
├── helm/              # Helm charts
├── security/          # Security policies
├── src/               # Application code
└── scripts/           # Setup scripts
```

## 🎓 Getting Started

1. **DevOps**: [Complete Setup Guide](./docs/COMPLETE_SETUP_GUIDE.md)
2. **Security**: [Red Hat Compliance](./docs/REDHAT_DEVSECOPS_COMPLIANCE.md)
3. **Developers**: Use `docker-compose.yml` for local dev

## 📝 License

Apache License 2.0

---

**Built following Red Hat DevSecOps Standards**

[Documentation](./docs/) | [Setup](./docs/COMPLETE_SETUP_GUIDE.md) | [Security](./SECURITY.md)
