#!/usr/bin/env bash
# Development backend: dev profile = demo data, ephemeral JWT secret, mock payments, moderator (resolver) admin@ecycle.local / Admin@12345
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d db mongo
SPRING_PROFILES_ACTIVE=dev mvn -q -pl backend -am spring-boot:run
