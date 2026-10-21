# 📋 Brasfoot 2026 — Release Notes v2026.1 (Beta v1)

**Data de Lançamento:** Outubro de 2026  
**Versão:** `v2026.1 Beta v1` (`brasfoot_release_betav1`)  
**Compatibilidade:** Windows 7 / 8 / 10 / 11 (Java JRE 8 ou superior / JDK 17+ compatível)  
**Executável Principal:** `Brasfoot2026.exe` (Launcher Nativo x64)  
**Agente Mod:** `brasfoot-agent.jar` (Bytecode canônico Java 8 - Major Version 52.0)  

---

## 🌟 O Que Há de Novo na Versão Beta v1 (`brasfoot_release_betav1`)

Esta versão traz a consolidação das principais inovações técnicas e funcionais solicitadas para a temporada 2026, com foco em estabilidade do modo simulação, imersão gerencial e assistência tática.

---

### 1. 🏖️ Estabilização Definitiva do Modo Férias (Tecla F4)
- **Causa Raiz Resolvida**: Na versão anterior, o avanço automático da simulação parava de forma intermitente durante os campeonatos estaduais e aguardava intervenção manual do treinador. A investigação identificou que rodadas com datas não estritamente coincidentes e métodos nativos de agendamento de jogos (`best.s.dJ()`) sofriam exceções silenciosas de `NullPointerException` e retenção da Swing Event Dispatch Thread (EDT).
- **Opção Inteligente Pré-Selecionada**: A opção **"🏆 Até o Fim do Estadual"** agora vem selecionada por padrão ao abrir o modal (F4). O sistema calcula a data exata da grande final do estadual do clube do jogador.
- **Transição Fluida Pós-Partida**: Implementada garantia de despacho de avanço na EDT Swing (`SwingUtilities.invokeLater`), simulando o clique do botão de continuidade e impedindo que a partida fique retida em tela.
- **Blindagem Completa Anti-NPE**: Tratamento defensivo nas chamadas nativas de rodadas (`best.s.dJ()`), telas de tabela (`a.ho`) e calendário (`a.cS`).
- **Log Estruturado em Tempo Real**: Adicionado log de diagnóstico em `mod_debug.log` registrando cada etapa do fluxo de férias (início, rodada simulada, escalação automática e término das férias).

---

### 2. 🎯 Sistema de Metas da Diretoria (`SeasonGoalsController`)
- **Carta de Objetivos na Caixa de Entrada**: No primeiro dia de cada nova temporada (ou no carregamento do save), a presidência do clube envia uma carta oficial com as expectativas e cobranças para o ano.
- **Metas Calibradas por Força e Divisão**:
  - **Série A (Top Clubs)**: Disputa obrigatória por título, classificação garantida para a Copa Libertadores e finais de Copa do Brasil/Estadual.
  - **Série A (Intermediários)**: Vaga na Copa Sul-Americana e campanhas sólidas nos torneios mata-mata.
  - **Série B / C**: Acesso de divisão como meta prioritária e permanência sólida.
- **Avaliação Contínua e Registro em Disco**: Acompanhamento semestral e anual do cumprimento de metas, salvo automaticamente no formato JSON (`metas_<Clube>_<Ano>.json`).

---

### 3. 🔍 Scout Inteligente na Caixa de Entrada (`ScoutController` + `ScoutInboxCellRenderer`)
- **Relatórios Periódicos de Mercado**: O departamento de inteligência e scout do clube envia relatórios detalhados diretamente para a caixa de notícias do jogo.
- **Diagnóstico Automático de Carências**: O assistente analisa o elenco atual, identifica setores deficientes em número de atletas ou média de força (ex: escassez de zagueiros, laterais ou atacantes) e prioriza a busca nessas posições.
- **Recomendações Acessíveis**: Sugestões de contratação calibradas de acordo com as finanças e o caixa disponível do clube.
- **Visual Estilizado na Inbox**: Renderizador personalizado (`ScoutInboxCellRenderer`) com badges coloridos por posição (GOL, LAT, ZAG, MEI, ATA), destaque de força e valor de mercado.

---

### 4. 🌟 Live Rating & Craque do Jogo / MVP (`LiveRatingCalculator` + `MatchMvpController`)
- **Notas Dinâmicas de 0.0 a 10.0 Durante a Partida**: Algoritmo que calcula lance a lance a nota de desempenho de cada titular e reserva utilizado.
- **Critérios Reais de Avaliação**: Gols marcados, assistências, defesas difíceis do goleiro, desarmes, passes decisivos, faltas e cartões aplicados.
- **Eleição do Craque da Partida (MVP)**: Tela de encerramento do jogo com destaque visual do melhor jogador em campo, troféu de MVP e resumo das notas do elenco.

---

### 5. 🔄 Rotação Tática & Análise de Elenco (`LineupRotationController`, `TacticalAnalysisController`)
- **Prevenção de Lesões & Controle de Fadiga**: Assistente inteligente que identifica atletas com energia abaixo do nível ideal e sugere substituições preventivas antes de jogos decisivos.
- **Leitura do Adversário**: Análise pré-jogo da força e estilo tático do rival, recomendando esquemas mais defensivos ou ofensivos.

---

### 6. 📅 Início Oficial da Temporada em 2026
- Calendário inicial totalmente posicionado no ano de **2026**.
- Tabelas de estaduais e ligas nacionais sincronizadas com o cenário oficial de 2026.

---

### 7. ⚙️ Compilação Canônica em Bytecode Java 8 (Major Version 52.0)
- Todo o código fonte Java foi compilado utilizando o parâmetro rigoroso `--release 8`.
- 100% compatível retroativamente com runtimes legados do JRE 8, JRE 11, JRE 17 e JRE 21, sem riscos de `UnsupportedClassVersionError`.
- Totalmente compatível com o launcher x64 `Brasfoot2026.exe`.

---

## 🏛️ Funcionalidades Herdadas e Mantidas da Versão Base

### Divisões Nacionais Calibradas com a Realidade Oficial de 2026
- **Série A (Top 20 | Forças 16 a 19)**:
  - Alinhada rigorosamente com a tabela oficial de 2026: **Athletico-PR (17)**, **Botafogo (19)**, **Flamengo (19)**, **Palmeiras (19)**, **Atlético-MG (18)**, **Corinthians (18)**, **Fluminense (18)**, **Internacional (18)**, **São Paulo (18)**, **Bahia (17)**, **Cruzeiro (17)**, **Grêmio (17)**, **Red Bull Bragantino (17)**, **Santos (17)**, **Vasco (17)**, **Chapecoense (16)**, **Coritiba (16)**, **Mirassol (16)**, **Remo (16)** e **Vitória (16)**.
- **Série B (21 a 40 | Forças 13 a 15)**:
  - 20 clubes oficiais: América-MG (15), Atlético-GO (15), Ceará (15), Criciúma (15), Cuiabá (15), Fortaleza (15), Goiás (15), Juventude (15), Sport (15), Avaí (14), Londrina (14), Novorizontino (14), Operário-PR (14), Ponte Preta (14), Vila Nova (14), Athletic Club (13), Botafogo-SP (13), CRB (13), Náutico (13) e São Bernardo (13).
- **Série C (41 a 60 | Forças 10 a 11)**:
  - 20 clubes oficiais: Amazonas FC, Botafogo-PB, Brusque, Ferroviária, Figueirense, Guarani, Ituano, Maringá, Paysandu, Santa Cruz, Volta Redonda, Ypiranga-RS, Anápolis, Barra-SC, Caxias, Confiança, Floresta, Inter de Limeira, Itabaiana e Maranhão.
- **Série D / Estaduais (Forças ≤ 9)**:
  - Clubes nivelados em força compatível com as divisões de acesso.

### Sincronização com o Transfermarkt & De-duplicação Estrita
- **Zero Duplicatas na Base**: De-duplicação automática por ID único (TID) em toda a base `.ban`. Jogadores pertencem unicamente a seus clubes atuais.
- **16 Titulares Reais por Minutagem**: 11 titulares + 5 reservas imediatos identificados pelo tempo real de jogo.
- **Regra de Estrelas**: Apenas clubes da Série A (força ≥ 16) recebem exatamente 1 estrela para o atleta de maior valor de mercado.
- **Treinadores Oficiais e Bandeiras**: Nomes reais de comissão técnica e correção completa de nacionalidades de atletas estrangeiros.
- **Preservação de Características**: Manutenção das características originais (`cr1` e `cr2`) de mais de 14.600 atletas da base oficial.

### Simulação Instantânea (Tecla F8)
- Pressione **F8** durante qualquer partida para concluir o jogo na hora com placar oficial e estatísticas completas.

### Launcher Nativo Windows (`Brasfoot2026.exe`)
- Executável C# nativo x64, com ícone oficial integrado, inicialização direta sem telas pretas de terminal e injeção automática de parâmetros (`-javaagent:brasfoot-agent.jar`).

### Watchdog Anti-Congelamento
- Monitor contínuo em segundo plano que detecta botões retidos em `WAIT_CURSOR` por mais de 1,2s e destrava a interface automaticamente.

---

## 🎮 Guia Rápido de Instalação e Testes

1. Extraia o conteúdo de **`brasfoot_release_betav1.zip`** em qualquer pasta de sua preferência.
2. Dê duplo clique em **`Brasfoot2026.exe`** para iniciar o jogo.
3. **Para testar o Modo Férias (F4)**:
   - Abra a tela principal da sua equipe e pressione **F4**.
   - A opção **"🏆 Até o Fim do Estadual"** já estará selecionada.
   - Clique em **"Confirmar"** e acompanhe a simulação automática contínua de todo o campeonato estadual até o retorno das férias.
4. **Para testar o Scout & Metas da Diretoria**:
   - Inicie uma nova temporada e abra a **Caixa de Entrada / Notícias**.
   - Verifique a carta oficial de metas da diretoria e os relatórios de contratações do scout com os novos badges visuais.
5. **Para testar o Craque do Jogo (Live Rating / MVP)**:
   - Jogue qualquer partida e observe o cálculo das notas dos jogadores e a tela final com a eleição do MVP da partida.
6. **Para testar a Simulação Instantânea (F8)**:
   - Em qualquer partida, aperte **F8** para avançar o placar imediatamente.

---
*Desenvolvido com dedicação para a comunidade Brasfoot. Bom jogo a todos!*
