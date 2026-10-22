#!/usr/bin/env bash
set -e

export DISPLAY=${DISPLAY:-:99}
export SCREEN_RESOLUTION=${SCREEN_RESOLUTION:-1024x740x24}
export PORT=${PORT:-8080}
export APP_DIR=/app

echo "============================================================"
echo " ⚽ Brasfoot 2026 Cloud Server (Mod v2026.1 Beta v1)"
echo " Display: $DISPLAY ($SCREEN_RESOLUTION) | Porta Web: $PORT"
echo "============================================================"

# Limpar locks antigos do X11 se o container reiniciar
rm -f /tmp/.X99-lock /tmp/.X11-unix/X99

# 1. Iniciar Display Virtual Xvfb
Xvfb "$DISPLAY" -screen 0 "$SCREEN_RESOLUTION" -ac +extension GLX +render -noreset >/tmp/xvfb.log 2>&1 &
sleep 1.5

# 2. Configurar e iniciar gerenciador de janelas leve (Fluxbox sem barra inferior)
mkdir -p ~/.fluxbox
cat << 'EOF' > ~/.fluxbox/init
session.screen0.toolbar.visible: false
session.screen0.slit.autoHide: true
session.screen0.workspaces: 1
session.screen0.focusNewWindows: true
EOF
fluxbox >/tmp/fluxbox.log 2>&1 &
sleep 1

# 3. Loop que mantém o x11vnc sempre vivo e blindado contra travamentos de XRandR/SHM/XDamage no Docker
(
  while true; do
    echo "[VNC Loop] Iniciando x11vnc..."
    x11vnc \
      -display "$DISPLAY" \
      -forever \
      -shared \
      -nopw \
      -rfbport 5900 \
      -xkb \
      -noxrandr \
      -noshm \
      -noxdamage \
      -noxrecord \
      -wait 10 \
      -defer 10 >>/tmp/x11vnc.log 2>&1 || true
    echo "[VNC Loop] x11vnc reiniciando em 1s..."
    sleep 1
  done
) &
sleep 1

# 4. Assistente de Janelas X11: garante que a janela inicial do Brasfoot não corte os botões inferiores e fique centralizada
(
  while true; do
    for wid in $(xdotool search --onlyvisible --name "." 2>/dev/null || true); do
      wname=$(xdotool getwindowname "$wid" 2>/dev/null || true)
      if [[ "$wname" == *"Mod Brasfoot"* ]] || [[ "$wname" == *"Brasfoot"* ]]; then
        eval $(xdotool getwindowgeometry --shell "$wid" 2>/dev/null || true)
        # Se for a janela de menu inicial (600x400 original que cortava com a barra de menu), aumenta 36px de altura e centraliza
        if [[ -n "$HEIGHT" ]] && (( HEIGHT >= 380 && HEIGHT <= 435 )) && (( WIDTH >= 580 && WIDTH <= 640 )); then
          xdotool windowsize "$wid" 600 445 2>/dev/null || true
          xdotool windowmove "$wid" 212 140 2>/dev/null || true
        fi
      fi
    done
    sleep 1.5
  done
) &

# 5. Loop que mantém o Brasfoot 2026 + Java Agent sempre aberto
(
  cd /app
  while true; do
    echo "[Brasfoot Loop] Iniciando brasfoot.jar com brasfoot-agent.jar..."
    java \
      -Dfile.encoding=UTF-8 \
      -Dsun.java2d.xrender=false \
      -Dsun.java2d.pmoffscreen=false \
      -Dsun.java2d.noddraw=true \
      -Dawt.useSystemAAFontSettings=on \
      -Dswing.aatext=true \
      -Xms128m -Xmx320m \
      -javaagent:/app/brasfoot-agent.jar \
      -jar /app/brasfoot.jar >>/tmp/brasfoot.log 2>&1 || true
    echo "[Brasfoot Loop] Processo encerrado. Reiniciando em 2 segundos..."
    sleep 2
  done
) &

# 6. Iniciar Gateway Web (noVNC + WebSocket /websockify + API Multiplayer) na porta $PORT
exec python3 /app/cloud/server.py
