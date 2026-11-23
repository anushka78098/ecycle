@echo off
cd /d %~dp0..
mvn -q -pl frontend -am javafx:run
