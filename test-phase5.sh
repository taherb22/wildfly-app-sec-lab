#!/bin/bash
# ============================================================
# Phase 5 Test Script — PWA Frontend & Security (Merged)
# ============================================================

BASE_URL="${BASE_URL:-http://localhost:8080/phoenix-iam/}"

BOLD="\033[1m"
GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[0;33m"
RESET="\033[0m"

echo -e "${BOLD}=== Phase 5 Testing: PWA Frontend & Security ===${RESET}\n"

# ------------------------------------------------------------
# Test 1: Application availability
# ------------------------------------------------------------
echo -e "${BOLD}[1] Checking application availability${RESET}"
curl -s -I "$BASE_URL/" | grep -q "200" \
  && echo -e "${GREEN}✓ Application reachable${RESET}" \
  || echo -e "${RED}✗ Application not reachable${RESET}"
echo ""

# ------------------------------------------------------------
# Test 2: Service Worker presence
# ------------------------------------------------------------
echo -e "${BOLD}[2] Checking Service Worker${RESET}"
curl -s "$BASE_URL/sw.js" | grep -q "CACHE_NAME" \
  && echo -e "${GREEN}✓ Service Worker found${RESET}" \
  || echo -e "${RED}✗ Service Worker missing${RESET}"
echo ""

# ------------------------------------------------------------
# Test 3: Cached assets (static files)
# ------------------------------------------------------------
echo -e "${BOLD}[3] Checking cached static assets${RESET}"
curl -s "$BASE_URL/lib/purify.es.js" | grep -q "DOMPurify" \
  && echo -e "${GREEN}✓ DOMPurify library accessible${RESET}" \
  || echo -e "${RED}✗ DOMPurify library missing${RESET}"

curl -s "$BASE_URL/services/offline-queue.js" | grep -q "enqueue" \
  && echo -e "${GREEN}✓ Offline queue module present${RESET}" \
  || echo -e "${RED}✗ Offline queue module missing${RESET}"
echo ""

# ------------------------------------------------------------
# Test 4: Content Security Policy (header or meta)
# ------------------------------------------------------------
echo -e "${BOLD}[4] Checking CSP configuration${RESET}"
curl -s -I "$BASE_URL/" | grep -qi "content-security-policy" \
  && echo -e "${GREEN}✓ CSP header present${RESET}" \
  || echo -e "${YELLOW}⚠ CSP header not found (meta tag may be used)${RESET}"
echo ""

# ------------------------------------------------------------
# Manual Tests Section
# ------------------------------------------------------------
echo -e "${BOLD}=== Manual Tests Required ===${RESET}"

echo -e "${YELLOW}[5] Offline Mode Test${RESET}"
echo " - Open DevTools → Network → Enable 'Offline'"
echo " - Reload application"
echo " - Expected: App loads from cache without errors"
echo ""

echo -e "${YELLOW}[6] Background Sync / Offline Queue${RESET}"
echo " - Go offline"
echo " - Perform an action (login/register)"
echo " - Go online"
echo " - Expected: Request is replayed successfully"
echo ""

echo -e "${YELLOW}[7] Secure Storage Verification${RESET}"
echo " - Open DevTools → Application → Cache Storage"
echo " - Expected: No auth tokens in Service Worker cache"
echo " - Tokens only in sessionStorage / IndexedDB"
echo ""

echo -e "${YELLOW}[8] DOMPurify XSS Sanitization${RESET}"
echo " - Inject: <script>alert('xss')</script> in any input"
echo " - Expected: No alert, script removed or escaped"
echo ""

echo -e "${YELLOW}[9] CSP Violation Test${RESET}"
echo " - Try inline <script>alert(1)</script> in console"
echo " - Expected: CSP blocks execution"
echo ""

echo -e "${BOLD}=== Phase 5 Automated Checks Completed ===${RESET}"
echo -e "${GREEN}✓ Automated tests passed (if no red errors above)${RESET}"
echo -e "${YELLOW}⚠ Manual tests must be validated by tester${RESET}"
