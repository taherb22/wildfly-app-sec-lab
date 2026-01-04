#!/bin/bash

WILDFLY_HOME=${WILDFLY_HOME:-$HOME/wildfly}
DEPLOY_DIR="$WILDFLY_HOME/standalone/deployments"
APP_NAME="phoenix-iam.war"

echo "=== Checking Phoenix IAM Deployment Status ==="
echo ""

# Check if WildFly is running
if pgrep -f "wildfly" > /dev/null; then
    echo "✓ WildFly is running"
    echo "  PID: $(pgrep -f wildfly)"
else
    echo "✗ WildFly is not running"
    exit 1
fi
echo ""

# Check deployment files
echo "Deployment files in $DEPLOY_DIR:"
ls -lh "$DEPLOY_DIR/$APP_NAME"* 2>/dev/null || echo "  No deployment files found"
echo ""

# Check deployment status
if [ -f "$DEPLOY_DIR/$APP_NAME.deployed" ]; then
    echo "✓ Application DEPLOYED successfully!"
    echo ""
    echo "Application is available at:"
    echo "  http://localhost:8080/phoenix-iam/api"
elif [ -f "$DEPLOY_DIR/$APP_NAME.failed" ]; then
    echo "✗ Application deployment FAILED"
    echo ""
    echo "Error details:"
    cat "$DEPLOY_DIR/$APP_NAME.failed"
elif [ -f "$DEPLOY_DIR/$APP_NAME.isdeploying" ]; then
    echo "⏳ Application is currently DEPLOYING..."
elif [ -f "$DEPLOY_DIR/$APP_NAME.pending" ]; then
    echo "⏳ Application deployment is PENDING..."
elif [ -f "$DEPLOY_DIR/$APP_NAME.undeployed" ]; then
    echo "○ Application is UNDEPLOYED"
else
    echo "? Unknown deployment status"
fi
echo ""

# Show recent logs
echo "=== Recent WildFly logs ==="
if [ -f "wildfly.log" ]; then
    echo "Last 30 lines from wildfly.log:"
    tail -n 30 wildfly.log
elif [ -f "$WILDFLY_HOME/standalone/log/server.log" ]; then
    echo "Last 30 lines from server.log:"
    tail -n 30 "$WILDFLY_HOME/standalone/log/server.log"
else
    echo "No log files found"
fi
