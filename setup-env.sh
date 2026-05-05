#!/bin/bash
# Minimal env setup for the OpsVision harness.
# Source this file (`. setup-env.sh`) — running it in a subshell won't export to your current shell.

export OPENAI_API_KEY="${OPENAI_API_KEY:-}"
export DB_USERNAME=opsvision
export DB_PASSWORD=opsvision

if [ -z "$OPENAI_API_KEY" ]; then
    echo "WARNING: OPENAI_API_KEY is not set. The app will refuse to start."
    echo "         Run: export OPENAI_API_KEY=sk-..."
fi

echo "DB_USERNAME=$DB_USERNAME"
echo "DB_PASSWORD=$DB_PASSWORD"
echo
echo "Ensure PostgreSQL on :5432 (db=opsvision, user=opsvision) and Redis on :6379 are running."
echo "Ensure the MCP server is reachable at http://localhost:8081/mcp."
echo
echo "Then start with: mvn spring-boot:run"
