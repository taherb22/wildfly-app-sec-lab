#!/bin/bash

echo "=== Recent WildFly Errors ==="
echo ""

if [ -f "wildfly.log" ]; then
    echo "Last 100 lines with errors/exceptions:"
    grep -i -A 5 "error\|exception\|failed" wildfly.log | tail -100
elif [ -f "$WILDFLY_HOME/standalone/log/server.log" ]; then
    echo "Last 100 lines with errors/exceptions from server.log:"
    grep -i -A 5 "error\|exception\|failed" "$WILDFLY_HOME/standalone/log/server.log" | tail -100
else
    echo "No log files found"
fi
