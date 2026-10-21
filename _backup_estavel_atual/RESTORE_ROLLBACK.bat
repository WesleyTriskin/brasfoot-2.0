@echo off
chcp 65001 >nul
echo ========================================================
echo       RESTAURANDO VERSAO ESTAVEL DO BRASFOOT MOD
echo              Versao v2026.1 Beta v1
echo ========================================================

set GAME_DIR=C:\Brasfoot22-23\
set BACKUP_DIR=%~dp0

if exist "%BACKUP_DIR%brasfoot-agent.jar" (
    copy /Y "%BACKUP_DIR%brasfoot-agent.jar" "%GAME_DIR%brasfoot-agent.jar" >nul
    echo [OK] brasfoot-agent.jar restaurado para v2026.1 Beta v1!
)

if exist "%BACKUP_DIR%Brasfoot2026.exe" (
    copy /Y "%BACKUP_DIR%Brasfoot2026.exe" "%GAME_DIR%Brasfoot2026.exe" >nul
    echo [OK] Brasfoot2026.exe restaurado para v2026.1 Beta v1!
)

echo.
echo ========================================================
echo Pronto! Versao estavel restaurada com sucesso.
echo ========================================================
pause
