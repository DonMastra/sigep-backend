@echo off
echo ========================================
echo  SiGEP Backend - Stop Script
echo ========================================
echo.

echo Deteniendo servicios Docker...
docker-compose down

echo.
echo ✓ Servicios detenidos
pause

