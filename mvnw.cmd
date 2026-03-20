@REM Maven Wrapper startup script for Windows
@echo off
@REM set title of command window
title %0
@REM enable echoing by setting MAVEN_BATCH_ECHO to 'on'
@if "%MAVEN_BATCH_ECHO%" == "on"  echo %MAVEN_BATCH_ECHO%

set ERROR_CODE=0

@REM Find java.exe
where java >nul 2>nul
if %ERRORLEVEL% equ 0 (
    set JAVA_CMD=java
) else if defined JAVA_HOME (
    set JAVA_CMD="%JAVA_HOME%\bin\java.exe"
) else (
    echo ERROR: JAVA_HOME is not set and java is not in PATH.
    exit /B 1
)

@REM Execute Maven
%JAVA_CMD% -classpath .mvn\wrapper\maven-wrapper.jar "-Dmaven.multiModuleProjectDirectory=%CD%" org.apache.maven.wrapper.MavenWrapperMain %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
exit /B %ERROR_CODE%
