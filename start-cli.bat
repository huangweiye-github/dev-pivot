@echo off
set currentWorkDir=%CD%
java -jar -Dfile.encoding=UTF-8 %~dp0\target\dev-pivot-1.0-SNAPSHOT.jar  currentWorkDir=%CD%
pause
