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
