#!/usr/bin/env python3
"""
Brasfoot 2026 Cloud Gateway Server (Render / Railway / Docker)
Unifica na mesma porta ($PORT):
1. Streaming WebSocket (/websockify -> x11vnc 127.0.0.1:5900) e cliente noVNC
2. API Multiplayer de Salas, Controle de Turno (Mouse), Chat em Tempo Real
3. Envio de Teclas de Atalho do Mod (F4 Modo Férias, F8 Simular, F2 Status, etc.) via xdotool
4. Gerenciamento de Saves (.bfg) na pasta sav/ + Backup opcional no Supabase
"""

import asyncio
import json
import os
import subprocess
import time
import urllib.request
from pathlib import Path
from aiohttp import web

PORT = int(os.environ.get("PORT", "8080"))
VNC_HOST = "127.0.0.1"
VNC_PORT = int(os.environ.get("VNC_INTERNAL_PORT", "5900"))
NOVNC_DIR = Path(os.environ.get("NOVNC_DIR", "/usr/share/novnc"))
APP_DIR = Path(os.environ.get("APP_DIR", "/app"))
SAV_DIR = APP_DIR / "sav"
ROOMS_FILE = SAV_DIR / "multiplayer_rooms.json"

SUPABASE_URL = os.environ.get("NEXT_PUBLIC_SUPABASE_URL", "")
SUPABASE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "") or os.environ.get("NEXT_PUBLIC_SUPABASE_ANON_KEY", "")
SUPABASE_BUCKET = os.environ.get("SUPABASE_STORAGE_BUCKET", "brasfoot-saves")

SAV_DIR.mkdir(parents=True, exist_ok=True)

# Estado em memória das salas multiplayer (persistido em sav/multiplayer_rooms.json)
ROOMS = {}


def load_rooms():
    global ROOMS
    if ROOMS_FILE.exists():
        try:
            ROOMS = json.loads(ROOMS_FILE.read_text(encoding="utf-8"))
        except Exception:
            ROOMS = {}
    if "sala-principal" not in ROOMS:
        ROOMS["sala-principal"] = {
            "id": "sala-principal",
            "name": "🏆 Brasileirão 2026 — Sala Principal",
            "season": 2026,
            "maxPlayers": 8,
            "hasPassword": False,
            "password": "",
            "activeTurnPlayerId": None,
            "activeTurnPlayerName": "Livre (Qualquer técnico)",
            "players": {},
            "chat": [
                {
                    "id": "sys-1",
                    "sender": "Sistema Brasfoot 2026",
                    "club": "Mod v2026.1",
                    "text": "Bem-vindos! Adicionem seus treinadores no jogo e usem o botão 'Assumir Vez' para escalar seu time sem conflito de mouse.",
                    "timestamp": int(time.time() * 1000),
                }
            ],
            "createdAt": int(time.time() * 1000),
        }
    save_rooms()


def save_rooms():
    try:
        ROOMS_FILE.write_text(json.dumps(ROOMS, ensure_ascii=False, indent=2), encoding="utf-8")
    except Exception:
        pass


def public_room_view(room: dict) -> dict:
    now_ms = int(time.time() * 1000)
    # Limpar jogadores inativos há mais de 2 minutos
    stale_ids = [
        pid for pid, p in room.get("players", {}).items()
        if now_ms - p.get("lastSeen", 0) > 120_000
    ]
    for pid in stale_ids:
        del room["players"][pid]
        if room.get("activeTurnPlayerId") == pid:
            room["activeTurnPlayerId"] = None
            room["activeTurnPlayerName"] = "Livre (Qualquer técnico)"

    return {
        "id": room["id"],
        "name": room["name"],
        "season": room.get("season", 2026),
        "maxPlayers": room.get("maxPlayers", 8),
        "hasPassword": bool(room.get("password")),
        "activeTurnPlayerId": room.get("activeTurnPlayerId"),
        "activeTurnPlayerName": room.get("activeTurnPlayerName") or "Livre (Qualquer técnico)",
        "players": list(room.get("players", {}).values()),
        "chat": room.get("chat", [])[-60:],
        "createdAt": room.get("createdAt"),
    }


@web.middleware
async def cors_middleware(request: web.Request, handler):
    if request.method == "OPTIONS":
        resp = web.Response(status=204)
    else:
        try:
            resp = await handler(request)
        except web.HTTPException as ex:
            resp = ex
        except Exception as e:
            resp = web.json_response({"error": str(e)}, status=500)

    resp.headers["Access-Control-Allow-Origin"] = "*"
    resp.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
    resp.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization, X-Room-Token"
    # Permitir embutir no iframe da Vercel
    if "X-Frame-Options" in resp.headers:
        del resp.headers["X-Frame-Options"]
    return resp


async def handle_health(request: web.Request):
    # Verificar se o processo java do brasfoot está rodando
    proc = subprocess.run(["pgrep", "-f", "brasfoot.jar"], capture_output=True, text=True)
    game_running = proc.returncode == 0
    saves = [f.name for f in SAV_DIR.glob("*.bfg")]
    return web.json_response({
        "status": "online",
        "gameRunning": game_running,
        "season": 2026,
        "version": "v2026.1 Beta v1",
        "savesCount": len(saves),
        "saves": saves,
        "roomsCount": len(ROOMS),
    })


async def handle_list_rooms(request: web.Request):
    return web.json_response({
        "rooms": [public_room_view(r) for r in ROOMS.values()]
    })


async def handle_create_room(request: web.Request):
    body = await request.json()
    name = (body.get("name") or "Sala Brasfoot 2026").strip()
    password = (body.get("password") or "").strip()
    max_players = int(body.get("maxPlayers") or 8)
    room_id = body.get("id") or f"sala-{int(time.time())}"

    ROOMS[room_id] = {
        "id": room_id,
        "name": name,
        "season": 2026,
        "maxPlayers": max_players,
        "hasPassword": bool(password),
        "password": password,
        "activeTurnPlayerId": None,
        "activeTurnPlayerName": "Livre (Qualquer técnico)",
        "players": {},
        "chat": [
            {
                "id": f"sys-{int(time.time())}",
                "sender": "Sistema",
                "club": "Brasfoot 2026",
                "text": f"Sala '{name}' criada! Bom campeonato a todos!",
                "timestamp": int(time.time() * 1000),
            }
        ],
        "createdAt": int(time.time() * 1000),
    }
    save_rooms()
    return web.json_response({"room": public_room_view(ROOMS[room_id])})


async def handle_get_room(request: web.Request):
    room_id = request.match_info["room_id"]
    room = ROOMS.get(room_id)
    if not room:
        return web.json_response({"error": "Sala não encontrada"}, status=404)
    return web.json_response({"room": public_room_view(room)})


async def handle_join_room(request: web.Request):
    room_id = request.match_info["room_id"]
    room = ROOMS.get(room_id)
    if not room:
        return web.json_response({"error": "Sala não encontrada"}, status=404)

    body = await request.json()
    player_id = body.get("playerId") or f"p-{int(time.time() * 1000)}"
    player_name = (body.get("playerName") or "Treinador").strip()
    club_name = (body.get("club") or "Sem Clube").strip()
    password = (body.get("password") or "").strip()

    if room.get("password") and room["password"] != password:
        return web.json_response({"error": "Senha da sala incorreta."}, status=403)

    is_new = player_id not in room["players"]
    room["players"][player_id] = {
        "id": player_id,
        "name": player_name,
        "club": club_name,
        "lastSeen": int(time.time() * 1000),
    }

    # Se não houver ninguém no controle da vez, dá a vez ao primeiro treinador que entrar
    if not room.get("activeTurnPlayerId"):
        room["activeTurnPlayerId"] = player_id
        room["activeTurnPlayerName"] = f"{player_name} ({club_name})"

    if is_new:
        room["chat"].append({
            "id": f"join-{int(time.time() * 1000)}",
            "sender": "Sistema",
            "club": "Lobby",
            "text": f"⚽ {player_name} ({club_name}) entrou na sala!",
            "timestamp": int(time.time() * 1000),
        })
        save_rooms()

    return web.json_response({"room": public_room_view(room), "playerId": player_id})


async def handle_turn_room(request: web.Request):
    room_id = request.match_info["room_id"]
    room = ROOMS.get(room_id)
    if not room:
        return web.json_response({"error": "Sala não encontrada"}, status=404)

    body = await request.json()
    action = body.get("action", "claim")  # claim | release | next
    player_id = body.get("playerId")
    player_name = body.get("playerName", "Treinador")
    club_name = body.get("club", "")

    players_list = list(room.get("players", {}).values())

    if action == "release":
        room["activeTurnPlayerId"] = None
        room["activeTurnPlayerName"] = "Livre (Qualquer técnico)"
    elif action == "next" and len(players_list) > 1:
        current_id = room.get("activeTurnPlayerId")
        idx = next((i for i, p in enumerate(players_list) if p["id"] == current_id), -1)
        next_player = players_list[(idx + 1) % len(players_list)]
        room["activeTurnPlayerId"] = next_player["id"]
        room["activeTurnPlayerName"] = f"{next_player['name']} ({next_player['club']})"
        room["chat"].append({
            "id": f"turn-{int(time.time() * 1000)}",
            "sender": "Controle de Vez",
            "club": "Turno",
            "text": f"🎮 Vez passada para {next_player['name']} ({next_player['club']})!",
            "timestamp": int(time.time() * 1000),
        })
    else:
        label = f"{player_name} ({club_name})" if club_name else player_name
        room["activeTurnPlayerId"] = player_id
        room["activeTurnPlayerName"] = label

    save_rooms()
    return web.json_response({"room": public_room_view(room)})


async def handle_chat_room(request: web.Request):
    room_id = request.match_info["room_id"]
    room = ROOMS.get(room_id)
    if not room:
        return web.json_response({"error": "Sala não encontrada"}, status=404)

    body = await request.json()
    sender = (body.get("sender") or "Treinador").strip()
    club = (body.get("club") or "").strip()
    text = (body.get("text") or "").strip()
    if not text:
        return web.json_response({"error": "Mensagem vazia"}, status=400)

    room["chat"].append({
        "id": f"msg-{int(time.time() * 1000)}",
        "sender": sender,
        "club": club,
        "text": text,
        "timestamp": int(time.time() * 1000),
    })
    if len(room["chat"]) > 100:
        room["chat"] = room["chat"][-100:]
    save_rooms()
    return web.json_response({"room": public_room_view(room)})


ALLOWED_KEYS = {
    "F2": "F2",       # Central de Estatísticas & BI
    "F3": "F3",       # Atalho Mod
    "F4": "F4",       # Modo Férias
    "F8": "F8",       # Simulação Instantânea de Partida
    "Escape": "Escape",
    "Return": "Return",
    "space": "space",
}


async def handle_send_key(request: web.Request):
    body = await request.json()
    key = body.get("key", "")
    if key not in ALLOWED_KEYS:
        return web.json_response({"error": f"Tecla '{key}' não permitida."}, status=400)

    env = os.environ.copy()
    env["DISPLAY"] = os.environ.get("DISPLAY", ":99")
    subprocess.Popen(["xdotool", "key", ALLOWED_KEYS[key]], env=env)
    return web.json_response({"success": True, "key": key})


async def handle_restart_game(request: web.Request):
    subprocess.Popen(["pkill", "-f", "brasfoot.jar"])
    return web.json_response({"success": True, "message": "Brasfoot 2026 será reiniciado automaticamente em 2 segundos."})


async def handle_list_saves(request: web.Request):
    saves = []
    for f in sorted(SAV_DIR.glob("*.bfg"), key=lambda p: p.stat().st_mtime, reverse=True):
        st = f.stat()
        saves.append({
            "name": f.name,
            "sizeBytes": st.st_size,
            "updatedAt": int(st.st_mtime * 1000),
        })
    return web.json_response({"saves": saves})


async def handle_download_save(request: web.Request):
    filename = Path(request.match_info["filename"]).name
    target = SAV_DIR / filename
    if not target.exists():
        return web.json_response({"error": "Save não encontrado"}, status=404)
    return web.FileResponse(
        path=target,
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


async def handle_upload_save(request: web.Request):
    reader = await request.multipart()
    field = await reader.next()
    if not field or not field.filename:
        return web.json_response({"error": "Arquivo inválido"}, status=400)

    filename = Path(field.filename).name
    if not filename.endswith(".bfg"):
        filename += ".bfg"

    dest = SAV_DIR / filename
    with dest.open("wb") as f:
        while True:
            chunk = await field.read_chunk()
            if not chunk:
                break
            f.write(chunk)

    return web.json_response({"success": True, "filename": filename})


async def handle_websockify(request: web.Request):
    """Proxy WebSocket de baixa latência para a porta TCP do x11vnc (127.0.0.1:5900)"""
    ws = web.WebSocketResponse(protocols=("binary", "base64"))
    await ws.prepare(request)

    try:
        reader, writer = await asyncio.open_connection(VNC_HOST, VNC_PORT)
    except Exception as e:
        await ws.close(code=1011, message=str(e).encode())
        return ws

    async def ws_to_tcp():
        try:
            async for msg in ws:
                if msg.type == web.WSMsgType.BINARY:
                    writer.write(msg.data)
                    await writer.drain()
                elif msg.type == web.WSMsgType.TEXT:
                    writer.write(msg.data.encode("latin1"))
                    await writer.drain()
                elif msg.type in (web.WSMsgType.CLOSE, web.WSMsgType.ERROR):
                    break
        except Exception:
            pass
        finally:
            try:
                writer.close()
            except Exception:
                pass

    async def tcp_to_ws():
        try:
            while not ws.closed:
                data = await reader.read(65536)
                if not data:
                    break
                await ws.send_bytes(data)
        except Exception:
            pass
        finally:
            if not ws.closed:
                await ws.close()

    await asyncio.gather(ws_to_tcp(), tcp_to_ws())
    return ws


async def handle_root(request: web.Request):
    """Redireciona / para o cliente noVNC embutido com autoconnect e resize=scale"""
    raise web.HTTPFound("/vnc_lite.html?autoconnect=true&resize=scale&reconnect=true")


def create_app() -> web.Application:
    load_rooms()
    app = web.Application(middlewares=[cors_middleware], client_max_size=100 * 1024 * 1024)

    # Rotas da API e WebSocket VNC
    app.router.add_get("/api/health", handle_health)
    app.router.add_get("/api/rooms", handle_list_rooms)
    app.router.add_post("/api/rooms", handle_create_room)
    app.router.add_get("/api/rooms/{room_id}", handle_get_room)
    app.router.add_post("/api/rooms/{room_id}/join", handle_join_room)
    app.router.add_post("/api/rooms/{room_id}/turn", handle_turn_room)
    app.router.add_post("/api/rooms/{room_id}/chat", handle_chat_room)
    app.router.add_post("/api/key", handle_send_key)
    app.router.add_post("/api/restart", handle_restart_game)
    app.router.add_get("/api/saves", handle_list_saves)
    app.router.add_get("/api/saves/download/{filename}", handle_download_save)
    app.router.add_post("/api/saves/upload", handle_upload_save)

    # Endpoint WebSocket do noVNC
    app.router.add_get("/websockify", handle_websockify)
    app.router.add_get("/", handle_root)

    # Servir arquivos estáticos do noVNC (/usr/share/novnc)
    if NOVNC_DIR.exists():
        app.router.add_static("/", path=str(NOVNC_DIR), show_index=False)

    return app


if __name__ == "__main__":
    print(f"[Brasfoot Cloud] Iniciando Gateway na porta {PORT}...")
    web.run_app(create_app(), host="0.0.0.0", port=PORT)
