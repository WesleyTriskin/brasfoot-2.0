#!/usr/bin/env bash
set -e

export DISPLAY=${DISPLAY:-:99}
export SCREEN_RESOLUTION=${SCREEN_RESOLUTION:-1280x800x24}
export PORT=${PORT:-8080}
export APP_DIR=/app

echo "============================================================"
echo " ⚽ Brasfoot 2026 Cloud Server (Mod v2026.1 Beta v1)"
echo " Display: $DISPLAY ($SCREEN_RESOLUTION) | Porta Web: $PORT"
echo "============================================================"

# Limpar locks antigos do X11 se o container reiniciar
rm -f /tmp/.X99-lock /tmp/.X11-unix/X99

# 1. Iniciar Display Virtual Xvfb
Xvfb "$DISPLAY" -screen 0 "$SCREEN_RESOLUTION" -ac +extension GLX +render -noreset &
sleep 1.5

# 2. Configurar e iniciar gerenciador de janelas leve (Fluxbox sem barra inferior)
mkdir -p ~/.fluxbox
cat << 'EOF' > ~/.fluxbox/init
session.screen0.toolbar.visible: false
session.screen0.slit.autoHide: true
session.screen0.workspaces: 1
EOF
fluxbox >/dev/null 2>&1 &
sleep 1

# 3. Iniciar x11vnc em modo compartilhado (-shared permite vários amigos conectados ao mesmo tempo)
x11vnc -display "$DISPLAY" -forever -shared -nopw -rfbport 5900 -xkb -wait 10 -defer 10 -quiet &
sleep 1

# 4. Loop que mantém o Brasfoot 2026 + Java Agent sempre aberto
(
  cd /app
  while true; do
    echo "[Brasfoot Loop] Iniciando brasfoot.jar com brasfoot-agent.jar..."
    java \
      -Dfile.encoding=UTF-8 \
      -Dsun.java2d.xrender=false \
      -Dawt.useSystemAAFontSettings=on \
      -Dswing.aatext=true \
      -Xms128m -Xmx320m \
      -javaagent:/app/brasfoot-agent.jar \
      -jar /app/brasfoot.jar || true
    echo "[Brasfoot Loop] Processo encerrado. Reiniciando em 2 segundos..."
    sleep 2
  done
) &

# 5. Iniciar Gateway Web (noVNC + WebSocket /websockify + API Multiplayer) na porta $PORT
exec python3 /app/cloud/server.py

