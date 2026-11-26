@echo off
cd /d %~dp0..
docker compose up -d db mongo
if errorlevel 1 exit /b %errorlevel%
set SPRING_PROFILES_ACTIVE=dev
call mvn -q -pl backend -am -DskipTests install
if errorlevel 1 exit /b %errorlevel%
call mvn -q -f backend\pom.xml spring-boot:run
