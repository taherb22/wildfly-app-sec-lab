# Phase 2 Test Scripts

This directory contains test scripts for validating the Phase 2 implementation (OAuth 2.1 + PKCE, MFA/TOTP, Redis sessions).

## Prerequisites

- WildFly running with the deployed phoenix-iam.war
- `jq` for JSON parsing: `sudo apt install jq` (Ubuntu/Debian) or `brew install jq` (macOS)
- `openssl` for PKCE generation
- `curl` for HTTP requests

## Scripts

### 1. OAuth 2.1 + PKCE Flow Test (`test-phase2-oauth.sh`)

Tests the complete authorization code flow with PKCE and state validation.

**What it tests:**
- `/dev/seed` - Seeds test tenant, identity, and grant
- `/authorize` - PKCE validation, state validation, session creation
- `/login/authorization` - User authentication with Argon2, rate limiting
- `/oauth/token` - Code exchange with code_verifier, Ed25519 token issuance
- Protected endpoint access with Bearer token

**Run:**
```bash
chmod +x test-phase2-oauth.sh
./test-phase2-oauth.sh
```

**Custom base URL:**
```bash
BASE_URL=https://iam.taherboudrigua.me ./test-phase2-oauth.sh
```

**Expected output:**
- Test data seeded (tenant=TENANT_TEST, username=john)
- PKCE values generated
- Session cookie received from /authorize
- Authorization code received after login
- Access token and refresh token received
- Token decoded showing claims (subject, roles, tenant_id, scope, jti)
- Protected endpoint returns user list

### 2. MFA (TOTP) Flow Test (`test-phase2-mfa.sh`)

Tests TOTP enrollment and verification for multi-factor authentication.

**What it tests:**
- `/api/auth/register` - User registration
- `/mfa/enroll` - TOTP secret generation, QR code creation
- `/mfa/verify` - TOTP code verification
- `/api/auth/login` - Login with TOTP required

**Run:**
```bash
chmod +x test-phase2-mfa.sh
./test-phase2-mfa.sh
```

**Interactive steps:**
1. Script registers a new user (`mfauser`)
2. Enrolls TOTP and displays QR code (data URI)
3. Prompts you to enter TOTP code from authenticator app
4. Verifies and enables TOTP
5. Prompts for new code to test login

**Expected output:**
- User registered successfully
- TOTP secret and QR code data URI
- TOTP verification success
- Login with TOTP succeeds and returns JWT token

## Redis Session Testing

To test Redis-backed sessions and rate limiting:

1. Enable Redis in `src/main/resources/META-INF/microprofile-config.properties`:
```properties
redis.enabled=true
session.store=redis
rate.limit.store=redis
```

2. Start Redis (via Phase 1 compose stack):
```bash
cd infra/phase1
docker compose up -d redis-master redis-replica redis-sentinel-1 redis-sentinel-2 redis-sentinel-3
```

3. Rebuild and redeploy:
```bash
mvn clean package
cp target/phoenix-iam.war $WILDFLY_HOME/standalone/deployments/
```

4. Run the OAuth test script - sessions will now be stored in Redis with sliding expiration

## Rate Limiting Test

The OAuth script will trigger rate limiting if you run the login step repeatedly:

```bash
# Trigger rate limit (5 attempts in 15 minutes by default)
for i in {1..6}; do
  curl -s -b /tmp/cookies.txt \
    -X POST "http://localhost:8080/phoenix-iam/login/authorization" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "username=john" \
    --data-urlencode "password=wrong" | head -n1
done
```

Expected: 6th attempt returns HTTP 429 with `Retry-After` header.

## Troubleshooting

### "jq: command not found"
Install jq: `sudo apt install jq` or `brew install jq`

### "Failed to get session cookie"
- Check WildFly is running: `curl -I http://localhost:8080/phoenix-iam/`
- Check datasource is configured: `./setup-datasource.cli`
- Check deployment: `ls $WILDFLY_HOME/standalone/deployments/phoenix-iam.war.deployed`

### "No authorization code in redirect"
- Login failed: check username/password (default: john/SecurePass123!)
- Tenant/identity not seeded: run `/dev/seed` endpoint manually
- Check WildFly logs: `tail -f $WILDFLY_HOME/standalone/log/server.log`

### TOTP verification fails
- Ensure device clock is synchronized (TOTP is time-based)
- Try the previous or next code (30-second window with ±1 tolerance)
- Re-enroll if secret was corrupted

## What Phase 2 Implements

✅ **OAuth 2.1 Authorization Server**
- PKCE enforced (S256 only)
- Strong state validation (16-512 chars, URL-safe pattern)
- Authorization code flow with redirect
- Token endpoint with code exchange

✅ **MFA (TOTP)**
- Secret generation (Base32, 20 bytes)
- QR code provisioning (SVG data URI)
- Time-based code verification (30s period, ±1 window)
- Login enforcement when enabled

✅ **Session Management**
- Memory or Redis-backed storage
- Sliding expiration (default 5 minutes)
- Cookie-based session tracking (httpOnly, secure, SameSite=Strict)

✅ **JWT Tokens (Ed25519)**
- Rotating or external signing keys
- Claims: subject, roles, tenant_id, scope, jti, iat, exp
- Access token (17 min default) + refresh token (3 hours)

✅ **Rate Limiting**
- Memory or Redis-backed counters
- Configurable limits (default: 5 attempts / 15 min / IP)
- Returns 429 + Retry-After header

✅ **Security Features**
- Argon2id password hashing (23 iterations, 97MB memory)
- X-Forwarded-For / X-Real-IP support for rate limiting
- CSRF protection via state parameter
- Authorization code single-use enforcement
