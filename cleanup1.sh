#!/bin/bash

# ============================================================
#  cleanup.sh — secure-ai-gateway repo hygiene script
#  Branch: feature/a00336136
#  Run from the ROOT of the cloned repository
# ============================================================

set -e  # Exit on any error

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Colour

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  secure-ai-gateway — Repo Cleanup     ${NC}"
echo -e "${BLUE}========================================${NC}\n"

# ── Safety check: must be run from the repo root ──────────────
if [ ! -f "pom.xml" ]; then
  echo -e "${RED}ERROR: pom.xml not found.${NC}"
  echo "Please run this script from the root of the repository."
  exit 1
fi

if [ ! -d ".git" ]; then
  echo -e "${RED}ERROR: .git directory not found.${NC}"
  echo "Please run this script from the root of the repository."
  exit 1
fi

echo -e "${GREEN}✔ Repository root confirmed.${NC}\n"

# ── 1. Create .gitignore ───────────────────────────────────────
echo -e "${YELLOW}[1/5] Creating .gitignore...${NC}"

cat > .gitignore << 'EOF'
# ── Maven build output ──────────────────────────────────────
target/
!.mvn/wrapper/maven-wrapper.jar
!**/src/main/**/target/
!**/src/test/**/target/

# ── IDE files ───────────────────────────────────────────────
.idea/
*.iml
*.iws
*.ipr
.vscode/
*.code-workspace
.classpath
.project
.settings/
.factorypath

# ── Environment & secrets ────────────────────────────────────
.env
.env.*
*.env
application-local.yml
application-local.properties

# ── OS files ─────────────────────────────────────────────────
.DS_Store
Thumbs.db
desktop.ini

# ── Logs ─────────────────────────────────────────────────────
*.log
logs/

# ── Compiled classes & archives ──────────────────────────────
*.class
*.jar
*.war
*.ear
*.nar
*.zip
*.tar.gz

# ── JaCoCo coverage ──────────────────────────────────────────
*.exec

# ── SonarQube cache ──────────────────────────────────────────
.sonar/
.scannerwork/

# ── Docker ───────────────────────────────────────────────────
.docker/

# ── OWASP dependency check cache ────────────────────────────
dependency-check-data/
EOF

echo -e "${GREEN}✔ .gitignore created.${NC}\n"

# ── 2. Remove target/ from git tracking ───────────────────────
echo -e "${YELLOW}[2/5] Removing target/ from git tracking...${NC}"

if git ls-files --error-unmatch target/ > /dev/null 2>&1; then
  git rm -r --cached target/
  echo -e "${GREEN}✔ target/ removed from tracking.${NC}\n"
else
  echo -e "${GREEN}✔ target/ was not tracked — nothing to remove.${NC}\n"
fi

# ── 3. Investigate & handle duplicate secure-ai-gateway/ dir ──
echo -e "${YELLOW}[3/5] Checking for duplicate secure-ai-gateway/ subdirectory...${NC}"

if [ -d "secure-ai-gateway" ]; then
  echo -e "${YELLOW}  Found nested secure-ai-gateway/ directory.${NC}"

  # Check if it has its own pom.xml (i.e. it's a real Maven module)
  if [ -f "secure-ai-gateway/pom.xml" ]; then
    echo -e "${YELLOW}  It contains a pom.xml — checking if it duplicates root src/...${NC}"

    ROOT_JAVA=$(find src/main/java -name "*.java" 2>/dev/null | wc -l | tr -d ' ')
    NESTED_JAVA=$(find secure-ai-gateway/src/main/java -name "*.java" 2>/dev/null | wc -l | tr -d ' ')

    echo "  Root src/main/java:              ${ROOT_JAVA} Java files"
    echo "  secure-ai-gateway/src/main/java: ${NESTED_JAVA} Java files"

    echo -e "\n${YELLOW}  ACTION REQUIRED:${NC}"
    echo "  If these counts are similar this subdirectory is a duplicate."
    echo "  To remove it, run:"
    echo -e "  ${BLUE}  git rm -r --cached secure-ai-gateway/${NC}"
    echo -e "  ${BLUE}  rm -rf secure-ai-gateway/${NC}"
    echo -e "  ${BLUE}  git add -A && git commit -m \"chore: remove duplicate nested module\"${NC}"
    echo ""
    echo "  Skipping automatic deletion — please verify manually first."
  else
    echo -e "${YELLOW}  No pom.xml found inside it — likely an accidental empty/stray folder.${NC}"
    git rm -r --cached secure-ai-gateway/ 2>/dev/null || true
    rm -rf secure-ai-gateway/
    echo -e "${GREEN}✔ Stray secure-ai-gateway/ directory removed.${NC}"
  fi
else
  echo -e "${GREEN}✔ No duplicate directory found.${NC}"
fi
echo ""

# ── 4. Move excess .md files into docs/ ───────────────────────
echo -e "${YELLOW}[4/5] Moving excess markdown files into docs/...${NC}"

mkdir -p docs/fix-history

MOVED=0
CLEANUP_DOCS=(
  "CODE_ANALYSIS_FIXES_COMPLETE.md"
  "COMPLETE_RESOLUTION.md"
  "CVE_DETAILED_MAPPING.md"
  "CVE_REMEDIATION_CHECKLIST.md"
  "CVE_REMEDIATION_SUMMARY.md"
  "DEPENDENCY_VERSION_FIX.md"
  "FINAL_FIXES_SUMMARY.md"
  "FINAL_SOLUTION.md"
  "FINAL_VERIFICATION_REPORT.md"
  "FIX_SUMMARY.md"
  "IMPLEMENTATION_REPORT.md"
  "POM_CHANGES.md"
  "PRODUCTION_BUILD_FIX.md"
  "QUICK_REFERENCE.md"
  "QUICK_START.md"
  "SPRINGDOC_FIX_FINAL.md"
  "WEBJARS_FIX_FINAL.md"
)

for doc in "${CLEANUP_DOCS[@]}"; do
  if [ -f "$doc" ]; then
    git mv "$doc" "docs/fix-history/$doc" 2>/dev/null || mv "$doc" "docs/fix-history/$doc"
    echo "  Moved: $doc → docs/fix-history/"
    MOVED=$((MOVED + 1))
  fi
done

if [ $MOVED -eq 0 ]; then
  echo -e "${GREEN}✔ No excess markdown files found.${NC}"
else
  echo -e "${GREEN}✔ Moved $MOVED file(s) into docs/fix-history/.${NC}"
fi
echo ""

# ── 5. Stage all changes & commit ─────────────────────────────
echo -e "${YELLOW}[5/5] Staging and committing changes...${NC}"

git add -A

# Check if there's anything to commit
if git diff --cached --quiet; then
  echo -e "${GREEN}✔ Nothing to commit — repo is already clean.${NC}"
else
  git commit -m "chore: repo cleanup

- Add .gitignore (excludes target/, IDE files, secrets, logs)
- Remove target/ build output from version control
- Move fix-history markdown files into docs/fix-history/
- Remove stray files from root"

  echo -e "${GREEN}✔ Changes committed.${NC}"
fi

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}  Cleanup complete!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "Next steps:"
echo "  1. Review 'docs/fix-history/' and delete anything you don't need:"
echo -e "     ${BLUE}rm -rf docs/fix-history/${NC}  (optional)"
echo ""
echo "  2. If the nested secure-ai-gateway/ needed manual removal, do that now."
echo ""
echo "  3. Push to remote:"
echo -e "     ${BLUE}git push origin feature/a00336136${NC}"
echo ""
