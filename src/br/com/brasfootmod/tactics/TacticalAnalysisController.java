package br.com.brasfootmod.tactics;

import br.com.brasfootmod.BrasfootAgent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Controlador de Análise Tática e Sugestão de Formações da Comissão Técnica.
 *
 * Funcionalidade:
 * - Analisa o elenco do clube humano (força média, virtudes táticas e distribuição posicional).
 * - Sugere duas formações táticas completas:
 *   - Estilo de jogo: Equilibrado, Ataque total ou Contra-ataque
 *   - Marcação: Leve, Pesada ou Muito pesada
 *   - Concentrar ataques: Pelo meio ou Pelas laterais
 * - Disparo restrito e inteligente: enviado APENAS DUAS VEZES POR ANO via Caixa de Entrada:
 *   1) Início da Temporada (Janeiro): Pré-temporada e planejamento anual.
 *   2) Meio da Temporada (Julho): Intertemporada, ajustes pós-janela e mata-matas.
 * - Layout em 2 colunas lado a lado otimizado para a altura exata do visualizador nativo (198px).
 */
public class TacticalAnalysisController {

    private static final Set<String> relatoriosEnviadosChaves = new HashSet<>();
    private static Calendar ultimaDataVerificacao = null;

    private static final String[] POSICOES_NOMES = {
        "Goleiro", "Lateral", "Zagueiro", "Meio-Campo", "Atacante"
    };

    private static void log(String msg) {
        System.out.println("[MOD TÁTICA] " + msg);
    }

    /**
     * Verificação periódica chamada pelo timer do agente
     */
    public static void checkPeriodicTacticalReport() {
        try {
            Object srObj = br.com.brasfootmod.vacation.BrasfootVacationController.getSRObject();
            if (srObj == null) return;

            Method bbMethod = srObj.getClass().getMethod("bb");
            Calendar curCal = (Calendar) bbMethod.invoke(srObj);
            if (curCal == null) return;

            // Executa verificação apenas quando a data do jogo mudar
            if (ultimaDataVerificacao != null &&
                curCal.get(Calendar.YEAR) == ultimaDataVerificacao.get(Calendar.YEAR) &&
                curCal.get(Calendar.DAY_OF_YEAR) == ultimaDataVerificacao.get(Calendar.DAY_OF_YEAR)) {
                return;
            }
            ultimaDataVerificacao = (Calendar) curCal.clone();

            int year = curCal.get(Calendar.YEAR);
            int month = curCal.get(Calendar.MONTH); // 0 = Jan, 6 = Jul
            int day = curCal.get(Calendar.DAY_OF_MONTH);

            // Janela 1: Início de Temporada (Janeiro, dias 10 a 25)
            // Janela 2: Meio de Temporada (Julho, dias 1 a 15)
            String semestreChave = null;
            if (month == Calendar.JANUARY && day >= 10 && day <= 25) {
                semestreChave = year + "_JAN";
            } else if (month == Calendar.JULY && day >= 1 && day <= 15) {
                semestreChave = year + "_JUL";
            }

            if (semestreChave == null) return;
            if (relatoriosEnviadosChaves.contains(semestreChave)) return;

            Object humanCoach = getHumanCoach(srObj);
            if (humanCoach == null) return;

            Object humanClub = getHumanClub(humanCoach);
            if (humanClub == null) return;

            String title = "[Tática] Sugestão de Formação";

            // Idempotência na Caixa de Entrada nativa (lQ)
            try {
                Method lQMethod = humanCoach.getClass().getMethod("lQ");
                List<?> msgs = (List<?>) lQMethod.invoke(humanCoach);
                if (msgs != null) {
                    for (Object m : msgs) {
                        Method vpMethod = m.getClass().getMethod("vp");
                        String t = (String) vpMethod.invoke(m);
                        if (title.equals(t) || (t != null && (t.contains("Sugestão de Formação") || t.contains("Relatório Tático")))) {
                            Method vqMethod = m.getClass().getMethod("vq");
                            String body = (String) vqMethod.invoke(m);
                            String periodoIdentificador = (month == Calendar.JANUARY) ? "Planejamento Inicial" : "Intertemporada";
                            if (body != null && body.contains(periodoIdentificador)) {
                                relatoriosEnviadosChaves.add(semestreChave);
                                return;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            Method kcMethod = humanClub.getClass().getMethod("kc");
            List<?> squad = (List<?>) kcMethod.invoke(humanClub);
            if (squad == null || squad.isEmpty()) return;

            String clubName = (String) humanClub.getClass().getMethod("getNome").invoke(humanClub);

            // Realiza a análise tática avançada do elenco
            TacticalPlan plan = analisarElencoParaTatica(humanClub, squad, month == Calendar.JANUARY);

            // Gera o corpo HTML com padrão visual lado a lado sem cortes
            String html = gerarHtmlRelatorioTatico(clubName, curCal, plan);

            log("📋 Gerando parecer tático semestral para " + clubName + " (" + semestreChave + ")");
            boolean entregue = entregarMensagemInbox(humanCoach, title, html);

            if (entregue) {
                relatoriosEnviadosChaves.add(semestreChave);
                log("📩 Relatório tático da comissão técnica entregue na Caixa de Entrada!");
            }
        } catch (Throwable t) {
            log("Erro ao processar relatório tático: " + t.getMessage());
        }
    }

    private static class TacticalOption {
        String esquema = "4-4-2";
        String estiloJogo = "Equilibrado";
        String marcacao = "Pesada";
        String porOndeJogar = "Pelo meio";
        String justificativa = "";
        String aplicacao = "";
        List<String> destaquesChave = new ArrayList<>();
    }

    private static class TacticalPlan {
        boolean isInicioTemporada = true;
        int totalJogadores = 0;
        double forcaMediaGeral = 0.0;
        double idadeMediaGeral = 0.0;
        int[] contagemPorPos = new int[5];
        double[] forcaMediaPorPos = new double[5];
        String setorMaisForte = "";
        TacticalOption principal = new TacticalOption();
        TacticalOption alternativa = new TacticalOption();
    }

    private static TacticalPlan analisarElencoParaTatica(Object humanClub, List<?> squad, boolean isInicioTemporada) {
        TacticalPlan plan = new TacticalPlan();
        plan.isInicioTemporada = isInicioTemporada;
        plan.totalJogadores = squad.size();

        int somaForcaGeral = 0;
        int somaIdadeGeral = 0;
        int[] somaForcaPos = new int[5];

        // Mapeamento de características táticas do elenco
        int criacaoArmPas = 0;
        int velocidadeDriVel = 0;
        int presencaAreaFinCab = 0;
        int desarmeMarcacao = 0;

        List<Object> goleiros = new ArrayList<>();
        List<Object> laterais = new ArrayList<>();
        List<Object> zagueiros = new ArrayList<>();
        List<Object> meias = new ArrayList<>();
        List<Object> atacantes = new ArrayList<>();

        for (Object p : squad) {
            try {
                int pos = (Integer) p.getClass().getMethod("getPosicao").invoke(p);
                int forca = (Integer) p.getClass().getMethod("fi").invoke(p);
                int idade = (Integer) p.getClass().getMethod("getIdade").invoke(p);

                somaForcaGeral += forca;
                somaIdadeGeral += idade;

                if (pos >= 0 && pos < 5) {
                    plan.contagemPorPos[pos]++;
                    somaForcaPos[pos] += forca;
                }

                String car = br.com.brasfootmod.scout.ScoutController.extrairCaracteristicasJogador(p).toLowerCase();
                if (car.contains("arm") || car.contains("pas")) criacaoArmPas++;
                if (car.contains("vel") || car.contains("dri") || car.contains("cru")) velocidadeDriVel++;
                if (car.contains("fin") || car.contains("cab")) presencaAreaFinCab++;
                if (car.contains("des") || car.contains("mar")) desarmeMarcacao++;

                if (pos == 0) goleiros.add(p);
                else if (pos == 1) laterais.add(p);
                else if (pos == 2) zagueiros.add(p);
                else if (pos == 3) meias.add(p);
                else if (pos == 4) atacantes.add(p);
            } catch (Throwable ignored) {}
        }

        if (plan.totalJogadores > 0) {
            plan.forcaMediaGeral = (double) somaForcaGeral / plan.totalJogadores;
            plan.idadeMediaGeral = (double) somaIdadeGeral / plan.totalJogadores;
        }

        // Ordena listas de atletas de cada setor por força decrescente (fi)
        java.util.Comparator<Object> compForca = (a, b) -> {
            try {
                int fa = (Integer) a.getClass().getMethod("fi").invoke(a);
                int fb = (Integer) b.getClass().getMethod("fi").invoke(b);
                return Integer.compare(fb, fa);
            } catch (Throwable t) { return 0; }
        };
        goleiros.sort(compForca);
        laterais.sort(compForca);
        zagueiros.sort(compForca);
        meias.sort(compForca);
        atacantes.sort(compForca);

        // Forças médias dos titulares potenciais de cada setor (para evitar distorção de juniores reservas)
        double top1Gk = calcularMediaTopN(goleiros, 1);
        double top2Lat = calcularMediaTopN(laterais, 2);
        double top2Zag = calcularMediaTopN(zagueiros, 2);
        double top3Zag = calcularMediaTopN(zagueiros, 3);
        double top3Mei = calcularMediaTopN(meias, 3);
        double top4Mei = calcularMediaTopN(meias, 4);
        double top2Ata = calcularMediaTopN(atacantes, 2);
        double top3Ata = calcularMediaTopN(atacantes, 3);

        int qtdLat = laterais.size();
        int qtdZag = zagueiros.size();
        int qtdMei = meias.size();
        int qtdAta = atacantes.size();

        // Identifica o setor titular mais forte do clube
        double[] forcaTitularSetor = {top1Gk, top2Lat, top2Zag, top3Mei, top2Ata};
        int melhorSetorIdx = 0;
        double maiorForcaSetor = -1;
        for (int i = 0; i < 5; i++) {
            if (plan.contagemPorPos[i] > 0) {
                plan.forcaMediaPorPos[i] = (double) somaForcaPos[i] / plan.contagemPorPos[i];
                if (forcaTitularSetor[i] > maiorForcaSetor) {
                    maiorForcaSetor = forcaTitularSetor[i];
                    melhorSetorIdx = i;
                }
            }
        }
        plan.setorMaisForte = POSICOES_NOMES[melhorSetorIdx];

        // Nível, reputação e porte do clube no jogo
        int nivelClube = 10;
        int repClube = 3;
        int divClube = 0;
        try {
            if (humanClub != null) {
                nivelClube = (Integer) humanClub.getClass().getMethod("getNivel").invoke(humanClub);
                repClube = (Integer) humanClub.getClass().getMethod("getReputacao").invoke(humanClub);
                divClube = (Integer) humanClub.getClass().getMethod("getDivisao").invoke(humanClub);
            }
        } catch (Throwable ignored) {}

        boolean isClubeElite = nivelClube >= 15 || repClube >= 4 || plan.forcaMediaGeral >= 42.0;
        boolean isClubePequeno = nivelClube <= 7 || divClube >= 2 || plan.forcaMediaGeral <= 29.0;

        // Análise de virtudes e assimetrias táticas reais do elenco
        boolean trioAtaquePoderoso = qtdAta >= 3 && (top3Ata >= plan.forcaMediaGeral - 1.5) && (top3Ata >= top3Mei - 1.5);
        boolean carenciaLaterais = (qtdLat <= 2) || (top2Lat < top2Zag - 2.8) || (top2Lat < plan.forcaMediaGeral - 3.0);
        boolean zagaMuitoFortePara3Zagueiros = qtdZag >= 4 && (top3Zag >= plan.forcaMediaGeral - 1.0);
        boolean meioDominanteAbsoluto = qtdMei >= 6 && (top4Mei >= top2Ata + 2.5) && (top4Mei >= top2Zag + 1.5);
        boolean timeDefensivoReativo = isClubePequeno && (top3Zag >= top2Ata);

        // =========================================================================
        // 1. DETERMINAÇÃO DA FORMAÇÃO PRINCIPAL (OPÇÃO 1)
        // Opções no Brasfoot:
        // Estilo de jogo: Equilibrado | Ataque total | Contra-ataque
        // Marcação: Leve | Pesada | Muito pesada
        // Concentrar ataques: Pelo meio | Pelas laterais
        // =========================================================================
        TacticalOption p1 = plan.principal;

        if (isClubeElite && trioAtaquePoderoso) {
            p1.esquema = "4-3-3";
            p1.estiloJogo = "Ataque total";
            p1.porOndeJogar = (velocidadeDriVel >= criacaoArmPas) ? "Pelas laterais" : "Pelo meio";
            p1.marcacao = (desarmeMarcacao >= 5) ? "Muito pesada" : "Pesada";
            p1.justificativa = "Tridente ofensivo de elite para pressionar o adversário no campo de ataque.";
            p1.aplicacao = "Ideal para ditar o ritmo em casa e impor superioridade técnica.";
            adicionarDestaques(p1.destaquesChave, atacantes, 2);
            adicionarDestaques(p1.destaquesChave, meias, 1);
        } else if (carenciaLaterais && zagaMuitoFortePara3Zagueiros) {
            p1.esquema = "3-5-2";
            p1.estiloJogo = isClubeElite ? "Equilibrado" : "Contra-ataque";
            p1.porOndeJogar = "Pelas laterais";
            p1.marcacao = isClubePequeno ? "Muito pesada" : "Pesada";
            p1.justificativa = "Trio de zagueiros firme que blinda a grande área e potencializa o apoio dos alas.";
            p1.aplicacao = "Excelente compactação que supre carências nas laterais sem perder presença de área.";
            adicionarDestaques(p1.destaquesChave, zagueiros, 2);
            adicionarDestaques(p1.destaquesChave, atacantes, 1);
        } else if (timeDefensivoReativo && zagaMuitoFortePara3Zagueiros) {
            p1.esquema = "5-3-2";
            p1.estiloJogo = "Contra-ataque";
            p1.porOndeJogar = "Pelo meio";
            p1.marcacao = "Muito pesada";
            p1.justificativa = "Linha de cinco defensores para neutralizar adversários mais fortes e contra-atacar.";
            p1.aplicacao = "Postura de contenção máxima para somar pontos cruciais na temporada.";
            adicionarDestaques(p1.destaquesChave, zagueiros, 2);
            adicionarDestaques(p1.destaquesChave, atacantes, 1);
        } else if (meioDominanteAbsoluto) {
            p1.esquema = "4-5-1";
            p1.estiloJogo = "Equilibrado";
            p1.porOndeJogar = "Pelo meio";
            p1.marcacao = "Pesada";
            p1.justificativa = "Preenchimento total da faixa central para reter posse e ditar o ritmo de jogo.";
            p1.aplicacao = "Indicado para dominar confrontos truncados e controlar o meio de campo.";
            adicionarDestaques(p1.destaquesChave, meias, 2);
            adicionarDestaques(p1.destaquesChave, atacantes, 1);
        } else if (trioAtaquePoderoso && top3Ata >= top2Zag) {
            p1.esquema = "4-3-3";
            p1.estiloJogo = isClubePequeno ? "Equilibrado" : "Ataque total";
            p1.porOndeJogar = (velocidadeDriVel >= criacaoArmPas) ? "Pelas laterais" : "Pelo meio";
            p1.marcacao = "Pesada";
            p1.justificativa = "Três atacantes móveis explorando amplitude e finalização na grande área.";
            p1.aplicacao = "Estrutura vertical para agredir linhas defensivas compactas.";
            adicionarDestaques(p1.destaquesChave, atacantes, 2);
            adicionarDestaques(p1.destaquesChave, meias, 1);
        } else {
            p1.esquema = "4-4-2";
            p1.estiloJogo = isClubeElite ? "Ataque total" : "Equilibrado";
            p1.porOndeJogar = (velocidadeDriVel > criacaoArmPas) ? "Pelas laterais" : "Pelo meio";
            p1.marcacao = isClubePequeno ? "Muito pesada" : "Pesada";
            p1.justificativa = "Equilíbrio absoluto entre linhas de quatro para máxima consistência tática.";
            p1.aplicacao = "Formação estável e versátil para a espinha dorsal da equipe.";
            adicionarDestaques(p1.destaquesChave, atacantes, 1);
            adicionarDestaques(p1.destaquesChave, meias, 1);
            adicionarDestaques(p1.destaquesChave, zagueiros, 1);
        }

        // =========================================================================
        // 2. DETERMINAÇÃO DA FORMAÇÃO ALTERNATIVA (OPÇÃO 2)
        // Em Julho (Mata-Matas): Variação estratégica copeira ou abafa decisivo
        // Em Janeiro (Início): Variação para clássicos, jogos fora e rodagem
        // =========================================================================
        TacticalOption p2 = plan.alternativa;

        if (p1.esquema.equals("4-3-3")) {
            if (!isInicioTemporada) {
                p2.esquema = "4-4-2";
                p2.estiloJogo = "Contra-ataque";
                p2.porOndeJogar = "Pelo meio";
                p2.marcacao = "Muito pesada";
                p2.justificativa = "Compactação copeira com duas linhas de 4 para mata-matas fora de casa.";
                p2.aplicacao = "Estratégia para defender vantagens em eliminatórias e saídas em velocidade.";
            } else {
                p2.esquema = "4-4-2";
                p2.estiloJogo = "Equilibrado";
                p2.porOndeJogar = "Pelo meio";
                p2.marcacao = "Pesada";
                p2.justificativa = "Variação sólida de 4 defensores para jogos truncados e clássicos.";
                p2.aplicacao = "Maior segurança na intermediária sem abrir mão da presença ofensiva.";
            }
            adicionarDestaques(p2.destaquesChave, meias, 1);
            adicionarDestaques(p2.destaquesChave, atacantes, 1);
        } else if (p1.esquema.equals("3-5-2")) {
            if (!isInicioTemporada && trioAtaquePoderoso) {
                p2.esquema = "4-3-3";
                p2.estiloJogo = "Ataque total";
                p2.porOndeJogar = "Pelas laterais";
                p2.marcacao = "Muito pesada";
                p2.justificativa = "Pressão alta total com três atacantes para reverter resultados em copas.";
                p2.aplicacao = "Opção de abafa para quando a vitória for obrigatória na eliminatória.";
                adicionarDestaques(p2.destaquesChave, atacantes, 2);
            } else {
                p2.esquema = "4-4-2";
                p2.estiloJogo = isClubeElite ? "Ataque total" : "Equilibrado";
                p2.porOndeJogar = (velocidadeDriVel > criacaoArmPas) ? "Pelas laterais" : "Pelo meio";
                p2.marcacao = "Pesada";
                p2.justificativa = "Linha de quatro defensores para dar suporte direto nas laterais do campo.";
                p2.aplicacao = "Variação recomendada para jogos sob forte pressão nas alas.";
                adicionarDestaques(p2.destaquesChave, atacantes, 1);
                adicionarDestaques(p2.destaquesChave, laterais, 1);
            }
        } else if (p1.esquema.equals("5-3-2")) {
            p2.esquema = "4-4-2";
            p2.estiloJogo = "Equilibrado";
            p2.porOndeJogar = "Pelo meio";
            p2.marcacao = "Pesada";
            p2.justificativa = "Avanço das linhas para propor o jogo quando estiver precisando do gol.";
            p2.aplicacao = "Transição da retranca para postura competitiva diante de placar desfavorável.";
            adicionarDestaques(p2.destaquesChave, meias, 1);
            adicionarDestaques(p2.destaquesChave, atacantes, 1);
        } else if (p1.esquema.equals("4-5-1")) {
            p2.esquema = trioAtaquePoderoso ? "4-3-3" : "4-4-2";
            p2.estiloJogo = "Ataque total";
            p2.porOndeJogar = "Pelas laterais";
            p2.marcacao = "Muito pesada";
            p2.justificativa = "Agredir com força pelas pontas para furar bloqueios defensivos adversários.";
            p2.aplicacao = "Ideal para momentos em que o controle de posse precisa de mais agressividade.";
            adicionarDestaques(p2.destaquesChave, atacantes, 2);
        } else { // p1.esquema == "4-4-2"
            if (zagaMuitoFortePara3Zagueiros) {
                p2.esquema = "3-5-2";
                p2.estiloJogo = "Contra-ataque";
                p2.porOndeJogar = "Pelas laterais";
                p2.marcacao = "Pesada";
                p2.justificativa = "Três zagueiros para blindar a grande área e explorar saídas velozes com os alas.";
                p2.aplicacao = "Estratégia sólida para enfrentar times favoritos ou jogos como visitante.";
                adicionarDestaques(p2.destaquesChave, zagueiros, 2);
            } else if (trioAtaquePoderoso) {
                p2.esquema = "4-3-3";
                p2.estiloJogo = "Ataque total";
                p2.porOndeJogar = "Pelas laterais";
                p2.marcacao = "Muito pesada";
                p2.justificativa = "Formação agressiva de três homens de frente para pressionar desde a saída de bola.";
                p2.aplicacao = "Plano ofensivo de abafa para buscar a virada ou ampliar saldo de gols.";
                adicionarDestaques(p2.destaquesChave, atacantes, 2);
            } else {
                p2.esquema = "4-5-1";
                p2.estiloJogo = "Contra-ataque";
                p2.porOndeJogar = "Pelo meio";
                p2.marcacao = "Pesada";
                p2.justificativa = "Povoar a intermediária e congelar a posse de bola sob forte pressão.";
                p2.aplicacao = "Recomendada para esfriar o jogo e segurar vantagens no placar.";
                adicionarDestaques(p2.destaquesChave, meias, 2);
            }
        }

        return plan;
    }

    private static double calcularMediaTopN(List<Object> jogadores, int n) {
        if (jogadores == null || jogadores.isEmpty() || n <= 0) return 0.0;
        int limit = Math.min(n, jogadores.size());
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += obterForcaAtleta(jogadores.get(i));
        }
        return (double) sum / limit;
    }

    private static int obterForcaAtleta(Object p) {
        if (p == null) return 0;
        try {
            return (Integer) p.getClass().getMethod("fi").invoke(p);
        } catch (Throwable t) { return 0; }
    }

    private static void adicionarDestaques(List<String> destList, List<Object> squadList, int qtd) {
        int added = 0;
        for (Object p : squadList) {
            if (added >= qtd) break;
            try {
                String nome = (String) p.getClass().getMethod("getNome").invoke(p);
                int forca = (Integer) p.getClass().getMethod("fi").invoke(p);
                int pos = (Integer) p.getClass().getMethod("getPosicao").invoke(p);
                String tag = nome + " (" + POSICOES_NOMES[pos].substring(0, 3).toUpperCase() + ", " + forca + ")";
                if (!destList.contains(tag)) {
                    destList.add(tag);
                    added++;
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Gera o corpo HTML em formato de 2 colunas lado a lado perfeitamente enquadrado para caber
     * integralmente no painel nativo (198px) sem cortes verticais nem horizontais.
     */
    private static String gerarHtmlRelatorioTatico(String clubName, Calendar curCal, TacticalPlan plan) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        String dataStr = sdf.format(curCal.getTime());

        String periodoNome = plan.isInicioTemporada
                ? "Planejamento Inicial (Jan)"
                : "Intertemporada / Mata-Matas (Jul)";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='margin:0; padding:0px 2px; font-family:Tahoma,Arial,sans-serif; color:#111827; background-color:#FFFFFF;'>");

        // Header Superior (370px)
        sb.append("<table width='370' cellpadding='0' cellspacing='0' style='border-bottom:2px solid #1E3A8A; padding-bottom:1px; margin-bottom:2px;'>");
        sb.append("  <tr>");
        sb.append("    <td><b><font color='#1E3A8A' size='2'>PARECER TÁTICO DO ASSISTENTE</font></b></td>");
        sb.append("    <td align='right'><font size='1' color='#6B7280'>").append(dataStr).append("</font></td>");
        sb.append("  </tr>");
        sb.append("</table>");

        // Diagnóstico Síntese
        sb.append("<table width='370' cellpadding='0' cellspacing='0' style='margin-bottom:3px;'>");
        sb.append("  <tr><td style='font-size:9.5px; color:#374151;'>");
        sb.append("    <b><font color='#1E3A8A'>").append(periodoNome).append(":</font></b> ");
        sb.append("Elenco com ").append(plan.totalJogadores).append(" atletas (Força: <b>")
          .append(String.format(Locale.US, "%.1f", plan.forcaMediaGeral))
          .append("</b>). Setor mais forte: <b>").append(plan.setorMaisForte).append("</b>.");
        sb.append("  </td></tr>");
        sb.append("</table>");

        // Tabela de Comparação Lado a Lado (2 Colunas de 182px cada = 370px total)
        sb.append("<table width='370' cellpadding='0' cellspacing='0' style='font-size:9px;'>");
        sb.append("  <tr>");

        // Coluna 1: Opção 1 (Principal)
        sb.append("    <td width='182' valign='top' style='background:#F8FAFC; border:1px solid #93C5FD; border-top:3px solid #2563EB; padding:3px 4px;'>");
        sb.append("      <table width='100%' cellpadding='0' cellspacing='0' style='font-size:9px;'>");
        sb.append("        <tr><td style='border-bottom:1px solid #DBEAFE; padding-bottom:1px;'>");
        sb.append("          <b><font color='#1E40AF'>★ Opção 1: ").append(plan.principal.esquema).append("</font></b>");
        sb.append("          <font color='#2563EB' size='1'> (Principal)</font>");
        sb.append("        </td></tr>");
        sb.append("        <tr><td style='padding-top:2px;'><b>Estilo de jogo:</b> <font color='#111827'>").append(plan.principal.estiloJogo).append("</font></td></tr>");
        sb.append("        <tr><td><b>Marcação:</b> <font color='#111827'>").append(plan.principal.marcacao).append("</font></td></tr>");
        sb.append("        <tr><td><b>Concentrar ataques:</b> <font color='#111827'>").append(plan.principal.porOndeJogar).append("</font></td></tr>");
        sb.append("        <tr><td style='color:#475569; font-size:8.5px; padding-top:2px;'><i>\"").append(plan.principal.justificativa).append("\"</i></td></tr>");
        if (!plan.principal.destaquesChave.isEmpty()) {
            sb.append("        <tr><td style='font-size:8px; color:#1E40AF; padding-top:1px;'><b>Pilares:</b> ").append(String.join(", ", plan.principal.destaquesChave)).append("</td></tr>");
        }
        sb.append("      </table>");
        sb.append("    </td>");

        sb.append("    <td width='6'></td>"); // Espaçador entre colunas

        // Coluna 2: Opção 2 (Variação)
        sb.append("    <td width='182' valign='top' style='background:#F8FAFC; border:1px solid #A7F3D0; border-top:3px solid #0D9488; padding:3px 4px;'>");
        sb.append("      <table width='100%' cellpadding='0' cellspacing='0' style='font-size:9px;'>");
        sb.append("        <tr><td style='border-bottom:1px solid #CCFBF1; padding-bottom:1px;'>");
        sb.append("          <b><font color='#065F46'>🛡️ Opção 2: ").append(plan.alternativa.esquema).append("</font></b>");
        String subOpcao2 = plan.isInicioTemporada ? " (Alternativa)" : " (Mata-Mata)";
        sb.append("          <font color='#0D9488' size='1'>").append(subOpcao2).append("</font>");
        sb.append("        </td></tr>");
        sb.append("        <tr><td style='padding-top:2px;'><b>Estilo de jogo:</b> <font color='#111827'>").append(plan.alternativa.estiloJogo).append("</font></td></tr>");
        sb.append("        <tr><td><b>Marcação:</b> <font color='#111827'>").append(plan.alternativa.marcacao).append("</font></td></tr>");
        sb.append("        <tr><td><b>Concentrar ataques:</b> <font color='#111827'>").append(plan.alternativa.porOndeJogar).append("</font></td></tr>");
        sb.append("        <tr><td style='color:#475569; font-size:8.5px; padding-top:2px;'><i>\"").append(plan.alternativa.justificativa).append("\"</i></td></tr>");
        if (!plan.alternativa.destaquesChave.isEmpty()) {
            sb.append("        <tr><td style='font-size:8px; color:#065F46; padding-top:1px;'><b>Pilares:</b> ").append(String.join(", ", plan.alternativa.destaquesChave)).append("</td></tr>");
        }
        sb.append("      </table>");
        sb.append("    </td>");

        sb.append("  </tr>");
        sb.append("</table>");

        // Rodapé de Orientação
        sb.append("<table width='370' cellpadding='0' cellspacing='0' style='margin-top:2px;'>");
        sb.append("  <tr><td style='font-size:8.5px; color:#6B7280; font-style:italic;'>");
        sb.append("    Dica: Configure o Esquema, Estilo, Marcação e Ataque antes da partida.");
        sb.append("  </td></tr>");
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

            return msgObj != null;
        } catch (Throwable t) {
            log("Erro ao instanciar mensagem da comissão técnica: " + t.getMessage());
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
}
