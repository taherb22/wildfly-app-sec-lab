#!/bin/bash

WILDFLY_VERSION="31.0.0.Final"
WILDFLY_DIR="$HOME/wildfly"

echo "Installing WildFly ${WILDFLY_VERSION}..."

# Download WildFly
cd /tmp
wget https://github.com/wildfly/wildfly/releases/download/${WILDFLY_VERSION}/wildfly-${WILDFLY_VERSION}.tar.gz

# Extract
tar -xzf wildfly-${WILDFLY_VERSION}.tar.gz

# Move to home directory
mv wildfly-${WILDFLY_VERSION} $WILDFLY_DIR

# Set permissions
chmod +x $WILDFLY_DIR/bin/*.sh

echo "WildFly installed to: $WILDFLY_DIR"
echo ""
echo "Add this to your ~/.bashrc:"
echo "export WILDFLY_HOME=$WILDFLY_DIR"
echo "export PATH=\$PATH:\$WILDFLY_HOME/bin"
