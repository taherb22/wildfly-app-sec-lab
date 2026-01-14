#!/bin/bash

set -e

echo "=== Phoenix IAM - Build and Deploy ==="
echo ""

# Check if WildFly is installed
if [ -z "$WILDFLY_HOME" ]; then
    if [ -d "$HOME/wildfly-38.0.0.Final" ]; then
        export WILDFLY_HOME="$HOME/wildfly-38.0.0.Final"
    elif [ -d "$HOME/wildfly" ]; then
        export WILDFLY_HOME="$HOME/wildfly"
    else
        echo "Error: WILDFLY_HOME not set and WildFly not found in $HOME/wildfly-38.0.0.Final"
        echo "Please run: ./install-wildfly.sh"
        exit 1
    fi
fi

echo "Using WILDFLY_HOME: $WILDFLY_HOME"
echo ""

# Build the project
echo "1. Building project..."
mvn clean package -DskipTests
echo ""

# Start WildFly in background if not running
if ! pgrep -f "wildfly" > /dev/null; then
    echo "2. Starting WildFly..."
    $WILDFLY_HOME/bin/standalone.sh > wildfly.log 2>&1 &
    WILDFLY_PID=$!
    echo "WildFly starting with PID: $WILDFLY_PID"
    echo "Waiting for WildFly to start..."
    sleep 10
else
    echo "2. WildFly already running"
fi
echo ""

# Setup datasource
echo "3. Setting up datasource..."
if [ -f "setup-datasource.cli" ]; then
    $WILDFLY_HOME/bin/jboss-cli.sh --connect --file=setup-datasource.cli 2>/dev/null || echo "Datasource already exists or error occurred"
fi
echo ""

# Deploy application
echo "4. Deploying application..."
cp target/phoenix-iam.war $WILDFLY_HOME/standalone/deployments/
echo "Waiting for deployment..."
sleep 5
echo ""

# Check deployment status
echo "5. Checking deployment status..."
if [ -f "$WILDFLY_HOME/standalone/deployments/phoenix-iam.war.deployed" ]; then
    echo "✓ Application deployed successfully!"
    echo ""
    echo "Application URLs:"
    echo "  - Base URL: http://localhost:8080/phoenix-iam"
    echo "  - API: http://localhost:8080/phoenix-iam/api"
    echo "  - Login: POST http://localhost:8080/phoenix-iam/api/auth/login"
    echo ""
    echo "View logs: tail -f wildfly.log"
elif [ -f "$WILDFLY_HOME/standalone/deployments/phoenix-iam.war.failed" ]; then
    echo "✗ Deployment failed!"
    echo "Check: $WILDFLY_HOME/standalone/deployments/phoenix-iam.war.failed"
else
    echo "⏳ Deployment in progress..."
fi
echo ""
