package br.com.brasfootmod.goals;

import br.com.brasfootmod.BrasfootAgent;
import br.com.brasfootmod.vacation.BrasfootVacationController;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Controlador de Objetivos de Temporada & Cobrança de Desempenho da Diretoria (v2).
 *
 * Melhorias implementadas:
 * 1. Detecção Dinâmica de TODAS as Competições do Clube:
 *    - Brasileirão / Liga Nacional (b == 1)
 *    - Estadual (b == 3)
 *    - Copa do Brasil (Copa Nacional)
 *    - Copa Libertadores / Copa Sul-Americana (bB / f.r / f.H / f.z)
 *    - Recopa Sul-Americana / Supercopa / Mundial de Clubes (se classificado)
 * 2. Plano de Progressão de Carreira:
 *    - Analisa histórico de anos anteriores (ano - 1, ano - 2).
 *    - Se o time se classificou 2 anos seguidos para a Libertadores, o objetivo sobe para Disputar o Título!
 *    - Se foi campeão, o objetivo vira o Bicampeonato.
 * 3. Tolerância Realista e Ponderação de Demissão:
 *    - O Estadual NÃO é determinante para demissão (peso reduzido).
 *    - Margem de tolerância: Se a meta era o Título e terminou em 2º ou 3º, é excelente campanha e o técnico permanece!
 *    - A demissão só é cabível em caso de fracasso crítico (ex: briga pelo título que termina em 10º+, ou rebaixamento).
 * 4. Correção no Balanço Semestral e Fim de Ano:
 *    - Identificação estrita da Liga Nacional (b == 1) evitando falso match com o Estadual.
 *    - Cálculo de posição por fallback caso a tabela não esteja indexada no momento.
 *    - Envio do meio do ano garantido no 1º turno e veredito final ao término do campeonato.
 */
public class SeasonGoalsController {

    public static volatile boolean ENABLED = true;
    private static final String KILL_SWITCH_FILE = "C:\\Brasfoot22-23\\disable_season_goals.flag";
    private static final String LOG_FILE = "C:\\Brasfoot22-23\\mod_debug.log";
    private static final String SAV_DIR = "C:\\Brasfoot22-23\\sav";

    private static long ultimoCheckTempoReal = 0;
    private static SeasonGoal metaAtiva = null;

    public static class CompGoal {
        public String nomeCompeticao = "";
        public String tipoCompeticao = ""; // "NACIONAL", "ESTADUAL", "COPA_NACIONAL", "CONTINENTAL", "RECOPA_SUPERCOPA"
        public String metaDescricao = "";
        public int metaPosicaoMax = -1; // Para ligas de pontos corridos

        public CompGoal() {}
        public CompGoal(String nome, String tipo, String meta, int posMax) {
            this.nomeCompeticao = nome;
            this.tipoCompeticao = tipo;
            this.metaDescricao = meta;
            this.metaPosicaoMax = posMax;
        }
    }

    public static class SeasonGoal {
        public int ano = 2026;
        public String nomeClube = "";
        public int divisao = 1;
        public int nivelForca = 16;
        public int reputacao = 5;

        // Meta Principal da Liga
        public int metaPosBrasileiraoMax = 16;
        public String tituloMetaBrasileirao = "Permanência na Série A";
        public String nivelExigencia = "Alta";
        public String motivoProgressao = ""; // Justificativa de evolução histórica

        // Lista de todas as competições do ano
        public List<CompGoal> metasCompeticoes = new ArrayList<CompGoal>();

        // Flags de Fases Anuais
        public boolean cartaInicioEnviada = false;
        public boolean balancoMeioEnviado = false;
        public boolean vereditoFinalEnviado = false;

        // Resultados
        public int posicaoMeioAno = -1;
        public int rodadaMeioAno = -1;
        public String statusMeioAno = "PENDENTE";
        public int posicaoFinal = -1;
        public int pontosFinal = 0;
        public String statusFinal = "PENDENTE";

        // Controle de Chegada no Clube / Gestão de Transição
        public int rodadaEntrada = 0; // Quantos jogos de liga o clube já havia jogado quando o treinador assumiu
        public boolean contratadoComTemporadaEmAndamento = false;
    }

    public static void log(String msg) {
        try {
            FileWriter fw = new FileWriter(LOG_FILE, true);
            fw.write(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + " [METAS] " + msg + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    public static boolean isFeatureActive() {
        if (!ENABLED) return false;
        File f = new File(KILL_SWITCH_FILE);
        return !f.exists();
    }

    /**
     * Ciclo periódico chamado pelo BrasfootAgent (a cada ~1,5s - 2s)
     */
    public static void checkPeriodicGoalsUpdate() {
        if (!isFeatureActive()) return;

        long now = System.currentTimeMillis();
        if (now - ultimoCheckTempoReal < 1500) {
            return;
        }
        ultimoCheckTempoReal = now;

        try {
            Object srObj = BrasfootVacationController.getSRObject();
            if (srObj == null) return;

            Calendar curCal = BrasfootVacationController.getCurrentGameCalendar();
            if (curCal == null) return;

            Object humanCoach = getHumanCoach(srObj);
            if (humanCoach == null) return;

            Object humanClub = getHumanClub(humanCoach);
            if (humanClub == null) return;

            String clubName = (String) humanClub.getClass().getMethod("getNome").invoke(humanClub);
            int curYear = curCal.get(Calendar.YEAR);
            int curMonth = curCal.get(Calendar.MONTH); // 0 = Jan, 6 = Jul, 11 = Dez

            // Carrega ou inicializa a meta da temporada
            if (metaAtiva == null || !clubName.equals(metaAtiva.nomeClube) || metaAtiva.ano != curYear) {
                metaAtiva = carregarOuGerarMeta(srObj, humanClub, clubName, curYear);
            }

            if (metaAtiva == null) return;

            // Busca a liga nacional estrita (b == 1)
            Object ligaObj = buscarLigaNacionalEstrita(srObj, humanClub);

            // ==========================================
            // FASE 1: Início de Temporada / Boas-vindas
            // ==========================================
            boolean jaTemNoInbox = treinadorJaPossuiMensagem(humanCoach, "Objetivos Oficiais")
                    || treinadorJaPossuiMensagem(humanCoach, "Pacto de Gestão");
            if (!metaAtiva.cartaInicioEnviada || !jaTemNoInbox) {
                enviarCartaInicioTemporada(humanCoach, humanClub, curCal, metaAtiva);
                metaAtiva.cartaInicioEnviada = true;
                salvarMetaEmJson(metaAtiva);
                log("📩 Carta de Expectativas detalhada entregue a " + clubName + " para a temporada " + curYear);
                return; // PAUSA O CICLO: Dá respiro e impede disparo de múltiplas fases no mesmo segundo
            }

            // ==========================================
            // FASE 2: Meio de Temporada (Julho / Fim do 1º Turno do Brasileirão)
            // ==========================================
            if (!metaAtiva.balancoMeioEnviado && metaAtiva.cartaInicioEnviada) {
                int totalTimes = obterTotalTimes(ligaObj, 20);
                int metadeTurno = totalTimes > 0 ? (totalTimes / 2) : 19; // ex: 19 jogos no Brasileirão

                // Se o treinador foi contratado no returno (após a metade do campeonato), é isento do balanço de meio de ano:
                if (metaAtiva.contratadoComTemporadaEmAndamento && metaAtiva.rodadaEntrada >= metadeTurno) {
                    metaAtiva.balancoMeioEnviado = true;
                    metaAtiva.statusMeioAno = "ISENTO_CONTRATADO_NO_RETURNO";
                    salvarMetaEmJson(metaAtiva);
                    return;
                }

                int jogosJogados = obterJogosJogados(ligaObj, humanClub);

                // TRAVAS RIGOROSAS ANTI-DISPARO PRECOCE:
                // 1. NUNCA disparar durante os Campeonatos Estaduais (meses 0 a 4: Janeiro a Maio)!
                // 2. O Brasileirão PRECISA ter completado o primeiro turno (pelo menos metade dos jogos disputados).
                // 3. O mês deve ser a partir de Junho/Julho (curMonth >= 5).
                boolean atingiuMeioAno = (curMonth >= 5) && (jogosJogados >= metadeTurno);

                if (atingiuMeioAno) {
                    int posAtual = calcularPosicaoNaLiga(ligaObj, humanClub, totalTimes);
                    if (posAtual > 0 && posAtual <= totalTimes) {
                        executarBalancoMeioAno(humanCoach, humanClub, curCal, metaAtiva, posAtual, jogosJogados, totalTimes);
                        metaAtiva.balancoMeioEnviado = true;
                        metaAtiva.posicaoMeioAno = posAtual;
                        metaAtiva.rodadaMeioAno = jogosJogados;
                        salvarMetaEmJson(metaAtiva);
                        log("📊 Balanço Semestral do Presidente entregue para " + clubName + " (Posição: " + posAtual + "º após " + jogosJogados + " jogos, Status: " + metaAtiva.statusMeioAno + ")");
                        return; // PAUSA O CICLO
                    }
                }
            }

            // ==========================================
            // FASE 3: Encerramento de Temporada (Dezembro / Liga Finalizada)
            // ==========================================
            if (!metaAtiva.vereditoFinalEnviado) {
                int totalTimes = obterTotalTimes(ligaObj, 20);
                int totalJogosLiga = totalTimes > 0 ? (totalTimes - 1) * 2 : 38;
                int jogosJogados = obterJogosJogados(ligaObj, humanClub);
                int jogosSobComando = Math.max(0, jogosJogados - metaAtiva.rodadaEntrada);
                boolean campeonatoTerminado = verificarLigaTerminada(ligaObj);

                // Se contratado com campeonato já terminado ou sem nenhuma partida comandada na temporada:
                if (metaAtiva.contratadoComTemporadaEmAndamento && (metaAtiva.rodadaEntrada >= totalJogosLiga || campeonatoTerminado || jogosSobComando <= 0)) {
                    metaAtiva.vereditoFinalEnviado = true;
                    metaAtiva.statusFinal = "ISENTO_FIM_TEMPORADA";
                    salvarMetaEmJson(metaAtiva);
                    log("🛡️ Veredito de fim de temporada dispensado para " + clubName + ": Treinador recém-chegado sem jogos sob seu comando.");
                    return;
                }

                // O Veredito Final só pode rodar se a Liga Nacional tiver sido REALMENTE concluída
                // (todos os jogos jogados ou dezembro após a rodada 38)
                boolean horaDoVeredito = (campeonatoTerminado && jogosJogados >= totalJogosLiga)
                        || (curMonth == 11 && curCal.get(Calendar.DAY_OF_MONTH) >= 10 && jogosJogados >= (totalJogosLiga - 2));

                if (horaDoVeredito) {
                    int posFinal = calcularPosicaoNaLiga(ligaObj, humanClub, totalTimes);
                    int ptsFinal = obterPontosNaLiga(ligaObj, humanClub);
                    int nRebaixados = obterRebaixados(ligaObj, 4);

                    if (posFinal > 0 && posFinal <= totalTimes) {
                        executarVereditoFimDeAno(humanCoach, humanClub, curCal, metaAtiva, posFinal, ptsFinal, totalTimes, nRebaixados);
                        metaAtiva.vereditoFinalEnviado = true;
                        metaAtiva.posicaoFinal = posFinal;
                        metaAtiva.pontosFinal = ptsFinal;
                        salvarMetaEmJson(metaAtiva);
                        log("🏆 Veredito Final da Temporada executado para " + clubName + " (Final: " + posFinal + "º, Status: " + metaAtiva.statusFinal + ")");
                    }
                }
            }

        } catch (Throwable t) {
            log("Erro em checkPeriodicGoalsUpdate: " + t.getMessage());
        }
    }

    /**
     * Gera ou carrega a meta da temporada do clube
     */
    private static SeasonGoal carregarOuGerarMeta(Object srObj, Object humanClub, String clubName, int ano) {
        SeasonGoal g = carregarMetaDeJson(clubName, ano);
        if (g != null && g.metasCompeticoes != null && !g.metasCompeticoes.isEmpty()) {
            return g;
        }

        g = new SeasonGoal();
        g.ano = ano;
        g.nomeClube = clubName;

        try {
            g.divisao = (Integer) humanClub.getClass().getMethod("getDivisao").invoke(humanClub);
            g.nivelForca = (Integer) humanClub.getClass().getMethod("getNivel").invoke(humanClub);
            g.reputacao = (Integer) humanClub.getClass().getMethod("getReputacao").invoke(humanClub);
        } catch (Throwable ignored) {}

        // ==========================================
        // 1. ANÁLISE DE PROGRESSÃO HISTÓRICA
        // ==========================================
        SeasonGoal anoAnterior = carregarMetaDeJson(clubName, ano - 1);
        SeasonGoal anoRetrasado = carregarMetaDeJson(clubName, ano - 2);

        boolean bicampeaoPotencial = (anoAnterior != null && anoAnterior.posicaoFinal == 1);
        boolean libertadoresSeguidas = (anoAnterior != null && anoAnterior.posicaoFinal > 0 && anoAnterior.posicaoFinal <= 6)
                && (anoRetrasado != null && anoRetrasado.posicaoFinal > 0 && anoRetrasado.posicaoFinal <= 6);
        boolean subiuDeDivisao = (anoAnterior != null && anoAnterior.divisao > g.divisao);

        // ==========================================
        // 2. OBJETIVO DO BRASILEIRÃO / LIGA NACIONAL
        // ==========================================
        if (g.divisao == 1) { // SÉRIE A
            if (bicampeaoPotencial) {
                g.metaPosBrasileiraoMax = 1;
                g.tituloMetaBrasileirao = "Conquistar o Bicampeonato Brasileiro";
                g.nivelExigencia = "Máxima";
                g.motivoProgressao = "Após o título espetacular no ano anterior, a diretoria exige a manutenção da hegemonia!";
            } else if (libertadoresSeguidas || g.nivelForca >= 19) {
                g.metaPosBrasileiraoMax = 3;
                g.tituloMetaBrasileirao = "Disputar o Título Nacional (G3)";
                g.nivelExigencia = "Máxima";
                if (libertadoresSeguidas) {
                    g.motivoProgressao = "Com 2 classificações consecutivas para a Libertadores, o clube deu o salto de patamar exigido: lutar pelo título!";
                }
            } else if (g.nivelForca >= 17) {
                g.metaPosBrasileiraoMax = 6;
                g.tituloMetaBrasileirao = "Vaga na Copa Libertadores (G6)";
                g.nivelExigencia = "Alta";
            } else if (g.nivelForca == 16) {
                g.metaPosBrasileiraoMax = 12;
                g.tituloMetaBrasileirao = "Vaga na Copa Sul-Americana (Top 12)";
                g.nivelExigencia = "Moderada";
            } else {
                g.metaPosBrasileiraoMax = 16;
                g.tituloMetaBrasileirao = "Permanência na Série A (Fugir do Z4)";
                g.nivelExigencia = subiuDeDivisao ? "Essencial (Recém-Promovido)" : "Essencial";
            }
        } else if (g.divisao == 2) { // SÉRIE B
            if (g.nivelForca >= 14) {
                g.metaPosBrasileiraoMax = 4;
                g.tituloMetaBrasileirao = "Acesso à Série A (Top 4)";
                g.nivelExigencia = "Alta";
            } else {
                g.metaPosBrasileiraoMax = 16;
                g.tituloMetaBrasileirao = "Permanência na Série B";
                g.nivelExigencia = "Moderada";
            }
        } else if (g.divisao == 3) { // SÉRIE C
            g.metaPosBrasileiraoMax = (g.nivelForca >= 11) ? 4 : 16;
            g.tituloMetaBrasileirao = (g.nivelForca >= 11) ? "Acesso à Série B" : "Permanência na Série C";
            g.nivelExigencia = "Alta";
        } else {
            g.metaPosBrasileiraoMax = 4;
            g.tituloMetaBrasileirao = "Acesso de Divisão Nacional";
            g.nivelExigencia = "Alta";
        }

        // ==========================================
        // 3. DETECÇÃO DE TODAS AS COMPETIÇÕES DO CLUBE
        // ==========================================
        g.metasCompeticoes = detectarTodasCompeticoesDoClube(srObj, humanClub, g);

        // ==========================================
        // 4. DETECÇÃO DE CONTRATAÇÃO COM TEMPORADA EM ANDAMENTO
        // ==========================================
        try {
            Object ligaObj = buscarLigaNacionalEstrita(srObj, humanClub);
            int jogosAtuais = obterJogosJogados(ligaObj, humanClub);
            int totalTimes = obterTotalTimes(ligaObj, 20);
            int totalJogosLiga = totalTimes > 0 ? (totalTimes - 1) * 2 : 38;
            int metadeTurno = totalTimes > 0 ? (totalTimes / 2) : 19;

            g.rodadaEntrada = Math.max(0, jogosAtuais);
            g.contratadoComTemporadaEmAndamento = (jogosAtuais > 0);

            // Se contratado após o 1º turno, isenta do balanço de meio de ano anterior
            if (jogosAtuais >= metadeTurno) {
                g.balancoMeioEnviado = true;
                g.statusMeioAno = "ISENTO_CONTRATADO_NO_RETURNO";
            }

            // Se contratado na reta final (faltando 12 jogos ou menos, ou liga terminada):
            if (jogosAtuais >= (totalJogosLiga - 12) || verificarLigaTerminada(ligaObj)) {
                g.vereditoFinalEnviado = true;
                g.statusFinal = "ISENTO_RETA_FINAL";
            }
        } catch (Throwable ignored) {}

        salvarMetaEmJson(g);
        return g;
    }

    /**
     * Mapeia todas as competições que o clube disputa na temporada
     */
    private static List<CompGoal> detectarTodasCompeticoesDoClube(Object srObj, Object humanClub, SeasonGoal g) {
        List<CompGoal> list = new ArrayList<CompGoal>();

        // 1. Liga Nacional
        String nomeLiga = "Brasileirão - Série " + (g.divisao == 1 ? "A" : (g.divisao == 2 ? "B" : (g.divisao == 3 ? "C" : "D")));
        try {
            Object liga = buscarLigaNacionalEstrita(srObj, humanClub);
            if (liga != null) {
                nomeLiga = (String) liga.getClass().getMethod("getNome").invoke(liga);
            }
        } catch (Throwable ignored) {}
        list.add(new CompGoal(nomeLiga, "NACIONAL", g.tituloMetaBrasileirao, g.metaPosBrasileiraoMax));

        // 2. Estadual (se houver)
        try {
            Object est = buscarEstadualDoClube(srObj, humanClub);
            if (est != null) {
                String nomeEst = (String) est.getClass().getMethod("getNome").invoke(est);
                String metaEst;
                if (g.nivelForca >= 18) {
                    metaEst = "Conquistar o Título Estadual";
                } else if (g.nivelForca >= 16) {
                    metaEst = "Alcançar a Final / Semifinal";
                } else {
                    metaEst = "Campanha digna na elite estadual";
                }
                list.add(new CompGoal(nomeEst, "ESTADUAL", metaEst, -1));
            }
        } catch (Throwable ignored) {}

        // 3. Copa do Brasil (Copa Nacional)
        // No Brasfoot, clubes das Séries A e B disputam a Copa do Brasil
        if (g.divisao <= 2 || g.nivelForca >= 13) {
            String metaCopaBr;
            if (g.nivelForca >= 18) {
                metaCopaBr = "Alcançar as Semifinais";
            } else if (g.nivelForca >= 16) {
                metaCopaBr = "Alcançar as Quartas de Final";
            } else {
                metaCopaBr = "Chegar às Oitavas / Campanha sólida";
            }
            list.add(new CompGoal("Copa do Brasil", "COPA_NACIONAL", metaCopaBr, -1));
        }

        // 4. Copas Continentais / Internacionais (Libertadores, Sul-Americana, Recopa, Mundial)
        try {
            List<Object> compsInternacionais = buscarCopasInternacionaisDoClube(srObj, humanClub);
            for (Object comp : compsInternacionais) {
                String nomeComp = (String) comp.getClass().getMethod("getNome").invoke(comp);
                if (nomeComp == null || nomeComp.isEmpty()) continue;

                // Evita duplicar se já foi adicionada
                boolean jaExiste = false;
                for (CompGoal cg : list) {
                    if (cg.nomeCompeticao.equalsIgnoreCase(nomeComp)) {
                        jaExiste = true;
                        break;
                    }
                }
                if (jaExiste) continue;

                String metaInter;
                if (nomeComp.contains("Libertadores") || nomeComp.contains("Campeões")) {
                    metaInter = (g.nivelForca >= 18) ? "Disputar o Título / Semifinal" : "Avançar da Fase de Grupos / Oitavas";
                    list.add(new CompGoal(nomeComp, "CONTINENTAL", metaInter, -1));
                } else if (nomeComp.contains("Sul-Americana") || nomeComp.contains("Europa")) {
                    metaInter = (g.nivelForca >= 17) ? "Chegar à Final / Semifinal" : "Avançar ao Mata-mata";
                    list.add(new CompGoal(nomeComp, "CONTINENTAL", metaInter, -1));
                } else if (nomeComp.contains("Mundial")) {
                    list.add(new CompGoal(nomeComp, "MUNDIAL", "Disputar a Final do Mundial", -1));
                } else if (nomeComp.contains("Recopa") || nomeComp.contains("Supercopa")) {
                    list.add(new CompGoal(nomeComp, "RECOPA_SUPERCOPA", "Disputar o Título em jogo único/ida e volta", -1));
                }
            }
        } catch (Throwable ignored) {}

        return list;
    }

    /**
     * Envia a Carta de Expectativas da Temporada (Fase 1)
     */
    private static void enviarCartaInicioTemporada(Object humanCoach, Object humanClub, Calendar curCal, SeasonGoal g) {
        String dataStr = String.format("%02d/%02d/%d", curCal.get(Calendar.DAY_OF_MONTH), curCal.get(Calendar.MONTH) + 1, curCal.get(Calendar.YEAR));
        String title = g.contratadoComTemporadaEmAndamento
                ? "[Diretoria] Boas-vindas & Pacto de Gestão — " + g.ano
                : "[Diretoria] Objetivos Oficiais da Temporada " + g.ano;

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:sans-serif; margin:2px; padding:0;'>");

        // Cabeçalho Oficial
        sb.append("<table width='370' cellpadding='0' cellspacing='0' style='border-bottom:2px solid #1F2937; margin-bottom:4px;'>");
        sb.append("  <tr>");
        sb.append("    <td><b><font color='#111827' size='2'>CONSELHO DIRETIVO — ").append(escapeHtml(g.nomeClube.toUpperCase())).append("</font></b></td>");
        sb.append("    <td align='right'><font size='1' color='#6B7280'>").append(dataStr).append("</font></td>");
        sb.append("  </tr>");
        sb.append("</table>");

        if (g.contratadoComTemporadaEmAndamento) {
            sb.append("<div style='background-color:#FEF3C7; border-left:3px solid #F59E0B; color:#92400E; padding:3px 5px; font-size:8.5px; margin-bottom:4px;'>");
            sb.append("<b>🤝 Contratação em Andamento:</b> Você assume a equipe na rodada ").append(g.rodadaEntrada + 1).append(". ");
            sb.append("A diretoria concede respaldo integral à sua comissão técnica e garante imunidade para planejamento.");
            sb.append("</div>");
        }

        sb.append("<div style='font-size:9.5px; color:#374151; margin-bottom:4px; line-height:1.2;'>");
        sb.append("Prezado Professor, apresentamos as diretrizes estratégicas da instituição para a temporada <b>").append(g.ano).append("</b>:");
        sb.append("</div>");

        // Notificação de Progressão Histórica (se aplicável)
        if (g.motivoProgressao != null && !g.motivoProgressao.isEmpty()) {
            sb.append("<div style='background-color:#EFF6FF; border-left:3px solid #3B82F6; color:#1E40AF; padding:2px 4px; font-size:8.5px; margin-bottom:4px;'>");
            sb.append("<b>📈 Salto de Patamar:</b> ").append(escapeHtml(g.motivoProgressao));
            sb.append("</div>");
        }

        // Tabela Completa de Todas as Competições
        sb.append("<table width='370' cellpadding='2' cellspacing='0' style='font-size:9px; border:1px solid #D1D5DB; background-color:#F9FAFB; margin-bottom:4px;'>");
        sb.append("  <tr style='background-color:#E5E7EB; color:#111827; font-weight:bold;'>");
        sb.append("    <th width='130' align='left' style='padding:2px;'>COMPETIÇÃO</th>");
        sb.append("    <th width='240' align='left' style='padding:2px;'>OBJETIVO EXIGIDO PELA DIRETORIA</th>");
        sb.append("  </tr>");

        if (g.metasCompeticoes != null) {
            for (CompGoal cg : g.metasCompeticoes) {
                String corMeta = "#111827";
                if ("NACIONAL".equals(cg.tipoCompeticao)) corMeta = "#1E40AF";
                else if ("CONTINENTAL".equals(cg.tipoCompeticao)) corMeta = "#047857";
                else if ("MUNDIAL".equals(cg.tipoCompeticao)) corMeta = "#B45309";

                sb.append("  <tr style='border-top:1px solid #E5E7EB;'>");
                sb.append("    <td style='padding:2px;'><b>").append(escapeHtml(cg.nomeCompeticao)).append("</b></td>");
                sb.append("    <td style='padding:2px;'><font color='").append(corMeta).append("'><b>").append(escapeHtml(cg.metaDescricao)).append("</b></font></td>");
                sb.append("  </tr>");
            }
        }
        sb.append("</table>");

        sb.append("<div style='font-size:8.5px; color:#4B5563; font-style:italic; line-height:1.2; margin-top:3px;'>");
        sb.append("* O Nacional é a prioridade da temporada. O Estadual tem caráter preparatório e não gera demissão.");
        sb.append("</div>");
        sb.append("</body></html>");

        entregarMensagemInbox(humanCoach, title, sb.toString());
    }

    /**
     * Executa a avaliação do meio de temporada (Fase 2)
     */
    private static void executarBalancoMeioAno(Object humanCoach, Object humanClub, Calendar curCal, SeasonGoal g,
                                               int posAtual, int jogosJogados, int totalTimes) {
        String dataStr = String.format("%02d/%02d/%d", curCal.get(Calendar.DAY_OF_MONTH), curCal.get(Calendar.MONTH) + 1, curCal.get(Calendar.YEAR));
        String title = "[Diretoria] Balanço Semestral de Gestão — Temporada " + g.ano;

        String status;
        String badgeColor;
        String parecerTexto;

        int z4Inicio = totalTimes - 3; // ex: 17 no Brasileirão de 20 clubes

        // Tolerância inteligente
        if (posAtual <= g.metaPosBrasileiraoMax) {
            status = "NO_CAMINHO";
            badgeColor = "#059669";
            parecerTexto = "A diretoria manifesta plena satisfação com o rendimento da equipe. O clube cumpre com rigor os objetivos pactuados no início do ano. A torcida mantém total apoio ao seu trabalho.";
            ajustarConfianca(humanCoach, +3, +3);
        } else if (posAtual <= g.metaPosBrasileiraoMax + 3 && posAtual < z4Inicio) {
            status = "ALERTA";
            badgeColor = "#D97706";
            parecerTexto = "A equipe está ligeiramente abaixo da meta traçada (" + posAtual + "º lugar), porém próxima do pelotão desejado. A diretoria cobra maior regularidade no returno para assegurar a meta.";
            ajustarConfianca(humanCoach, -3, -2);
        } else {
            status = "CRITICO";
            badgeColor = "#DC2626";
            parecerTexto = "A diretoria e o conselho manifestam profunda apreensão com a colocação (" + posAtual + "º lugar). A equipe está distante da meta planejada. Resultados expressivos são requeridos no returno.";
            ajustarConfianca(humanCoach, -12, -12);
        }

        g.statusMeioAno = status;

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:sans-serif; margin:2px; padding:0;'>");

        sb.append("<table width='370' cellpadding='0' cellspacing='0' style='border-bottom:2px solid #1F2937; margin-bottom:4px;'>");
        sb.append("  <tr>");
        sb.append("    <td><b><font color='#111827' size='2'>BALANÇO SEMESTRAL DA DIRETORIA</font></b></td>");
        sb.append("    <td align='right'><font size='1' color='#6B7280'>").append(dataStr).append("</font></td>");
        sb.append("  </tr>");
        sb.append("</table>");

        sb.append("<table width='370' cellpadding='3' cellspacing='0' style='font-size:9px; border:1px solid #D1D5DB; background-color:#F9FAFB; margin-bottom:4px;'>");
        sb.append("  <tr>");
        sb.append("    <td><b>Posição na Liga:</b> <font color='#111827' size='2'><b>").append(posAtual).append("º Lugar</b></font> (após ").append(jogosJogados).append(" jogos)</td>");
        sb.append("    <td align='right'><b>Status:</b> <font color='").append(badgeColor).append("'><b>[").append(status.replace("_", " ")).append("]</b></font></td>");
        sb.append("  </tr>");
        sb.append("  <tr style='border-top:1px solid #E5E7EB;'>");
        sb.append("    <td colspan='2'><b>Meta Estipulada:</b> ").append(escapeHtml(g.tituloMetaBrasileirao)).append(" (Máx: ").append(g.metaPosBrasileiraoMax).append("º)</td>");
        sb.append("  </tr>");
        sb.append("</table>");

        sb.append("<div style='font-size:9px; color:#374151; line-height:1.25; margin-bottom:4px;'>");
        sb.append("<b>Parecer do Presidente:</b><br>");
        sb.append(parecerTexto);
        sb.append("</div>");

        sb.append("<div style='font-size:8px; color:#6B7280; font-style:italic;'>");
        sb.append("Este relatório semestral baliza a evolução do índice de aprovação da diretoria e da torcida.");
        sb.append("</div>");
        sb.append("</body></html>");

        entregarMensagemInbox(humanCoach, title, sb.toString());
    }

    /**
     * Executa o Veredito Final de encerramento da temporada (Fase 3) com tolerância matizada
     */
    private static void executarVereditoFimDeAno(Object humanCoach, Object humanClub, Calendar curCal, SeasonGoal g,
                                                 int posFinal, int ptsFinal, int totalTimes, int nRebaixados) {
        String dataStr = String.format("%02d/%02d/%d", curCal.get(Calendar.DAY_OF_MONTH), curCal.get(Calendar.MONTH) + 1, curCal.get(Calendar.YEAR));
        String title = "[Diretoria] Veredito Anual do Conselho — Fim da Temporada " + g.ano;

        int z4Inicio = totalTimes - nRebaixados + 1;
        boolean foiRebaixado = posFinal >= z4Inicio;
        boolean foiCampeao = (posFinal == 1);

        String statusFinal;
        String badgeColor;
        String parecerFinal;
        boolean aplicarDemissao = false;

        // ==========================================
        // TOLERÂNCIA REALISTA PONDERADA
        // ==========================================
        if (foiCampeao || (posFinal <= 3 && g.metaPosBrasileiraoMax >= 6) || (posFinal <= 6 && g.metaPosBrasileiraoMax >= 12)) {
            // Superou as expectativas com louvor!
            statusFinal = "SUCESSO_LOUVOR";
            badgeColor = "#059669";
            parecerFinal = "Campanha memorável! A equipe superou todas as expectativas da diretoria e da torcida. Seu trabalho consolidou um patamar vitorioso. Prestígio máximo e renovação garantida!";
            ajustarReputacao(humanCoach, +3);
            definirConfiancaAbsoluta(humanCoach, 100, 100);

        } else if (posFinal <= g.metaPosBrasileiraoMax && !foiRebaixado) {
            // Cumpriu a meta à risca
            statusFinal = "CUMPRIU";
            badgeColor = "#059669";
            parecerFinal = "A diretoria congratula a comissão técnica pelo dever cumprido. As metas fundamentais da temporada foram atingidas com competência, assegurando solidez esportiva ao clube.";
            ajustarReputacao(humanCoach, +1);
            definirConfiancaAbsoluta(humanCoach, 90, 85);

        } else if (!foiRebaixado && (posFinal <= g.metaPosBrasileiraoMax + 3 || (g.metaPosBrasileiraoMax == 3 && posFinal <= 5))) {
            // Quase lá / Tolerância próxima (Ex: Meta era Título e terminou em 2º a 4º; ou meta G6 e terminou em 7º/8º)
            // NESTE CASO, NÃO HÁ DEMISSÃO! O treinador permanece respaldado.
            statusFinal = "CAMPANHA_SOLIDA";
            badgeColor = "#2563EB";
            parecerFinal = "A equipe lutou até as rodadas finais e encerrou a temporada muito próxima do objetivo máximo (" + posFinal + "º lugar). A diretoria reconhece a competitividade demonstrada e garante a continuidade do seu comando para o próximo ano.";
            definirConfiancaAbsoluta(humanCoach, 75, 70);

        } else if (!foiRebaixado && posFinal <= g.metaPosBrasileiraoMax + 6 && g.divisao == 1) {
            // Fracasso moderado sem rebaixamento (Ex: Meta era título e terminou em 7º; ou meta G6 e terminou em 11º)
            // Recebe advertência forte, mas permanece sob pressão sem demissão imediata.
            statusFinal = "FRACASSO_MODERADO";
            badgeColor = "#D97706";
            parecerFinal = "A temporada encerra-se com saldo insatisfatório. A equipe ficou consideravelmente abaixo das aspirações da torcida. A diretoria concederá mais um voto de confiança, mas exigirá resposta imediata nas primeiras rodadas do próximo ano.";
            ajustarReputacao(humanCoach, -1);
            definirConfiancaAbsoluta(humanCoach, 45, 40);

        } else {
            // Fracasso Crítico: Rebaixamento OU Desempenho Vexatório (Ex: Meta Título e terminou em 12º+, ou rebaixado na Série A)
            statusFinal = "FRACASSO_CRITICO";
            badgeColor = "#DC2626";
            parecerFinal = "A diretoria e a presidência consideram o desfecho da temporada inaceitável. O resultado de " + posFinal + "º lugar destoa totalmente dos investimentos e da grandeza do clube. O conselho deliberativo decreta a rescisão contratual imediata.";
            ajustarReputacao(humanCoach, -3);
            definirConfiancaAbsoluta(humanCoach, 5, 5); // Cai para < 10% (gatilho de demissão)
            aplicarDemissao = true;
        }

        // ==========================================
        // IMUNIDADE PARA CONTRATAÇÕES EM ANDAMENTO
        // ==========================================
        int totalJogosLiga = totalTimes > 0 ? (totalTimes - 1) * 2 : 38;
        int jogosSobComando = Math.max(0, totalJogosLiga - g.rodadaEntrada);
        boolean contratadoNaRetaFinal = g.contratadoComTemporadaEmAndamento && (g.rodadaEntrada >= (totalJogosLiga - 12));
        boolean comandouPoucosJogos = g.contratadoComTemporadaEmAndamento && (jogosSobComando < 15);
        boolean contratacaoRecenteSemJogos = (jogosSobComando <= 0);

        if (aplicarDemissao && (contratadoNaRetaFinal || comandouPoucosJogos || contratacaoRecenteSemJogos)) {
            aplicarDemissao = false;
            statusFinal = "ISENTO_TRANSICAO";
            badgeColor = "#4B5563";
            parecerFinal = "Apesar do desfecho amargo da temporada (" + posFinal + "º lugar), o Conselho Deliberativo reconhece expressamente que você assumiu a equipe na rodada " + (g.rodadaEntrada + 1) + " com a competição em andamento (apenas " + Math.max(0, jogosSobComando) + " jogos sob seu comando). A diretoria não imputa os resultados herdados ao seu trabalho e confirma respaldo integral para você conduzir a pré-temporada e a reconstrução do clube no próximo ano.";
            definirConfiancaAbsoluta(humanCoach, 85, 75);
        }

        g.statusFinal = statusFinal;

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:sans-serif; margin:2px; padding:0;'>");

        sb.append("<table width='370' cellpadding='0' cellspacing='0' style='border-bottom:2px solid #1F2937; margin-bottom:4px;'>");
        sb.append("  <tr>");
        sb.append("    <td><b><font color='#111827' size='2'>VEREDITO ANUAL — ").append(escapeHtml(g.nomeClube.toUpperCase())).append("</font></b></td>");
        sb.append("    <td align='right'><font size='1' color='#6B7280'>").append(dataStr).append("</font></td>");
        sb.append("  </tr>");
        sb.append("</table>");

        sb.append("<table width='370' cellpadding='3' cellspacing='0' style='font-size:9px; border:1px solid #D1D5DB; background-color:#F9FAFB; margin-bottom:4px;'>");
        sb.append("  <tr>");
        sb.append("    <td><b>Classificação Final:</b> <font color='#111827' size='2'><b>").append(posFinal).append("º Lugar</b></font> (").append(ptsFinal).append(" pts)</td>");
        sb.append("    <td align='right'><b>Resultado:</b> <font color='").append(badgeColor).append("'><b>[").append(statusFinal.replace("_", " ")).append("]</b></font></td>");
        sb.append("  </tr>");
        sb.append("  <tr style='border-top:1px solid #E5E7EB;'>");
        sb.append("    <td colspan='2'><b>Meta Estipulada:</b> ").append(escapeHtml(g.tituloMetaBrasileirao)).append(" (Máx: ").append(g.metaPosBrasileiraoMax).append("º)</td>");
        sb.append("  </tr>");
        sb.append("</table>");

        sb.append("<div style='font-size:9px; color:#374151; line-height:1.25; margin-bottom:4px;'>");
        sb.append("<b>Resolução do Conselho Deliberativo:</b><br>");
        sb.append(parecerFinal);
        sb.append("</div>");

        if (aplicarDemissao) {
            sb.append("<div style='background-color:#FEE2E2; color:#B91C1C; padding:3px; font-size:8.5px; font-weight:bold; border:1px solid #FCA5A5;'>");
            sb.append("⚠️ DECISÃO DA DIRETORIA: Rescisão contratual sumária efetuada.");
            sb.append("</div>");
        } else {
            sb.append("<div style='background-color:#ECFDF5; color:#047857; padding:3px; font-size:8.5px; font-weight:bold; border:1px solid #A7F3D0;'>");
            sb.append("✅ DECISÃO DA DIRETORIA: Permanência confirmada para a temporada seguinte.");
            sb.append("</div>");
        }
        sb.append("</body></html>");

        entregarMensagemInbox(humanCoach, title, sb.toString());

        if (aplicarDemissao) {
            SwingUtilities.invokeLater(() -> {
                try {
                    dispararDemissaoNativa(humanCoach, humanClub);
                } catch (Throwable t) {
                    log("Erro ao disparar demissão nativa: " + t.getMessage());
                }
            });
        }
    }

    /**
     * Localiza a Liga Nacional estrita do clube (f.s com b == 1)
     */
    public static Object buscarLigaNacionalEstrita(Object srObj, Object humanClub) {
        try {
            Method rMethod = srObj.getClass().getMethod("R");
            List<?> rodadas = (List<?>) rMethod.invoke(srObj);
            if (rodadas != null) {
                for (Object rod : rodadas) {
                    Method jMethod = rod.getClass().getMethod("j");
                    List<?> comps = (List<?>) jMethod.invoke(rod);
                    if (comps != null) {
                        for (Object comp : comps) {
                            if (comp.getClass().getName().equals("f.s")) {
                                Method bM = comp.getClass().getMethod("b");
                                int tipo = ((Number) bM.invoke(comp)).intValue();
                                if (tipo == 1) { // 1 = LIGA NACIONAL
                                    Method yK = comp.getClass().getMethod("yK");
                                    List<?> times = (List<?>) yK.invoke(comp);
                                    if (times != null && times.contains(humanClub)) {
                                        return comp;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Localiza o Estadual do clube (f.s com b == 3)
     */
    private static Object buscarEstadualDoClube(Object srObj, Object humanClub) {
        try {
            Method rMethod = srObj.getClass().getMethod("R");
            List<?> rodadas = (List<?>) rMethod.invoke(srObj);
            if (rodadas != null) {
                for (Object rod : rodadas) {
                    Method jMethod = rod.getClass().getMethod("j");
                    List<?> comps = (List<?>) jMethod.invoke(rod);
                    if (comps != null) {
                        for (Object comp : comps) {
                            if (comp.getClass().getName().equals("f.s")) {
                                Method bM = comp.getClass().getMethod("b");
                                int tipo = ((Number) bM.invoke(comp)).intValue();
                                if (tipo == 3) { // 3 = ESTADUAL
                                    Method yK = comp.getClass().getMethod("yK");
                                    List<?> times = (List<?>) yK.invoke(comp);
                                    if (times != null && times.contains(humanClub)) {
                                        return comp;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Localiza todas as Copas Internacionais / Mata-mata que o clube disputa
     */
    private static List<Object> buscarCopasInternacionaisDoClube(Object srObj, Object humanClub) {
        List<Object> comps = new ArrayList<Object>();
        Set<String> nomesAdicionados = new HashSet<String>();

        // 1. Checa array bB() em best.f
        try {
            Method bBM = srObj.getClass().getMethod("bB");
            Object[] arr = (Object[]) bBM.invoke(srObj);
            if (arr != null) {
                for (Object c : arr) {
                    if (c == null) continue;
                    boolean participa = false;
                    try {
                        Method uM = c.getClass().getMethod("U", humanClub.getClass());
                        participa = Boolean.TRUE.equals(uM.invoke(c, humanClub));
                    } catch (Throwable ignored) {}

                    if (!participa) {
                        try {
                            Method yK = c.getClass().getMethod("yK");
                            List<?> times = (List<?>) yK.invoke(c);
                            if (times != null && times.contains(humanClub)) participa = true;
                        } catch (Throwable ignored) {}
                    }

                    if (participa) {
                        String n = (String) c.getClass().getMethod("getNome").invoke(c);
                        if (!nomesAdicionados.contains(n)) {
                            comps.add(c);
                            nomesAdicionados.add(n);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Checa rodadas do calendário para torneios tipo f.z
        try {
            Method rMethod = srObj.getClass().getMethod("R");
            List<?> rodadas = (List<?>) rMethod.invoke(srObj);
            if (rodadas != null) {
                for (Object rod : rodadas) {
                    Method jMethod = rod.getClass().getMethod("j");
                    List<?> jList = (List<?>) jMethod.invoke(rod);
                    if (jList != null) {
                        for (Object c : jList) {
                            if (c.getClass().getName().equals("f.z")) {
                                boolean participa = false;
                                try {
                                    Method uM = c.getClass().getMethod("U", humanClub.getClass());
                                    participa = Boolean.TRUE.equals(uM.invoke(c, humanClub));
                                } catch (Throwable ignored) {}

                                if (participa) {
                                    String n = (String) c.getClass().getMethod("getNome").invoke(c);
                                    if (!nomesAdicionados.contains(n)) {
                                        comps.add(c);
                                        nomesAdicionados.add(n);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return comps;
    }

    /**
     * Obtém o array oficial de estatísticas do clube na liga/competição via clube.d(compObj)
     * Retorna int[8]:
     * [0] = Pontos (nT)
     * [1] = Jogos disputados (T)
     * [2] = Vitórias (bX)
     * [3] = Derrotas (T - (bX + d))
     * [4] = Empates (d)
     * [5] = Gols Pró (nK)
     * [6] = Gols Contra (nL)
     * [7] = Saldo de Gols (nK - nL)
     */
    public static int[] obterStatsOficiaisDoClube(Object humanClub, Object ligaObj) {
        if (humanClub == null || ligaObj == null) return new int[8];
        try {
            for (Method m : humanClub.getClass().getMethods()) {
                if (m.getName().equals("d") && m.getParameterTypes().length == 1 && m.getReturnType().equals(int[].class)) {
                    Object res = m.invoke(humanClub, ligaObj);
                    if (res instanceof int[]) {
                        return (int[]) res;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return new int[8];
    }

    /**
     * Calcula a colocação atual do clube na liga com fallback inteligente
     */
    public static int calcularPosicaoNaLiga(Object ligaObj, Object humanClub) {
        return calcularPosicaoNaLiga(ligaObj, humanClub, 20);
    }

    public static int calcularPosicaoNaLiga(Object ligaObj, Object humanClub, int totalTimes) {
        if (ligaObj == null || humanClub == null) return -1;

        // Se a competição ainda não teve jogos disputados pelo clube, não há colocação válida
        int jogos = obterJogosJogados(ligaObj, humanClub);
        if (jogos == 0) return -1;

        try {
            Method pMethod = ligaObj.getClass().getMethod("P", humanClub.getClass());
            int p = ((Number) pMethod.invoke(ligaObj, humanClub)).intValue();
            // P(clube) no Brasfoot já é 1-indexado (retorna 1 a totalTimes)
            if (p > 0 && p <= totalTimes) return p;
        } catch (Throwable ignored) {}

        try {
            Field zbField = ligaObj.getClass().getDeclaredField("Zb");
            zbField.setAccessible(true);
            List<?> zb = (List<?>) zbField.get(ligaObj);
            if (zb != null) {
                int idx = zb.indexOf(humanClub);
                if (idx >= 0 && (idx + 1) <= totalTimes) return idx + 1;
            }
        } catch (Throwable ignored) {}

        return -1;
    }

    private static int obterJogosJogados(Object ligaObj, Object humanClub) {
        if (ligaObj == null || humanClub == null) return 0;
        try {
            int[] stats = obterStatsOficiaisDoClube(humanClub, ligaObj);
            if (stats != null && stats.length >= 2) {
                return stats[1]; // stats[1] é T (jogos jogados)
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static int obterPontosNaLiga(Object ligaObj, Object humanClub) {
        if (ligaObj == null || humanClub == null) return 0;
        try {
            int[] stats = obterStatsOficiaisDoClube(humanClub, ligaObj);
            if (stats != null && stats.length >= 1) {
                return stats[0]; // stats[0] é nT (pontos ganhos)
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static int obterTotalTimes(Object ligaObj, int def) {
        if (ligaObj == null) return def;
        try {
            Method nTimesMethod = ligaObj.getClass().getMethod("getnTimes");
            return ((Number) nTimesMethod.invoke(ligaObj)).intValue();
        } catch (Throwable ignored) {}
        return def;
    }

    private static int obterRebaixados(Object ligaObj, int def) {
        if (ligaObj == null) return def;
        try {
            Method nRebMethod = ligaObj.getClass().getMethod("getnRebaixados");
            return ((Number) nRebMethod.invoke(ligaObj)).intValue();
        } catch (Throwable ignored) {}
        return def;
    }

    private static boolean verificarLigaTerminada(Object ligaObj) {
        if (ligaObj == null) return false;
        try {
            Method yZMethod = ligaObj.getClass().getMethod("yZ");
            return Boolean.TRUE.equals(yZMethod.invoke(ligaObj));
        } catch (Throwable ignored) {}
        return false;
    }

    private static void dispararDemissaoNativa(Object humanCoach, Object humanClub) {
        try {
            ClassLoader cl = BrasfootAgent.getGameClassLoader();
            Class<?> sClass = Class.forName("best.S", true, cl);
            Class<?> alClass = Class.forName("best.al", true, cl);
            Class<?> ahClass = Class.forName("best.ah", true, cl);

            Method keMethod = humanClub.getClass().getMethod("kE");
            Object coachInterino = keMethod.invoke(humanClub);

            Method demitirMethod = sClass.getMethod("a", alClass, alClass, ahClass, int.class);
            demitirMethod.invoke(null, humanCoach, coachInterino, humanClub, 3);

            Method hMethod = humanClub.getClass().getMethod("h", alClass);
            hMethod.invoke(humanClub, coachInterino);

            Method nMethod = humanCoach.getClass().getMethod("n", ahClass);
            nMethod.invoke(humanCoach, new Object[]{null});

            log("🚨 Demissão nativa do treinador executada com sucesso por descumprimento de metas!");
        } catch (Throwable t) {
            log("Falha ao invocar best.S.a para demissão: " + t.getMessage());
        }
    }

    private static void ajustarConfianca(Object humanCoach, int deltaDir, int deltaTorc) {
        try {
            Method cjMethod = humanCoach.getClass().getMethod("cj", int.class);
            cjMethod.invoke(humanCoach, deltaDir);

            Method ckMethod = humanCoach.getClass().getMethod("ck", int.class);
            ckMethod.invoke(humanCoach, deltaTorc);
        } catch (Throwable ignored) {}
    }

    private static void definirConfiancaAbsoluta(Object humanCoach, int valDir, int valTorc) {
        try {
            Method clMethod = humanCoach.getClass().getMethod("cl", int.class);
            clMethod.invoke(humanCoach, valDir);

            Method cmMethod = humanCoach.getClass().getMethod("cm", int.class);
            cmMethod.invoke(humanCoach, valTorc);
        } catch (Throwable ignored) {}
    }

    private static void ajustarReputacao(Object humanCoach, int delta) {
        try {
            Method getRepMethod = humanCoach.getClass().getMethod("getReputacao");
            int rep = ((Number) getRepMethod.invoke(humanCoach)).intValue();
            int novaRep = Math.max(1, Math.min(10, rep + delta));

            Method setRepMethod = humanCoach.getClass().getMethod("setReputacao", int.class);
            setRepMethod.invoke(humanCoach, novaRep);
        } catch (Throwable ignored) {}
    }

    private static Object getHumanCoach(Object srObj) {
        try {
            Method lMethod = srObj.getClass().getMethod("L");
            List<?> coaches = (List<?>) lMethod.invoke(srObj);
            if (coaches != null) {
                for (Object coach : coaches) {
                    Method isHuman = coach.getClass().getMethod("jZ");
                    if (Boolean.TRUE.equals(isHuman.invoke(coach))) {
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

    private static boolean entregarMensagemInbox(Object humanCoach, String title, String htmlBody) {
        try {
            ClassLoader cl = BrasfootAgent.getGameClassLoader();
            Class<?> asClass = Class.forName("components.as", true, cl);
            Class<?> alClass = Class.forName("best.al", true, cl);

            Constructor<?> cons = asClass.getConstructor(alClass, int.class, int.class, String.class, String.class);
            Object msgObj = cons.newInstance(humanCoach, 0, -1, title, htmlBody);
            return msgObj != null;
        } catch (Throwable t) {
            log("Erro ao instanciar mensagem components.as: " + t.getMessage());
        }
        return false;
    }

    public static boolean treinadorJaPossuiMensagem(Object humanCoach, String termo) {
        if (humanCoach == null || termo == null) return false;
        try {
            Method lqMethod = humanCoach.getClass().getMethod("lQ");
            List<?> msgs = (List<?>) lqMethod.invoke(humanCoach);
            if (msgs != null) {
                for (Object m : msgs) {
                    if (m == null) continue;
                    Method vpMethod = m.getClass().getMethod("vp");
                    String title = (String) vpMethod.invoke(m);
                    if (title != null && title.contains(termo)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ==========================================
    // PERSISTÊNCIA EM JSON ISOLADO
    // ==========================================

    private static File getArquivoJsonMeta(String clubName, int ano) {
        String cleanClub = clubName.replaceAll("[^a-zA-Z0-9_]", "_");
        File dir = new File(SAV_DIR);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "metas_" + cleanClub + "_" + ano + ".json");
    }

    private static void salvarMetaEmJson(SeasonGoal g) {
        try {
            File f = getArquivoJsonMeta(g.nomeClube, g.ano);
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"ano\": ").append(g.ano).append(",\n");
            sb.append("  \"nomeClube\": \"").append(g.nomeClube).append("\",\n");
            sb.append("  \"divisao\": ").append(g.divisao).append(",\n");
            sb.append("  \"nivelForca\": ").append(g.nivelForca).append(",\n");
            sb.append("  \"metaPosBrasileiraoMax\": ").append(g.metaPosBrasileiraoMax).append(",\n");
            sb.append("  \"tituloMetaBrasileirao\": \"").append(g.tituloMetaBrasileirao).append("\",\n");
            sb.append("  \"nivelExigencia\": \"").append(g.nivelExigencia).append("\",\n");
            sb.append("  \"motivoProgressao\": \"").append(g.motivoProgressao != null ? g.motivoProgressao : "").append("\",\n");
            sb.append("  \"cartaInicioEnviada\": ").append(g.cartaInicioEnviada).append(",\n");
            sb.append("  \"balancoMeioEnviado\": ").append(g.balancoMeioEnviado).append(",\n");
            sb.append("  \"vereditoFinalEnviado\": ").append(g.vereditoFinalEnviado).append(",\n");
            sb.append("  \"posicaoMeioAno\": ").append(g.posicaoMeioAno).append(",\n");
            sb.append("  \"statusMeioAno\": \"").append(g.statusMeioAno).append("\",\n");
            sb.append("  \"posicaoFinal\": ").append(g.posicaoFinal).append(",\n");
            sb.append("  \"pontosFinal\": ").append(g.pontosFinal).append(",\n");
            sb.append("  \"statusFinal\": \"").append(g.statusFinal).append("\",\n");
            sb.append("  \"rodadaEntrada\": ").append(g.rodadaEntrada).append(",\n");
            sb.append("  \"contratadoComTemporadaEmAndamento\": ").append(g.contratadoComTemporadaEmAndamento).append(",\n");

            // Salva array de competições
            sb.append("  \"competicoes\": [\n");
            if (g.metasCompeticoes != null) {
                for (int i = 0; i < g.metasCompeticoes.size(); i++) {
                    CompGoal cg = g.metasCompeticoes.get(i);
                    sb.append("    {\"nome\": \"").append(cg.nomeCompeticao).append("\", ");
                    sb.append("\"tipo\": \"").append(cg.tipoCompeticao).append("\", ");
                    sb.append("\"meta\": \"").append(cg.metaDescricao).append("\", ");
                    sb.append("\"posMax\": ").append(cg.metaPosicaoMax).append("}");
                    if (i < g.metasCompeticoes.size() - 1) sb.append(",");
                    sb.append("\n");
                }
            }
            sb.append("  ]\n");
            sb.append("}\n");

            FileWriter fw = new FileWriter(f);
            fw.write(sb.toString());
            fw.close();
        } catch (Throwable t) {
            log("Erro ao salvar meta em JSON: " + t.getMessage());
        }
    }

    private static SeasonGoal carregarMetaDeJson(String clubName, int ano) {
        try {
            File f = getArquivoJsonMeta(clubName, ano);
            if (!f.exists()) return null;

            BufferedReader br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();

            String json = sb.toString();
            SeasonGoal g = new SeasonGoal();
            g.ano = extrairIntJson(json, "ano", ano);
            g.nomeClube = extrairStringJson(json, "nomeClube", clubName);
            g.divisao = extrairIntJson(json, "divisao", 1);
            g.nivelForca = extrairIntJson(json, "nivelForca", 16);
            g.metaPosBrasileiraoMax = extrairIntJson(json, "metaPosBrasileiraoMax", 16);
            g.tituloMetaBrasileirao = extrairStringJson(json, "tituloMetaBrasileirao", "Permanência");
            g.nivelExigencia = extrairStringJson(json, "nivelExigencia", "Alta");
            g.motivoProgressao = extrairStringJson(json, "motivoProgressao", "");
            g.cartaInicioEnviada = extrairBoolJson(json, "cartaInicioEnviada", false);
            g.balancoMeioEnviado = extrairBoolJson(json, "balancoMeioEnviado", false);
            g.vereditoFinalEnviado = extrairBoolJson(json, "vereditoFinalEnviado", false);
            g.posicaoMeioAno = extrairIntJson(json, "posicaoMeioAno", -1);
            g.statusMeioAno = extrairStringJson(json, "statusMeioAno", "PENDENTE");
            g.posicaoFinal = extrairIntJson(json, "posicaoFinal", -1);
            g.pontosFinal = extrairIntJson(json, "pontosFinal", 0);
            g.statusFinal = extrairStringJson(json, "statusFinal", "PENDENTE");
            g.rodadaEntrada = extrairIntJson(json, "rodadaEntrada", 0);
            g.contratadoComTemporadaEmAndamento = extrairBoolJson(json, "contratadoComTemporadaEmAndamento", false);

            // Extrai competições do JSON se presente
            try {
                int cIdx = json.indexOf("\"competicoes\":");
                if (cIdx >= 0) {
                    g.metasCompeticoes = new ArrayList<CompGoal>();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{\"nome\":\\s*\"(.*?)\",\\s*\"tipo\":\\s*\"(.*?)\",\\s*\"meta\":\\s*\"(.*?)\",\\s*\"posMax\":\\s*(-?\\d+)\\}").matcher(json.substring(cIdx));
                    while (m.find()) {
                        g.metasCompeticoes.add(new CompGoal(m.group(1), m.group(2), m.group(3), Integer.parseInt(m.group(4))));
                    }
                }
            } catch (Throwable ignored) {}

            return g;
        } catch (Throwable t) {
            log("Erro ao carregar meta de JSON: " + t.getMessage());
        }
        return null;
    }

    private static String extrairStringJson(String json, String key, String def) {
        try {
            String pattern = "\"" + key + "\":\\s*\"(.*?)\"";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
            if (m.find()) return m.group(1);
        } catch (Throwable ignored) {}
        return def;
    }

    private static int extrairIntJson(String json, String key, int def) {
        try {
            String pattern = "\"" + key + "\":\\s*(-?\\d+)";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Throwable ignored) {}
        return def;
    }

    private static boolean extrairBoolJson(String json, String key, boolean def) {
        try {
            String pattern = "\"" + key + "\":\\s*(true|false)";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
            if (m.find()) return Boolean.parseBoolean(m.group(1));
        } catch (Throwable ignored) {}
        return def;
    }

    public static void main(String[] args) {
        System.out.println("[SeasonGoalsController v2] ✅ Módulo de Objetivos de Temporada atualizado com sucesso!");
        System.out.println("[SeasonGoalsController v2] Status ativo: " + isFeatureActive());
    }
}
