#!/usr/bin/env bash
set -e

export DISPLAY=${DISPLAY:-:99}
export PORT=${PORT:-8080}
export APP_DIR=/app

echo "============================================================"
echo " ⚽ Brasfoot 2026 Cloud Server (TigerVNC + Auto-Fullscreen)"
echo " Display: $DISPLAY | Porta Web: $PORT"
echo "============================================================"

rm -f /tmp/.X99-lock /tmp/.X11-unix/X99 /tmp/vnc.sock

# 1. Iniciar Xtigervnc via Unix Socket (/tmp/vnc.sock) + porta interna 59099 com Blacklist desativada
Xtigervnc "$DISPLAY" \
  -geometry 1024x720 \
  -depth 24 \
  -rfbunixpath /tmp/vnc.sock \
  -rfbport -1 \
  -SecurityTypes None \
  -AlwaysShared \
  -AcceptKeyEvents \
  -AcceptPointerEvents \
  -BlacklistThreshold 1000000 \
  -BlacklistTimeout 0 \
  -ac >/tmp/vnc.log 2>&1 &
sleep 1.5

# 2. Iniciar Fluxbox sem barra inferior e sem aviso de wallpaper (fbsetbg)
mkdir -p ~/.fluxbox
cat << 'EOF' > ~/.fluxbox/init
session.screen0.toolbar.visible: false
session.screen0.slit.autoHide: true
session.screen0.workspaces: 1
session.screen0.focusNewWindows: true
session.screen0.rootCommand: true
EOF
cat << 'EOF' > ~/.fluxbox/apps
[app] (name=.*)
  [Deco] {NONE}
[end]
EOF
fluxbox >/tmp/fluxbox.log 2>&1 &
sleep 1

# 3. Monitor inteligente de resolução / Tela Cheia:
# Pega sempre a MAIOR janela visível do Brasfoot para nunca cortar telas como Configurações Iniciais (a.hU) ou o Jogo
(
  LAST_RES=""
  while true; do
    MAX_W=0
    MAX_H=0
    TOP_WID=""
    for wid in $(xdotool search --onlyvisible --name "." 2>/dev/null || true); do
      wname=$(xdotool getwindowname "$wid" 2>/dev/null || true)
      if [[ "$wname" != "FocusProxy" ]] && [[ "$wname" != "Content window" ]] && [[ "$wname" != *"xmessage"* ]]; then
        eval $(xdotool getwindowgeometry --shell "$wid" 2>/dev/null || true)
        if [[ -n "$WIDTH" ]] && (( WIDTH >= 350 )); then
          xdotool windowmove "$wid" 0 0 2>/dev/null || true
          if (( WIDTH > MAX_W )); then MAX_W=$WIDTH; fi
          if (( HEIGHT > MAX_H )); then MAX_H=$HEIGHT; fi
          TOP_WID=$wid
        fi
      fi
    done

    if (( MAX_W > 0 && MAX_H > 0 )); then
      DESIRED_RES="${MAX_W}x${MAX_H}"
      if [[ "$DESIRED_RES" != "$LAST_RES" ]]; then
        xrandr --fb "$DESIRED_RES" 2>/dev/null || true
        LAST_RES="$DESIRED_RES"
      fi
      if [[ -n "$TOP_WID" ]]; then
        xdotool windowfocus "$TOP_WID" 2>/dev/null || true
      fi
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
