# Phoenix IAM - Identity and Access Management System

A secure IAM system built with Jakarta EE, deployed on WildFly, featuring JWT authentication, Argon2 password hashing, and MQTT integration.

## Features

- ✅ User registration and authentication
- ✅ JWT token-based authorization
- ✅ Argon2 password hashing
- ✅ Role-based access control
- ✅ RESTful API
- ✅ MQTT event publishing (optional)
- ✅ MicroProfile Config integration

## Technology Stack

- **Jakarta EE 11** - Enterprise Java framework
- **WildFly 31** - Application server
- **Hibernate 6.4** - JPA implementation
- **H2 Database** - In-memory database (development)
- **Nimbus JOSE JWT** - JWT token handling
- **Argon2** - Password hashing
- **Eclipse Paho** - MQTT client

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- WildFly 31.0.0.Final

### Installation

```bash
# 1. Install WildFly (if not already installed)
./install-wildfly.sh

# 2. Set environment variables
export WILDFLY_HOME=$HOME/wildfly
export PATH=$PATH:$WILDFLY_HOME/bin

# 3. Build and deploy
./run-project.sh
```

### Manual Build & Deploy

```bash
# Build the project
mvn clean package

# Start WildFly (if not running)
$WILDFLY_HOME/bin/standalone.sh &

# Setup datasource
$WILDFLY_HOME/bin/jboss-cli.sh --connect --file=setup-datasource.cli

# Deploy
cp target/phoenix-iam.war $WILDFLY_HOME/standalone/deployments/
```

### Testing

```bash
# Run automated tests
./test-api.sh

# Or manually test endpoints
curl -X POST http://localhost:8080/phoenix-iam/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"testuser","email":"test@example.com","password":"Test123!"}'

curl -X POST http://localhost:8080/phoenix-iam/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"testuser","password":"Test123!"}'
```

## API Endpoints

### Public Endpoints (No Authentication)

#### Register User
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "john",
  "email": "john@example.com",
  "password": "SecurePass123!"
}
```

#### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "john",
  "password": "SecurePass123!"
}

Response:
{
  "token": "eyJhbGc...",
  "username": "john",
  "roles": ["USER"]
}
```

#### Enroll MFA (TOTP)
```http
POST /api/auth/mfa/enroll
Content-Type: application/json

{
  "username": "john",
  "password": "SecurePass123!"
}

Response:
{
  "secret": "BASE32SECRET",
  "otpauth_uri": "otpauth://totp/...",
  "qr_data_uri": "data:image/svg+xml;base64,..."
}
```

#### Verify MFA (TOTP)
```http
POST /api/auth/mfa/verify
Content-Type: application/json

{
  "username": "john",
  "password": "SecurePass123!",
  "code": "123456"
}
```

### Protected Endpoints (Require JWT)

#### Get All Users
```http
GET /api/users
Authorization: Bearer <JWT_TOKEN>
```

#### Get User by ID
```http
GET /api/users/{id}
Authorization: Bearer <JWT_TOKEN>
```

#### Delete User
```http
DELETE /api/users/{id}
Authorization: Bearer <JWT_TOKEN>
```

## Configuration

Configuration is managed via MicroProfile Config in:
`src/main/resources/META-INF/microprofile-config.properties`

```properties
# JWT Configuration
jwt.secret=phoenix-iam-secret-key-minimum-32-characters-for-hs256-algorithm
jwt.expiration.minutes=60

# Redis + sessions + rate limiting (set redis.enabled=true to use Redis)
redis.enabled=false
session.store=memory
rate.limit.store=redis

# JWT key source (memory | config | elytron | vault)
jwt.key.source=memory
jwt.key.jwk=
jwt.elytron.store.path=
jwt.elytron.store.password=
jwt.elytron.store.alias=

# MQTT Configuration (optional)
mqtt.broker.url=tcp://localhost:1883
mqtt.client.id=phoenix-iam
mqtt.topic.prefix=phoenix/iam/
```

## Project Structure

```
wildfly-app-sec-lab/
├── src/main/java/xyz/kaaniche/phoenix/iam/
│   ├── entity/          # JPA entities
│   │   └── User.java
│   ├── service/         # Business logic
│   │   ├── JwtService.java
│   │   ├── PasswordService.java
│   │   ├── UserService.java
│   │   └── MqttService.java
│   ├── rest/            # REST endpoints
│   │   ├── JaxRsActivator.java
│   │   ├── AuthResource.java
│   │   └── UserResource.java
│   ├── security/        # Security components
│   │   └── JwtAuthenticationFilter.java
│   └── controllers/     # Additional controllers
├── src/main/resources/
│   └── META-INF/
│       ├── persistence.xml
│       └── microprofile-config.properties
├── src/main/webapp/WEB-INF/
│   ├── beans.xml
│   └── jboss-web.xml
├── pom.xml
├── setup-datasource.cli
├── run-project.sh
├── redeploy.sh
├── test-api.sh
└── README.md
```

## Security Features

### Password Hashing
- **Algorithm**: Argon2id
- **Memory**: 65536 KB
- **Iterations**: 10
- **Parallelism**: 1

### JWT Tokens
- **Algorithm**: HS256
- **Expiration**: 60 minutes (configurable)
- **Claims**: username, roles, issuer, issue time, expiration

### Authentication Filter
- Automatic JWT validation on protected endpoints
- Bearer token authentication
- Role-based access control

## Development Scripts

- `install-wildfly.sh` - Install WildFly
- `run-project.sh` - Build and deploy application
- `redeploy.sh` - Quick rebuild and redeploy
- `test-api.sh` - Test all endpoints
- `check-status.sh` - Check deployment status
- `wait-for-deployment.sh` - Wait for deployment completion
- `check-logs.sh` - View recent logs

## Troubleshooting

### Check Deployment Status
```bash
./check-status.sh
```

### View Logs
```bash
tail -f wildfly.log
# or
tail -f $WILDFLY_HOME/standalone/log/server.log
```

### Redeploy Application
```bash
./redeploy.sh
```

### Clean Build
```bash
mvn clean package
rm -rf $WILDFLY_HOME/standalone/deployments/phoenix-iam.war*
cp target/phoenix-iam.war $WILDFLY_HOME/standalone/deployments/
```

## Production Considerations

1. **Database**: Replace H2 with PostgreSQL/MySQL
2. **JWT Secret**: Use strong, unique secret from environment variables
3. **HTTPS**: Enable SSL/TLS
4. **Password Policy**: Enforce strong password requirements
5. **Rate Limiting**: Add rate limiting to auth endpoints
6. **Logging**: Configure centralized logging
7. **Monitoring**: Add health checks and metrics

## License

This project is for educational purposes.

## Author

Phoenix IAM - Built with Jakarta EE and WildFly
