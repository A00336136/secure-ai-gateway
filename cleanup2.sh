#!/bin/bash

# ============================================================
#  cleanup.sh — secure-ai-gateway repo hygiene script
#  Run AFTER cleanup-dryrun.sh confirms what will change.
#  Branch: feature/a00336136
# ============================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

echo -e "${BLUE}╔══════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   secure-ai-gateway — Repo Cleanup Script   ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════╝${NC}\n"

# ── Guard: correct directory ───────────────────────────────────
if [ ! -f "pom.xml" ] || [ ! -d ".git" ]; then
  echo -e "${RED}ERROR: Run this from the root of your cloned repository.${NC}"
  exit 1
fi
echo -e "${GREEN}✔ Repository root confirmed: $(pwd)${NC}"
echo -e "${CYAN}  Branch: $(git branch --show-current)${NC}\n"

# ── Guard: confirm before proceeding ──────────────────────────
echo -e "${YELLOW}This script will:${NC}"
echo "  1. Create .gitignore"
echo "  2. Remove target/ from git tracking"
echo "  3. Handle duplicate secure-ai-gateway/ directory"
echo "  4. Move 17 excess .md files → docs/fix-history/"
echo "  5. Commit all changes"
echo ""
echo -e "${YELLOW}Have you run cleanup-dryrun.sh and reviewed the output? (y/n)${NC}"
read -r CONFIRM
if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
  echo -e "${CYAN}Please run ./cleanup-dryrun.sh first, then come back.${NC}"
  exit 0
fi
echo ""

# ── Create a safety backup tag so you can undo ────────────────
BACKUP_TAG="backup/pre-cleanup-$(date +%Y%m%d-%H%M%S)"
git tag "$BACKUP_TAG"
echo -e "${GREEN}✔ Safety backup tag created: ${CYAN}$BACKUP_TAG${NC}"
echo -e "  To undo everything: ${CYAN}git reset --hard $BACKUP_TAG${NC}\n"

# ── STEP 1: Create .gitignore ─────────────────────────────────
echo -e "${BOLD}[1/5] Creating .gitignore...${NC}"

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

# ── STEP 2: Remove target/ from git tracking ──────────────────
echo -e "${BOLD}[2/5] Removing target/ from git tracking...${NC}"

if git ls-files --error-unmatch target/ > /dev/null 2>&1; then
  TARGET_FILES=$(git ls-files target/ | wc -l | tr -d ' ')
  echo -e "  Removing ${TARGET_FILES} tracked files from target/..."
  git rm -r --cached target/ --quiet
  echo -e "${GREEN}✔ target/ removed from git tracking (files still exist locally).${NC}\n"
else
  echo -e "${GREEN}✔ target/ was not tracked by git — nothing to do.${NC}\n"
fi

# ── STEP 3: Handle duplicate secure-ai-gateway/ directory ─────
echo -e "${BOLD}[3/5] Checking nested secure-ai-gateway/ directory...${NC}"

if [ -d "secure-ai-gateway" ]; then
  echo -e "${YELLOW}  Found nested secure-ai-gateway/ directory.${NC}"

  if [ -f "secure-ai-gateway/pom.xml" ]; then
    ROOT_JAVA=$(find src/main/java -name "*.java" 2>/dev/null | wc -l | tr -d ' ')
    NESTED_JAVA=$(find secure-ai-gateway/src/main/java -name "*.java" 2>/dev/null | wc -l | tr -d ' ')

    echo -e "  Root src/main/java:              ${BOLD}${ROOT_JAVA}${NC} Java files"
    echo -e "  secure-ai-gateway/src/main/java: ${BOLD}${NESTED_JAVA}${NC} Java files"
    echo ""
    echo -e "${YELLOW}  This nested directory has its own pom.xml.${NC}"
    echo -e "${YELLOW}  Delete it? This cannot be undone via this script (use the backup tag). (y/n)${NC}"
    read -r DELETE_NESTED
    if [[ "$DELETE_NESTED" == "y" || "$DELETE_NESTED" == "Y" ]]; then
      git rm -r --cached "secure-ai-gateway/" 2>/dev/null || true
      rm -rf "secure-ai-gateway/"
      echo -e "${GREEN}✔ Nested secure-ai-gateway/ removed.${NC}\n"
    else
      echo -e "${CYAN}  Skipped. You can remove it manually later.${NC}\n"
    fi
  else
    # No pom.xml — safe to auto-remove
    echo -e "  No pom.xml inside — stray folder, auto-removing..."
    git rm -r --cached "secure-ai-gateway/" 2>/dev/null || true
    rm -rf "secure-ai-gateway/"
    echo -e "${GREEN}✔ Stray secure-ai-gateway/ directory removed.${NC}\n"
  fi
else
  echo -e "${GREEN}✔ No duplicate directory found.${NC}\n"
fi

# ── STEP 4: Move excess .md files to docs/fix-history/ ────────
echo -e "${BOLD}[4/5] Moving excess markdown files to docs/fix-history/...${NC}"

mkdir -p docs/fix-history

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

MOVED=0
for doc in "${CLEANUP_DOCS[@]}"; do
  if [ -f "$doc" ]; then
    git mv "$doc" "docs/fix-history/$doc"
    echo -e "  ${CYAN}→ $doc${NC}"
    MOVED=$((MOVED+1))
  fi
done

if [ $MOVED -eq 0 ]; then
  echo -e "${GREEN}✔ No excess markdown files found.${NC}"
else
  echo -e "${GREEN}✔ Moved $MOVED file(s) to docs/fix-history/.${NC}"
fi
echo ""

# ── STEP 5: Stage everything and commit ───────────────────────
echo -e "${BOLD}[5/5] Committing all changes...${NC}"

git add -A

if git diff --cached --quiet; then
  echo -e "${GREEN}✔ Nothing to commit — repo was already clean.${NC}"
else
  git commit -m "chore: repo cleanup

- Add .gitignore (target/, IDE, secrets, logs, SonarQube cache)
- Remove target/ build artefacts from version control
- Move fix-history markdown docs into docs/fix-history/
- Remove stray nested directory

Backup tag: $BACKUP_TAG"

  echo -e "${GREEN}✔ Committed successfully.${NC}"
fi

echo ""
echo -e "${BLUE}╔══════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║          Cleanup Complete! ✔               ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${BOLD}Next steps:${NC}"
echo ""
echo -e "  1. ${CYAN}Re-run the dry-run to confirm everything is green:${NC}"
echo -e "     ./cleanup-dryrun.sh"
echo ""
echo -e "  2. ${CYAN}Push to remote:${NC}"
echo -e "     git push origin feature/a00336136"
echo -e "     git push origin $BACKUP_TAG   # push the safety tag too"
echo ""
echo -e "  3. ${CYAN}If you need to undo everything:${NC}"
echo -e "     git reset --hard $BACKUP_TAG"
echo -e "     git push --force origin feature/a00336136"
echo ""
