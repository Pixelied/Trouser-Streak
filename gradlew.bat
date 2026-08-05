@echo off
setlocal enabledelayedexpansion
set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
if exist "%WRAPPER_JAR%" (
  if defined JAVA_HOME (
    "%JAVA_HOME%\bin\java.exe" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
  ) else (
    java -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
  )
  exit /b %ERRORLEVEL%
)
set "VERSION=9.5.1"
set "SHA256=bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f"
if defined GRADLE_USER_HOME (set "CACHE=%GRADLE_USER_HOME%\wrapper\manual\gradle-%VERSION%") else (set "CACHE=%USERPROFILE%\.gradle\wrapper\manual\gradle-%VERSION%")
set "ZIP=%CACHE%\gradle-%VERSION%-bin.zip"
set "DIST=%CACHE%\gradle-%VERSION%"
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%CACHE%" mkdir "%CACHE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%ZIP%'; if ((Get-FileHash -Algorithm SHA256 '%ZIP%').Hash.ToLower() -ne '%SHA256%') { throw 'Gradle checksum mismatch' }; $tmp='%CACHE%\extract'; Remove-Item -Recurse -Force $tmp -ErrorAction Ignore; Expand-Archive -LiteralPath '%ZIP%' -DestinationPath $tmp; Move-Item -LiteralPath ($tmp+'\gradle-%VERSION%') -Destination '%DIST%'; Remove-Item -Recurse -Force $tmp"
  if errorlevel 1 exit /b %ERRORLEVEL%
)
call "%DIST%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
