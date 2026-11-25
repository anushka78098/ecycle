@echo off
cd /d %~dp0..
set SPRING_PROFILES_ACTIVE=dev
set MAVEN_OPTS=-Duser.timezone=UTC
mvn -q -pl backend -am org.springframework.boot:spring-boot-maven-plugin:3.3.5:run
