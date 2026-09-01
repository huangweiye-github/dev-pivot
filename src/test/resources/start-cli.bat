@echo off
chcp 65001 > nul
set "ROOT=%~dp0..\..\.."
pushd "%ROOT%"
if %ERRORLEVEL% NEQ 0 (
    echo Failed to locate project root
    pause
    exit /b 1
)
set "ROOT=%CD%\"

echo === DevPivot CLI ===
echo Project: %ROOT%
echo.

echo [1/3] Resolving dependencies...
call mvn dependency:resolve -q -DincludeScope=runtime
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to resolve dependencies
    pause
    exit /b 1
)

echo [2/3] Compiling...
call mvn compile -q
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Compile failed
    pause
    exit /b 1
)

echo [3/3] Building classpath...
call mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile="%ROOT%target\classpath.txt"
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to resolve classpath
    pause
    exit /b 1
)

echo Starting DevPivot CLI...
powershell -NoProfile -Command "$cp = Get-Content '%ROOT%target\classpath.txt'; '-cp target\classes;' + $cp | Out-File -Encoding ascii '%ROOT%target\java.args'"

java -Dfile.encoding=UTF-8 -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 @"%ROOT%target\java.args" com.hwy.devpivot.cli.jline.JlienMain

popd
pause
