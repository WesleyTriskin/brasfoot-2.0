#!/usr/bin/env bash
set -e

export DISPLAY=${DISPLAY:-:99}
export PORT=${PORT:-8080}
export APP_DIR=/app

echo "============================================================"
echo " ⚽ Brasfoot 2026 Cloud Server (Ultra-Light 0.1 CPU Tuned)"
echo " Display: $DISPLAY | Porta Web: $PORT"
echo "============================================================"

rm -f /tmp/.X99-lock /tmp/.X11-unix/X99 /tmp/vnc.sock

# 1. Iniciar Xtigervnc calibrado para baixa CPU (-FrameRate 15, -CompressLevel 1)
Xtigervnc "$DISPLAY" \
  -geometry 1024x720 \
  -depth 16 \
  -FrameRate 15 \
  -CompressLevel 1 \
  -QualityLevel 5 \
  -rfbunixpath /tmp/vnc.sock \
  -rfbport -1 \
  -SecurityTypes None \
  -AlwaysShared \
  -AcceptKeyEvents \
  -AcceptPointerEvents \
  -BlacklistThreshold 1000000 \
  -BlacklistTimeout 0 \
  -ac >/tmp/vnc.log 2>&1 &
sleep 1

# 2. Iniciar Fluxbox ultra-leve (sem barra, sem bordas, sem fbsetbg, posicionando janelas no centro/topo)
mkdir -p ~/.fluxbox
cat << 'EOF' > ~/.fluxbox/init
session.screen0.toolbar.visible: false
session.screen0.slit.autoHide: true
session.screen0.workspaces: 1
session.screen0.focusNewWindows: true
session.screen0.rootCommand: true
session.screen0.windowPlacement: CenterPlacement
EOF
cat << 'EOF' > ~/.fluxbox/apps
[app] (name=.*)
  [Deco] {NONE}
  [Position] (CENTER) {0 0}
[end]
EOF
fluxbox >/tmp/fluxbox.log 2>&1 &
sleep 0.5

# 3. Loop que mantém o Brasfoot 2026 + Java Agent sempre aberto
# Flags cruciais para 0.1 CPU do Render Free:
# -XX:+UseSerialGC e -XX:TieredStopAtLevel=1 evitam que as threads do JIT C2 travem os 0.1 CPU ao abrir telas!
(
  cd /app
  while true; do
    echo "[Brasfoot Loop] Iniciando brasfoot.jar (Java 8 Ultra-Light)..."
    java \
      -Dfile.encoding=UTF-8 \
      -Dsun.java2d.xrender=false \
      -Dsun.java2d.pmoffscreen=false \
      -XX:+UseSerialGC \
      -XX:TieredStopAtLevel=1 \
      -Xms128m -Xmx320m \
      -javaagent:/app/brasfoot-agent.jar \
      -jar /app/brasfoot.jar >>/tmp/brasfoot.log 2>&1 || true
    echo "[Brasfoot Loop] Processo encerrado. Reiniciando em 2 segundos..."
    sleep 2
  done
) &

# 4. Iniciar Gateway Web na porta $PORT
exec python3 /app/cloud/server.py
