@echo off
echo ========================================
echo  SiGEP Backend - Startup Script
echo ========================================
echo.

echo [1/4] Iniciando servicios Docker (PostgreSQL + Redis)...
docker-compose up -d
if %errorlevel% neq 0 (
    echo Error: No se pudieron iniciar los servicios Docker
    echo Asegurate de tener Docker Desktop corriendo
    pause
    exit /b 1
)
echo ✓ Servicios Docker iniciados

echo.
echo [2/4] Esperando a que los servicios esten listos...
timeout /t 10 /nobreak > nul
echo ✓ Servicios listos

echo.
echo [3/4] Compilando el proyecto...
call gradlew clean build -x test
if %errorlevel% neq 0 (
    echo Error en la compilacion
    pause
    exit /b 1
)
echo ✓ Proyecto compilado exitosamente

echo.
echo [4/4] Iniciando la aplicacion...
echo.
echo La aplicacion estara disponible en: http://localhost:8080
echo Swagger UI: http://localhost:8080/swagger-ui.html
echo.
call gradlew :application:bootRun

