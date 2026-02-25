#!/bin/bash

# ============================================================
#  cleanup-dryrun.sh — DRY RUN / PREFLIGHT CHECK
#  Run this FIRST to see exactly what cleanup.sh will do.
#  Nothing is modified, deleted, or committed.
# ============================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

PASS=0
WARN=0
FAIL=0

echo -e "${BLUE}╔══════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   secure-ai-gateway — DRY RUN PREFLIGHT     ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════╝${NC}"
echo -e "${CYAN}  No files will be changed. Read-only checks.${NC}\n"

# ── GUARD: must be run from repo root ─────────────────────────
if [ ! -f "pom.xml" ] || [ ! -d ".git" ]; then
  echo -e "${RED}✖ ERROR: Run this from the root of your cloned repository.${NC}"
  echo "  Expected: pom.xml and .git/ to exist here."
  exit 1
fi
echo -e "${GREEN}✔ Running from repo root: $(pwd)${NC}\n"

echo -e "${BOLD}━━━ CHECK 1: .gitignore ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
if [ -f ".gitignore" ]; then
  echo -e "${GREEN}✔ .gitignore already exists.${NC}"
  # Check if target/ is covered
  if grep -q "^target/" .gitignore 2>/dev/null; then
    echo -e "${GREEN}  ✔ target/ is already ignored.${NC}"
  else
    echo -e "${YELLOW}  ⚠ target/ is NOT listed in .gitignore — cleanup will add it.${NC}"
    WARN=$((WARN+1))
  fi
else
  echo -e "${RED}  ✖ .gitignore is MISSING — cleanup will create it.${NC}"
  FAIL=$((FAIL+1))
fi
echo ""

echo -e "${BOLD}━━━ CHECK 2: target/ directory ━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
if [ -d "target" ]; then
  TARGET_SIZE=$(du -sh target 2>/dev/null | cut -f1)
  TARGET_FILES=$(find target -type f 2>/dev/null | wc -l | tr -d ' ')

  # Is it tracked by git?
  if git ls-files --error-unmatch target/ > /dev/null 2>&1; then
    echo -e "${RED}  ✖ target/ IS tracked by git (${TARGET_FILES} files, ~${TARGET_SIZE})${NC}"
    echo -e "    cleanup will run: ${CYAN}git rm -r --cached target/${NC}"
    FAIL=$((FAIL+1))
  else
    echo -e "${GREEN}  ✔ target/ exists locally but is NOT tracked by git.${NC}"
    PASS=$((PASS+1))
  fi
else
  echo -e "${GREEN}  ✔ No target/ directory found.${NC}"
  PASS=$((PASS+1))
fi
echo ""

echo -e "${BOLD}━━━ CHECK 3: Duplicate secure-ai-gateway/ subdirectory ━━${NC}"
if [ -d "secure-ai-gateway" ]; then
  echo -e "${YELLOW}  ⚠ Nested secure-ai-gateway/ directory EXISTS.${NC}"

  if [ -f "secure-ai-gateway/pom.xml" ]; then
    ROOT_JAVA=$(find src/main/java -name "*.java" 2>/dev/null | wc -l | tr -d ' ')
    NESTED_JAVA=$(find secure-ai-gateway/src/main/java -name "*.java" 2>/dev/null | wc -l | tr -d ' ')
    echo -e "    Root src/main/java:              ${ROOT_JAVA} Java files"
    echo -e "    secure-ai-gateway/src/main/java: ${NESTED_JAVA} Java files"

    if [ "$ROOT_JAVA" -gt 0 ] && [ "$NESTED_JAVA" -gt 0 ]; then
      echo -e "${RED}    ✖ BOTH have Java source — likely a duplicate module!${NC}"
      echo -e "    cleanup will prompt you to confirm before removing."
      FAIL=$((FAIL+1))
    else
      echo -e "${YELLOW}    ⚠ One appears empty — likely safe to remove.${NC}"
      WARN=$((WARN+1))
    fi
  else
    echo -e "${YELLOW}    No pom.xml inside — stray/empty folder.${NC}"
    echo -e "    cleanup will auto-remove it."
    WARN=$((WARN+1))
  fi

  # Is it git-tracked?
  if git ls-files --error-unmatch "secure-ai-gateway/" > /dev/null 2>&1; then
    echo -e "${RED}    ✖ It IS tracked by git — cleanup will untrack it.${NC}"
  else
    echo -e "${YELLOW}    Not yet tracked by git (untracked directory).${NC}"
  fi
else
  echo -e "${GREEN}  ✔ No duplicate secure-ai-gateway/ directory.${NC}"
  PASS=$((PASS+1))
fi
echo ""

echo -e "${BOLD}━━━ CHECK 4: Excess markdown files in root ━━━━━━━━━━━━━━${NC}"
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

FOUND_DOCS=()
for doc in "${CLEANUP_DOCS[@]}"; do
  [ -f "$doc" ] && FOUND_DOCS+=("$doc")
done

if [ ${#FOUND_DOCS[@]} -eq 0 ]; then
  echo -e "${GREEN}  ✔ No excess markdown files in root.${NC}"
  PASS=$((PASS+1))
else
  echo -e "${YELLOW}  ⚠ ${#FOUND_DOCS[@]} excess .md files found — will be moved to docs/fix-history/:${NC}"
  for doc in "${FOUND_DOCS[@]}"; do
    echo -e "    ${CYAN}→ $doc${NC}"
  done
  WARN=$((WARN+1))
fi
echo ""

echo -e "${BOLD}━━━ CHECK 5: Git status snapshot ━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${CYAN}  Current branch:${NC} $(git branch --show-current)"
echo -e "${CYAN}  Uncommitted changes:${NC}"
GIT_STATUS=$(git status --short)
if [ -z "$GIT_STATUS" ]; then
  echo -e "${GREEN}    Working tree is clean.${NC}"
  PASS=$((PASS+1))
else
  echo "$GIT_STATUS" | head -20 | sed 's/^/    /'
  UNCOMMITTED=$(echo "$GIT_STATUS" | wc -l | tr -d ' ')
  echo -e "${YELLOW}    $UNCOMMITTED uncommitted change(s) — cleanup will include these in the commit.${NC}"
  WARN=$((WARN+1))
fi
echo ""

echo -e "${BOLD}━━━ CHECK 6: Root directory — what will remain after cleanup ━━${NC}"
echo -e "  Kept at root (essential files):"
KEEP=("pom.xml" "Dockerfile" "docker-compose.yml" "Jenkinsfile" "README.md" "owasp-suppressions.xml" "qodana.yaml" ".gitignore")
for f in "${KEEP[@]}"; do
  if [ -f "$f" ]; then
    echo -e "${GREEN}    ✔ $f${NC}"
  else
    echo -e "${YELLOW}    - $f (not found)${NC}"
  fi
done
echo ""

# ── SUMMARY ───────────────────────────────────────────────────
echo -e "${BLUE}╔══════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║              DRY RUN SUMMARY                ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════╝${NC}"
echo -e "${RED}  ✖ FAILURES (cleanup required):  $FAIL${NC}"
echo -e "${YELLOW}  ⚠ WARNINGS (will be cleaned):  $WARN${NC}"
echo -e "${GREEN}  ✔ PASSING (already clean):      $PASS${NC}"
echo ""

if [ $FAIL -gt 0 ] || [ $WARN -gt 0 ]; then
  echo -e "${YELLOW}  → Repo needs cleanup. Safe to run cleanup.sh${NC}"
  echo -e "    ${CYAN}chmod +x cleanup.sh && ./cleanup.sh${NC}"
else
  echo -e "${GREEN}  → Repo looks clean already!${NC}"
fi
echo ""
echo -e "${CYAN}  TIP: After cleanup.sh, re-run this dry-run script${NC}"
echo -e "${CYAN}  to confirm everything shows green before pushing.${NC}"
echo ""
