#!/usr/bin/env bash
# Development backend: dev profile = demo data, ephemeral JWT secret, mock payments, moderator (resolver) admin@ecycle.local / Admin@12345
set -euo pipefail
cd "$(dirname "$0")/.."
<<<<<<< HEAD
docker compose up -d db mongo
SPRING_PROFILES_ACTIVE=dev mvn -q -pl backend -am -DskipTests install
SPRING_PROFILES_ACTIVE=dev mvn -q -f backend/pom.xml spring-boot:run
=======
SPRING_PROFILES_ACTIVE=dev MAVEN_OPTS=-Duser.timezone=UTC \
  mvn -q -pl backend -am org.springframework.boot:spring-boot-maven-plugin:3.3.5:run
>>>>>>> 02fd9838c309fba438b0b36e2c6397bb31c02a7e
