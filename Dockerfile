FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=America/Sao_Paulo
ENV LANG=pt_BR.UTF-8
ENV LANGUAGE=pt_BR:pt
ENV LC_ALL=pt_BR.UTF-8

# Instalar Java 17 (com suporte completo a GUI/Swing), Xvfb, Fluxbox, x11vnc, noVNC, xdotool e Python aiohttp
RUN apt-get update && apt-get install -y --no-install-recommends \
    locales \
    tzdata \
    openjdk-17-jre \
    xvfb \
    x11vnc \
    fluxbox \
    novnc \
    websockify \
    xdotool \
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

# Copiar binários do Brasfoot 2026, Java Agent, elencos 2026 e assets
COPY brasfoot.jar brasfoot-agent.jar options.bcf caracteristicas_brasil_40_times.json /app/
COPY teams /app/teams
COPY conf_estadual /app/conf_estadual
COPY conf_ligas_nacionais /app/conf_ligas_nacionais
COPY img /app/img
COPY selecoes /app/selecoes
COPY sons /app/sons
COPY trofeus /app/trofeus
COPY sav /app/sav
COPY cloud /app/cloud

# Personalizar vnc_lite.html do noVNC para ocupar 100% da tela sem barra superior intrusiva
RUN if [ -f /usr/share/novnc/vnc_lite.html ]; then \
      sed -i 's/<title>.*<\/title>/<title>Brasfoot 2026 Cloud<\/title>/' /usr/share/novnc/vnc_lite.html && \
      sed -i 's/#top_bar {/\#top_bar { display: none !important;/' /usr/share/novnc/vnc_lite.html; \
    fi \
    && chmod +x /app/cloud/entrypoint.sh /app/cloud/server.py

EXPOSE 8080

CMD ["/app/cloud/entrypoint.sh"]
