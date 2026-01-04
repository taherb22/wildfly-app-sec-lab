#!/bin/bash

WILDFLY_HOME=${WILDFLY_HOME:-$HOME/wildfly}
DEPLOY_DIR="$WILDFLY_HOME/standalone/deployments"
APP_NAME="phoenix-iam.war"
MAX_WAIT=60
WAITED=0

echo "Waiting for deployment (max ${MAX_WAIT}s)..."

while [ $WAITED -lt $MAX_WAIT ]; do
    if [ -f "$DEPLOY_DIR/$APP_NAME.deployed" ]; then
        echo ""
        echo "✓ Deployment successful!"
        echo ""
        echo "Test the API with:"
        echo "  curl http://localhost:8080/phoenix-iam/api/auth/login -H 'Content-Type: application/json' -d '{\"username\":\"test\",\"password\":\"test\"}'"
        exit 0
    elif [ -f "$DEPLOY_DIR/$APP_NAME.failed" ]; then
        echo ""
        echo "✗ Deployment failed!"
        echo ""
        cat "$DEPLOY_DIR/$APP_NAME.failed"
        echo ""
        echo "Check logs: tail -f wildfly.log"
        exit 1
    fi
    
    echo -n "."
    sleep 2
    WAITED=$((WAITED + 2))
done

echo ""
echo "⏳ Deployment timeout reached"
echo "Check status manually: ./check-status.sh"
