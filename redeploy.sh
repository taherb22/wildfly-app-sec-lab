#!/bin/bash

set -e

WILDFLY_HOME=${WILDFLY_HOME:-$HOME/wildfly}
DEPLOY_DIR="$WILDFLY_HOME/standalone/deployments"
APP_NAME="phoenix-iam.war"

echo "=== Redeploying Phoenix IAM ==="
echo ""

# Remove old deployment files
echo "1. Cleaning old deployment..."
rm -f "$DEPLOY_DIR/$APP_NAME"*
echo ""

# Rebuild
echo "2. Building application..."
mvn clean package -DskipTests
echo ""

# Setup datasource
echo "3. Configuring datasource..."
$WILDFLY_HOME/bin/jboss-cli.sh --connect --file=setup-datasource.cli 2>&1 | grep -v "Operation failed" || true
echo ""

# Deploy
echo "4. Deploying application..."
cp target/phoenix-iam.war $DEPLOY_DIR/
echo ""

# Wait for deployment
echo "5. Waiting for deployment..."
./wait-for-deployment.sh
