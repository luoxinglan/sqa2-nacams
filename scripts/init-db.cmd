@echo off
rem ============================================================
rem  SQA2 NACAMS - first-time DB bootstrap (run once, as admin user not required)
rem  Prerequisites: MYSQL_HOME set (or mysql.exe on PATH)
rem  ASCII only (cmd compatibility)
rem ============================================================
setlocal enabledelayedexpansion
if "%MYSQL_HOME%"=="" (
  if exist "C:\Users\25485\mysql8" (set "MYSQL_HOME=C:\Users\25485\mysql8")
)
if "%MYSQL_HOME%"=="" (
  echo [ERROR] Set MYSQL_HOME to your MySQL 8 install dir first.
  echo         e.g. set MYSQL_HOME=C:\mysql-8.0.29-winx64
  exit /b 1
)
echo Using MYSQL_HOME=%MYSQL_HOME%

if not exist "%MYSQL_HOME%\data" (
  echo Initializing data dir (root with EMPTY password) ...
  "%MYSQL_HOME%\bin\mysqld.exe" --initialize-insecure --basedir="%MYSQL_HOME%" --datadir="%MYSQL_HOME%\data" --console
)

echo Creating database and user (root, empty password) ...
"%MYSQL_HOME%\bin\mysql.exe" -u root --skip-password -e "CREATE DATABASE IF NOT EXISTS sqa2_nacams CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS 'sqa2'@'localhost' IDENTIFIED BY 'Sqa2#2026'; CREATE USER IF NOT EXISTS 'sqa2'@'%%' IDENTIFIED BY 'Sqa2#2026'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,REFERENCES,INDEX,ALTER ON sqa2_nacams.* TO 'sqa2'@'localhost'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,REFERENCES,INDEX,ALTER ON sqa2_nacams.* TO 'sqa2'@'%%'; FLUSH PRIVILEGES;"
if errorlevel 1 (
  echo [ERROR] DB bootstrap failed - see message above.
  exit /b 1
)
echo.
echo DB ready: sqa2_nacams / user sqa2 / password Sqa2#2026
echo Next: copy backend\src\main\resources\application-local.yml.example to
echo       application-local.yml and fill db password + BOOTSTRAP password,
echo       then run scripts\start-all.cmd
endlocal
