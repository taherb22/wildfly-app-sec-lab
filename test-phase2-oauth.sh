#!/usr/bin/env bash
set -euo pipefail

# Phase 2 OAuth 2.1 + PKCE Flow Test Script
# Tests: /authorize, /login/authorization, /oauth/token with PKCE + state validation

BASE_URL="${BASE_URL:-http://localhost:8080/phoenix-iam}"
REDIRECT_URI="http://localhost/callback"
CLIENT_ID="TENANT_TEST"
USERNAME="john"
PASSWORD="SecurePass123!"
SCOPE="openid profile"

echo "=== Phase 2 OAuth 2.1 + PKCE Flow Test ==="
echo "Base URL: $BASE_URL"
echo

# Step 1: Seed test data (tenant, identity, grant)
echo "[1/6] Seeding test data..."
SEED_RESPONSE=$(curl -s --max-time 10 -X POST "$BASE_URL/rest-iam/dev/seed")
echo "$SEED_RESPONSE" | jq . 2>/dev/null || echo "$SEED_RESPONSE"
echo

# Step 2: Generate PKCE values
echo "[2/6] Generating PKCE code_verifier and code_challenge..."
CODE_VERIFIER=$(openssl rand -base64 64 | tr -d '=+/\n' | cut -c1-64)
CODE_CHALLENGE=$(printf "%s" "$CODE_VERIFIER" | openssl dgst -sha256 -binary | openssl base64 | tr '+/' '-_' | tr -d '=\n')
STATE=$(openssl rand -base64 32 | tr -d '=+/\n' | cut -c1-32)

echo "code_verifier: $CODE_VERIFIER"
echo "code_challenge: $CODE_CHALLENGE"
echo "state: $STATE"
echo

# Step 3: Request authorization code (GET /authorize)
echo "[3/6] Requesting authorization (GET /authorize)..."
AUTHORIZE_URL="$BASE_URL/authorize?client_id=$CLIENT_ID&redirect_uri=$(printf %s "$REDIRECT_URI" | jq -sRr @uri)&response_type=code&scope=$(printf %s "$SCOPE" | jq -sRr @uri)&state=$STATE&code_challenge_method=S256&code_challenge=$CODE_CHALLENGE"

AUTHORIZE_RESPONSE=$(curl -s --max-time 10 -i -c /tmp/cookies.txt "$AUTHORIZE_URL")
HTTP_STATUS=$(echo "$AUTHORIZE_RESPONSE" | head -n1 | awk '{print $2}')
COOKIE=$(echo "$AUTHORIZE_RESPONSE" | grep -i "set-cookie: signInId=" | sed 's/.*signInId=\([^;]*\).*/\1/' | head -n1)

if [ -z "$COOKIE" ]; then
    echo "ERROR: Failed to get session cookie from /authorize"
    echo "$AUTHORIZE_RESPONSE"
    exit 1
fi

echo "Session cookie: signInId=$COOKIE"
echo "HTTP Status: $HTTP_STATUS"
echo

# Step 4: Submit login credentials (POST /login/authorization)
echo "[4/6] Submitting login credentials..."
LOGIN_RESPONSE=$(curl -s --max-time 10 -i -b /tmp/cookies.txt -c /tmp/cookies.txt \
    -X POST "$BASE_URL/login/authorization" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "username=$USERNAME" \
    --data-urlencode "password=$PASSWORD")

LOGIN_STATUS=$(echo "$LOGIN_RESPONSE" | head -n1 | awk '{print $2}')
LOCATION=$(echo "$LOGIN_RESPONSE" | grep -i "^location:" | sed 's/location: //i' | tr -d '\r')

if [ -z "$LOCATION" ]; then
    echo "ERROR: No redirect location found after login"
    echo "$LOGIN_RESPONSE"
    exit 1
fi

echo "Login HTTP Status: $LOGIN_STATUS"
echo "Redirect location: $LOCATION"
echo

# Step 5: Extract authorization code from redirect
echo "[5/6] Extracting authorization code..."
AUTH_CODE=$(echo "$LOCATION" | sed -n 's/.*code=\([^&]*\).*/\1/p' | head -n1)
# URL-decode the authorization code
AUTH_CODE=$(printf '%b' "${AUTH_CODE//%/\\x}")
RETURNED_STATE=$(echo "$LOCATION" | sed -n 's/.*state=\([^&]*\).*/\1/p' | head -n1)

if [ -z "$AUTH_CODE" ]; then
    echo "ERROR: No authorization code in redirect"
    echo "Location: $LOCATION"
    exit 1
fi

echo "Authorization code: $AUTH_CODE"
echo "Returned state: $RETURNED_STATE"

if [ "$STATE" != "$RETURNED_STATE" ]; then
    echo "WARNING: State mismatch! Expected: $STATE, Got: $RETURNED_STATE"
fi
echo

# Step 6: Exchange code for tokens (POST /oauth/token)
echo "[6/6] Exchanging authorization code for tokens..."
# URL-encode the code_verifier for form submission (+ and / need encoding)
ENCODED_CODE_VERIFIER=$(printf %s "$CODE_VERIFIER" | jq -sRr @uri)
TOKEN_RESPONSE=$(curl -s --max-time 10 -X POST "$BASE_URL/oauth/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=authorization_code" \
    --data-urlencode "code=$AUTH_CODE" \
    --data-urlencode "code_verifier=$CODE_VERIFIER")

echo "$TOKEN_RESPONSE" | jq . 2>/dev/null || echo "$TOKEN_RESPONSE"
echo

# Extract and decode access token
ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty' 2>/dev/null)
if [ -n "$ACCESS_TOKEN" ]; then
    echo "=== Access Token Claims ==="
    echo "$ACCESS_TOKEN" | awk -F. '{print $2}' | base64 -d -w0 2>/dev/null | jq . 2>/dev/null || echo "$ACCESS_TOKEN" | awk -F. '{print $2}' | base64 -D 2>/dev/null | jq . || echo "Failed to decode token"
    echo
fi

# Test token with a protected endpoint (optional)
if [ -n "$ACCESS_TOKEN" ]; then
    echo "=== Testing Access Token with /rest-iam/users ==="
    USERS_RESPONSE=$(curl -s --max-time 10 -H "Authorization: Bearer $ACCESS_TOKEN" "$BASE_URL/rest-iam/users")
    echo "$USERS_RESPONSE" | jq . 2>/dev/null || echo "$USERS_RESPONSE"
    echo
fi

# Cleanup
rm -f /tmp/cookies.txt

echo "=== Phase 2 Test Complete ==="
