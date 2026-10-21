@echo off
chcp 65001 >nul
echo ========================================================
echo       RESTAURANDO VERSAO ESTAVEL DO BRASFOOT MOD
echo              Versao v2026.1 Beta v1
echo ========================================================

set BACKUP_DIR=C:\Brasfoot22-23\_backup_estavel_atual
set AGENT_BACKUP=C:\Brasfoot22-23\brasfoot-agent.jar.STABLE_BACKUP

if exist "%AGENT_BACKUP%" (
    copy /Y "%AGENT_BACKUP%" "C:\Brasfoot22-23\brasfoot-agent.jar" >nul
    echo [OK] brasfoot-agent.jar restaurado para v2026.1 Beta v1!
) else if exist "%BACKUP_DIR%\brasfoot-agent.jar" (
    copy /Y "%BACKUP_DIR%\brasfoot-agent.jar" "C:\Brasfoot22-23\brasfoot-agent.jar" >nul
    echo [OK] brasfoot-agent.jar restaurado a partir de _backup_estavel_atual!
) else (
    echo [ERRO] Backup estavel nao encontrado!
    pause
    exit /b 1
)

if exist "%BACKUP_DIR%\Brasfoot2026.exe" (
    copy /Y "%BACKUP_DIR%\Brasfoot2026.exe" "C:\Brasfoot22-23\Brasfoot2026.exe" >nul
    echo [OK] Launcher Brasfoot2026.exe restaurado com sucesso!
)

echo.
echo ========================================================
echo Pronto! Versao estavel restaurada.
echo Inicie o jogo pelo Brasfoot2026.exe normalmente.
echo ========================================================
pause
