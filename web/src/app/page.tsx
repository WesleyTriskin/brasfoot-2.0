"use client";

import React, { useEffect, useState, useRef } from "react";

interface Player {
  id: string;
  name: string;
  club: string;
  lastSeen: number;
}

interface ChatMessage {
  id: string;
  sender: string;
  club: string;
  text: string;
  timestamp: number;
}

interface Room {
  id: string;
  name: string;
  season: number;
  maxPlayers: number;
  hasPassword: boolean;
  activeTurnPlayerId: string | null;
  activeTurnPlayerName: string;
  players: Player[];
  chat: ChatMessage[];
}

interface SaveFile {
  name: string;
  sizeBytes: number;
  updatedAt: number;
}

const CLUBES_2026 = [
  "Flamengo", "Palmeiras", "Botafogo", "Atlético-MG", "Corinthians",
  "Fluminense", "Internacional", "São Paulo", "Bahia", "Cruzeiro",
  "Grêmio", "Red Bull Bragantino", "Santos", "Vasco", "Athletico-PR",
  "Vitória", "Coritiba", "Chapecoense", "Mirassol", "Remo",
  "Fortaleza", "Ceará", "Sport", "Goiás", "Criciúma",
  "Juventude", "Cuiabá", "América-MG", "Ponte Preta", "Guarani",
  "Santa Cruz", "Paysandu", "Náutico", "Vila Nova", "Avaí",
];

const DEFAULT_SERVER_URL =
  process.env.NEXT_PUBLIC_BRASFOOT_SERVER_URL || "https://brasfoot-2-0.onrender.com";

export default function BrasfootCloudApp() {
  // Identidade do Treinador
  const [playerId, setPlayerId] = useState<string>("");
  const [playerName, setPlayerName] = useState<string>("");
  const [playerClub, setPlayerClub] = useState<string>("Flamengo");
  const [isProfileReady, setIsProfileReady] = useState<boolean>(false);

  // Servidor Cloud (Render / Railway)
  const [serverUrl, setServerUrl] = useState<string>(DEFAULT_SERVER_URL);
  const [serverStatus, setServerStatus] = useState<"checking" | "online" | "waking">("checking");
  const [showSettingsModal, setShowSettingsModal] = useState<boolean>(false);
  const [tempServerUrl, setTempServerUrl] = useState<string>(DEFAULT_SERVER_URL);

  // Salas Multiplayer
  const [rooms, setRooms] = useState<Room[]>([]);
  const [currentRoom, setCurrentRoom] = useState<Room | null>(null);
  const [showCreateRoomModal, setShowCreateRoomModal] = useState<boolean>(false);
  const [newRoomName, setNewRoomName] = useState<string>("");
  const [newRoomPassword, setNewRoomPassword] = useState<string>("");
  const [newRoomMax, setNewRoomMax] = useState<number>(8);

  // Controle de Vez (Mouse Lock) & Tela
  const [freeMouseMode, setFreeMouseMode] = useState<boolean>(false);
  const [chatInput, setChatInput] = useState<string>("");
  const [copiedInvite, setCopiedInvite] = useState<boolean>(false);
  const [sendingKey, setSendingKey] = useState<string | null>(null);

  // Gerenciador de Saves (.bfg)
  const [showSavesModal, setShowSavesModal] = useState<boolean>(false);
  const [savesList, setSavesList] = useState<SaveFile[]>([]);
  const [uploadingSave, setUploadingSave] = useState<boolean>(false);

  const gameContainerRef = useRef<HTMLDivElement>(null);
  const chatEndRef = useRef<HTMLDivElement>(null);

  // Inicializar perfil salvo e parâmetros da URL (?sala=...&server=...)
  useEffect(() => {
    let pid = localStorage.getItem("bf2026_player_id");
    if (!pid) {
      pid = "coach-" + Math.random().toString(36).substring(2, 9);
      localStorage.setItem("bf2026_player_id", pid);
    }
    setPlayerId(pid);

    const savedName = localStorage.getItem("bf2026_player_name") || "";
    const savedClub = localStorage.getItem("bf2026_player_club") || "Flamengo";
    const savedServer = localStorage.getItem("bf2026_server_url") || DEFAULT_SERVER_URL;

    const params = new URLSearchParams(window.location.search);
    const serverParam = params.get("server");
    const activeServer = (serverParam || savedServer).replace(/\/+$/, "");

    setPlayerName(savedName);
    setPlayerClub(savedClub);
    setServerUrl(activeServer);
    setTempServerUrl(activeServer);

    if (savedName) {
      setIsProfileReady(true);
    }
  }, []);

  // Polling de Salas e Status do Servidor Cloud
  useEffect(() => {
    if (!serverUrl) return;

    const syncWithServer = async () => {
      try {
        const res = await fetch(`${serverUrl}/api/rooms`, { cache: "no-store" });
        if (!res.ok) throw new Error("Server waking");
        const data = await res.json();
        setServerStatus("online");
        setRooms(data.rooms || []);

        // Se estiver em uma sala, enviar heartbeat/join para manter presença ativa
        if (currentRoom && isProfileReady && playerName) {
          const joinRes = await fetch(`${serverUrl}/api/rooms/${currentRoom.id}/join`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              playerId,
              playerName,
              club: playerClub,
            }),
          });
          if (joinRes.ok) {
            const joinData = await joinRes.json();
            setCurrentRoom(joinData.room);
          }
        }
      } catch {
        setServerStatus("waking");
        // Sala de fallback local caso o container no Render ainda esteja acordando
        if (rooms.length === 0) {
          const fallbackRoom: Room = {
            id: "sala-principal",
            name: "🏆 Brasileirão 2026 — Sala Principal",
            season: 2026,
            maxPlayers: 8,
            hasPassword: false,
            activeTurnPlayerId: playerId,
            activeTurnPlayerName: playerName ? `${playerName} (${playerClub})` : "Livre",
            players: playerName
              ? [{ id: playerId, name: playerName, club: playerClub, lastSeen: Date.now() }]
              : [],
            chat: [
              {
                id: "init-1",
                sender: "Sistema Cloud",
                club: "v2026.1",
                text: "Conectando ao container Docker do Brasfoot 2026...",
                timestamp: Date.now(),
              },
            ],
          };
          setRooms([fallbackRoom]);
        }
      }
    };

    syncWithServer();
    const timer = setInterval(syncWithServer, 2500);
    return () => clearInterval(timer);
  }, [serverUrl, currentRoom?.id, isProfileReady, playerName, playerClub, playerId]);

  // Entrar automaticamente na sala se vier por link de convite (?sala=...)
  useEffect(() => {
    if (!isProfileReady || currentRoom || rooms.length === 0) return;
    const params = new URLSearchParams(window.location.search);
    const roomParam = params.get("sala");
    if (roomParam) {
      const found = rooms.find((r) => r.id === roomParam);
      if (found) {
        handleJoinRoom(found);
      }
    }
  }, [isProfileReady, rooms, currentRoom]);

  const handleSaveProfile = (e: React.FormEvent) => {
    e.preventDefault();
    if (!playerName.trim()) return;
    localStorage.setItem("bf2026_player_name", playerName.trim());
    localStorage.setItem("bf2026_player_club", playerClub);
    setIsProfileReady(true);
  };

  const handleJoinRoom = async (room: Room) => {
    let password = "";
    if (room.hasPassword) {
      password = window.prompt(`Digite a senha da sala "${room.name}":`) || "";
    }
    try {
      const res = await fetch(`${serverUrl}/api/rooms/${room.id}/join`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          playerId,
          playerName,
          club: playerClub,
          password,
        }),
      });
      if (res.status === 403) {
        alert("Senha incorreta!");
        return;
      }
      if (res.ok) {
        const data = await res.json();
        setCurrentRoom(data.room);
        return;
      }
    } catch {
      // Fallback imediato
    }
    setCurrentRoom(room);
  };

  const handleCreateRoom = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newRoomName.trim()) return;
    try {
      const res = await fetch(`${serverUrl}/api/rooms`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: newRoomName.trim(),
          password: newRoomPassword.trim(),
          maxPlayers: newRoomMax,
        }),
      });
      if (res.ok) {
        const data = await res.json();
        setShowCreateRoomModal(false);
        setNewRoomName("");
        setNewRoomPassword("");
        await handleJoinRoom(data.room);
        return;
      }
    } catch {
      // Fallback local
      const created: Room = {
        id: "sala-" + Date.now(),
        name: newRoomName.trim(),
        season: 2026,
        maxPlayers: newRoomMax,
        hasPassword: Boolean(newRoomPassword.trim()),
        activeTurnPlayerId: playerId,
        activeTurnPlayerName: `${playerName} (${playerClub})`,
        players: [{ id: playerId, name: playerName, club: playerClub, lastSeen: Date.now() }],
        chat: [],
      };
      setRooms((prev) => [...prev, created]);
      setCurrentRoom(created);
      setShowCreateRoomModal(false);
    }
  };

  const handleTurnAction = async (action: "claim" | "next" | "release") => {
    if (!currentRoom) return;
    try {
      const res = await fetch(`${serverUrl}/api/rooms/${currentRoom.id}/turn`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          action,
          playerId,
          playerName,
          club: playerClub,
        }),
      });
      if (res.ok) {
        const data = await res.json();
        setCurrentRoom(data.room);
      }
    } catch {
      setCurrentRoom({
        ...currentRoom,
        activeTurnPlayerId: action === "release" ? null : playerId,
        activeTurnPlayerName:
          action === "release" ? "Livre (Qualquer técnico)" : `${playerName} (${playerClub})`,
      });
    }
  };

  const handleSendChat = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatInput.trim() || !currentRoom) return;
    const msgText = chatInput.trim();
    setChatInput("");

    try {
      const res = await fetch(`${serverUrl}/api/rooms/${currentRoom.id}/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          sender: playerName,
          club: playerClub,
          text: msgText,
        }),
      });
      if (res.ok) {
        const data = await res.json();
        setCurrentRoom(data.room);
        setTimeout(() => chatEndRef.current?.scrollIntoView({ behavior: "smooth" }), 50);
        return;
      }
    } catch {
      // Fallback local
      setCurrentRoom({
        ...currentRoom,
        chat: [
          ...currentRoom.chat,
          {
            id: "local-" + Date.now(),
            sender: playerName,
            club: playerClub,
            text: msgText,
            timestamp: Date.now(),
          },
        ],
      });
    }
  };

  const handleSendModKey = async (key: string) => {
    setSendingKey(key);
    try {
      await fetch(`${serverUrl}/api/key`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ key }),
      });
    } catch {
      // ignore
    } finally {
      setTimeout(() => setSendingKey(null), 400);
    }
  };

  const fetchSaves = async () => {
    try {
      const res = await fetch(`${serverUrl}/api/saves`);
      if (res.ok) {
        const data = await res.json();
        setSavesList(data.saves || []);
      }
    } catch {
      setSavesList([]);
    }
  };

  const handleUploadSave = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadingSave(true);
    try {
      const formData = new FormData();
      formData.append("file", file);
      await fetch(`${serverUrl}/api/saves/upload`, {
        method: "POST",
        body: formData,
      });
      await fetchSaves();
    } catch {
      alert("Erro ao enviar o save para o servidor cloud.");
    } finally {
      setUploadingSave(false);
    }
  };

  const handleCopyInvite = () => {
    if (!currentRoom) return;
    const url = new URL(window.location.href);
    url.searchParams.set("sala", currentRoom.id);
    url.searchParams.set("server", serverUrl);
    navigator.clipboard.writeText(url.toString());
    setCopiedInvite(true);
    setTimeout(() => setCopiedInvite(false), 3000);
  };

  const handleToggleFullscreen = () => {
    if (!gameContainerRef.current) return;
    if (!document.fullscreenElement) {
      gameContainerRef.current.requestFullscreen().catch(() => {});
    } else {
      document.exitFullscreen().catch(() => {});
    }
  };

  const isMyTurn =
    freeMouseMode ||
    !currentRoom?.activeTurnPlayerId ||
    currentRoom.activeTurnPlayerId === playerId;

  const vncStreamUrl = `${serverUrl}/vnc_lite.html?autoconnect=true&scale=true&reconnect=true`;

  return (
    <div className="min-h-screen flex flex-col bg-[#07110d] text-emerald-50">
      {/* Header Principal */}
      <header className="h-16 border-b border-emerald-900/60 bg-[#0a1913]/90 backdrop-blur-md px-6 flex items-center justify-between sticky top-0 z-40">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500 to-green-700 flex items-center justify-center shadow-lg shadow-emerald-900/50 font-black text-xl text-black">
            ⚽
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-extrabold text-lg tracking-tight text-white">
                Brasfoot 2026 <span className="text-emerald-400">Cloud</span>
              </h1>
              <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                Mod v2026.1 Beta
              </span>
            </div>
            <p className="text-xs text-emerald-300/70">
              Salas Multiplayer Online • Séries A, B, C e D 2026
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          {/* Status do Container Cloud */}
          <button
            onClick={() => setShowSettingsModal(true)}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-emerald-950/80 border border-emerald-800/60 hover:border-emerald-500/50 text-xs transition-all cursor-pointer"
            title="Clique para configurar a URL do Servidor Render/Railway"
          >
            <span
              className={`w-2.5 h-2.5 rounded-full ${
                serverStatus === "online"
                  ? "bg-emerald-400 shadow-[0_0_8px_#34d399]"
                  : "bg-amber-400 animate-pulse"
              }`}
            />
            <span className="font-semibold text-emerald-100">
              {serverStatus === "online" ? "Servidor Jogo: Online" : "Servidor: Acordando / Config"}
            </span>
          </button>

          {isProfileReady && (
            <div className="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-lg bg-emerald-900/30 border border-emerald-800/40 text-xs">
              <span className="text-emerald-400 font-bold">👔 {playerName}</span>
              <span className="text-emerald-300/60">•</span>
              <span className="text-white font-semibold">{playerClub}</span>
              <button
                onClick={() => setIsProfileReady(false)}
                className="ml-1 text-emerald-400 hover:underline text-[11px] cursor-pointer"
              >
                (Trocar)
              </button>
            </div>
          )}

          {currentRoom && (
            <button
              onClick={() => setCurrentRoom(null)}
              className="px-3 py-1.5 rounded-lg bg-red-500/15 hover:bg-red-500/25 text-red-300 border border-red-500/30 text-xs font-bold transition-all cursor-pointer"
            >
              Sair da Sala
            </button>
          )}
        </div>
      </header>

      {/* Conteúdo Principal */}
      <main className="flex-1 flex flex-col">
        {!isProfileReady ? (
          /* PASSO 1: IDENTIFICAÇÃO DO TREINADOR */
          <div className="flex-1 flex items-center justify-center p-6">
            <div className="max-w-md w-full bg-[#0d1f18] border border-emerald-800/60 rounded-2xl p-8 shadow-2xl">
              <div className="text-center mb-6">
                <div className="w-16 h-16 rounded-2xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-3xl mx-auto mb-3">
                  👔
                </div>
                <h2 className="text-2xl font-extrabold text-white">Credencial de Treinador</h2>
                <p className="text-sm text-emerald-300/70 mt-1">
                  Escolha seu nome e clube para entrar nas salas online com seus amigos.
                </p>
              </div>

              <form onSubmit={handleSaveProfile} className="space-y-4">
                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-emerald-300/80 mb-1.5">
                    Seu Nome / Apelido
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="Ex: Matheus, Wesley..."
                    value={playerName}
                    onChange={(e) => setPlayerName(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl bg-[#07110d] border border-emerald-800 focus:border-emerald-400 focus:outline-none text-white text-sm"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-emerald-300/80 mb-1.5">
                    Seu Clube na Temporada 2026
                  </label>
                  <select
                    value={playerClub}
                    onChange={(e) => setPlayerClub(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl bg-[#07110d] border border-emerald-800 focus:border-emerald-400 focus:outline-none text-white text-sm cursor-pointer"
                  >
                    {CLUBES_2026.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </div>

                <button
                  type="submit"
                  className="w-full py-3.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-black font-extrabold text-sm shadow-lg shadow-emerald-500/20 transition-all cursor-pointer mt-2"
                >
                  Entrar no Lobby de Salas →
                </button>
              </form>
            </div>
          </div>
        ) : !currentRoom ? (
          /* PASSO 2: LOBBY DE SALAS MULTIPLAYER */
          <div className="max-w-5xl w-full mx-auto p-6 space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-[#0d1f18] border border-emerald-800/50 rounded-2xl p-6">
              <div>
                <h2 className="text-2xl font-extrabold text-white">Salas Multiplayer Online</h2>
                <p className="text-sm text-emerald-300/70 mt-1">
                  Entre na sala com seus amigos para jogar a mesma carreira do Brasfoot 2026 em tempo real.
                </p>
              </div>
              <div className="flex items-center gap-3">
                <button
                  onClick={() => {
                    fetchSaves();
                    setShowSavesModal(true);
                  }}
                  className="px-4 py-2.5 rounded-xl bg-emerald-950 hover:bg-emerald-900 border border-emerald-700/60 text-emerald-200 font-bold text-xs transition-all cursor-pointer"
                >
                  💾 Saves na Nuvem (.bfg)
                </button>
                <button
                  onClick={() => setShowCreateRoomModal(true)}
                  className="px-5 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-black font-extrabold text-xs shadow-lg shadow-emerald-500/20 transition-all cursor-pointer"
                >
                  + Criar Nova Sala
                </button>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {rooms.map((room) => (
                <div
                  key={room.id}
                  className="bg-[#0d1f18] border border-emerald-800/50 hover:border-emerald-500/60 rounded-2xl p-6 flex flex-col justify-between transition-all shadow-lg"
                >
                  <div>
                    <div className="flex items-center justify-between gap-2 mb-2">
                      <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                        Temporada {room.season}
                      </span>
                      <span className="text-xs text-emerald-300/70 font-semibold">
                        👥 {room.players?.length || 0} / {room.maxPlayers} técnicos
                      </span>
                    </div>
                    <h3 className="text-lg font-extrabold text-white mb-1">
                      {room.hasPassword ? "🔒 " : ""}
                      {room.name}
                    </h3>
                    <p className="text-xs text-emerald-300/70 mb-4">
                      Controle atual:{" "}
                      <strong className="text-emerald-200">{room.activeTurnPlayerName}</strong>
                    </p>

                    {room.players && room.players.length > 0 && (
                      <div className="flex flex-wrap gap-1.5 mb-5">
                        {room.players.map((p) => (
                          <span
                            key={p.id}
                            className="px-2.5 py-1 rounded-lg bg-[#07110d] border border-emerald-800/60 text-[11px] font-medium text-emerald-200"
                          >
                            👔 {p.name} ({p.club})
                          </span>
                        ))}
                      </div>
                    )}
                  </div>

                  <button
                    onClick={() => handleJoinRoom(room)}
                    className="w-full py-3 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-black font-extrabold text-xs transition-all cursor-pointer"
                  >
                    Entrar na Sala e Jogar →
                  </button>
                </div>
              ))}
            </div>
          </div>
        ) : (
          /* PASSO 3: ARENA DO JOGO + CONTROLE DE VEZ + CHAT DA SALA */
          <div className="flex-1 flex flex-col lg:flex-row overflow-hidden h-[calc(100vh-4rem)]">
            {/* Coluna Esquerda / Centro: Barra de Controle + Tela do Brasfoot 2026 */}
            <div className="flex-1 flex flex-col bg-[#050c09] overflow-hidden">
              {/* Barra Superior da Sala (Turnos + Atalhos do Mod) */}
              <div className="bg-[#0d1f18] border-b border-emerald-900/60 px-4 py-2.5 flex flex-wrap items-center justify-between gap-2">
                {/* Indicador de Vez / Mouse */}
                <div className="flex items-center gap-2 flex-wrap">
                  <span
                    className={`px-3 py-1 rounded-lg text-xs font-extrabold flex items-center gap-1.5 border ${
                      isMyTurn
                        ? "bg-emerald-500/20 text-emerald-300 border-emerald-500/40"
                        : "bg-amber-500/20 text-amber-300 border-amber-500/40"
                    }`}
                  >
                    {isMyTurn ? "🟢 Mouse Liberado (Sua Vez)" : `🟡 Vez de: ${currentRoom.activeTurnPlayerName}`}
                  </span>

                  {!isMyTurn && (
                    <button
                      onClick={() => handleTurnAction("claim")}
                      className="px-3 py-1 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-black font-extrabold text-xs transition-all cursor-pointer"
                    >
                      ✋ Assumir Vez Agora
                    </button>
                  )}

                  {isMyTurn && currentRoom.players.length > 1 && (
                    <button
                      onClick={() => handleTurnAction("next")}
                      className="px-3 py-1 rounded-lg bg-amber-400 hover:bg-amber-300 text-black font-extrabold text-xs transition-all cursor-pointer"
                    >
                      ⏭️ Passar a Vez pro Amigo
                    </button>
                  )}

                  <button
                    onClick={() => setFreeMouseMode(!freeMouseMode)}
                    className="px-2.5 py-1 rounded-lg bg-emerald-950 border border-emerald-800 text-[11px] text-emerald-300 hover:text-white cursor-pointer"
                    title="Desbloqueia o mouse mesmo quando for a vez de outro jogador"
                  >
                    {freeMouseMode ? "🔓 Mouse Livre Ativo" : "🔒 Trava de Turno Ativa"}
                  </button>
                </div>

                {/* Botões de Atalho dos Mods do Brasfoot 2026 */}
                <div className="flex items-center gap-1.5 flex-wrap">
                  <button
                    onClick={() => handleSendModKey("F4")}
                    className={`px-2.5 py-1 rounded-lg text-xs font-bold border transition-all cursor-pointer ${
                      sendingKey === "F4"
                        ? "bg-emerald-400 text-black border-emerald-300"
                        : "bg-emerald-950/90 hover:bg-emerald-900 text-emerald-200 border-emerald-700/60"
                    }`}
                    title="Abrir Modo Férias Estável (Tecla F4)"
                  >
                    🏖️ Férias (F4)
                  </button>

                  <button
                    onClick={() => handleSendModKey("F8")}
                    className={`px-2.5 py-1 rounded-lg text-xs font-bold border transition-all cursor-pointer ${
                      sendingKey === "F8"
                        ? "bg-emerald-400 text-black border-emerald-300"
                        : "bg-emerald-950/90 hover:bg-emerald-900 text-emerald-200 border-emerald-700/60"
                    }`}
                    title="Simular Partida Instantaneamente (Tecla F8)"
                  >
                    ⚡ Simular (F8)
                  </button>

                  <button
                    onClick={() => handleSendModKey("F2")}
                    className="px-2.5 py-1 rounded-lg bg-emerald-950/90 hover:bg-emerald-900 text-emerald-200 border border-emerald-700/60 text-xs font-bold transition-all cursor-pointer"
                    title="Central de Estatísticas & BI (Tecla F2)"
                  >
                    📊 BI (F2)
                  </button>

                  <button
                    onClick={() => {
                      fetchSaves();
                      setShowSavesModal(true);
                    }}
                    className="px-2.5 py-1 rounded-lg bg-emerald-950/90 hover:bg-emerald-900 text-emerald-200 border border-emerald-700/60 text-xs font-bold transition-all cursor-pointer"
                  >
                    💾 Saves
                  </button>

                  <button
                    onClick={handleCopyInvite}
                    className="px-2.5 py-1 rounded-lg bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 border border-emerald-500/40 text-xs font-bold transition-all cursor-pointer"
                  >
                    {copiedInvite ? "✅ Link Copiado!" : "🔗 Convidar Amigos"}
                  </button>

                  <button
                    onClick={handleToggleFullscreen}
                    className="px-2.5 py-1 rounded-lg bg-emerald-950/90 hover:bg-emerald-900 text-emerald-200 border border-emerald-700/60 text-xs font-bold transition-all cursor-pointer"
                    title="Tela Cheia"
                  >
                    ⛶
                  </button>
                </div>
              </div>

              {/* Container do Stream Ao Vivo do Brasfoot 2026 (noVNC) */}
              <div ref={gameContainerRef} className="flex-1 relative bg-black flex items-center justify-center">
                <iframe
                  src={vncStreamUrl}
                  title="Brasfoot 2026 Cloud Stream"
                  className={`w-full h-full border-0 ${isMyTurn ? "pointer-events-auto" : "pointer-events-none"}`}
                  allow="fullscreen; clipboard-read; clipboard-write"
                />

                {/* Banner sutil quando está no modo espectador */}
                {!isMyTurn && (
                  <div className="absolute bottom-4 left-1/2 -translate-x-1/2 bg-black/80 backdrop-blur-md border border-amber-500/40 px-4 py-2 rounded-xl flex items-center gap-3 shadow-2xl">
                    <span className="text-xs text-amber-200 font-semibold">
                      👀 Assistindo à vez de <strong>{currentRoom.activeTurnPlayerName}</strong>
                    </span>
                    <button
                      onClick={() => handleTurnAction("claim")}
                      className="px-3 py-1 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-black font-extrabold text-xs cursor-pointer"
                    >
                      Assumir Controle
                    </button>
                  </div>
                )}
              </div>
            </div>

            {/* Coluna Direita: Técnicos na Sala + Chat Multiplayer */}
            <aside className="w-full lg:w-80 bg-[#0d1f18] border-t lg:border-t-0 lg:border-l border-emerald-900/60 flex flex-col h-72 lg:h-full shrink-0">
              {/* Lista de Amigos Online na Sala */}
              <div className="p-4 border-b border-emerald-900/60">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-xs font-extrabold uppercase tracking-wider text-emerald-300">
                    Técnicos na Sala ({currentRoom.players?.length || 1})
                  </h3>
                  <span className="text-[11px] text-emerald-400 font-semibold">{currentRoom.name}</span>
                </div>
                <div className="space-y-1.5 max-h-32 overflow-y-auto pr-1">
                  {currentRoom.players?.map((p) => {
                    const isTurn = currentRoom.activeTurnPlayerId === p.id;
                    return (
                      <div
                        key={p.id}
                        className={`flex items-center justify-between px-3 py-1.5 rounded-lg text-xs border ${
                          isTurn
                            ? "bg-emerald-500/15 border-emerald-500/40 text-white"
                            : "bg-[#07110d]/60 border-emerald-900/40 text-emerald-200/80"
                        }`}
                      >
                        <div className="truncate">
                          <span className="font-bold">{p.name}</span>{" "}
                          <span className="text-emerald-400 text-[11px]">({p.club})</span>
                        </div>
                        {isTurn && (
                          <span className="text-[10px] font-extrabold px-1.5 py-0.5 rounded bg-emerald-500 text-black shrink-0">
                            NA VEZ
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Chat da Sala */}
              <div className="flex-1 flex flex-col overflow-hidden">
                <div className="flex-1 overflow-y-auto p-4 space-y-2.5">
                  {currentRoom.chat?.map((m) => (
                    <div key={m.id} className="bg-[#07110d]/80 border border-emerald-900/40 rounded-xl p-2.5 text-xs">
                      <div className="flex items-center justify-between mb-1">
                        <span className="font-bold text-emerald-300">
                          {m.sender}{" "}
                          {m.club && <span className="text-emerald-500 font-normal">({m.club})</span>}
                        </span>
                      </div>
                      <p className="text-emerald-50/90 break-words leading-relaxed">{m.text}</p>
                    </div>
                  ))}
                  <div ref={chatEndRef} />
                </div>

                <form onSubmit={handleSendChat} className="p-3 border-t border-emerald-900/60 flex gap-2">
                  <input
                    type="text"
                    placeholder="Mandar mensagem na sala..."
                    value={chatInput}
                    onChange={(e) => setChatInput(e.target.value)}
                    className="flex-1 px-3 py-2 rounded-xl bg-[#07110d] border border-emerald-800 focus:border-emerald-400 focus:outline-none text-xs text-white"
                  />
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-black font-extrabold text-xs cursor-pointer"
                  >
                    Enviar
                  </button>
                </form>
              </div>
            </aside>
          </div>
        )}
      </main>

      {/* Modal: Criar Nova Sala */}
      {showCreateRoomModal && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#0d1f18] border border-emerald-700/60 rounded-2xl p-6 max-w-md w-full shadow-2xl">
            <h3 className="text-lg font-extrabold text-white mb-4">Criar Nova Sala Multiplayer</h3>
            <form onSubmit={handleCreateRoom} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-emerald-300 mb-1">Nome da Sala</label>
                <input
                  type="text"
                  required
                  placeholder="Ex: Copa dos Amigos 2026"
                  value={newRoomName}
                  onChange={(e) => setNewRoomName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#07110d] border border-emerald-800 text-sm text-white"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-emerald-300 mb-1">
                  Senha Opcional (deixe em branco para sala aberta)
                </label>
                <input
                  type="text"
                  placeholder="Sem senha"
                  value={newRoomPassword}
                  onChange={(e) => setNewRoomPassword(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#07110d] border border-emerald-800 text-sm text-white"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-emerald-300 mb-1">Máximo de Treinadores</label>
                <input
                  type="number"
                  min={2}
                  max={20}
                  value={newRoomMax}
                  onChange={(e) => setNewRoomMax(Number(e.target.value))}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#07110d] border border-emerald-800 text-sm text-white"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowCreateRoomModal(false)}
                  className="px-4 py-2 rounded-xl border border-emerald-800 text-xs font-bold text-emerald-300 cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl bg-emerald-500 text-black font-extrabold text-xs cursor-pointer"
                >
                  Criar Sala
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Gerenciador de Saves (.bfg) */}
      {showSavesModal && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#0d1f18] border border-emerald-700/60 rounded-2xl p-6 max-w-lg w-full shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-extrabold text-white">💾 Gerenciador de Saves (.bfg)</h3>
              <button
                onClick={() => setShowSavesModal(false)}
                className="text-emerald-400 hover:text-white text-sm font-bold cursor-pointer"
              >
                ✕
              </button>
            </div>
            <p className="text-xs text-emerald-300/80">
              Baixe o save da partida para guardar no seu PC ou envie um arquivo <code>.bfg</code> para continuar o campeonato no servidor.
            </p>

            <div className="space-y-2 max-h-60 overflow-y-auto">
              {savesList.length === 0 ? (
                <div className="p-4 rounded-xl bg-[#07110d] border border-emerald-900/50 text-center text-xs text-emerald-300/60">
                  Nenhum arquivo .bfg encontrado na pasta sav/ do servidor ainda. Salve o jogo dentro do Brasfoot para ele aparecer aqui!
                </div>
              ) : (
                savesList.map((s) => (
                  <div
                    key={s.name}
                    className="flex items-center justify-between p-3 rounded-xl bg-[#07110d] border border-emerald-800/60 text-xs"
                  >
                    <div>
                      <p className="font-bold text-white">{s.name}</p>
                      <p className="text-[11px] text-emerald-400/70">
                        {(s.sizeBytes / 1024).toFixed(1)} KB •{" "}
                        {new Date(s.updatedAt).toLocaleString("pt-BR")}
                      </p>
                    </div>
                    <a
                      href={`${serverUrl}/api/saves/download/${encodeURIComponent(s.name)}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="px-3 py-1.5 rounded-lg bg-emerald-500 text-black font-bold text-xs"
                    >
                      ⬇ Baixar
                    </a>
                  </div>
                ))
              )}
            </div>

            <div className="pt-2 border-t border-emerald-900/60 flex items-center justify-between">
              <label className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-black font-extrabold text-xs cursor-pointer">
                {uploadingSave ? "Enviando..." : "⬆ Subir Save (.bfg) do PC"}
                <input type="file" accept=".bfg" onChange={handleUploadSave} className="hidden" />
              </label>
              <button
                onClick={() => setShowSavesModal(false)}
                className="px-4 py-2 rounded-xl border border-emerald-800 text-xs font-bold text-emerald-300 cursor-pointer"
              >
                Fechar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Configurar URL do Servidor Cloud (Render / Railway) */}
      {showSettingsModal && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#0d1f18] border border-emerald-700/60 rounded-2xl p-6 max-w-md w-full shadow-2xl space-y-4">
            <h3 className="text-lg font-extrabold text-white">⚙️ Conexão do Servidor Cloud</h3>
            <p className="text-xs text-emerald-300/80">
              Informe a URL pública gerada pelo <strong>Render</strong> ou <strong>Railway</strong> onde o container Docker do Brasfoot 2026 está rodando.
            </p>
            <input
              type="url"
              value={tempServerUrl}
              onChange={(e) => setTempServerUrl(e.target.value)}
              placeholder="https://brasfoot-cloud.onrender.com"
              className="w-full px-3.5 py-2.5 rounded-xl bg-[#07110d] border border-emerald-800 text-sm text-white"
            />
            <div className="flex justify-between items-center pt-2">
              <button
                type="button"
                onClick={async () => {
                  try {
                    await fetch(`${serverUrl}/api/restart`, { method: "POST" });
                    alert("Comando de reinício enviado para o Brasfoot 2026!");
                  } catch {
                    alert("Não foi possível contatar o servidor.");
                  }
                }}
                className="px-3 py-2 rounded-xl bg-red-500/20 text-red-300 border border-red-500/30 text-xs font-bold cursor-pointer"
              >
                🔄 Reiniciar Jogo no Servidor
              </button>
              <div className="flex gap-2">
                <button
                  onClick={() => setShowSettingsModal(false)}
                  className="px-4 py-2 rounded-xl border border-emerald-800 text-xs font-bold text-emerald-300 cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  onClick={() => {
                    const clean = tempServerUrl.trim().replace(/\/+$/, "");
                    setServerUrl(clean);
                    localStorage.setItem("bf2026_server_url", clean);
                    setShowSettingsModal(false);
                  }}
                  className="px-4 py-2 rounded-xl bg-emerald-500 text-black font-extrabold text-xs cursor-pointer"
                >
                  Salvar
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

