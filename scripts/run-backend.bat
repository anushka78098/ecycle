@echo off
cd /d %~dp0..
<<<<<<< HEAD
docker compose up -d db mongo
if errorlevel 1 exit /b %errorlevel%
set SPRING_PROFILES_ACTIVE=dev
call mvn -q -pl backend -am -DskipTests install
if errorlevel 1 exit /b %errorlevel%
call mvn -q -f backend\pom.xml spring-boot:run
=======
set SPRING_PROFILES_ACTIVE=dev
set MAVEN_OPTS=-Duser.timezone=UTC
mvn -q -pl backend -am org.springframework.boot:spring-boot-maven-plugin:3.3.5:run
>>>>>>> 02fd9838c309fba438b0b36e2c6397bb31c02a7e
