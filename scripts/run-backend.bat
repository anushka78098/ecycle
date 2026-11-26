@echo off
cd /d %~dp0..
docker compose up -d db mongo
set SPRING_PROFILES_ACTIVE=dev
mvn -q -pl backend -am spring-boot:run
