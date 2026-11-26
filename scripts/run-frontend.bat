@echo off
cd /d %~dp0..
call mvn -q -pl frontend -am -DskipTests install
if errorlevel 1 exit /b %errorlevel%
call mvn -q -f frontend\pom.xml javafx:run
