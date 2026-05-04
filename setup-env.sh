#!/bin/bash

# OpsVision WMS Investigation Harness - Environment Setup Script

echo "🚀 Setting up OpsVision WMS Investigation Harness..."

# Set environment variables for this session
export GOOGLE_GENAI_API_KEY=YOUR_GOOGLE_GENAI_API_KEY_HERE
export DB_USERNAME=opsvision
export DB_PASSWORD=password
export MCP_BASE_URL=http://localhost:8080

echo "✅ Environment variables set for this session"

# Add to your shell profile for permanent setup
echo ""
echo "📝 To make these permanent, add to your ~/.zshrc:"
echo "export GOOGLE_GENAI_API_KEY=YOUR_GOOGLE_GENAI_API_KEY_HERE"
echo "export DB_USERNAME=opsvision"
echo "export DB_PASSWORD=password"
echo "export MCP_BASE_URL=http://localhost:8080"

echo ""
echo "🔍 Checking Docker containers..."

# Wait for containers to be ready
max_attempts=30
attempt=0

while [ $attempt -lt $max_attempts ]; do
    if docker ps | grep -q "opsvision-postgres.*Up" && docker ps | grep -q "opsvision-redis.*Up"; then
        echo "✅ PostgreSQL and Redis containers are running!"
        break
    else
        echo "⏳ Waiting for containers to start... (attempt $((attempt + 1))/$max_attempts)"
        sleep 5
        ((attempt++))
    fi
done

if [ $attempt -eq $max_attempts ]; then
    echo "❌ Containers did not start within expected time. Please check Docker logs."
    exit 1
fi

echo ""
echo "🎯 Ready to start the application!"
echo "Run: mvn spring-boot:run"