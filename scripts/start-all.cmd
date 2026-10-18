@echo off
rem ============================================================
rem  SQA2 NACAMS - start MySQL (if needed) + backend + frontend
rem  ASCII only (cmd compatibility)
rem ============================================================
setlocal
set "REPO=%~dp0.."

rem ---- locate JDK ----
if "%JAVA_HOME%"=="" (
  if exist "C:\Users\25485\jdk21" (set "JAVA_HOME=C:\Users\25485\jdk21")
)
if "%JAVA_HOME%"=="" (
  echo [WARN] JAVA_HOME not set - set it to JDK 17/21 install dir.
)

rem ---- locate MySQL ----
if "%MYSQL_HOME%"=="" (
  if exist "C:\Users\25485\mysql8" (set "MYSQL_HOME=C:\Users\25485\mysql8")
)
if not "%MYSQL_HOME%"=="" (
  netstat -ano | findstr ":3306 .*LISTENING" >nul 2>&1
  if errorlevel 1 (
    echo Starting MySQL from %MYSQL_HOME% ...
    start "sqa2-mysql" "%MYSQL_HOME%\bin\mysqld.exe" --console --basedir="%MYSQL_HOME%" --datadir="%MYSQL_HOME%\data" --port=3306 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci --default-time-zone=+08:00
    timeout /t 5 /nobreak >nul
  ) else (
    echo MySQL already running on 3306.
  )
)

rem ---- backend (Spring Boot, port 8080) ----
echo Starting backend on http://localhost:8080 ...
start "sqa2-backend" cmd /c "cd /d "%REPO%" && set "JAVA_HOME=%JAVA_HOME%" && set "PATH=%JAVA_HOME%\bin;%PATH%" && C:\Users\25485\maven\bin\mvn.cmd -f backend\pom.xml spring-boot:run"

rem ---- frontend (Vite dev server, port 5173) ----
echo Starting frontend on http://localhost:5173 ...
start "sqa2-frontend" cmd /c "cd /d "%REPO%\frontend" && npm run dev"

echo.
echo ================================================================
echo  Portal:   http://localhost:5173
echo  Swagger:  http://localhost:8080/swagger-ui.html
echo  Admin:    admin (password = BOOTSTRAP_PASSWORD in application-local.yml)
echo  Close the three popup windows to stop the system.
echo ================================================================
endlocal
