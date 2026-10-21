package br.com.brasfootmod.scout;

import br.com.brasfootmod.BrasfootAgent;
import br.com.brasfootmod.vacation.BrasfootVacationController;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.FileWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;
import java.util.List;

/**
 * Controlador de Inteligência de Mercado e Scout do Brasfoot 22-23 (Opção 1 - Passiva).
 *
 * Funcionalidades:
 * 1. Diagnóstico do Elenco Humano:
 *    - Analisa carências numéricas (falta de jogadores por posição).
 *    - Identifica defasagem de força do setor em relação à força média do time.
 *    - Detecta envelhecimento de setores prioritários.
 * 2. Motor de Varredura de Mercado com Filtro Geográfico Realista:
 *    - Para times brasileiros: sugere majoritariamente jogadores brasileiros e sul-americanos.
 *    - Atletas atuando no Brasil, ligas sul-americanas ou brasileiros no exterior.
 *    - Bloqueia estrelas europeias (como Leroy Sané, Haaland, Mbappé).
 * 3. Envio Passivo e Enquadramento Perfeito:
 *    - Layout HTML otimizado para a altura exata do visualizador nativo (198px),
 *      garantindo que 100% dos dados fiquem visíveis sem cortes verticais ou horizontais.
 */
public class ScoutController {

    private static long ultimoCheckTempoReal = 0;
    private static Calendar ultimaDataRelatorio = null;
    private static final Set<String> relatoriosEnviadosChaves = new HashSet<>();
    private static String ultimoClubeMonitorado = null;

    // Snapshot para detecção de vendas e reposição imediata
    private static class PlayerSnapshot {
        Object playerObj;
        String nome;
        int posicao;
        int forca;
        int idade;
        int cr1;
        int cr2;
        String caracteristicas = "";

        PlayerSnapshot(Object obj, String n, int pos, int f, int id, int cr1, int cr2, String car) {
            this.playerObj = obj;
            this.nome = n;
            this.posicao = pos;
            this.forca = f;
            this.idade = id;
            this.cr1 = cr1;
            this.cr2 = cr2;
            this.caracteristicas = car != null ? car : "";
        }
    }
    private static final Map<String, PlayerSnapshot> snapshotUltimoElenco = new HashMap<>();

    // Códigos oficiais de países sul-americanos no Brasfoot
    public static final Set<Integer> SUL_AMERICANOS = new HashSet<>(Arrays.asList(
            29,  // Brasil
            11,  // Argentina
            26,  // Bolívia
            42,  // Chile
            46,  // Colômbia
            60,  // Equador
            150, // Paraguai
            151, // Peru
            195, // Uruguai
            198  // Venezuela
    ));

    private static final String[] POSICOES_NOMES = {
            "Goleiro", "Lateral", "Zagueiro", "Meio-Campo", "Atacante"
    };

    private static final String[] POSICOES_ABREV = {
            "GOL", "LAT", "ZAG", "MEI", "ATA"
    };

    private static final int[] MINIMO_RECOMENDADO = {
            2, // Goleiros (mín 2)
            3, // Laterais (mín 3)
            4, // Zagueiros (mín 4)
            5, // Meias (mín 5)
            4  // Atacantes (mín 4)
    };

    public static void log(String msg) {
        try {
            FileWriter fw = new FileWriter("C:\\Brasfoot22-23\\mod_debug.log", true);
            fw.write(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + " [SCOUT] " + msg + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    /**
     * Injeta o ScoutInboxCellRenderer na tabela de mensagens se a tela a.bn estiver aberta
     */
    public static void verificarEInjetarInboxRenderer() {
        for (Window w : Window.getWindows()) {
            if (w == null || !w.isShowing()) continue;

            Component bnComp = BrasfootAgent.findComponentBySuffix(w, ".bn");
            if (bnComp == null && w instanceof Container) {
                bnComp = findComponentClass((Container) w, "a.bn");
            }

            if (bnComp != null) {
                try {
                    Field adField = bnComp.getClass().getDeclaredField("Ad");
                    adField.setAccessible(true);
                    JTable table = (JTable) adField.get(bnComp);

                    if (table != null) {
                        try {
                            Field vdField = bnComp.getClass().getDeclaredField("vd");
                            vdField.setAccessible(true);
                            JPanel vd = (JPanel) vdField.get(bnComp);
                            if (vd != null && !Color.WHITE.equals(vd.getBackground())) {
                                vd.setBackground(Color.WHITE);
                                vd.repaint();
                            }
                        } catch (Throwable ignored) {}

                        if (table.getClientProperty("ScoutRendererInjected") == null) {
                            table.putClientProperty("ScoutRendererInjected", Boolean.TRUE);

                            ScoutInboxCellRenderer customRenderer = new ScoutInboxCellRenderer();

                            try {
                                Class<?> asCls = Class.forName("components.as");
                                table.setDefaultRenderer(asCls, customRenderer);
                            } catch (Throwable ignored) {}

                            table.setDefaultRenderer(Object.class, customRenderer);
                            for (int col = 0; col < table.getColumnCount(); col++) {
                                table.getColumnModel().getColumn(col).setCellRenderer(customRenderer);
                            }

                            table.repaint();
                            log("ScoutInboxCellRenderer injetado com sucesso na caixa de mensagens!");
                        }
                    }
                } catch (Throwable t) {
                    log("Erro ao injetar renderer em a.bn: " + t.getMessage());
                }
            }
        }
    }

    private static Component findComponentClass(Container container, String className) {
        if (container.getClass().getName().equals(className)) return container;
        for (Component c : container.getComponents()) {
            if (c.getClass().getName().equals(className)) return c;
            if (c instanceof Container) {
                Component found = findComponentClass((Container) c, className);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Ciclo periódico verificado a cada 50ms pelo agente
     */
    public static void checkPeriodicScoutUpdate() {
        // 1. Sempre verifica se a Caixa de Entrada (a.bn) está aberta para aplicar o renderer e scroll sem delay (a cada 50ms)
        verificarEInjetarInboxRenderer();

        long now = System.currentTimeMillis();
        if (now - ultimoCheckTempoReal < 2000) {
            return;
        }
        ultimoCheckTempoReal = now;

        // 2. Verifica se um jogo/save está carregado
        Object srObj = BrasfootVacationController.getSRObject();
        if (srObj == null) return;

        Calendar curCal = BrasfootVacationController.getCurrentGameCalendar();
        if (curCal == null) return;

        Object humanCoach = getHumanCoach(srObj);
        if (humanCoach == null) return;

        Object humanClub = getHumanClub(humanCoach);
        if (humanClub == null) return;

        try {
            Method kcMethod = humanClub.getClass().getMethod("kc");
            List<?> squadAtual = (List<?>) kcMethod.invoke(humanClub);
            if (squadAtual != null && !squadAtual.isEmpty()) {
                // Monitor de Vendas / Reposição Imediata:
                // Sempre que um jogador sair do elenco humano (vendido, transferido, etc.),
                // gera instantaneamente um relatório de reposição para a posição dele
                verificarVendasElenco(srObj, humanCoach, humanClub, curCal, squadAtual);
            }
        } catch (Throwable ignored) {}

        // 3. Cadência: Enviar no início ou a cada ~25-30 dias de jogo
        boolean enviar = false;
        if (ultimaDataRelatorio == null) {
            enviar = true;
        } else {
            long diffMillis = curCal.getTimeInMillis() - ultimaDataRelatorio.getTimeInMillis();
            long diffDias = diffMillis / (1000L * 60 * 60 * 24);
            if (diffDias >= 28) {
                enviar = true;
            }
        }

        if (enviar) {
            executarEnvioRelatorio(srObj, humanCoach, humanClub, curCal);
        }
    }

    /**
     * Executa a análise e envia o relatório para a caixa de entrada
     */
    private static void executarEnvioRelatorio(Object srObj, Object humanCoach, Object humanClub, Calendar curCal) {
        try {
            String clubName = (String) humanClub.getClass().getMethod("getNome").invoke(humanClub);
            Method kcMethod = humanClub.getClass().getMethod("kc");
            List<?> squad = (List<?>) kcMethod.invoke(humanClub);

            if (squad == null || squad.isEmpty()) return;

            // Chave única para o clube e mês do relatório
            String mesChave = clubName + "_" + curCal.get(Calendar.YEAR) + "_" + (curCal.get(Calendar.MONTH) + 1);
            if (relatoriosEnviadosChaves.contains(mesChave)) {
                return;
            }

            // 1. Mapeia posições recentemente scoutadas na Caixa de Entrada para garantir rodízio
            Map<Integer, Integer> recenciaPorPos = obterHistoricoPosicoesScoutadas(humanCoach);

            // 2. Diagnóstico inteligente e rotativo do Elenco
            SquadDiagnosis diagnosis = diagnosticarElenco(squad, recenciaPorPos);

            // 3. Varredura de Mercado (Filtros regionais e de reputação realistas)
            List<ScoutCandidate> targets = buscarAlvosMercado(srObj, humanCoach, humanClub, diagnosis);

            // Trava absoluta de segurança: NUNCA entregar relatório sem alvos para a Caixa de Entrada!
            if (targets == null || targets.isEmpty()) {
                log("⚠️ Nenhum alvo encontrado para " + clubName + " na posição " + POSICOES_NOMES[diagnosis.posicaoAlvo] + ". Relatório em branco evitado.");
                return;
            }

            // 4. Monta o corpo HTML perfeitamente enquadrado para não cortar
            String html = gerarHtmlRelatorio(clubName, curCal, diagnosis, targets);

            // 5. Título do relatório
            String title = "[Scout] " + diagnosis.tituloSugestao;

            // Idempotência: verifica se a mensagem exata já consta no lQ()
            try {
                Method lQMethod = humanCoach.getClass().getMethod("lQ");
                List<?> msgs = (List<?>) lQMethod.invoke(humanCoach);
                if (msgs != null) {
                    for (Object m : msgs) {
                        Method vpMethod = m.getClass().getMethod("vp");
                        String t = (String) vpMethod.invoke(m);
                        if (title.equals(t)) {
                            relatoriosEnviadosChaves.add(mesChave);
                            ultimaDataRelatorio = (Calendar) curCal.clone();
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            log("🔎 Gerando relatório de Scout para " + clubName + " (" + title + ")");
            boolean entregue = entregarMensagemInbox(humanCoach, title, html);

            if (entregue) {
                ultimaDataRelatorio = (Calendar) curCal.clone();
                relatoriosEnviadosChaves.add(mesChave);
                log("📩 Relatório do Scout entregue com sucesso na Caixa de Entrada!");
            }
        } catch (Throwable t) {
            log("Erro ao executar envio de relatório: " + t.getMessage());
        }
    }

    /**
     * Mapeia a recência das posições já scoutadas na Caixa de Entrada do treinador
     * (1 = última scoutada, 2 = penúltima, etc.) para evitar repetições no rodízio
     */
    private static Map<Integer, Integer> obterHistoricoPosicoesScoutadas(Object humanCoach) {
        Map<Integer, Integer> recenciaPorPos = new HashMap<>();
        try {
            Method lQMethod = humanCoach.getClass().getMethod("lQ");
            List<?> msgs = (List<?>) lQMethod.invoke(humanCoach);
            if (msgs != null) {
                int recenciaCounter = 1;
                for (int i = msgs.size() - 1; i >= 0 && recenciaCounter <= 4; i--) {
                    Object m = msgs.get(i);
                    Method vpMethod = m.getClass().getMethod("vp");
                    String title = (String) vpMethod.invoke(m);
                    if (title != null && title.startsWith("[Scout]")) {
                        for (int p = 0; p < 5; p++) {
                            if (title.contains(POSICOES_NOMES[p]) || (p == 4 && title.contains("Promessas"))) {
                                if (!recenciaPorPos.containsKey(p)) {
                                    recenciaPorPos.put(p, recenciaCounter);
                                    recenciaCounter++;
                                }
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return recenciaPorPos;
    }

    private static class SquadDiagnosis {
        int posicaoAlvo = 2; // Default: Zagueiro
        String motivo = "";
        String tituloSugestao = "";
        String tipoRelatorio = "GERAL"; // "CARENCIA", "DESEMPENHO", "RENOVACAO", "REPOSICAO", "OPORTUNIDADE"
        double notaMinimaRequerida = 0.0;
        int totalJogadores = 0;
        double forcaMediaGeral = 0.0;
        double notaMediaGeral = 0.0;
        int[] contagemPorPos = new int[5];
        double[] forcaMediaPorPos = new double[5];
        double[] idadeMediaPorPos = new double[5];
        double[] notaMediaPorPos = new double[5];

        // Dados específicos para Reposição Inteligente de Vendas
        int forcaJogadorVendido = 0;
        int idadeJogadorVendido = 0;
        String caracteristicasJogadorVendido = "";
        String perfilJogadorVendido = ""; // "Ofensivo", "Defensivo", "Finalizador", "Velocista", "Misto"
    }

    public static String formatarClubeNome(String nome) {
        if (nome == null) return "";
        if (nome.equals("Paris Saint Germain") || nome.equals("Paris Saint-Germain")) return "Paris SG";
        if (nome.contains("Atlético de Madrid") || nome.contains("Atlético Madri") || nome.contains("Atletico Madrid")) return "Atl. Madrid";
        if (nome.contains("Athletico Paranaense")) return "Athletico-PR";
        if (nome.contains("América Mineiro")) return "América-MG";
        if (nome.contains("Red Bull Bragantino")) return "RB Bragantino";
        if (nome.contains("Manchester United")) return "Man. United";
        if (nome.contains("Manchester City")) return "Man. City";
        if (nome.length() > 14) {
            return nome.substring(0, 13) + ".";
        }
        return nome;
    }

    public static String formatarAtletaNome(String nome) {
        if (nome == null) return "";
        if (nome.length() > 15) {
            return nome.substring(0, 14) + ".";
        }
        return nome;
    }

    public static final String[] CARACTERISTICAS_NOMES = {
        "Pen", "Rep", "Ref", "SGo", "Arm", "Cab", "Cru", "Des", "Dri", "Fin", "Mar", "Pas", "Res", "Vel"
    };

    public static String extrairCaracteristicasJogador(Object p) {
        if (p == null) return "--";
        try {
            Method mCr1 = p.getClass().getMethod("getCr1");
            Method mCr2 = p.getClass().getMethod("getCr2");
            int cr1 = ((Number) mCr1.invoke(p)).intValue();
            int cr2 = ((Number) mCr2.invoke(p)).intValue();

            String s1 = (cr1 >= 0 && cr1 < CARACTERISTICAS_NOMES.length) ? CARACTERISTICAS_NOMES[cr1] : null;
            String s2 = (cr2 >= 0 && cr2 < CARACTERISTICAS_NOMES.length) ? CARACTERISTICAS_NOMES[cr2] : null;

            if (s1 != null && s2 != null) {
                return s1.toLowerCase() + "/" + s2.toLowerCase();
            } else if (s1 != null) {
                return s1.toLowerCase();
            }
        } catch (Throwable ignored) {}
        return "--";
    }

    public static boolean isOfensiva(String car) {
        if (car == null || car.equals("--")) return false;
        String lc = car.toLowerCase();
        return lc.contains("pas") || lc.contains("fin") || lc.contains("arm")
                || lc.contains("dri") || lc.contains("vel") || lc.contains("cru");
    }

    public static boolean isDefensiva(String car) {
        if (car == null || car.equals("--")) return false;
        String lc = car.toLowerCase();
        return lc.contains("mar") || lc.contains("des") || lc.contains("cab");
    }

    public static class ScoutCandidate {
        public Object player;
        public String nome = "";
        public int posicao = 0;
        public String posAbrev = "";
        public int forca = 0;
        public int idade = 0;
        public double nota = 0.0;
        public String notaFormatada = "--";
        public int playerPais = 0;
        public int clubePais = 0;
        public String clubeNome = "";
        public String caracteristicas = "";
        public String valorFormatado = "";
        public String situacao = ""; // "Empréstimo", "À Venda", "Livre", "Disponível"
        public String perfilTag = ""; // "Reforço Imediato", "Jovem Promessa", "Oportunidade"
        public int scoreAfinidade = 0;
        public int clubeRep = 0;
        public int clubeDiv = 0;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ScoutCandidate that = (ScoutCandidate) o;
            if (player != null && that.player != null) {
                return player.equals(that.player);
            }
            return Objects.equals(nome, that.nome) && Objects.equals(clubeNome, that.clubeNome);
        }

        @Override
        public int hashCode() {
            if (player != null) return player.hashCode();
            return Objects.hash(nome, clubeNome);
        }
    }

    /**
     * Diagnóstico conciso das carências e pontos fracos do elenco com sistema de rodízio inteligente de setores
     */
    /**
     * Diagnóstico conciso das carências e pontos fracos do elenco com:
     * 1. Detecção estrita de carências numéricas reais
     * 2. Quando não é carência, seleção aleatória e não-linear de setores (sem ordem fixa)
     * 3. Cruzamento com a classificação média (nota) dos jogadores do setor
     */
    private static SquadDiagnosis diagnosticarElenco(List<?> squad, Map<Integer, Integer> recenciaPorPos) {
        SquadDiagnosis diag = new SquadDiagnosis();
        diag.totalJogadores = squad.size();

        int[] somaForca = new int[5];
        int[] somaIdade = new int[5];
        double[] somaNota = new double[5];
        int[] countComNota = new int[5];
        int forcaGeralTotal = 0;
        double notaGeralTotal = 0.0;
        int totalComNota = 0;

        for (Object p : squad) {
            try {
                int pos = (Integer) p.getClass().getMethod("getPosicao").invoke(p);
                int forca = (Integer) p.getClass().getMethod("fi").invoke(p);
                int idade = (Integer) p.getClass().getMethod("getIdade").invoke(p);
                double nota = BrasfootAgent.calcularNota(p);

                if (pos >= 0 && pos < 5) {
                    diag.contagemPorPos[pos]++;
                    somaForca[pos] += forca;
                    somaIdade[pos] += idade;
                    forcaGeralTotal += forca;

                    if (nota >= 2.0) {
                        somaNota[pos] += nota;
                        countComNota[pos]++;
                        notaGeralTotal += nota;
                        totalComNota++;
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (diag.totalJogadores > 0) {
            diag.forcaMediaGeral = (double) forcaGeralTotal / diag.totalJogadores;
        }
        if (totalComNota > 0) {
            diag.notaMediaGeral = notaGeralTotal / totalComNota;
        }

        for (int i = 0; i < 5; i++) {
            if (diag.contagemPorPos[i] > 0) {
                diag.forcaMediaPorPos[i] = (double) somaForca[i] / diag.contagemPorPos[i];
                diag.idadeMediaPorPos[i] = (double) somaIdade[i] / diag.contagemPorPos[i];
            }
            if (countComNota[i] > 0) {
                diag.notaMediaPorPos[i] = somaNota[i] / countComNota[i];
            }
        }

        // 1. Verifica se há carência numérica aguda no elenco
        int setorComCarencia = -1;
        int maiorDeficit = 0;
        for (int i = 0; i < 5; i++) {
            if (diag.contagemPorPos[i] < MINIMO_RECOMENDADO[i]) {
                int deficit = MINIMO_RECOMENDADO[i] - diag.contagemPorPos[i];
                if (deficit > maiorDeficit) {
                    maiorDeficit = deficit;
                    setorComCarencia = i;
                }
            }
        }

        int posEscolhida;
        String tipo;

        if (setorComCarencia >= 0) {
            // Existe carência numérica real no elenco: prioridade para recomposição
            posEscolhida = setorComCarencia;
            tipo = "CARENCIA";
        } else {
            // NÃO HÁ CARÊNCIA NUMÉRICA:
            // Regra: Não seguir ordem fixa (goleiro, zagueiro, lateral, meia, atacante).
            // Fazer de forma aleatória e dinâmica, cruzando com notas médias!

            // Filtra posições candidatas que NÃO foram reportadas no mês anterior
            List<Integer> candidatos = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                int recencia = recenciaPorPos != null && recenciaPorPos.containsKey(i) ? recenciaPorPos.get(i) : 0;
                if (recencia == 1) continue; // Pula o setor do mês imediatamente anterior
                candidatos.add(i);
            }
            if (candidatos.isEmpty()) {
                for (int i = 0; i < 5; i++) candidatos.add(i);
            }

            // Embaralha aleatoriamente para garantir imprevisibilidade total
            Collections.shuffle(candidatos);

            // Verifica se algum dos setores candidatos possui classificação média baixa (< 6.40)
            int setorComBaixaNota = -1;
            double menorNota = 999.0;
            for (int pos : candidatos) {
                if (diag.notaMediaPorPos[pos] >= 2.0 && diag.notaMediaPorPos[pos] < 6.40) {
                    if (diag.notaMediaPorPos[pos] < menorNota) {
                        menorNota = diag.notaMediaPorPos[pos];
                        setorComBaixaNota = pos;
                    }
                }
            }

            Random rnd = new Random();
            if (setorComBaixaNota >= 0 && rnd.nextDouble() < 0.75) {
                // Foco em Desempenho / Classificação Média defasada
                posEscolhida = setorComBaixaNota;
                tipo = "DESEMPENHO";
            } else {
                // Sorteio aleatório entre os setores disponíveis
                posEscolhida = candidatos.get(rnd.nextInt(candidatos.size()));
                if (diag.notaMediaPorPos[posEscolhida] >= 2.0 && diag.notaMediaPorPos[posEscolhida] <= 6.50) {
                    tipo = "DESEMPENHO";
                } else if (diag.idadeMediaPorPos[posEscolhida] >= 29.5) {
                    tipo = "RENOVACAO";
                } else if (diag.forcaMediaGeral - diag.forcaMediaPorPos[posEscolhida] >= 0.5) {
                    tipo = "REFORCO";
                } else {
                    tipo = "OPORTUNIDADE";
                }
            }
        }

        diag.posicaoAlvo = posEscolhida;
        diag.tipoRelatorio = tipo;

        // Monta motivo e título baseado no tipo
        if ("CARENCIA".equals(tipo)) {
            diag.tituloSugestao = "Carência: " + POSICOES_NOMES[posEscolhida];
            diag.motivo = "Carência na posição de " + POSICOES_NOMES[posEscolhida] + " ("
                    + diag.contagemPorPos[posEscolhida] + " opções, mín. " + MINIMO_RECOMENDADO[posEscolhida] + "). Alvos mapeados:";
        } else if ("DESEMPENHO".equals(tipo)) {
            double mediaSetor = diag.notaMediaPorPos[posEscolhida];
            diag.notaMinimaRequerida = mediaSetor;
            diag.tituloSugestao = "Desempenho: " + POSICOES_NOMES[posEscolhida] + " (Média: "
                    + String.format(Locale.US, "%.2f", mediaSetor) + ")";
            diag.motivo = "A classificação média dos nossos " + POSICOES_NOMES[posEscolhida] + "s foi de <b>"
                    + String.format(Locale.US, "%.2f", mediaSetor)
                    + "</b> no período. Mapeamos opções de mercado com classificação superior para elevar o nível da posição:";
        } else if ("RENOVACAO".equals(tipo)) {
            diag.tituloSugestao = "Renovação: " + POSICOES_NOMES[posEscolhida];
            diag.motivo = "Setor de " + POSICOES_NOMES[posEscolhida] + " com idade elevada ("
                    + String.format(Locale.US, "%.1f", diag.idadeMediaPorPos[posEscolhida]) + "a), necessita renovação.";
        } else if ("REFORCO".equals(tipo)) {
            diag.tituloSugestao = "Reforço para " + POSICOES_NOMES[posEscolhida];
            diag.motivo = "Setor de " + POSICOES_NOMES[posEscolhida] + " com força ("
                    + String.format(Locale.US, "%.1f", diag.forcaMediaPorPos[posEscolhida])
                    + ") abaixo do time ("
                    + String.format(Locale.US, "%.1f", diag.forcaMediaGeral) + ").";
        } else {
            diag.tituloSugestao = "Oportunidades: " + POSICOES_NOMES[posEscolhida];
            diag.motivo = "Radar de mercado focado em novas opções para o setor de " + POSICOES_NOMES[posEscolhida] + ":";
        }

        return diag;
    }

            /**
     * Extrai os nomes dos atletas que já foram recomendados em relatórios anteriores do scout
     * para não repetir os mesmos jogadores mês a mês.
     */
    private static Set<String> obterAtletasSugeridosNaInbox(Object humanCoach) {
        Set<String> nomes = new HashSet<>();
        if (humanCoach == null) return nomes;
        try {
            Method lQMethod = humanCoach.getClass().getMethod("lQ");
            List<?> msgs = (List<?>) lQMethod.invoke(humanCoach);
            if (msgs != null) {
                Pattern p = Pattern.compile("<b>([^<]+)</b>");
                for (Object m : msgs) {
                    Method vpMethod = m.getClass().getMethod("vp");
                    String title = (String) vpMethod.invoke(m);
                    if (title != null && title.startsWith("[Scout]")) {
                        Method vqMethod = m.getClass().getMethod("vq");
                        String body = (String) vqMethod.invoke(m);
                        if (body != null) {
                            Matcher matcher = p.matcher(body);
                            while (matcher.find()) {
                                String n = matcher.group(1).trim();
                                // Filtra cabeçalhos, datas, valores numéricos e textos padrão
                                boolean hasDigit = false;
                                for (int i = 0; i < n.length(); i++) {
                                    if (Character.isDigit(n.charAt(i))) {
                                        hasDigit = true;
                                        break;
                                    }
                                }
                                if (hasDigit || n.equalsIgnoreCase("RELATÓRIO DO SCOUT")
                                        || n.equalsIgnoreCase("Diagnóstico:")
                                        || n.equalsIgnoreCase("Empréstimo")
                                        || n.equalsIgnoreCase("Livre")
                                        || n.equalsIgnoreCase("Consulte")) {
                                    continue;
                                }
                                nomes.add(n);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return nomes;
    }

    /**
     * Varre clubes e sem-contratos filtrando majoritariamente brasileiros e sul-americanos para times do Brasil
     */
    private static List<ScoutCandidate> buscarAlvosMercado(Object srObj, Object humanCoach, Object humanClub, SquadDiagnosis diag) {
        List<ScoutCandidate> result = new ArrayList<>();
        List<ScoutCandidate> titulares = new ArrayList<>();
        List<ScoutCandidate> promessas = new ArrayList<>();
        List<ScoutCandidate> oportunidades = new ArrayList<>();
        List<ScoutCandidate> todosCandidatos = new ArrayList<>();
        List<ScoutCandidate> fallbackCandidatos = new ArrayList<>();

        int humanClubPais = 29;
        int humanClubDiv = 1;
        int humanClubRep = 3;
        try {
            humanClubPais = (Integer) humanClub.getClass().getMethod("getPais").invoke(humanClub);
        } catch (Throwable ignored) {}
        try {
            humanClubDiv = (Integer) humanClub.getClass().getMethod("getDivisao").invoke(humanClub);
        } catch (Throwable ignored) {}
        try {
            humanClubRep = (Integer) humanClub.getClass().getMethod("getReputacao").invoke(humanClub);
        } catch (Throwable ignored) {}

        if (humanClubDiv <= 0) humanClubDiv = 1;
        if (humanClubRep <= 0) humanClubRep = 3;

        // Regra de Renovação / Idade Máxima:
        boolean isRenovacao = (diag.tituloSugestao != null && diag.tituloSugestao.contains("Renovação"))
                || (diag.motivo != null && (diag.motivo.toLowerCase().contains("renovação") || diag.motivo.toLowerCase().contains("renovacao")))
                || (diag.idadeMediaPorPos[diag.posicaoAlvo] >= 29.5);

        boolean isReposicao = "REPOSICAO".equals(diag.tipoRelatorio);
        boolean isDesempenho = "DESEMPENHO".equals(diag.tipoRelatorio);
        boolean isReforco = "REFORCO".equals(diag.tipoRelatorio)
                || (diag.motivo != null && (diag.motivo.toLowerCase().contains("abaixo do time")
                                         || diag.motivo.toLowerCase().contains("força")
                                         || diag.motivo.toLowerCase().contains("forca")));
        int maxIdadeGeral = isRenovacao ? 30 : (isReposicao ? Math.max(32, diag.idadeJogadorVendido) : 32);

        // Média de força do setor alvo
        double mediaSetor = diag.forcaMediaPorPos[diag.posicaoAlvo];
        if (mediaSetor <= 0) {
            mediaSetor = diag.forcaMediaGeral;
        }

        // Se for reposição por venda, o balizador é a força do atleta vendido
        double forcaReferencia = (isReposicao && diag.forcaJogadorVendido > 0)
                ? Math.max(mediaSetor, (double) diag.forcaJogadorVendido)
                : mediaSetor;

        // Piso calibrado por categoria de contratação para garantir qualidade e realismo:
        // 1. Jogador Pronto: titular ou reserva imediato para disputar posição com o elenco atual
        int forcaMinPronto = Math.max(22, (int) Math.round(forcaReferencia - (isReposicao ? 3 : 5)));

        // 2. Oportunidade: jogador à venda, empréstimo ou sem contrato (livre)
        int forcaMinOportunidade = Math.max(22, (int) Math.round(forcaReferencia - (isReposicao ? 5 : 7)));

        // 3. Jovem Promessa (Sub-21): atleta jovem em desenvolvimento avaliado por potencial futuro
        int forcaMinPromessa = Math.max(24, (int) Math.round(forcaReferencia - (isReposicao ? 10 : 12)));

        // REGRA DE OURO: Quando for melhoria de força no setor (REFORÇO),
        // TODOS os atletas sugeridos OBRIGATORIAMENTE devem ter força MAIOR que a média do setor!
        int pisoForcaReforco = (int) Math.floor(mediaSetor) + 1;
        if (isReforco) {
            forcaMinPronto = Math.max(forcaMinPronto, pisoForcaReforco);
            forcaMinOportunidade = Math.max(forcaMinOportunidade, pisoForcaReforco);
            forcaMinPromessa = Math.max(forcaMinPromessa, pisoForcaReforco);
        }

        // Piso geral mínimo de corte para evitar atletas amadores/muito fracos
        int pisoForcaGeral = Math.min(forcaMinPronto, Math.min(forcaMinOportunidade, forcaMinPromessa));

        try {
            Method pMethod = srObj.getClass().getMethod("P");
            List<?> clubs = (List<?>) pMethod.invoke(srObj);

            if (clubs != null) {
                for (Object club : clubs) {
                    if (club == null || club.equals(humanClub)) continue;

                    int clubPais = 0;
                    int clubDiv = 1;
                    int clubRep = 3;
                    try {
                        clubPais = (Integer) club.getClass().getMethod("getPais").invoke(club);
                    } catch (Throwable ignored) {}
                    try {
                        clubDiv = (Integer) club.getClass().getMethod("getDivisao").invoke(club);
                    } catch (Throwable ignored) {}
                    try {
                        clubRep = (Integer) club.getClass().getMethod("getReputacao").invoke(club);
                    } catch (Throwable ignored) {}

                    if (clubDiv <= 0) clubDiv = 1;
                    if (clubRep <= 0) clubRep = 3;

                    Method kcMethod = club.getClass().getMethod("kc");
                    List<?> players = (List<?>) kcMethod.invoke(club);
                    if (players == null) continue;

                    String clubNome = (String) club.getClass().getMethod("getNome").invoke(club);

                    for (Object p : players) {
                        ScoutCandidate cand = extrairCandidato(p, clubNome, clubPais, clubRep, clubDiv,
                                                               humanClubRep, humanClubDiv, diag.posicaoAlvo);
                        if (cand == null) continue;

                        // Trava de integridade: Neymar pertence ao Santos (não ao PSG)
                        if ("Neymar".equalsIgnoreCase(cand.nome) && clubNome != null && clubNome.toLowerCase().contains("paris")) {
                            continue;
                        }

                        // Regra de Reforço: se o relatório for melhoria de força no setor, a força DEVE superar a média do setor
                        if (isReforco && cand.forca < pisoForcaReforco) {
                            continue;
                        }

                        // Hard Cap de Idade: se for renovação, NENHUM atleta > 30 anos é aceito
                        if (cand.idade > maxIdadeGeral) {
                            continue;
                        }

                        // Filtro de Desempenho / Classificação Média:
                        if (isDesempenho && diag.notaMinimaRequerida > 0) {
                            // Para relatório de desempenho, o atleta OBRIGATORIAMENTE precisa ter nota oficial registrada (>= 2.0)
                            // E sua classificação média deve ser ESTRITAMENTE SUPERIOR à média do setor do time humano!
                            if (cand.nota < 2.0 || cand.nota <= diag.notaMinimaRequerida) {
                                continue;
                            }
                            // Concede bônus expressivo de afinidade proporcional ao quanto ele supera o setor
                            cand.scoreAfinidade += 100 + (int) Math.round((cand.nota - diag.notaMinimaRequerida) * 50.0);
                        }

                        // Filtro geográfico e de reputação realista balizado em forcaReferencia
                        if (!isCandidatoValidoParaClube(cand.playerPais, cand.clubePais, cand.clubeRep, cand.clubeDiv,
                                humanClubPais, humanClubRep, humanClubDiv, cand.idade, cand.forca, forcaReferencia,
                                cand.situacao.equals("Empréstimo"), cand.situacao.equals("À Venda"))) {
                            continue;
                        }

                        // Coleta para fallback com piso flexibilizado caso a busca estrita não atinja 3 atletas
                        if (cand.forca >= Math.max(20, (int) Math.round(forcaReferencia - 8)) && cand.idade <= (isRenovacao ? 31 : 33)) {
                            if (!isDesempenho || (cand.nota >= 2.0 && cand.nota > diag.notaMinimaRequerida)) {
                                if (!isReforco || cand.forca >= pisoForcaReforco) {
                                    fallbackCandidatos.add(cand);
                                }
                            }
                        }

                        // Piso de corte específico por perfil:
                        int pisoAtleta;
                        if (cand.idade <= 21) {
                            pisoAtleta = forcaMinPromessa;
                        } else if (cand.situacao.equals("Empréstimo") || cand.situacao.equals("À Venda") || "Livre".equals(cand.situacao)) {
                            pisoAtleta = forcaMinOportunidade;
                        } else {
                            pisoAtleta = forcaMinPronto;
                        }

                        if (cand.forca < pisoAtleta) {
                            continue;
                        }

                        // Filtro tático estrito: descarta volantes puramente marcadores (ex: des/mar) ao repor meia ofensivo
                        if (diag.posicaoAlvo == 3 && "Ofensivo".equals(diag.perfilJogadorVendido)) {
                            if (isDefensiva(cand.caracteristicas) && !isOfensiva(cand.caracteristicas)) continue;
                        } else if (diag.posicaoAlvo == 3 && "Defensivo".equals(diag.perfilJogadorVendido)) {
                            if (isOfensiva(cand.caracteristicas) && !isDefensiva(cand.caracteristicas)) continue;
                        } else if (diag.posicaoAlvo == 1 && "Ofensivo".equals(diag.perfilJogadorVendido)) {
                            if (isDefensiva(cand.caracteristicas) && !isOfensiva(cand.caracteristicas)) continue;
                        } else if (diag.posicaoAlvo == 1 && "Defensivo".equals(diag.perfilJogadorVendido)) {
                            if (isOfensiva(cand.caracteristicas) && !isDefensiva(cand.caracteristicas)) continue;
                        }

                        // Nota média como bônus moderado de desempate (máximo 8 pontos)
                        if (cand.nota >= 6.5) {
                            cand.scoreAfinidade += (int) Math.round((cand.nota - 6.0) * 4.0);
                        }

                        // Afinidade tática de características com o atleta vendido
                        if (diag.caracteristicasJogadorVendido != null && !diag.caracteristicasJogadorVendido.isEmpty()
                                && cand.caracteristicas != null && !cand.caracteristicas.isEmpty()) {
                            String[] partesVendido = diag.caracteristicasJogadorVendido.toLowerCase().split("/");
                            String candCar = cand.caracteristicas.toLowerCase();
                            int matches = 0;
                            for (String trait : partesVendido) {
                                trait = trait.trim();
                                if (!trait.isEmpty() && candCar.contains(trait)) {
                                    matches++;
                                }
                            }
                            if (matches >= 2) cand.scoreAfinidade += 80;
                            else if (matches == 1) cand.scoreAfinidade += 40;

                            if ("Ofensivo".equals(diag.perfilJogadorVendido)) {
                                if (candCar.contains("arm")) cand.scoreAfinidade += 25;
                                if (candCar.contains("pas")) cand.scoreAfinidade += 20;
                                if (candCar.contains("fin")) cand.scoreAfinidade += 20;
                            } else if ("Defensivo".equals(diag.perfilJogadorVendido)) {
                                if (candCar.contains("des")) cand.scoreAfinidade += 25;
                                if (candCar.contains("mar")) cand.scoreAfinidade += 25;
                            }
                        }

                        todosCandidatos.add(cand);

                        // 1. Oportunidade: jogador à venda ou empréstimo com força competitiva
                        if (cand.situacao.equals("Empréstimo") || cand.situacao.equals("À Venda") || "Livre".equals(cand.situacao)) {
                            if (cand.forca >= forcaMinOportunidade) {
                                ScoutCandidate opCand = clonarCandidato(cand);
                                opCand.perfilTag = "Oportunidade";
                                oportunidades.add(opCand);
                            }
                        }

                        // 2. Jovem Promessa: Sub-21 com bom potencial para o setor
                        if (cand.idade <= 21 && cand.forca >= forcaMinPromessa) {
                            ScoutCandidate promCand = clonarCandidato(cand);
                            promCand.perfilTag = "Jovem Promessa";
                            promessas.add(promCand);
                        }

                        // 3. Jogador Pronto: na média de força do setor, maduro (22 a 30 anos se renovação)
                        if (cand.forca >= forcaMinPronto && cand.idade >= 22 && cand.idade <= maxIdadeGeral) {
                            ScoutCandidate prontoCand = clonarCandidato(cand);
                            prontoCand.perfilTag = "Jogador Pronto";
                            titulares.add(prontoCand);
                        }
                    }
                }
            }

            // Varre jogadores sem contrato (bt)
            try {
                Method btMethod = srObj.getClass().getMethod("bt");
                List<?> freeAgents = (List<?>) btMethod.invoke(srObj);
                if (freeAgents != null) {
                    for (Object p : freeAgents) {
                        ScoutCandidate cand = extrairCandidato(p, "Sem Clube", 29, 0, 0,
                                                               humanClubRep, humanClubDiv, diag.posicaoAlvo);
                        if (cand == null) continue;
                        if (cand.idade > maxIdadeGeral) continue;
                        if (isDesempenho && diag.notaMinimaRequerida > 0) {
                            if (cand.nota < 2.0 || cand.nota <= diag.notaMinimaRequerida) continue;
                        }
                        if (isReforco && cand.forca < pisoForcaReforco) continue;
                        if (cand.forca < pisoForcaGeral) continue;

                        if (diag.posicaoAlvo == 3 && "Ofensivo".equals(diag.perfilJogadorVendido)) {
                            if (!isOfensiva(cand.caracteristicas)) continue;
                        } else if (diag.posicaoAlvo == 3 && "Defensivo".equals(diag.perfilJogadorVendido)) {
                            if (!isDefensiva(cand.caracteristicas)) continue;
                        } else if (diag.posicaoAlvo == 1 && "Ofensivo".equals(diag.perfilJogadorVendido)) {
                            if (!isOfensiva(cand.caracteristicas)) continue;
                        } else if (diag.posicaoAlvo == 1 && "Defensivo".equals(diag.perfilJogadorVendido)) {
                            if (!isDefensiva(cand.caracteristicas)) continue;
                        }

                        if (isCandidatoValidoParaClube(cand.playerPais, 29, 0, 0,
                                humanClubPais, humanClubRep, humanClubDiv, cand.idade, cand.forca, forcaReferencia,
                                false, true)) {
                            cand.situacao = "Livre";
                            if (cand.forca >= Math.max(20, (int) Math.round(forcaReferencia - 8)) && cand.idade <= (isRenovacao ? 31 : 33)) {
                                if (!isDesempenho || (cand.nota >= 2.0 && cand.nota > diag.notaMinimaRequerida)) {
                                    if (!isReforco || cand.forca >= pisoForcaReforco) {
                                        fallbackCandidatos.add(cand);
                                    }
                                }
                            }

                            int pisoAtleta;
                            if (cand.idade <= 21) {
                                pisoAtleta = forcaMinPromessa;
                            } else {
                                pisoAtleta = forcaMinOportunidade;
                            }
                            if (cand.forca < pisoAtleta) continue;
                            // Afinidade tática de características com o atleta vendido
                            if (diag.caracteristicasJogadorVendido != null && !diag.caracteristicasJogadorVendido.isEmpty()
                                    && cand.caracteristicas != null && !cand.caracteristicas.isEmpty()) {
                                String[] partesVendido = diag.caracteristicasJogadorVendido.toLowerCase().split("/");
                                String candCar = cand.caracteristicas.toLowerCase();
                                int matches = 0;
                                for (String trait : partesVendido) {
                                    trait = trait.trim();
                                    if (!trait.isEmpty() && candCar.contains(trait)) {
                                        matches++;
                                    }
                                }
                                if (matches >= 2) cand.scoreAfinidade += 80;
                                else if (matches == 1) cand.scoreAfinidade += 40;
                            }
                            cand.situacao = "Livre";
                            todosCandidatos.add(cand);

                            if (cand.forca >= forcaMinOportunidade) {
                                ScoutCandidate opCand = clonarCandidato(cand);
                                opCand.perfilTag = "Oportunidade";
                                oportunidades.add(opCand);
                            }
                            if (cand.idade <= 21 && cand.forca >= forcaMinPromessa) {
                                ScoutCandidate promCand = clonarCandidato(cand);
                                promCand.perfilTag = "Jovem Promessa";
                                promessas.add(promCand);
                            }
                            if (cand.forca >= forcaMinPronto && cand.idade >= 22 && cand.idade <= maxIdadeGeral) {
                                ScoutCandidate prontoCand = clonarCandidato(cand);
                                prontoCand.perfilTag = "Jogador Pronto";
                                titulares.add(prontoCand);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // Ordenações especializadas para cada categoria
            if (isDesempenho) {
                // Em relatórios de desempenho, CLASSIFICAÇÃO MÉDIA É SEMPRE O CRITÉRIO NÚMERO 1!
                java.util.Comparator<ScoutCandidate> compDesempenho = (a, b) -> {
                    int cmpNota = Double.compare(b.nota, a.nota);
                    if (cmpNota != 0) return cmpNota;
                    int cmpForca = Integer.compare(b.forca, a.forca);
                    if (cmpForca != 0) return cmpForca;
                    return Integer.compare(b.scoreAfinidade, a.scoreAfinidade);
                };
                titulares.sort(compDesempenho);
                promessas.sort(compDesempenho);
                oportunidades.sort(compDesempenho);
                todosCandidatos.sort(compDesempenho);
            } else {
                // 1. Jogadores Prontos: FORÇA é sempre o critério número 1
                titulares.sort((a, b) -> {
                    int cmpForca = Integer.compare(b.forca, a.forca);
                    if (cmpForca != 0) return cmpForca;
                    int cmpAfin = Integer.compare(b.scoreAfinidade, a.scoreAfinidade);
                    if (cmpAfin != 0) return cmpAfin;
                    int cmpNota = Double.compare(b.nota, a.nota);
                    if (cmpNota != 0) return cmpNota;
                    return Integer.compare(a.idade, b.idade);
                });

                // 2. Jovens Promessas: FORÇA primeiro, depois potencial
                promessas.sort((a, b) -> {
                    int cmpForca = Integer.compare(b.forca, a.forca);
                    if (cmpForca != 0) return cmpForca;
                    int potA = (a.forca * 3) - (a.idade * 2) + a.scoreAfinidade;
                    int potB = (b.forca * 3) - (b.idade * 2) + b.scoreAfinidade;
                    if (potB != potA) return Integer.compare(potB, potA);
                    return Double.compare(b.nota, a.nota);
                });

                // 3. Oportunidades: FORÇA primeiro, depois situação
                oportunidades.sort((a, b) -> {
                    int cmpForca = Integer.compare(b.forca, a.forca);
                    if (cmpForca != 0) return cmpForca;
                    int prioA = "Empréstimo".equals(a.situacao) ? 2 : ("À Venda".equals(a.situacao) ? 1 : 0);
                    int prioB = "Empréstimo".equals(b.situacao) ? 2 : ("À Venda".equals(b.situacao) ? 1 : 0);
                    if (prioB != prioA) return Integer.compare(prioB, prioA);
                    int cmpAfin = Integer.compare(b.scoreAfinidade, a.scoreAfinidade);
                    if (cmpAfin != 0) return cmpAfin;
                    return Double.compare(b.nota, a.nota);
                });

                // todosCandidatos: FORÇA É SEMPRE PRIMEIRO
                todosCandidatos.sort((a, b) -> {
                    int cmpForca = Integer.compare(b.forca, a.forca);
                    if (cmpForca != 0) return cmpForca;
                    int cmpAfin = Integer.compare(b.scoreAfinidade, a.scoreAfinidade);
                    if (cmpAfin != 0) return cmpAfin;
                    return Double.compare(b.nota, a.nota);
                });
            }

            // Anti-repetição de atletas: separa opções inéditas das já recomendadas nesta carreira
            Set<String> atletasJaSugeridos = obterAtletasSugeridosNaInbox(humanCoach);

            List<ScoutCandidate> opFrescas = new ArrayList<>();
            List<ScoutCandidate> opRepetidas = new ArrayList<>();
            for (ScoutCandidate c : oportunidades) {
                if (atletasJaSugeridos.contains(c.nome)) opRepetidas.add(c);
                else opFrescas.add(c);
            }

            List<ScoutCandidate> promFrescas = new ArrayList<>();
            List<ScoutCandidate> promRepetidas = new ArrayList<>();
            for (ScoutCandidate c : promessas) {
                if (atletasJaSugeridos.contains(c.nome)) promRepetidas.add(c);
                else promFrescas.add(c);
            }

            List<ScoutCandidate> prontosFrescos = new ArrayList<>();
            List<ScoutCandidate> prontosRepetidos = new ArrayList<>();
            for (ScoutCandidate c : titulares) {
                if (atletasJaSugeridos.contains(c.nome)) prontosRepetidos.add(c);
                else prontosFrescos.add(c);
            }

            List<ScoutCandidate> baseOp = !opFrescas.isEmpty() ? opFrescas : oportunidades;
            List<ScoutCandidate> baseProm = !promFrescas.isEmpty() ? promFrescas : promessas;
            List<ScoutCandidate> basePronto = !prontosFrescos.isEmpty() ? prontosFrescos : titulares;

            // Variabilidade dinâmica:
            // No modo REPOSICAO ou DESEMPENHO, restringe a seleção estritamente ao topo dos melhores (top 3)
            int poolSizeOp = (isReposicao || isDesempenho) ? Math.min(3, baseOp.size()) : Math.min(8, baseOp.size());
            int poolSizeProm = (isReposicao || isDesempenho) ? Math.min(3, baseProm.size()) : Math.min(8, baseProm.size());
            int poolSizePronto = (isReposicao || isDesempenho) ? Math.min(3, basePronto.size()) : Math.min(10, basePronto.size());

            List<ScoutCandidate> poolOp = new ArrayList<>(baseOp.subList(0, poolSizeOp));
            List<ScoutCandidate> poolProm = new ArrayList<>(baseProm.subList(0, poolSizeProm));
            List<ScoutCandidate> poolPronto = new ArrayList<>(basePronto.subList(0, poolSizePronto));

            // No relatório de desempenho ou reposição, não embaralha para não perder os líderes da nota/força
            if (!isDesempenho && !isReposicao) {
                Collections.shuffle(poolOp);
                Collections.shuffle(poolProm);
                Collections.shuffle(poolPronto);
            }

            Set<Object> playersAdicionados = new HashSet<>();
            java.util.function.Consumer<ScoutCandidate> adicionarUnico = (c) -> {
                if (c == null || result.size() >= 3) return;
                Object key = (c.player != null) ? c.player : c.nome;
                if (!playersAdicionados.contains(key)) {
                    playersAdicionados.add(key);
                    result.add(c);
                }
            };

            // 1. Prioridade 1: O melhor Jogador Pronto (maior força/nota)
            if (!poolPronto.isEmpty()) {
                adicionarUnico.accept(poolPronto.get(0));
            } else if (!basePronto.isEmpty()) {
                adicionarUnico.accept(basePronto.get(0));
            }

            // 2. Prioridade 2: Jovem Promessa (se houver no nível de força/nota)
            for (ScoutCandidate prom : poolProm) {
                if (result.size() >= 2) break;
                adicionarUnico.accept(prom);
            }
            if (result.size() < 2) {
                for (ScoutCandidate prom : baseProm) {
                    if (result.size() >= 2) break;
                    adicionarUnico.accept(prom);
                }
            }

            // 3. Prioridade 3: Oportunidade de mercado (se houver no nível de força/nota)
            for (ScoutCandidate op : poolOp) {
                if (result.size() >= 3) break;
                adicionarUnico.accept(op);
            }
            if (result.size() < 3) {
                for (ScoutCandidate op : baseOp) {
                    if (result.size() >= 3) break;
                    adicionarUnico.accept(op);
                }
            }

            // 4. Se faltar vagas para fechar 3 nomes, preenche com os melhores titulares
            for (ScoutCandidate c : basePronto) {
                if (result.size() >= 3) break;
                adicionarUnico.accept(c);
            }

            // 5. Se ainda faltar, preenche com todosCandidatos
            for (ScoutCandidate c : todosCandidatos) {
                if (result.size() >= 3) break;
                adicionarUnico.accept(c);
            }

            // 6. Fallback de garantia: se ainda faltar vagas para fechar 3 nomes, usa candidatos de fallback
            if (result.size() < 3 && !fallbackCandidatos.isEmpty()) {
                if (isDesempenho) {
                    fallbackCandidatos.sort((a, b) -> {
                        int cmpNota = Double.compare(b.nota, a.nota);
                        if (cmpNota != 0) return cmpNota;
                        return Integer.compare(b.forca, a.forca);
                    });
                } else {
                    fallbackCandidatos.sort((a, b) -> Integer.compare(b.forca, a.forca));
                }
                for (ScoutCandidate c : fallbackCandidatos) {
                    if (result.size() >= 3) break;
                    if (isDesempenho && (c.nota < 2.0 || c.nota <= diag.notaMinimaRequerida)) continue;
                    if (isReforco && c.forca < pisoForcaReforco) continue;
                    adicionarUnico.accept(c);
                }
            }

            // Trava final estrita para DESEMPENHO: expurga qualquer atleta que não supere a média do setor
            if (isDesempenho) {
                result.removeIf(c -> c.nota < 2.0 || c.nota <= diag.notaMinimaRequerida);
                result.sort((a, b) -> {
                    int cmpNota = Double.compare(b.nota, a.nota);
                    if (cmpNota != 0) return cmpNota;
                    return Integer.compare(b.forca, a.forca);
                });
            }

            // Trava final estrita para REFORÇO DE SETOR: expurga qualquer atleta cuja força não seja estritamente superior à média do setor
            if (isReforco) {
                result.removeIf(c -> c.forca < pisoForcaReforco);
                result.sort((a, b) -> Integer.compare(b.forca, a.forca));
            }

        } catch (Throwable t) {
            log("Erro ao buscar alvos no mercado: " + t.getMessage());
        }

        return result;
    }

    /**
     * Validação regional e de reputação realista:
     * - Limita teto de força compatível com a divisão e reputação do clube humano
     * - Impede que clubes de divisões inferiores (ex: Bahia na Série B) recebam sugestões
     *   de estrelas mundiais da Europa (ex: Bremer na Juventus).
     * - Times brasileiros contratam majoritariamente no Brasil e na América do Sul.
     */
    private static boolean isCandidatoValidoParaClube(int playerPais, int clubPais, int clubRep, int clubDiv,
                                                      int humanClubPais, int humanClubRep, int humanClubDiv,
                                                      int idade, int forca, double mediaSetor,
                                                      boolean emprestimo, boolean venda) {
        boolean isHumanBrasil = (humanClubPais == 29);

        // 1. Teto e Piso de Força Realista:
        // A força do jogador deve ser estritamente calibrada com o elenco do time humano.
        int maxDeltaForca = (humanClubDiv == 1 && humanClubRep >= 4) ? 4 : 3;
        double baseTeto = Math.max(mediaSetor, (humanClubDiv == 1 ? 48.0 : 42.0));
        int maxForcaPermitida = (int) Math.round(baseTeto + maxDeltaForca);
        if (forca > maxForcaPermitida) {
            return false;
        }

        // Piso de Força calibrado por perfil:
        // - Sub-21 (promessas): avaliado pelo potencial futuro
        // - Oportunidades (empréstimo/venda/livre): piso moderado
        // - Demais (titulares/prontos): piso rigoroso
        int minDeltaForca;
        if (idade <= 21) {
            minDeltaForca = 14; // Sub-21 avaliado por potencial de desenvolvimento futuro
        } else if (emprestimo || venda) {
            minDeltaForca = (humanClubDiv == 1 && humanClubRep >= 4) ? 7 : 8;
        } else {
            minDeltaForca = (humanClubDiv == 1 && humanClubRep >= 4) ? 5 : 6;
        }
        int minForcaPermitida = Math.max(18, (int) Math.round(mediaSetor - minDeltaForca));
        if (forca < minForcaPermitida) {
            return false;
        }

        if (!isHumanBrasil) {
            return true;
        }

        boolean playerSulAm = SUL_AMERICANOS.contains(playerPais);
        boolean clubSulAm = SUL_AMERICANOS.contains(clubPais);
        boolean clubBrasil = (clubPais == 29);

        // 2. Clubes fora da América do Sul (Europa, Ásia, etc.):
        if (!clubSulAm) {
            // Se o clube humano joga na 2ª divisão ou abaixo (Série B, C, D):
            // Não compra titulares nem estrelas da Europa em definitivo!
            if (humanClubDiv >= 2) {
                // Apenas empréstimo é viável para Série B vindo da Europa
                if (!emprestimo) {
                    return false;
                }
                // Não aceita empréstimo de gigantes mundiais (clubRep > humanClubRep + 1)
                if (clubRep > humanClubRep + 1) {
                    return false;
                }
                return true;
            }

            // Se o clube humano joga na 1ª divisão (Série A):
            // Bloqueia compras de jogadores de clubes com reputação estritamente superior
            // (ex: jogador da Juventus/Real Madrid com reputação 5 para clube com reputação 3 ou 4)
            // a não ser que esteja listado para empréstimo/venda ou seja veterano (repatriação aos 32+).
            if (clubRep > humanClubRep) {
                if (!emprestimo && !venda && idade < 32) {
                    return false;
                }
            }

            // Jogador na Europa tem que ser brasileiro ou sul-americano
            if (!playerSulAm && playerPais != 29) {
                return false;
            }

            return true;
        }

        // 3. Clubes na América do Sul (fora do Brasil):
        if (!clubBrasil && clubSulAm) {
            if (humanClubDiv >= 2 && clubRep > humanClubRep) {
                if (!emprestimo && !venda) {
                    return false;
                }
            }
            return true;
        }

        // 4. Clubes no Brasil:
        if (clubBrasil) {
            // Se o clube vendedor está na Série A (divisão 1) e o humano na Série B ou inferior (divisão >= 2):
            // Titulares de clubes maiores não descem para a Série B se forem da mesma faixa etária sem estarem listados
            if (humanClubDiv >= 2 && clubRep > humanClubRep) {
                if (!emprestimo && !venda && idade > 21 && forca > (int) Math.round(mediaSetor + 1)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private static ScoutCandidate clonarCandidato(ScoutCandidate c) {
        ScoutCandidate n = new ScoutCandidate();
        n.player = c.player;
        n.nome = c.nome;
        n.posicao = c.posicao;
        n.posAbrev = c.posAbrev;
        n.forca = c.forca;
        n.idade = c.idade;
        n.playerPais = c.playerPais;
        n.clubePais = c.clubePais;
        n.clubeNome = c.clubeNome;
        n.clubeRep = c.clubeRep;
        n.clubeDiv = c.clubeDiv;
        n.nota = c.nota;
        n.notaFormatada = c.notaFormatada;
        n.caracteristicas = c.caracteristicas;
        n.valorFormatado = c.valorFormatado;
        n.situacao = c.situacao;
        n.perfilTag = c.perfilTag;
        n.scoreAfinidade = c.scoreAfinidade;
        return n;
    }

    private static ScoutCandidate extrairCandidato(Object p, String clubeNome, int clubePais, int clubeRep, int clubeDiv,
                                                   int humanClubRep, int humanClubDiv, int targetPos) {
        try {
            int pos = (Integer) p.getClass().getMethod("getPosicao").invoke(p);
            if (pos != targetPos) return null;

            int status = (Integer) p.getClass().getMethod("getStatus").invoke(p);
            if (status != 0) return null;

            ScoutCandidate c = new ScoutCandidate();
            c.player = p;
            c.nome = (String) p.getClass().getMethod("getNome").invoke(p);
            c.posicao = pos;
            c.posAbrev = POSICOES_ABREV[pos];
            c.forca = (Integer) p.getClass().getMethod("fi").invoke(p);
            c.idade = (Integer) p.getClass().getMethod("getIdade").invoke(p);
            c.playerPais = (Integer) p.getClass().getMethod("getPais").invoke(p);
            c.clubePais = clubePais;
            c.clubeNome = clubeNome;
            c.clubeRep = clubeRep;
            c.clubeDiv = clubeDiv;

            // Classificação média (Nota) do atleta
            double nota = BrasfootAgent.calcularNota(p);
            c.nota = nota;
            if (nota >= 2.0) {
                c.notaFormatada = String.format(Locale.US, "%.2f", nota);
            } else {
                c.notaFormatada = "--";
            }

            // Pontuação de afinidade
            int score = 0;
            if (clubePais == 29) score += 50; // Atuando no Brasil
            else if (SUL_AMERICANOS.contains(clubePais)) score += 35; // Atuando na América do Sul
            if (c.playerPais == 29) score += 30; // Brasileiro
            else if (SUL_AMERICANOS.contains(c.playerPais)) score += 20; // Sul-americano

            // Bônus para mesma divisão ou divisão próxima
            if (clubeDiv == humanClubDiv && clubeDiv > 0) {
                score += 20;
            } else if (Math.abs(clubeDiv - humanClubDiv) == 1 && clubeDiv > 0) {
                score += 10;
            }

            // Bônus de proximidade de reputação
            if (Math.abs(clubeRep - humanClubRep) <= 1 && clubeRep > 0) {
                score += 15;
            }

            c.scoreAfinidade = score;

            // Formatação de valor
            try {
                int valor = (Integer) p.getClass().getMethod("fk").invoke(p);
                Method formatMoney = Class.forName("best.C").getMethod("a", double.class, int.class);
                c.valorFormatado = (String) formatMoney.invoke(null, (double) valor, 0);
            } catch (Throwable ignored) {
                c.valorFormatado = "Consulte";
            }

            // Situação de transferência / empréstimo
            boolean emprestimo = false;
            boolean venda = false;
            try {
                emprestimo = Boolean.TRUE.equals(p.getClass().getMethod("fz").invoke(p));
                venda = Boolean.TRUE.equals(p.getClass().getMethod("ft").invoke(p));
            } catch (Throwable ignored) {}

            if (emprestimo) {
                c.situacao = "Empréstimo";
            } else if (venda) {
                c.situacao = "À Venda";
            } else {
                c.situacao = "Disponível";
            }

            // Características usando mapeamento nativo oficial do Brasfoot
            c.caracteristicas = extrairCaracteristicasJogador(p);

            return c;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String gerarHtmlRelatorio(String clubName, Calendar curCal, SquadDiagnosis diag, List<ScoutCandidate> targets) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        String dataStr = sdf.format(curCal.getTime());

        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("<body style='margin:0; padding:0px 2px; font-family:Tahoma,Arial,sans-serif; color:#111827; background-color:#FFFFFF;'>");

        // Header Bar Minimalista Compacto (370px para caber com folga total no painel nativo)
        sb.append("<table width='370' cellpadding='0' cellspacing='0' style='border-bottom:2px solid #111827; padding-bottom:2px; margin-bottom:4px;'>");
        sb.append("  <tr>");
        sb.append("    <td><b><font color='#111827' size='2'>RELATÓRIO DO SCOUT</font></b></td>");
        sb.append("    <td align='right'><font size='1' color='#6B7280'>").append(dataStr).append("</font></td>");
        sb.append("  </tr>");
        sb.append("</table>");

        // Diagnóstico Clean Compacto
        sb.append("<table width='370' cellpadding='0' cellspacing='0' style='margin-bottom:4px;'>");
        sb.append("  <tr><td style='font-size:10px; color:#374151;'>");
        sb.append("    <b><font color='#111827'>Diagnóstico:</font></b> ").append(diag.motivo);
        sb.append("  </td></tr>");
        sb.append("</table>");

        // Tabela Minimalista Perfeitamente Calibrada (370px)
        sb.append("<table width='370' cellpadding='0' cellspacing='0' style='font-size:9.5px; border-collapse:collapse;'>");
        sb.append("  <tr style='border-bottom:1px solid #111827; color:#6B7280; font-size:9px; font-weight:bold;'>");
        sb.append("    <th align='left' width='92' style='padding:2px 1px;'>ATLETA</th>");
        sb.append("    <th align='center' width='18' style='padding:2px 1px;'>ID</th>");
        sb.append("    <th align='center' width='22' style='padding:2px 1px;'>POS</th>");
        sb.append("    <th align='center' width='42' style='padding:2px 1px;'>CAR</th>");
        sb.append("    <th align='center' width='20' style='padding:2px 1px;'>FOR</th>");
        sb.append("    <th align='center' width='26' style='padding:2px 1px;'>NOTA</th>");
        sb.append("    <th align='left' width='85' style='padding:2px 2px;'>CLUBE</th>");
        sb.append("    <th align='right' width='48' style='padding:2px 1px;'>VALOR</th>");
        sb.append("  </tr>");

        if (targets != null && !targets.isEmpty()) {
            for (ScoutCandidate c : targets) {
                String rowTooltip = c.perfilTag != null && !c.perfilTag.isEmpty() ? c.perfilTag : "Atleta Indicado";
                sb.append("  <tr title='").append(rowTooltip).append("' style='border-bottom:1px solid #E5E7EB;'>");
                
                String nomeFormatado = formatarAtletaNome(c.nome);
                sb.append("    <td nowrap style='padding:2px 1px;'><font color='#111827'><b>").append(nomeFormatado).append("</b></font></td>");
                sb.append("    <td align='center' style='padding:2px 1px;'><font color='#4B5563'>").append(c.idade).append("</font></td>");
                sb.append("    <td align='center' style='padding:2px 1px;'><font color='#4B5563'>").append(c.posAbrev).append("</font></td>");
                
                // Características (ex: pas/fin)
                String carStr = (c.caracteristicas != null && !c.caracteristicas.isEmpty()) ? c.caracteristicas : "--";
                sb.append("    <td align='center' style='padding:2px 1px;'><font color='#2563EB'><b>").append(carStr).append("</b></font></td>");

                sb.append("    <td align='center' style='padding:2px 1px;'><font color='#111827'><b>").append(c.forca).append("</b></font></td>");
                
                // Nota Média
                String corNota = c.nota >= 7.0 ? "#047857" : (c.nota >= 6.0 ? "#0D9488" : "#4B5563");
                sb.append("    <td align='center' style='padding:2px 1px;'><font color='").append(corNota).append("'><b>").append(c.notaFormatada).append("</b></font></td>");

                // Clube abreviado inteligentemente para não estourar a largura
                String clubeFormatado = formatarClubeNome(c.clubeNome);
                sb.append("    <td nowrap style='padding:2px 2px;'><font color='#4B5563'>").append(clubeFormatado).append("</font></td>");

                sb.append("    <td align='right' style='padding:2px 1px;'><font color='#111827'><b>");
                if ("Empréstimo".equals(c.situacao)) {
                    sb.append("Emp.");
                } else if (c.situacao != null && c.situacao.contains("Livre")) {
                    sb.append("Livre");
                } else {
                    sb.append(c.valorFormatado);
                }
                sb.append("</b></font></td>");
                sb.append("  </tr>");
            }
        }

        // Rodapé compacto conciso
        sb.append("  <tr>");
        sb.append("    <td colspan='8' style='padding-top:4px; font-size:8.5px; color:#6B7280; font-style:italic;'>");
        sb.append("      Dica: Sugestões em Oportunidade, Jovem Promessa e Jogador Pronto.");
        sb.append("    </td>");
        sb.append("  </tr>");

        sb.append("</table>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static boolean entregarMensagemInbox(Object humanCoach, String title, String htmlBody) {
        try {
            ClassLoader cl = BrasfootAgent.getGameClassLoader();
            Class<?> asClass = Class.forName("components.as", true, cl);
            Class<?> alClass = Class.forName("best.al", true, cl);

            Constructor<?> cons = asClass.getConstructor(alClass, int.class, int.class, String.class, String.class);
            Object msgObj = cons.newInstance(humanCoach, 0, -1, title, htmlBody);

            if (msgObj != null) {
                return true;
            }
        } catch (Throwable t) {
            log("Erro ao instanciar components.as: " + t.getMessage());
        }
        return false;
    }

    private static Object getHumanCoach(Object srObj) {
        try {
            Method lMethod = srObj.getClass().getMethod("L");
            List<?> coaches = (List<?>) lMethod.invoke(srObj);
            if (coaches != null) {
                for (Object coach : coaches) {
                    Method isHuman = coach.getClass().getMethod("jZ");
                    Boolean human = (Boolean) isHuman.invoke(coach);
                    if (Boolean.TRUE.equals(human)) {
                        return coach;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object getHumanClub(Object coach) {
        try {
            Method getClub = coach.getClass().getMethod("fg");
            return getClub.invoke(coach);
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Monitor ativo de vendas e transferências de saída do elenco humano.
     * Sempre que um jogador for vendido ou transferido, dispara instantaneamente
     * um relatório de reposição para a Caixa de Entrada.
     */
    private static void verificarVendasElenco(Object srObj, Object humanCoach, Object humanClub, Calendar curCal, List<?> squadAtual) {
        try {
            String clubName = (String) humanClub.getClass().getMethod("getNome").invoke(humanClub);
            if (ultimoClubeMonitorado == null || !ultimoClubeMonitorado.equals(clubName)) {
                ultimoClubeMonitorado = clubName;
                salvarSnapshotElenco(squadAtual);
                return;
            }

            if (snapshotUltimoElenco.isEmpty()) {
                salvarSnapshotElenco(squadAtual);
                return;
            }

            Set<String> chavesAtuais = new HashSet<>();
            for (Object p : squadAtual) {
                try {
                    String nome = (String) p.getClass().getMethod("getNome").invoke(p);
                    int pos = (Integer) p.getClass().getMethod("getPosicao").invoke(p);
                    chavesAtuais.add(nome + "_" + pos);
                } catch (Throwable ignored) {}
            }

            List<PlayerSnapshot> saidas = new ArrayList<>();
            for (Map.Entry<String, PlayerSnapshot> entry : snapshotUltimoElenco.entrySet()) {
                if (!chavesAtuais.contains(entry.getKey())) {
                    saidas.add(entry.getValue());
                }
            }

            // Atualiza snapshot
            salvarSnapshotElenco(squadAtual);

            // Para cada jogador que saiu do elenco, dispara imediatamente relatório de reposição!
            for (PlayerSnapshot pSaiu : saidas) {
                log("💰 Venda/saída de atleta detectada: " + pSaiu.nome + " (" + POSICOES_NOMES[pSaiu.posicao] + ", Força " + pSaiu.forca + ")");
                gerarRelatorioReposicao(srObj, humanCoach, humanClub, curCal, pSaiu);
            }
        } catch (Throwable t) {
            log("Erro ao verificar vendas do elenco: " + t.getMessage());
        }
    }

    private static void salvarSnapshotElenco(List<?> squad) {
        snapshotUltimoElenco.clear();
        for (Object p : squad) {
            try {
                String nome = (String) p.getClass().getMethod("getNome").invoke(p);
                int pos = (Integer) p.getClass().getMethod("getPosicao").invoke(p);
                int forca = (Integer) p.getClass().getMethod("fi").invoke(p);
                int idade = (Integer) p.getClass().getMethod("getIdade").invoke(p);
                int cr1 = 0;
                int cr2 = 0;
                try {
                    cr1 = (Integer) p.getClass().getMethod("getCr1").invoke(p);
                    cr2 = (Integer) p.getClass().getMethod("getCr2").invoke(p);
                } catch (Throwable ignored) {}
                String car = extrairCaracteristicasJogador(p);
                snapshotUltimoElenco.put(nome + "_" + pos, new PlayerSnapshot(p, nome, pos, forca, idade, cr1, cr2, car));
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Gera relatório de reposição imediata pós-venda focado na posição do jogador vendido
     */
    private static void gerarRelatorioReposicao(Object srObj, Object humanCoach, Object humanClub, Calendar curCal, PlayerSnapshot pSaiu) {
        try {
            String clubName = (String) humanClub.getClass().getMethod("getNome").invoke(humanClub);
            Method kcMethod = humanClub.getClass().getMethod("kc");
            List<?> squad = (List<?>) kcMethod.invoke(humanClub);

            SquadDiagnosis diag = new SquadDiagnosis();
            diag.posicaoAlvo = pSaiu.posicao;
            diag.tipoRelatorio = "REPOSICAO";
            diag.forcaJogadorVendido = pSaiu.forca;
            diag.idadeJogadorVendido = pSaiu.idade;
            diag.caracteristicasJogadorVendido = pSaiu.caracteristicas;
            diag.tituloSugestao = "Reposição: Venda de " + pSaiu.nome + " (" + POSICOES_ABREV[pSaiu.posicao] + ")";

            // Classifica perfil tático do atleta vendido
            if (pSaiu.posicao == 3) { // Meio-Campo
                boolean of = isOfensiva(pSaiu.caracteristicas);
                boolean def = isDefensiva(pSaiu.caracteristicas);
                if (of && !def) {
                    diag.perfilJogadorVendido = "Ofensivo";
                } else if (def && !of) {
                    diag.perfilJogadorVendido = "Defensivo";
                } else {
                    diag.perfilJogadorVendido = "Misto";
                }
            } else if (pSaiu.posicao == 1) { // Lateral
                boolean of = isOfensiva(pSaiu.caracteristicas);
                boolean def = isDefensiva(pSaiu.caracteristicas);
                if (of && !def) {
                    diag.perfilJogadorVendido = "Ofensivo";
                } else if (def && !of) {
                    diag.perfilJogadorVendido = "Defensivo";
                }
            } else if (pSaiu.posicao == 4) { // Atacante
                String lc = pSaiu.caracteristicas.toLowerCase();
                if (lc.contains("fin") || lc.contains("cab")) {
                    diag.perfilJogadorVendido = "Finalizador";
                } else if (lc.contains("vel") || lc.contains("dri")) {
                    diag.perfilJogadorVendido = "Velocista";
                }
            }

            String carTexto = (pSaiu.caracteristicas != null && !pSaiu.caracteristicas.isEmpty())
                    ? ", " + pSaiu.caracteristicas
                    : "";
            String perfilTexto;
            if ("Ofensivo".equals(diag.perfilJogadorVendido)) {
                perfilTexto = " Alvos mapeados com características ofensivas e nível compatível:";
            } else if ("Defensivo".equals(diag.perfilJogadorVendido)) {
                perfilTexto = " Alvos mapeados com perfil defensivo/marcação e nível compatível:";
            } else {
                perfilTexto = " Alvos mapeados para assumir a posição:";
            }

            diag.motivo = "Reposição imediata no elenco após a saída de <b>" + pSaiu.nome + "</b> ("
                    + POSICOES_NOMES[pSaiu.posicao] + ", Força " + pSaiu.forca + carTexto + ", " + pSaiu.idade + "a)." + perfilTexto;

            // Força de referência ancorada diretamente no atleta vendido para não ser diluída por reservas
            diag.forcaMediaPorPos[pSaiu.posicao] = pSaiu.forca;

            List<ScoutCandidate> targets = buscarAlvosMercado(srObj, humanCoach, humanClub, diag);
            if (targets.isEmpty()) return;

            String html = gerarHtmlRelatorio(clubName, curCal, diag, targets);
            String title = "[Scout] " + diag.tituloSugestao;

            boolean entregue = entregarMensagemInbox(humanCoach, title, html);
            if (entregue) {
                log("📩 Relatório de reposição entregue com sucesso para a saída de " + pSaiu.nome + "!");
            }
        } catch (Throwable t) {
            log("Erro ao gerar relatório de reposição: " + t.getMessage());
        }
    }
}
