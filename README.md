# ⚽ Brasfoot 2026 — Mod Suíte Oficial (v2026.1 Beta v1)

Mod avançado para Brasfoot com integração em tempo de execução via **Java Instrumentation Agent** (`brasfoot-agent.jar`) e Launcher nativo Windows x64 (`Brasfoot2026.exe`).

---

## 🚀 Como Executar

1. Certifique-se de possuir o Java instalado (compatível com Java 8, 11, 17 ou superior).
2. Execute o inicializador nativo:
   ```cmd
   Brasfoot2026.exe
   ```
   *(O launcher injeta automaticamente o Java Agent com os parâmetros otimizados para seu ambiente).*

---

## 🌟 Principais Recursos da Versão v2026.1 Beta v1

- **🏖️ Modo Férias Estável (Tecla F4):** Simulação contínua do estadual até a grande final com opção inteligente pré-selecionada, transição automática e blindagem contra travamentos na EDT.
- **🎯 Metas da Diretoria (`SeasonGoalsController`):** Carta de objetivos na caixa de entrada calibrada por força do clube e divisão, com avaliação semestral e anual.
- **🔍 Scout Inteligente (`ScoutController`):** Diagnóstico automático de carências do elenco e sugestões periódicas de mercado com badges coloridos por posição.
- **⭐ Live Rating & Craque do Jogo (MVP):** Notas de atuação (0 a 10) calculadas lance a lance na partida e eleição do MVP na tela pós-jogo.
- **🔄 Rotação Tática & Prevenção de Fadiga:** Assistente de escalação que poupa atletas antes de jogos decisivos.
- **🏆 Séries A, B, C e D Atualizadas para 2026:** Clubes oficiais, forças calibradas e de-duplicação estrita de jogadores por ID.
- **⚡ Simulação Instantânea (Tecla F8):** Encerre partidas no mesmo instante com placar e dados completos.

---

## 📁 Estrutura do Projeto

- `src/` — Código-fonte em Java de todos os controladores, agentes e patches.
- `Brasfoot2026.exe` — Launcher nativo Windows em C# x64.
- `brasfoot-agent.jar` — Bytecode compilado do agente Java (major version 52.0 / Java 8).
- `brasfoot.jar` — Binário base do Brasfoot 2022-2023.
- `teams/` — Banco de dados de elencos de clubes (.ban).
- `teams_backup_original/` — Backup de segurança para restauração de fallback dos elencos originais.
- `_backup_estavel_atual/` e `rollback_stable.bat` — Mecanismos de rollback instantâneo da suíte de mods.
- `tools/` — Scripts utilitários de build e empacotamento (`package_release.py`).
- `RELEASE_NOTES.md` — Detalhamento técnico de todas as novidades implementadas.
- `DOCUMENTACAO_COMPLETA_MODS.md` — Documentação técnica aprofundada de engenharia reversa e arquitetura do mod.

---

## 🛠️ Compilação

Para recompilar o agente Java a partir do código-fonte em `src/`:
```cmd
javac -encoding UTF-8 --release 8 -cp ".;brasfoot.jar;brasfoot-agent.jar" -d build_classes src/br/com/brasfootmod/**/*.java
jar uf brasfoot-agent.jar -C build_classes br
```
*(Ou utilize as ferramentas em `tools/package_release.py` para gerar o pacote zip de release).*
