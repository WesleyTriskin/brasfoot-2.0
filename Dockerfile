FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=America/Sao_Paulo
ENV LANG=pt_BR.UTF-8
ENV LANGUAGE=pt_BR:pt
ENV LC_ALL=pt_BR.UTF-8

# Instalar JDK 8 (runtime nativo exato do Brasfoot 22-23 / Kryo / SunGraphicsEnvironment), TigerVNC, Fluxbox, noVNC e xdotool
RUN apt-get update && apt-get install -y --no-install-recommends \
    locales \
    tzdata \
    openjdk-8-jdk \
    tigervnc-standalone-server \
    fluxbox \
    novnc \
    websockify \
    xdotool \
    x11-xserver-utils \
    procps \
    fonts-dejavu-core \
    fonts-liberation \
    python3 \
    python3-pip \
    python3-aiohttp \
    ca-certificates \
    curl \
    && locale-gen pt_BR.UTF-8 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copiar binários do Brasfoot 2026, Java Agent, código-fonte do Agent, elencos 2026 e assets
COPY brasfoot.jar brasfoot-agent.jar options.bcf caracteristicas_brasil_40_times.json /app/
COPY src /app/src
COPY teams /app/teams
COPY conf_estadual /app/conf_estadual
COPY conf_ligas_nacionais /app/conf_ligas_nacionais
COPY img /app/img
COPY selecoes /app/selecoes
COPY sons /app/sons
COPY trofeus /app/trofeus
COPY sav /app/sav
COPY cloud /app/cloud

# Recompilar brasfoot-agent.jar com o fix de clique Cloud e ajuste de tela em Java 8 nativo
RUN mkdir -p /tmp/build_classes && \
    find /app/src -name "*.java" > /tmp/sources.txt && \
    javac -encoding UTF-8 -source 8 -target 8 -cp "/app/brasfoot.jar:/app/brasfoot-agent.jar" -d /tmp/build_classes @/tmp/sources.txt && \
    jar uf /app/brasfoot-agent.jar -C /tmp/build_classes br && \
    rm -rf /tmp/build_classes /tmp/sources.txt

# Personalizar vnc_lite.html do noVNC e desativar popup fbsetbg/xmessage do Fluxbox
RUN ln -sf /bin/true /usr/bin/fbsetbg && \
    ln -sf /bin/true /usr/bin/xmessage && \
    if [ -f /usr/share/novnc/vnc_lite.html ]; then \
      sed -i 's/<title>.*<\/title>/<title>Brasfoot 2026 Cloud<\/title>/' /usr/share/novnc/vnc_lite.html && \
      sed -i 's/#top_bar {/\#top_bar { display: none !important;/' /usr/share/novnc/vnc_lite.html && \
      sed -i 's/background-color: dimgrey/background-color: #07110d/g' /usr/share/novnc/vnc_lite.html; \
    fi \
    && chmod +x /app/cloud/entrypoint.sh /app/cloud/server.py

EXPOSE 8080

CMD ["/app/cloud/entrypoint.sh"]
