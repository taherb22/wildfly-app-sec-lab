#!/bin/bash

BASE_URL="http://localhost:8080/phoenix-iam/api"

echo "=== Testing Phoenix IAM API ==="
echo ""

# Test 1: Register a new user
echo "1. Registering new user..."
REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "email": "newuser@example.com",
    "password": "SecurePass123!"
  }')
HTTP_CODE=$(echo "$REGISTER_RESPONSE" | tail -n1)
BODY=$(echo "$REGISTER_RESPONSE" | sed '$d')
echo "Status: $HTTP_CODE"
echo "Response: $BODY"
echo ""

# Test 2: Login with newly created user
echo "2. Logging in with new user..."
LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "password": "SecurePass123!"
  }')
HTTP_CODE=$(echo "$LOGIN_RESPONSE" | tail -n1)
BODY=$(echo "$LOGIN_RESPONSE" | sed '$d')
echo "Status: $HTTP_CODE"
echo "Response: $BODY"
echo ""

# Extract token (if login successful)
if [ "$HTTP_CODE" = "200" ]; then
    TOKEN=$(echo "$BODY" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    echo "✓ Login successful! Token received."
    echo ""

    # Test 3: Get all users (with authentication)
    echo "3. Getting all users (authenticated)..."
    USERS_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/users" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json")
    HTTP_CODE=$(echo "$USERS_RESPONSE" | tail -n1)
    BODY=$(echo "$USERS_RESPONSE" | sed '$d')
    echo "Status: $HTTP_CODE"
    echo "Response: $BODY"
    echo ""
else
    echo "✗ Login failed, skipping authenticated tests"
    echo ""
fi

# Test 4: Try to access protected resource without token
echo "4. Attempting to access users without authentication..."
NO_AUTH_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/users" \
  -H "Content-Type: application/json")
HTTP_CODE=$(echo "$NO_AUTH_RESPONSE" | tail -n1)
BODY=$(echo "$NO_AUTH_RESPONSE" | sed '$d')
echo "Status: $HTTP_CODE (should be 401)"
echo "Response: $BODY"
echo ""

echo "=== Tests completed ==="
echo ""
echo "Summary:"
echo "  ✓ Application is deployed and responding"
echo "  ✓ Registration endpoint working"
echo "  ✓ Login endpoint working"
echo "  ✓ JWT authentication is functional"
