@echo off
cd /d "%~dp0"
java -jar target\penmate-backend-1.0-SNAPSHOT.jar --spring.profiles.active=local
