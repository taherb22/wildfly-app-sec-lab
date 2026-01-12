#!/usr/bin/env bash
set -euo pipefail

# Phase 2 MFA (TOTP) Flow Test Script
# Tests: /api/auth/mfa/enroll, /api/auth/mfa/verify with registered User

BASE_URL="${BASE_URL:-http://localhost:8080/phoenix-iam}"
USERNAME="mfauser"
PASSWORD="MfaPass123!"
EMAIL="mfa@example.com"

echo "=== Phase 2 MFA (TOTP) Test ==="
echo "Base URL: $BASE_URL"
echo

# Step 1: Register a new user
echo "[1/4] Registering user with /api/auth/register..."
REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")

echo "$REGISTER_RESPONSE" | jq . 2>/dev/null || echo "$REGISTER_RESPONSE"

# Check if registration succeeded
if echo "$REGISTER_RESPONSE" | jq -e '.id' >/dev/null 2>&1; then
    USER_ID=$(echo "$REGISTER_RESPONSE" | jq -r '.id')
    echo "✅ User registered with ID: $USER_ID"
else
    echo "⚠️  Registration response: $REGISTER_RESPONSE"
    # Continue anyway - user might already exist
fi
echo

# Step 2: Enroll TOTP
echo "[2/4] Enrolling TOTP with /api/auth/mfa/enroll..."
ENROLL_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/mfa/enroll" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")

echo "$ENROLL_RESPONSE" | jq . 2>/dev/null || echo "$ENROLL_RESPONSE"
echo

SECRET=$(echo "$ENROLL_RESPONSE" | jq -r '.secret // empty')
OTPAUTH=$(echo "$ENROLL_RESPONSE" | jq -r '.otpauth_uri // empty')
QR=$(echo "$ENROLL_RESPONSE" | jq -r '.qr_data_uri // empty')

if [ -z "$SECRET" ]; then
    echo "ERROR: Failed to enroll TOTP"
    exit 1
fi

echo "✅ TOTP Secret: $SECRET"
echo "OTPAuth URI: $OTPAUTH"
echo
if [ -n "$QR" ]; then
    echo "QR Code (base64-encoded SVG, scan with authenticator app):"
    echo "$QR" | head -c 100
    echo "..."
fi
echo

# Step 3: Verify TOTP (auto-generate code)
echo "[3/4] Verifying TOTP code..."

# Generate TOTP code from secret using oathtool (if available) or prompt
if command -v oathtool &> /dev/null; then
    # Auto-generate TOTP code
    TOTP_CODE=$(oathtool --totp -b "$SECRET" 2>/dev/null || echo "")
    if [ -z "$TOTP_CODE" ]; then
        echo "ERROR: oathtool failed to generate TOTP code"
        exit 1
    fi
    echo "Generated TOTP code: $TOTP_CODE"
else
    # Manual input
    echo "Note: Install oathtool for automatic TOTP generation:"
    echo "  Ubuntu/Debian: sudo apt install oathtool"
    echo "  macOS: brew install oath-toolkit"
    echo ""
    echo "TOTP Secret: $SECRET"
    echo "OTPAuth URI: $OTPAUTH"
    echo ""
    echo "Enter the 6-digit TOTP code from your authenticator app:"
    read -r TOTP_CODE
fi

VERIFY_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/mfa/verify" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"code\":\"$TOTP_CODE\"}")

echo "$VERIFY_RESPONSE" | jq . 2>/dev/null || echo "$VERIFY_RESPONSE"
echo

# Step 4: Login with TOTP
echo "[4/4] Testing login with TOTP at /api/auth/login..."

# Generate new TOTP code for login (time-based, should be same within 30-second window)
if command -v oathtool &> /dev/null; then
    TOTP_CODE_LOGIN=$(oathtool --totp -b "$SECRET" 2>/dev/null || echo "")
    if [ -z "$TOTP_CODE_LOGIN" ]; then
        echo "ERROR: oathtool failed to generate TOTP code"
        exit 1
    fi
    echo "Generated new TOTP code: $TOTP_CODE_LOGIN"
else
    echo "Enter a new TOTP code from your authenticator app:"
    read -r TOTP_CODE_LOGIN
fi

LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"totp\":\"$TOTP_CODE_LOGIN\"}")

echo "$LOGIN_RESPONSE" | jq . 2>/dev/null || echo "$LOGIN_RESPONSE"

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token // empty')
if [ -n "$TOKEN" ]; then
    echo
    echo "=== Login Successful! ==="
    echo "JWT Token: $TOKEN"
    echo
    echo "=== Token Claims ==="
    echo "$TOKEN" | awk -F. '{print $2}' | base64 -d 2>/dev/null | jq . || echo "Failed to decode"
else
    echo "ERROR: Login with TOTP failed"
fi

echo
echo "=== Phase 2 MFA Test Complete ==="
