#!/usr/bin/env bash
set -e

export DISPLAY=${DISPLAY:-:99}
export PORT=${PORT:-8080}
export APP_DIR=/app

echo "============================================================"
echo " ⚽ Brasfoot 2026 Cloud Server (TigerVNC + Auto-Fullscreen)"
echo " Display: $DISPLAY | Porta Web: $PORT"
echo "============================================================"

rm -f /tmp/.X99-lock /tmp/.X11-unix/X99

# 1. Iniciar Xtigervnc (Servidor X11 + VNC nativo de alta performance, sem travamento de handshake)
Xtigervnc "$DISPLAY" \
  -geometry 1024x720 \
  -depth 24 \
  -rfbport 5900 \
  -localhost \
  -SecurityTypes None \
  -AlwaysShared \
  -AcceptKeyEvents \
  -AcceptPointerEvents \
  -ac >/tmp/vnc.log 2>&1 &
sleep 1.5

# 2. Iniciar Fluxbox sem barra inferior e maximizar janelas
mkdir -p ~/.fluxbox
cat << 'EOF' > ~/.fluxbox/init
session.screen0.toolbar.visible: false
session.screen0.slit.autoHide: true
session.screen0.workspaces: 1
session.screen0.focusNewWindows: true
EOF
cat << 'EOF' > ~/.fluxbox/apps
[app] (name=.*)
  [Deco] {NONE}
[end]
EOF
fluxbox >/tmp/fluxbox.log 2>&1 &
sleep 1

# 3. Monitor inteligente de resolução / Tela Cheia:
# - Quando está no menu inicial (532x332), ajusta o monitor para 532x332 (para preencher 100% da tela na web, bem grande!)
# - Quando abre o jogo/configuração/editor (1024x720+), ajusta o monitor para o tamanho exato da janela e posiciona em (0,0)!
(
  LAST_RES=""
  while true; do
    ACTIVE_W=""
    ACTIVE_H=""
    TARGET_WID=""
    for wid in $(xdotool search --onlyvisible --name "." 2>/dev/null || true); do
      wname=$(xdotool getwindowname "$wid" 2>/dev/null || true)
      if [[ "$wname" != "FocusProxy" ]] && [[ "$wname" != "Content window" ]]; then
        eval $(xdotool getwindowgeometry --shell "$wid" 2>/dev/null || true)
        if [[ -n "$WIDTH" ]] && (( WIDTH >= 450 )); then
          ACTIVE_W=$WIDTH
          ACTIVE_H=$HEIGHT
          TARGET_WID=$wid
        fi
      fi
    done

    if [[ -n "$TARGET_WID" ]] && [[ -n "$ACTIVE_W" ]] && [[ -n "$ACTIVE_H" ]]; then
      DESIRED_RES="${ACTIVE_W}x${ACTIVE_H}"
      if [[ "$DESIRED_RES" != "$LAST_RES" ]]; then
        xrandr --fb "$DESIRED_RES" 2>/dev/null || true
        LAST_RES="$DESIRED_RES"
      fi
      xdotool windowmove "$TARGET_WID" 0 0 2>/dev/null || true
      xdotool windowfocus "$TARGET_WID" 2>/dev/null || true
    fi
    sleep 0.6
  done
) &

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
      -jar /app/brasfoot.jar >>/tmp/brasfoot.log 2>&1 || true
    echo "[Brasfoot Loop] Processo encerrado. Reiniciando em 2 segundos..."
    sleep 2
  done
) &

# 5. Iniciar Gateway Web na porta $PORT
exec python3 /app/cloud/server.py
