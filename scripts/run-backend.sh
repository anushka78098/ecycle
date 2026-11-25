#!/usr/bin/env bash
# Development backend: dev profile = demo data, ephemeral JWT secret, mock payments, moderator (resolver) admin@ecycle.local / Admin@12345
set -euo pipefail
cd "$(dirname "$0")/.."
SPRING_PROFILES_ACTIVE=dev MAVEN_OPTS=-Duser.timezone=UTC \
  mvn -q -pl backend -am org.springframework.boot:spring-boot-maven-plugin:3.3.5:run
