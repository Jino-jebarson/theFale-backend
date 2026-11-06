@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Script pointing to existing Maven 3.9.9 installation
@REM ----------------------------------------------------------------------------
@echo off
set "MAVEN_CMD=C:\Users\Dell\.m2\wrapper\dists\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd"
if exist "%MAVEN_CMD%" (
    "%MAVEN_CMD%" %*
) else (
    mvn %*
)
