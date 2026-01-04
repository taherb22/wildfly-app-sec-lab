# Phoenix IAM — Code, Files, and Structure

This document explains the project structure, the purpose of each main file, and how the request/authentication flow works.

---

## 1) High-level overview

This is a Jakarta EE (WAR) application deployed to WildFly. It provides:

- User registration (`/api/auth/register`)
- User login with JWT issuance (`/api/auth/login`)
- Protected endpoints (e.g. `/api/users`) requiring `Authorization: Bearer <token>`
- Password hashing using Argon2
- Persistence via JPA/Hibernate to a WildFly-managed datasource (`PhoenixDS`)
- Optional MQTT event publishing (if used)

---

## 2) Folder structure (what goes where)

Typical layout (key parts only):

```
wildfly-app-sec-lab/
├─ pom.xml
├─ src/
│  ├─ main/
│  │  ├─ java/
│  │  │  └─ xyz/kaaniche/phoenix/iam/
│  │  │     ├─ entity/          (JPA entities)
│  │  │     ├─ service/         (business logic: password, JWT, user CRUD)
│  │  │     ├─ rest/            (JAX-RS resources + activator)
│  │  │     ├─ security/        (filters / auth helpers)
│  │  │     └─ controllers/     (decorators / app-specific controllers)
│  │  ├─ resources/
│  │  │  └─ META-INF/
│  │  │     ├─ persistence.xml  (JPA persistence unit)
│  │  │     └─ microprofile-config.properties (runtime config)
│  │  └─ webapp/
│  │     └─ WEB-INF/
│  │        ├─ beans.xml        (CDI enablement)
│  │        └─ jboss-web.xml    (context root / container-specific settings)
│  └─ test/
│     └─ resources/
│        └─ META-INF/microprofile-config.properties (test config)
├─ setup-datasource.cli         (WildFly CLI datasource provisioning)
├─ run-project.sh               (build + start + deploy helper)
├─ redeploy.sh                  (rebuild + redeploy helper)
├─ check-status.sh              (deployment status + logs helper)
├─ wait-for-deployment.sh       (polls for .deployed/.failed markers)
└─ test-api.sh                  (smoke tests via curl)
```

---

## 3) Build configuration (pom.xml)

`pom.xml` controls dependencies and packaging:

- `packaging=war`: produces a deployable `phoenix-iam.war`
- `jakarta.jakartaee-api` is `provided`: WildFly supplies Jakarta EE APIs at runtime
- `microprofile-config-api` is `provided`: WildFly supplies MicroProfile Config at runtime
- `argon2-jvm`: password hashing
- `nimbus-jose-jwt`: JWT creation/verification
- `paho mqttv5`: MQTT client (optional runtime feature)
- test deps: JUnit + SmallRye Config (so MP Config works during unit tests)

Why SmallRye Config in tests:
- In unit tests you are not running inside WildFly, so you need an implementation of MicroProfile Config available on the test classpath.

---

## 4) Runtime configuration (MicroProfile Config)

### `src/main/resources/META-INF/microprofile-config.properties`

Holds runtime settings (examples):
- `jwt.secret`: secret key used to sign HS256 tokens (must be >= 32 bytes for HS256)
- `jwt.expiration.minutes`: token lifetime
- `mqtt.*`: broker URL, client id, and topic prefix (if MQTT is used)

How it is consumed:
- The code reads config via MicroProfile Config (either `@ConfigProperty` or programmatic `Config` injection). If a value is missing, defaults are used where implemented.

### `src/test/resources/META-INF/microprofile-config.properties`

Test-only overrides to ensure unit tests run with stable values.

---

## 5) Persistence (JPA/Hibernate)

### `src/main/resources/META-INF/persistence.xml`

Defines the persistence unit and datasource mapping:

- `persistence-unit name="default"`: important if any code injects the default unit implicitly
- `jta-data-source` references the WildFly datasource JNDI name:
  - `java:jboss/datasources/PhoenixDS`
- Schema generation options are set for development (e.g., `drop-and-create` or `update` depending on your current config)

WildFly manages:
- connection pooling
- transaction integration (JTA)

---

## 6) CDI and WAR wiring

### `src/main/webapp/WEB-INF/beans.xml`

Enables CDI discovery. Without this, `@Inject` may not behave as expected depending on server defaults.

### `src/main/webapp/WEB-INF/jboss-web.xml` (if present)

Commonly used to set the application context root:
- `/phoenix-iam`
So the base URL becomes:
- `http://localhost:8080/phoenix-iam`

---

## 7) Application entrypoints (REST)

### `xyz.kaaniche.phoenix.iam.rest.JaxRsActivator`

A Jakarta REST `Application` class with:
- `@ApplicationPath("/api")`

This makes all REST resources reachable under `/api/...`.

Example:
- `/phoenix-iam/api/auth/login`
- `/phoenix-iam/api/users`

### `xyz.kaaniche.phoenix.iam.rest.AuthResource`

Responsibilities:
- `POST /auth/register`: create user (hash password, store user, default role assignment)
- `POST /auth/login`: authenticate and return a JWT on success

Flow for `/login`:
1. Read JSON body (`username`, `password`)
2. Validate credentials against stored hash
3. If valid, create JWT containing:
   - subject (`sub`) = username
   - roles claim (e.g. `["USER"]`)
   - `iat` + `exp`
4. Return token in JSON response

### `xyz.kaaniche.phoenix.iam.rest.UserResource`

Responsibilities:
- User CRUD-like endpoints (example shown in smoke tests: list users)

Important note:
- Returning `User` entities directly currently exposes `passwordHash` in the JSON response. This is functional but not acceptable for a real system. The usual fix is to return a DTO that excludes secrets.

---

## 8) Entity layer

### `xyz.kaaniche.phoenix.iam.entity.User`

Represents a user record:
- `id`, `username`, `email`
- `passwordHash` (Argon2)
- `roles` (collection mapped to a separate table, e.g. `user_roles`)
- timestamps like `createdAt`, `lastLogin`
- `enabled` flag

JPA annotations drive schema generation and mapping.

---

## 9) Service layer

### `xyz.kaaniche.phoenix.iam.service.PasswordService`

Uses Argon2 for:
- hashing new passwords
- verifying login attempts against stored hashes

Key property:
- passwords are never stored or compared in plain text.

### `xyz.kaaniche.phoenix.iam.service.UserService`

Encapsulates persistence operations:
- `createUser(...)`: persists new user with hashed password and roles
- `findByUsername(...)`, `findAll()`, `deleteUser(...)`, etc.
- `authenticate(...)`: verifies enabled user + Argon2 verification

Uses:
- `EntityManager` (`@PersistenceContext`)
- `@Transactional` on write operations

### `xyz.kaaniche.phoenix.iam.service.JwtService`

Encapsulates JWT operations:
- `generateToken(username, roles)`: signs an HS256 JWT using configured secret
- `validateToken(token)`: verifies signature and expiration and returns claims

Implementation detail that mattered during debugging:
- MicroProfile config must actually provide `jwt.secret`. If `jwtSecret` becomes null, token generation fails.
- Ensures HS256 secret length requirements (>= 32 bytes) by validation/padding if implemented.

### `xyz.kaaniche.phoenix.iam.service.MqttService` (optional)

If enabled/used:
- Connects to MQTT broker on startup
- Publishes IAM events to topic(s) like `phoenix/iam/<event>`

---

## 10) Security layer

### `xyz.kaaniche.phoenix.iam.security.JwtAuthenticationFilter`

A JAX-RS `ContainerRequestFilter` that protects endpoints by default:

- Allows unauthenticated access to:
  - `/auth/login`
  - `/auth/register`
- For all other endpoints:
  1. Reads `Authorization` header
  2. Requires `Bearer <token>`
  3. Validates token
  4. Sets a `SecurityContext` with principal + roles
  5. Rejects invalid/missing tokens with HTTP 401

This is why:
- `/api/users` returns 401 without a token
- `/api/users` returns 200 when a valid token is provided

---

## 11) Controllers / decorators (AuthorizationDecorator)

### `xyz.kaaniche.phoenix.iam.controllers.AuthorizationDecorator`

This is a CDI decorator over a `GenericDAO` that enforces authorization checks before delegating operations like save/edit/delete.

Key points:
- It builds a set of permissions for the current identity’s roles.
- It checks if the action on the entity is allowed.
- It throws `NotAuthorizedException` if not permitted.

A prior bug occurred in `Permission.implies(...)` due to Java pattern matching / casting rules between `java.security.Permission` and the inner `Permission` type; the safe approach is an explicit class check (`permission.getClass() == Permission.class`) + cast, then compare fields/attributes.

---

## 12) WildFly provisioning and run scripts

### `setup-datasource.cli`

Creates the datasource `PhoenixDS` in WildFly:
- JNDI name: `java:jboss/datasources/PhoenixDS`
- Uses H2 driver for dev
- The application’s `persistence.xml` points at this JNDI name

### `run-project.sh`

Automates:
1. Maven build (`mvn clean package -DskipTests`)
2. Start WildFly (if not running)
3. Provision datasource (if script present)
4. Copy WAR to deployments directory
5. Prints URLs / hints

### `redeploy.sh`

Automates:
- remove old deployment marker files
- rebuild
- redeploy

### `check-status.sh` / `wait-for-deployment.sh`

- `check-status.sh`: shows `.deployed` / `.failed` state and tails logs
- `wait-for-deployment.sh`: polls for deployment markers until success/failure/timeout

### `test-api.sh`

Smoke tests the API with `curl`:
1. Register user
2. Login, extract token
3. Call a protected endpoint with token
4. Verify protected endpoint fails without token (401)

---

## 13) What “working” currently means (and what is next)

What is working now:
- Registration persists users
- Login verifies Argon2 hash
- JWT token issuance works
- JWT validation protects endpoints

Known issues / follow-ups for a production-grade system:
- Do not return `passwordHash` in REST responses (use DTOs or JSON ignore annotations)
- Add role-based authorization on endpoints (e.g., only ADMIN can delete users)
- Use HTTPS and secure secret management for `jwt.secret`
- Consider refresh tokens / revocation strategy if needed
- Replace H2 with a real DB and migrations (Flyway/Liquibase)

---

## 14) Recently added files (what they do and how they connect)

This section is a “map” of the files we added/modified recently to get the app building, deploying, and working end-to-end.

### 14.1 Application code (runtime)

#### REST bootstrap
- **`src/main/java/xyz/kaaniche/phoenix/iam/rest/JaxRsActivator.java`**
  - **Purpose**: Enables JAX-RS and sets the API base path via `@ApplicationPath("/api")`.
  - **Effect**: All REST resources are reachable under `/phoenix-iam/api/...`.

#### Authentication endpoints
- **`src/main/java/xyz/kaaniche/phoenix/iam/rest/AuthResource.java`**
  - **Purpose**: Public endpoints for registration + login.
  - **Key behaviors**:
    - `POST /api/auth/register`: creates user; hashes password; assigns default role `USER`.
    - `POST /api/auth/login`: verifies password; issues JWT (`token`) on success.
  - **Dependencies**:
    - `UserService` for persistence/auth checks
    - `JwtService` for token generation

#### User endpoints (protected)
- **`src/main/java/xyz/kaaniche/phoenix/iam/rest/UserResource.java`**
  - **Purpose**: Protected endpoints for listing/fetching/deleting users.
  - **Important**: Right now it returns the `User` entity directly, which includes `passwordHash`.
    - Production approach: return a DTO that excludes secrets.

#### Entity (persistence model)
- **`src/main/java/xyz/kaaniche/phoenix/iam/entity/User.java`**
  - **Purpose**: JPA entity mapped to `users` table.
  - **Highlights**:
    - `username` and `email` are unique
    - `passwordHash` stores Argon2 hash (never plaintext)
    - `roles` stored in a separate join table via `@ElementCollection`

#### Services (business logic)
- **`src/main/java/xyz/kaaniche/phoenix/iam/service/PasswordService.java`**
  - **Purpose**: Argon2 hashing + verification.
  - **Used by**: `UserService.createUser(...)` (hashing) and `UserService.authenticate(...)` (verification).

- **`src/main/java/xyz/kaaniche/phoenix/iam/service/UserService.java`**
  - **Purpose**: User persistence operations + authentication check.
  - **Uses**:
    - `@PersistenceContext EntityManager` for DB access
    - `@Transactional` on write operations (persist/merge/remove)
    - `PasswordService` to verify password hashes

- **`src/main/java/xyz/kaaniche/phoenix/iam/service/JwtService.java`**
  - **Purpose**: JWT creation (HS256 signing) and validation (signature + expiry).
  - **Config dependency**:
    - Reads `jwt.secret` and `jwt.expiration.minutes` from MicroProfile Config.
  - **Why it mattered**: Token generation previously failed because `jwt.secret` resolved to null. The fix was to ensure runtime config exists and/or provide fallback behavior.

- **`src/main/java/xyz/kaaniche/phoenix/iam/service/MqttService.java`** (optional)
  - **Purpose**: Publishes IAM events to MQTT (if broker is reachable).
  - **Config**:
    - `mqtt.broker.url`, `mqtt.client.id`, `mqtt.topic.prefix`
  - **Lifecycle**:
    - Connects in `@PostConstruct`
    - Disconnects in `@PreDestroy`
  - **Note**: If MQTT broker isn’t running, it logs warnings and continues.

#### Security (request filter)
- **`src/main/java/xyz/kaaniche/phoenix/iam/security/JwtAuthenticationFilter.java`**
  - **Purpose**: Protects endpoints by validating `Authorization: Bearer <token>`.

---

## 15) SecureGate IAM Portal — Implementation Plan (per FINAL TECHNICAL SPEC)

This section turns the provided final specification into an actionable engineering plan.

### 15.1 Target product definition (what we are building)

**Name**: SecureGate IAM Portal  
**Type**: Secure enterprise IAM PWA with Zero Trust + data dissimulation

**Core functions to deliver**
- OAuth 2.1 Authorization Server + Resource Server validation
- Token lifecycle: issuance, rotation, revocation; replay protection (`jti`)
- MFA: TOTP enrollment + verification
- ABAC policy management (store + evaluate) and enforcement gateway
- Real-time audit logging + event streaming (WebSocket)
- Device trust scoring (incremental; start with deterministic signals)
- Steganographic secure transfer: AES-256-GCM + DCT/LSB embedding

### 15.2 Target deployment topology (3 domains)

1) **iam.yourdomain.me** (Identity Provider + OAuth2.1 AS)
- WildFly 38.0.1.Final (Preview) + Jakarta EE 11
- Elytron-based auth mechanisms
- Token engines:
  - JWT for OAuth flows (prefer RS256/ES256, strict alg whitelist)
  - PASETO v4 for internal service-to-service authentication
- Backing services:
  - PostgreSQL 16 (multi-tenancy + RLS)
  - Redis 7.2 (sessions + token replay prevention store)
  - Vault credential store integration (secrets)

2) **api.yourdomain.me** (Resource Server + ABAC enforcement gateway)
- JAX-RS APIs + validation filter
- ABAC decision point / policy evaluation
- Redis: `jti` tracking, rate limiting, session cache
- Artemis MQ: async audit/security events distribution
- mTLS required from iam → api for privileged flows

3) **www.yourdomain.me** (PWA Frontend)
- Lit 3.x components + Workbox 7.x service worker
- IndexedDB encryption (WebCrypto) for safe local persistence
- Real-time channel: WebSocket + SSE fallback
- CSP3 + SRI + DOMPurify

### 15.3 Security architecture plan (Zero Trust controls)

**Per-request verification**
- Validate token (PASETO or JWT) with strict alg whitelist and audience checks
- Bind token to device context:
  - Start: device_id claim + required header match
  - Extend: TLS fingerprint, geo/time, behavioral signals

**mTLS micro-segmentation**
- Separate cert chains for iam/api
- Client cert pinning for service-to-service requests
- Rotation policy (e.g., 90 days)

**Token replay prevention**
- Require `jti` on all access tokens
- Store `jti` in Redis with TTL matching token expiry
- Reject duplicates (replay) at api gateway

**Mitigations explicitly required by spec**
- Algorithm confusion: never accept HS* if using asymmetric verification; whitelist RS256/ES256 only for JWT; reject `none`
- Restrict WildFly management plane access (localhost/SSH tunnel)
- Disable EJB remote invocation if not needed (reduce deserialization surface)
- TLS 1.3 enforcement + HSTS

### 15.4 Data dissimulation (AES-GCM + DCT/LSB) delivery plan

**Module boundary**
- A dedicated “dissimulation service” inside `api.*` (or a separate internal service) with:
  - Encrypt: AES-256-GCM (96-bit IV, 128-bit tag)
  - KDF: PBKDF2-SHA512 (100k + salt) (per spec; consider Argon2id later)
  - Stego: DCT block transform + embed bits in mid-frequency coefficients
  - Quality gates: PSNR threshold (≥ 45dB target; reject low PSNR)

**Storage**
- Cover images managed in MinIO (S3-compatible)
- Audit exports and policy bundles embedded into cover images

### 15.5 Implementation roadmap (phases & acceptance criteria)

#### Phase 1 — Infrastructure foundation (Weeks 1–2)
Deliverables:
- WildFly 38.0.1.Final deployments for iam/api/www (or containers)
- PostgreSQL 16 cluster + backup strategy
- Redis 7.2 Sentinel/Cluster
- Traefik 3.x with TLS 1.3, HSTS preload headers
- Vault initialized for secrets / cert material
Acceptance criteria:
- All three services reachable on correct hosts
- TLS 1.3 only; TLS 1.2 refused
- Management endpoints restricted (not publicly reachable)

#### Phase 2 — IAM service (Weeks 3–4)
Deliverables:
- OAuth 2.1 authorization server:
  - PKCE enforced
  - State validation strong (CSRF-resistant)
- MFA:
  - TOTP enrollment (QR) + verification
- Session store in Redis (sliding expiration)
- JWT signing keys stored in Vault / Elytron credential store
Acceptance criteria:
- Auth code flow with PKCE works end-to-end
- MFA enrollment + login works
- Rate limiting in place (e.g., 5 attempts/15 min/IP)

#### Phase 3 — API gateway & ABAC (Weeks 5–6)
Deliverables:
- Resource server validation filter:
  - JWT: RS256/ES256 only; strict `aud` validation
  - PASETO for internal calls
- ABAC policy storage + evaluation:
  - Start with JSON policy model; evolve to visual builder later
- Token replay prevention:
  - `jti` required + Redis tracking
- WebSocket endpoint secured by token
Acceptance criteria:
- Requests without valid token denied
- JWT alg confusion tests fail (rejected)
- Replay tests fail (duplicate `jti` rejected)
- ABAC deny/permit decisions logged

#### Phase 4 — Steganography module (Week 7)
Deliverables:
- AES-256-GCM encryption service
- DCT/LSB embed/extract pipeline (OpenCV Java bindings)
- MinIO integration for cover images
- PSNR/MSE validation
Acceptance criteria:
- Embedding/extraction roundtrip passes
- PSNR thresholds enforced
- JPEG compression robustness target met (per spec)

#### Phase 5 — PWA frontend (Weeks 8–9)
Deliverables:
- Lit UI, offline queue, background sync
- Secure storage strategy (no token caching in service worker)
- CSP nonces, SRI, DOMPurify
Acceptance criteria:
- Lighthouse PWA targets met
- Offline UI does not leak tokens
- CSP violations monitored

#### Phase 6 — Security hardening (Weeks 10–11)
Deliverables:
- ZAP, Dependency-Check, Trivy, SAST gating
- Algorithm confusion tests, replay tests, SQLi/XSS test suite
- Observability dashboards (Prometheus/Grafana/Loki)
Acceptance criteria:
- No HIGH/CRITICAL CVEs in build artifacts
- Automated security tests pass

#### Phase 7 — Production deployment (Week 12)
Deliverables:
- All go-live checklist items satisfied
- DR test (RTO/RPO) executed
Acceptance criteria:
- Compliance evidence gathered (SOC2/ISO style artifacts)
- Immutable audit log retention configured

### 15.6 Gap vs current implementation (what exists today)

**Already implemented in this repo (baseline)**
- Basic user registration + login
- Argon2 password hashing
- HS256 JWT generation/validation
- JAX-RS endpoints and request filter protecting `/api/users`
- WildFly deployment scripts and datasource wiring for development

**Not yet implemented (required by spec)**
- OAuth 2.1 authorization server (auth code + PKCE, state binding)
- PASETO v4 for internal iam ↔ api authentication
- ABAC policy engine + UI policy builder
- Redis-backed session store + `jti` replay prevention + rate limiting
- mTLS between iam and api
- WebSocket security event streaming + audit pipeline (Artemis, Loki)
- Steganography module (AES-GCM + DCT/LSB) + MinIO cover image store
- Production hardening: alg whitelist RS256/ES256, management-plane lockdown, remove EJB remote, WAF, etc.

---
