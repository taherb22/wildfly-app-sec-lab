#!/bin/bash

# Find WildFly installation
if [ -d "/opt/wildfly" ]; then
    export WILDFLY_HOME="/opt/wildfly"
elif [ -d "$HOME/wildfly" ]; then
    export WILDFLY_HOME="$HOME/wildfly"
elif [ -d "/usr/local/wildfly" ]; then
    export WILDFLY_HOME="/usr/local/wildfly"
else
    echo "WildFly not found. Please install WildFly first."
    echo "Download from: https://www.wildfly.org/downloads/"
    exit 1
fi

echo "WILDFLY_HOME set to: $WILDFLY_HOME"
echo "WildFly version:"
$WILDFLY_HOME/bin/standalone.sh --version 2>/dev/null || echo "WildFly executable not found"
