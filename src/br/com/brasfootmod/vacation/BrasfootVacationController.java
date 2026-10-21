package br.com.brasfootmod.vacation;

import br.com.brasfootmod.BrasfootAgent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Controlador do Modo Férias do Brasfoot 22-23.
 * Opera 100% sobre as datas do calendário interno do jogo (c.a.SR.bb()).
 * Automatiza o ciclo completo de avanço:
 * 1. Dialogos modais prioritários (proposta de seleção a.gO, alertas JOptionPane, pré-jogo a.bz)
 * 2. Simulação instantânea de partidas (a.dV)
 * 3. Tabela de classificação pós-jogo (a.hk) ➔ Avança para a próxima rodada
 * 4. Tela principal do elenco (a.eg) ➔ Clica em 'Escalar Time'
 */
public class BrasfootVacationController {

    private static boolean emFerias = false;
    private static Calendar dataInicio = null;
    private static Calendar dataFim = null;
    private static int rodadaInicio = 0;

    // Configurações do Treinador para o Auxiliar
    private static boolean rejeitarPropostas = true;
    private static boolean pararSeZ4 = true;
    private static boolean autoEscalar = true;
    private static boolean pararNoFimDoEstadual = false;

    // Estatísticas acumuladas durante as férias
    private static int partidasDisputadas = 0;
    private static int vitorias = 0;
    private static int empates = 0;
    private static int derrotas = 0;
    private static int golsPro = 0;
    private static int golsContra = 0;
    private static final List<String> historicoPartidas = new ArrayList<>();
    private static final Set<String> partidasRegistradasChaves = new HashSet<>();
    private static final Set<Object> partidasRegistradasObjetos = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<String> eventos = new ArrayList<>();
    private static String clubeHumanoNome = "";

    // Componentes visuais
    private static BrasfootVacationOverlay overlay = null;
    private static Thread workerThread = null;
    private static volatile boolean running = false;
    private static long ultimoAcaoTime = 0;
    private static long ultimoCliqueEscalarTime = 0;

    public static void logToFile(String msg) {
        try {
            FileWriter fw = new FileWriter("C:\\Brasfoot22-23\\mod_debug.log", true);
            fw.write(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + " " + msg + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    public static boolean isEmFerias() {
        return emFerias;
    }

    public static Calendar getDataFim() {
        return dataFim;
    }

    public static String getClubeHumanoNome() {
        return clubeHumanoNome;
    }

    public static int getPartidasDisputadas() {
        return partidasDisputadas;
    }

    public static int getVitorias() {
        return vitorias;
    }

    public static int getEmpates() {
        return empates;
    }

    public static int getDerrotas() {
        return derrotas;
    }

    public static int getGolsPro() {
        return golsPro;
    }

    public static int getGolsContra() {
        return golsContra;
    }

    public static List<String> getHistoricoPartidas() {
        return Collections.unmodifiableList(historicoPartidas);
    }

    public static List<String> getEventos() {
        return Collections.unmodifiableList(eventos);
    }

    /**
     * Inicia o período de férias
     */
    public static void iniciarFerias(Calendar alvoData, boolean rejPropostas, boolean pararZ4, boolean autoEsc, boolean pararEstadual) {
        if (emFerias) return;

        dataInicio = getCurrentGameCalendar();
        if (dataInicio == null) {
            System.err.println("[MOD FÉRIAS] ❌ Erro: Não foi possível obter a data do jogo. Verifique se o save está carregado.");
            return;
        }

        dataFim = alvoData;
        rejeitarPropostas = rejPropostas;
        pararSeZ4 = pararZ4;
        autoEscalar = autoEsc;
        pararNoFimDoEstadual = pararEstadual;

        rodadaInicio = getCurrentRound();
        partidasDisputadas = 0;
        vitorias = 0;
        empates = 0;
        derrotas = 0;
        golsPro = 0;
        golsContra = 0;
        historicoPartidas.clear();
        partidasRegistradasChaves.clear();
        partidasRegistradasObjetos.clear();
        eventos.clear();

        clubeHumanoNome = getHumanClubName();

        // Ativa modo rápido/simulação nativo do Brasfoot (bK = true) e desativa pausa no intervalo (verJanelaSubs = 0)
        try {
            Object sr = getSRObject();
            if (sr != null) {
                Method hMethod = sr.getClass().getMethod("h", boolean.class);
                hMethod.invoke(sr, true);
                Method setSubs = sr.getClass().getMethod("setVerJanelaSubs", int.class);
                setSubs.invoke(sr, 0); // 0 = sem parar no intervalo
            }
        } catch (Throwable ignored) {}

        emFerias = true;
        logToFile("[MOD FÉRIAS] 🏖️ FÉRIAS INICIADAS! Clube: " + clubeHumanoNome + " | Início: " + formatCalendar(dataInicio) + " | Alvo: " + formatCalendar(dataFim) + " | pararEstadual=" + pararNoFimDoEstadual);
        System.out.println("==================================================");
        System.out.println("  🏖️ MOD FÉRIAS INICIADO COM SUCESSO! 🏖️");
        System.out.println("  Clube: " + clubeHumanoNome);
        System.out.println("  Data do Jogo Atual: " + formatCalendar(dataInicio));
        System.out.println("  Data de Retorno Alvo: " + formatCalendar(dataFim));
        System.out.println("==================================================");

        // Exibe o Overlay moderno de progresso no topo da tela
        SwingUtilities.invokeLater(() -> {
            if (overlay == null) {
                overlay = new BrasfootVacationOverlay();
            }
            overlay.atualizarStatus(dataInicio, dataFim, "Iniciando viagem...", 0);
            overlay.setVisible(true);
        });

        iniciarLoopAutomacao();
    }

    /**
     * Interrompe as férias imediatamente a pedido do usuário
     */
    public static void cancelarFeriasManual() {
        if (!emFerias) return;
        encerrarFerias(true, "Férias canceladas pelo treinador.");
    }

    /**
     * Encerra as férias (por conclusão ou interrupção de emergência)
     */
    public static void encerrarFerias(boolean interrompido, String motivo) {
        if (!emFerias) return;
        emFerias = false;
        logToFile("[MOD FÉRIAS] 🛬 FIM DAS FÉRIAS! Motivo: " + motivo + " | Interrompido: " + interrompido);

        encerrarLoopAutomacao();

        // Garante que qualquer partida pendente na tela a.dV seja finalizada e avançada
        try {
            Object activeMatch = BrasfootAgent.findActiveMatch();
            if (activeMatch != null) {
                if (!BrasfootAgent.isMatchFinished(activeMatch)) {
                    BrasfootAgent.simularPartidaCompleta(activeMatch);
                }
                avancarAposPartida(activeMatch);
            }
        } catch (Throwable ignored) {}

        // Restaura modo rápido nativo do Brasfoot para o padrão
        try {
            Object sr = getSRObject();
            if (sr != null) {
                Method hMethod = sr.getClass().getMethod("h", boolean.class);
                hMethod.invoke(sr, false);
                Method setSubs = sr.getClass().getMethod("setVerJanelaSubs", int.class);
                setSubs.invoke(sr, 1); // 1 = restaura pausa no intervalo nativa do Brasfoot
            }
        } catch (Throwable ignored) {}

        SwingUtilities.invokeLater(() -> {
            if (overlay != null) {
                overlay.setVisible(false);
                overlay.dispose();
                overlay = null;
            }

            // Sincronização garantida com o calendário oficial de partidas (c.a.SR.R())
            try {
                sincronizarHistoricoComCalendarioOficial();
            } catch (Throwable t) {
                logToFile("[MOD FÉRIAS] Erro na sincronização com calendário oficial: " + t.getMessage());
            }

            // Exibe a janela de relatório de retorno
            BrasfootVacationReportDialog.mostrarRelatorio(
                    dataInicio,
                    getCurrentGameCalendar(),
                    interrompido,
                    motivo,
                    partidasDisputadas,
                    vitorias,
                    empates,
                    derrotas,
                    golsPro,
                    golsContra,
                    historicoPartidas,
                    eventos
            );
        });

        System.out.println("[MOD FÉRIAS] 🛬 Fim das férias: " + motivo);
    }

    /**
     * Loop em segundo plano responsável pelo avanço automático do jogo durante as férias
     */
    private static void iniciarLoopAutomacao() {
        encerrarLoopAutomacao();

        running = true;
        workerThread = new Thread(() -> {
            System.out.println("[MOD FÉRIAS] 🔄 Worker thread de automação iniciado!");
            logToFile("[MOD FÉRIAS] 🔄 Worker thread de automação iniciado!");
            while (emFerias && running) {
                try {
                    processarTickAutomacao();
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    break;
                } catch (Throwable t) {
                    logToFile("[MOD FÉRIAS] Erro no tick: " + t.toString());
                    t.printStackTrace();
                }
            }
            System.out.println("[MOD FÉRIAS] ⏹️ Worker thread de automação finalizado.");
            logToFile("[MOD FÉRIAS] ⏹️ Worker thread de automação finalizado.");
        }, "BrasfootVacationWorker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private static void encerrarLoopAutomacao() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    /**
     * Executa a cada ciclo de automação (100ms)
     */
    private static void processarTickAutomacao() {
        Calendar dataJogoAtual = getCurrentGameCalendar();

        long now = System.currentTimeMillis();

        // 0. PRIORIDADE ABSOLUTA: SE HOUVER PARTIDA EM ANDAMENTO OU FINALIZADA (a.dV), INTERVALO (a.cD) OU PÊNALTIS
        // Conclui e avança a partida completamente ANTES de qualquer verificação de encerramento de férias!
        if (BrasfootAgent.verificarEProcessarPenaltisInstantaneo()) {
            ultimoAcaoTime = now;
            return;
        }

        Object match = BrasfootAgent.findActiveMatch();
        if (match != null) {
            if (!BrasfootAgent.isMatchFinished(match)) {
                BrasfootAgent.simularPartidaCompleta(match);
                ultimoAcaoTime = now;
                return;
            } else {
                registrarResultadoPartida(match);
                avancarAposPartida(match);
                ultimoAcaoTime = now;
                return;
            }
        }

        Object halftime = BrasfootAgent.findActiveHalftimeDialog();
        if (halftime != null) {
            BrasfootAgent.fecharIntervalo(halftime);
            ultimoAcaoTime = now;
            return;
        }

        // 1. Se estiver configurado para 'Até o Fim do Estadual', o encerramento é governado
        // EXCLUSIVAMENTE pelo término do estadual ou aproximação de jogo nacional.
        if (pararNoFimDoEstadual) {
            if (isProximaPartidaNaoEstadual(dataJogoAtual)) {
                encerrarFerias(false, "O Campeonato Estadual terminou! Retornando o comando ao treinador antes da estreia no Campeonato Brasileiro.");
                return;
            }
        } else {
            // 1.1 Para períodos normais (1 semana, 1 mês, data específica), verifica data alvo
            if (dataJogoAtual != null && dataFim != null) {
                if (!dataJogoAtual.before(dataFim)) {
                    encerrarFerias(false, "Período planejado concluído! O clube avançou até: " + formatCalendar(dataJogoAtual));
                    return;
                }
            }
        }

        // Atualiza overlay com data real do jogo e porcentagem
        if (overlay != null && dataJogoAtual != null && dataInicio != null && dataFim != null) {
            long totalMs = dataFim.getTimeInMillis() - dataInicio.getTimeInMillis();
            long decorridoMs = dataJogoAtual.getTimeInMillis() - dataInicio.getTimeInMillis();
            int pct = totalMs > 0 ? (int) Math.min(100, Math.max(0, (decorridoMs * 100) / totalMs)) : 50;
            String ultimoPlacar = historicoPartidas.isEmpty() ? "Aguardando próxima partida..." : historicoPartidas.get(historicoPartidas.size() - 1);
            overlay.atualizarStatus(dataJogoAtual, dataFim, ultimoPlacar, pct);
        }

        if (now - ultimoAcaoTime < 150) {
            return; // Throttle
        }

        // 3.5 TELA PRÉ-JOGO REAL (a.bz com campo de jogo e 'Jogar >>') - PRIORIDADE MÁXIMA
        Component bzActive = findActivePreGamePanel();
        if (bzActive != null) {
            if (executarPreJogoBz(bzActive)) {
                ultimoAcaoTime = now;
                return;
            }
        }

        // 4. DEMISSÕES NA RODADA (a.bl) - Fecha o diálogo imediatamente para desobstruir
        Window demissoesWin = findActiveDemissoesWindow();
        if (demissoesWin != null) {
            if (tratarDemissoesBl(demissoesWin)) {
                ultimoAcaoTime = now;
                return;
            }
        }

        // 5. ALERTAS E POPUPS JOPTIONPANE
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog && w.isShowing() && w != overlay) {
                if (tratarJOptionPane((JDialog) w)) {
                    ultimoAcaoTime = now;
                    return;
                }
            }
        }

        // 6. LEILÃO DE JOGADORES (a.dz) - Dispensar ofertas e avançar jogo
        Component dzComp = findActiveLeilaoPanel();
        if (dzComp != null) {
            if (tratarLeilaoDz(dzComp)) {
                ultimoAcaoTime = now;
                return;
            }
        }

        // 7. OFERTA DE COMPRA / PROPOSTA DE EMPRÉSTIMO (a.jm)
        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing() && w != overlay) {
                Component jmComp = BrasfootAgent.findComponentBySuffix(w, ".jm");
                if (jmComp != null) {
                    if (tratarOfertaCompraJm(jmComp, w)) {
                        ultimoAcaoTime = now;
                        return;
                    }
                }
            }
        }

        // 8. CONVITES DE SELEÇÃO (a.gO)
        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing() && w != overlay) {
                Component gOComp = BrasfootAgent.findComponentBySuffix(w, ".gO");
                if (gOComp != null) {
                    if (tratarPropostaSelecao(gOComp, w)) {
                        ultimoAcaoTime = now;
                        return;
                    }
                }
            }
        }

        // 9. ORDEM DE BATEDORES DE PÊNALTIS (a.C)
        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing() && w != overlay) {
                Component cComp = BrasfootAgent.findComponentBySuffix(w, ".C");
                if (cComp != null) {
                    if (tratarOrdemPenaltisC(cComp, w)) {
                        ultimoAcaoTime = now;
                        return;
                    }
                }
            }
        }

        // 10. DECISÃO POR PÊNALTIS (a.bg)
        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing() && w != overlay) {
                Component bgComp = BrasfootAgent.findComponentBySuffix(w, ".bg");
                if (bgComp != null) {
                    if (tratarPenaltisBg(bgComp)) {
                        ultimoAcaoTime = now;
                        return;
                    }
                }
            }
        }

        // 11. TABELA DE CLASSIFICAÇÃO / RESULTADOS DA RODADA (a.hk com 'Continuar >>')
        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing() && w != overlay) {
                Component hkComp = BrasfootAgent.findComponentBySuffix(w, ".hk");
                if (hkComp != null) {
                    if (avancarTabelaRodadaHk(hkComp)) {
                        ultimoAcaoTime = now;
                        return;
                    }
                }
            }
        }

        // 12. VIRADA DE TEMPORADA (a.cR com 'Iniciar novo ano >>')
        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing() && w != overlay) {
                Component crComp = BrasfootAgent.findComponentBySuffix(w, ".cR");
                if (crComp != null) {
                    if (avancarFimTemporadaCr(crComp)) {
                        ultimoAcaoTime = now;
                        return;
                    }
                }
            }
        }

        // 13. QUALQUER OUTRO JDIALOG EM PRIMEIRO PLANO (exceto overlay)
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog && w.isShowing() && w != overlay) {
                JDialog dlg = (JDialog) w;
                // Proteção: não fechar diálogos que pertençam à tela tática (a.bz), demissões (a.bl/a.dm) ou leilões (a.dz)
                if (findBzScreen(dlg) != null || findClickableComponent(dlg, "voltar ao time") != null || findClickableComponent(dlg, "taticas salvas") != null) {
                    Component bz = findActivePreGamePanel();
                    if (bz != null && executarPreJogoBz(bz)) {
                        ultimoAcaoTime = now;
                        return;
                    }
                    continue;
                }
                if (findClassInHierarchy(dlg, "bl") != null || findClassInHierarchy(dlg, "dm") != null || findClickableComponent(dlg, "demiss") != null || findClickableComponent(dlg, "demit") != null) {
                    if (tratarDemissoesBl(dlg)) {
                        ultimoAcaoTime = now;
                        return;
                    }
                    continue;
                }
                if (findClassInHierarchy(dlg, "dz") != null || findClickableComponent(dlg, "leilao") != null) {
                    Component dz = findActiveLeilaoPanel();
                    if (dz != null && tratarLeilaoDz(dz)) {
                        ultimoAcaoTime = now;
                        return;
                    }
                    continue;
                }

                logToFile("[MOD FÉRIAS] 🪟 JDialog genérico detectado: " + dlg.getTitle());
                if (tratarContainerGenerico(dlg)) {
                    ultimoAcaoTime = now;
                    return;
                }
                SwingUtilities.invokeLater(() -> dlg.dispose());
                logToFile("[MOD FÉRIAS] 🪟 JDialog genérico fechado via dispose()");
                ultimoAcaoTime = now;
                return;
            }
        }

        // 14. SOMENTE QUANDO NENHUM DIÁLOGO OU TELA ACIMA ESTIVER ABERTA:
        //     Tela Principal do Elenco (a.eg com 'Escalar Time')
        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing() && w != overlay) {
                Component egComp = BrasfootAgent.findComponentBySuffix(w, ".eg");
                if (egComp != null) {
                    if (avancarElencoEg(egComp)) {
                        ultimoAcaoTime = now;
                        return;
                    }
                }
            }
        }
    }

    /**
     * Localiza a janela do diálogo 'Demissões na Rodada' (a.bl) ou 'Demitido!' (a.dm)
     */
    public static Window findActiveDemissoesWindow() {
        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing() && w != overlay) {
                Component bl = BrasfootAgent.findComponentBySuffix(w, ".bl");
                if (bl != null) return w;
                Component dm = BrasfootAgent.findComponentBySuffix(w, ".dm");
                if (dm != null) return w;

                if (w instanceof JDialog) {
                    Component blComp = findClassInHierarchy((Container) w, "bl");
                    if (blComp != null) return w;
                    Component dmComp = findClassInHierarchy((Container) w, "dm");
                    if (dmComp != null) return w;

                    Component btn = findClickableComponent((Container) w, "continuar");
                    Component lblDemiss = findClickableComponent((Container) w, "demiss");
                    Component lblDemit = findClickableComponent((Container) w, "demit");
                    if (btn != null && (lblDemiss != null || lblDemit != null)) return w;
                }
            }
        }
        return null;
    }

    /**
     * Trata tela de 'Demissões na Rodada' (a.bl) ou 'Demitido!' (a.dm)
     */
    private static boolean tratarDemissoesBl(Window w) {
        logToFile("[MOD FÉRIAS] 📰 Tela de Demissão (a.bl / a.dm) detectada! Processando...");
        System.out.println("[MOD FÉRIAS] 📰 Tela de Demissão detectada: continuando...");

        SwingUtilities.invokeLater(() -> {
            boolean foiDemitidoHumano = false;
            try {
                Component dmComp = BrasfootAgent.findComponentBySuffix(w, ".dm");
                if (dmComp == null && w instanceof Container) {
                    dmComp = findClassInHierarchy((Container) w, "dm");
                }
                if (dmComp != null || (w instanceof Container && findClickableComponent((Container) w, "voce foi demitido") != null)) {
                    foiDemitidoHumano = true;
                    logToFile("[MOD FÉRIAS] ⚠️ O treinador foi demitido pela diretoria!");
                    eventos.add("O treinador foi demitido pela diretoria do clube.");
                }

                JButton btnContinuar = null;
                if (w instanceof Container) {
                    Component btn = findClickableComponent((Container) w, "continuar");
                    if (btn instanceof JButton) btnContinuar = (JButton) btn;
                }

                if (btnContinuar != null) {
                    btnContinuar.doClick();
                    for (ActionListener al : btnContinuar.getActionListeners()) {
                        try {
                            al.actionPerformed(new ActionEvent(btnContinuar, ActionEvent.ACTION_PERFORMED, "continuar"));
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable t) {
                logToFile("[MOD FÉRIAS] Erro ao tratar demissão: " + t.getMessage());
            }

            if (w != null) {
                w.dispose();
                logToFile("[MOD FÉRIAS] ✅ Diálogo de demissão fechado com sucesso!");
            }

            if (foiDemitidoHumano) {
                encerrarFerias(true, "Você foi demitido do clube pela diretoria!");
            }
        });
        return true;
    }

    /**
     * Localiza o painel ativo de Leilão de Jogadores (a.dz)
     */
    public static Component findActiveLeilaoPanel() {
        // 1. Instância estática global em best.S.gR
        try {
            ClassLoader cl = BrasfootAgent.getGameClassLoader();
            Class<?> sClass = Class.forName("best.S", true, cl);
            Field grField = sClass.getDeclaredField("gR");
            grField.setAccessible(true);
            Component gR = (Component) grField.get(null);
            if (gR != null && gR.isShowing()) return gR;
        } catch (Throwable ignored) {}

        // 2. Busca nas janelas ativas
        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing() && w != overlay) {
                Component dz = BrasfootAgent.findComponentBySuffix(w, ".dz");
                if (dz != null && dz.isShowing()) return dz;
                if (w instanceof Container) {
                    Component dzChild = findClassInHierarchy((Container) w, "dz");
                    if (dzChild != null && dzChild.isShowing()) return dzChild;
                    Component lblLeilao = findClickableComponent((Container) w, "leilao");
                    if (lblLeilao != null && lblLeilao.isShowing()) return w;
                }
            }
        }
        return null;
    }

    /**
     * Trata tela de 'Leilão de Jogadores' (a.dz)
     */
    private static boolean tratarLeilaoDz(Component dzComp) {
        try {
            verificarERegistrarPartidasDaRodada();
            sincronizarHistoricoComCalendarioOficial();
        } catch (Throwable ignored) {}
        logToFile("[MOD FÉRIAS] 🏷️ Tela de Leilão (a.dz) detectada! Avançando leilão...");
        System.out.println("[MOD FÉRIAS] 🏷️ Tela de Leilão (a.dz) detectada: avançando...");

        SwingUtilities.invokeLater(() -> {
            try {
                // 1. Clicar em 'Continuar Jogo >>' (Gb) se visível
                try {
                    Field gbField = dzComp.getClass().getDeclaredField("Gb");
                    gbField.setAccessible(true);
                    Component gb = (Component) gbField.get(dzComp);
                    if (gb != null && gb.isShowing()) {
                        logToFile("[MOD FÉRIAS] 🏷️ Clicando 'Continuar Jogo >>' (Gb)...");
                        clicarComponente(gb);
                    }
                } catch (Throwable ignored) {}

                // 2. Clicar em 'Não Ofertar' (Ge) se visível
                try {
                    Field geField = dzComp.getClass().getDeclaredField("Ge");
                    geField.setAccessible(true);
                    Component ge = (Component) geField.get(dzComp);
                    if (ge != null && ge.isShowing()) {
                        logToFile("[MOD FÉRIAS] 🏷️ Clicando 'Não Ofertar' (Ge)...");
                        clicarComponente(ge);
                    }
                } catch (Throwable ignored) {}

                // 3. Clicar em 'Ver próximo leilão' (Gd) se visível
                try {
                    Field gdField = dzComp.getClass().getDeclaredField("Gd");
                    gdField.setAccessible(true);
                    Component gd = (Component) gdField.get(dzComp);
                    if (gd != null && gd.isShowing()) {
                        logToFile("[MOD FÉRIAS] 🏷️ Clicando 'Ver próximo leilão' (Gd)...");
                        clicarComponente(gd);
                    }
                } catch (Throwable ignored) {}

                // 4. Invocação direta de nx() (que chama c.a.SR.ax() para avançar o leilão imediatamente)
                try {
                    Method nxMethod = dzComp.getClass().getDeclaredMethod("nx");
                    nxMethod.setAccessible(true);
                    nxMethod.invoke(dzComp);
                    logToFile("[MOD FÉRIAS] 🏷️ nx() invocado com sucesso!");
                } catch (Throwable t2) {
                    try {
                        Method puMethod = dzComp.getClass().getDeclaredMethod("pU");
                        puMethod.setAccessible(true);
                        puMethod.invoke(dzComp);
                        logToFile("[MOD FÉRIAS] 🏷️ pU() invocado!");
                    } catch (Throwable ignored) {}
                }

                // 5. Se estiver em um JDialog FT, fecha o diálogo
                try {
                    Field ftField = dzComp.getClass().getDeclaredField("FT");
                    ftField.setAccessible(true);
                    JDialog ft = (JDialog) ftField.get(dzComp);
                    if (ft != null && ft.isShowing()) {
                        ft.dispose();
                    }
                } catch (Throwable ignored) {}
            } catch (Throwable t) {
                logToFile("[MOD FÉRIAS] Erro em tratarLeilaoDz: " + t.getMessage());
            }
        });
        return true;
    }

    private static Component findClassInHierarchy(Container parent, String suffix) {
        if (parent == null) return null;
        for (Component c : parent.getComponents()) {
            if (c.getClass().getName().endsWith("." + suffix) || c.getClass().getName().equals(suffix)) {
                return c;
            }
            if (c instanceof Container) {
                Component found = findClassInHierarchy((Container) c, suffix);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Trata tela de 'Oferta de compra / Proposta de empréstimo' de jogador (a.jm)
     */
    private static boolean tratarOfertaCompraJm(Component jmComp, Window w) {
        if (rejeitarPropostas) {
            System.out.println("[MOD FÉRIAS] ❌ Proposta de transferência (a.jm) recusada pelo auxiliar...");
            eventos.add("Proposta de transferência recebida e recusada pelo auxiliar.");
            try {
                Field vcField = jmComp.getClass().getDeclaredField("vc");
                vcField.setAccessible(true);
                JButton btnRecusar = (JButton) vcField.get(jmComp);
                if (btnRecusar != null) {
                    btnRecusar.doClick();
                    return true;
                }
            } catch (Throwable ignored) {}

            if (w instanceof JDialog) {
                ((JDialog) w).dispose();
                return true;
            }
        }
        return false;
    }

    /**
     * Trata tela de 'Ordem de Batedores de Pênalti' (a.C)
     */
    private static boolean tratarOrdemPenaltisC(Component cComp, Window w) {
        try {
            Field vqField = cComp.getClass().getDeclaredField("vq");
            vqField.setAccessible(true);
            JButton btnJogar = (JButton) vqField.get(cComp);
            if (btnJogar != null) {
                System.out.println("[MOD FÉRIAS] 🥅 Ordem de batedores de pênalti (a.C) confirmada automaticamente...");
                btnJogar.doClick();
                return true;
            }
        } catch (Throwable ignored) {}
        if (w instanceof JDialog) {
            ((JDialog) w).dispose();
            return true;
        }
        return false;
    }

    /**
     * Trata tela de 'Decisão por Pênaltis' (a.bg) - Oculta janela e simula instantaneamente
     */
    private static boolean tratarPenaltisBg(Component bgComp) {
        BrasfootAgent.simularPenaltisInstantaneamente(bgComp);
        return true;
    }

    /**
     * Trata tela de propostas de seleções (a.gO)
     */
    private static boolean tratarPropostaSelecao(Component gOComp, Window w) {
        try {
            Field kjField = gOComp.getClass().getDeclaredField("Kj");
            kjField.setAccessible(true);
            JButton btnRejeitar = (JButton) kjField.get(gOComp);
            if (btnRejeitar != null) {
                System.out.println("[MOD FÉRIAS] 🚫 Rejeitando convites de seleção (clicando 'Rejeitar todos')...");
                eventos.add("Convites de seleção recebidos e rejeitados pelo auxiliar.");
                btnRejeitar.doClick();
                return true;
            }
        } catch (Throwable t) {
            if (w instanceof JDialog) {
                ((JDialog) w).dispose();
                return true;
            }
        }
        return false;
    }

    /**
     * Localiza o painel pré-jogo (a.bz) de forma segura em qualquer contêiner ou janela
     */
    public static Component findActivePreGamePanel() {
        try {
            ClassLoader cl = BrasfootAgent.getGameClassLoader();
            Class<?> bzClass = null;
            try {
                bzClass = Class.forName("a.bz", true, cl);
            } catch (Throwable t) {
                try {
                    bzClass = Class.forName("a.bz", true, Thread.currentThread().getContextClassLoader());
                } catch (Throwable t2) {
                    bzClass = Class.forName("a.bz");
                }
            }
            if (bzClass != null) {
                Field agField = bzClass.getDeclaredField("AG");
                agField.setAccessible(true);
                Component ag = (Component) agField.get(null);
                if (ag != null && ag.isShowing()) {
                    return ag;
                }
            }
        } catch (Throwable ignored) {}

        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing() && w != overlay) {
                Component bz = BrasfootAgent.findComponentBySuffix(w, ".bz");
                if (bz != null && bz.isShowing()) return bz;
                bz = findBzScreen(w);
                if (bz != null && bz.isShowing()) return bz;

                // Busca por componentes com texto exclusivo de a.bz
                if (w instanceof Container) {
                    Component btnVoltar = findClickableComponent((Container) w, "voltar ao time");
                    if (btnVoltar != null && btnVoltar.isShowing()) {
                        Component p = btnVoltar;
                        while (p != null) {
                            if (p.getClass().getName().endsWith(".bz") || p.getClass().getName().equals("a.bz")) {
                                return p;
                            }
                            if (p instanceof JDialog || p instanceof JFrame) break;
                            p = p.getParent();
                        }
                        if (btnVoltar.getParent() != null) return btnVoltar.getParent();
                    }

                    Component btnTaticas = findClickableComponent((Container) w, "taticas salvas");
                    if (btnTaticas != null && btnTaticas.isShowing()) {
                        Component p = btnTaticas;
                        while (p != null) {
                            if (p.getClass().getName().endsWith(".bz") || p.getClass().getName().equals("a.bz")) {
                                return p;
                            }
                            if (p instanceof JDialog || p instanceof JFrame) break;
                            p = p.getParent();
                        }
                        if (btnTaticas.getParent() != null) return btnTaticas.getParent();
                    }
                }
            }
        }
        return null;
    }

    private static Component findBzScreen(Component root) {
        if (root == null) return null;
        String name = root.getClass().getName().toLowerCase();
        if (name.endsWith(".bz") || name.equals("a.bz")) return root;

        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                Component found = findBzScreen(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Trata e avança a tela pré-jogo real (a.bz):
     * 1. Auto-escala ou preenche para garantir EXATAMENTE 11 titulares nos slots 1..25
     * 2. Aciona o botão nativo Bn ("Jogar >>") e seus ActionListeners
     * 3. Invoca oh() diretamente via reflexão
     * 4. Se a janela ainda estiver visível, invoca W(true) e oi() diretamente para iniciar a partida imediatamente
     */
    private static boolean executarPreJogoBz(Component bzComp) {
        logToFile("[executarPreJogoBz] ▶️ Tela pré-jogo (a.bz) detectada. Processando escalação...");
        System.out.println("[MOD FÉRIAS] 📋 Tela pré-jogo detectada: definindo tática e iniciando partida...");

        SwingUtilities.invokeLater(() -> {
            try {
                Field aeField = bzComp.getClass().getDeclaredField("AE");
                aeField.setAccessible(true);
                ArrayList<?> aeList = (ArrayList<?>) aeField.get(bzComp);
                if (aeList == null) {
                    logToFile("[executarPreJogoBz] aeList é null!");
                    return;
                }

                Field afField = bzComp.getClass().getDeclaredField("AF");
                afField.setAccessible(true);
                ArrayList<?> afList = (ArrayList<?>) afField.get(bzComp);

                Field aoField = bzComp.getClass().getDeclaredField("AO");
                aoField.setAccessible(true);
                ArrayList<Object> aoList = (ArrayList<Object>) aoField.get(bzComp);

                Field alField = bzComp.getClass().getDeclaredField("AL");
                alField.setAccessible(true);
                ImageIcon camisaIcon = (ImageIcon) alField.get(bzComp);

                int titulares = contarTitulares(aeList);
                logToFile("[executarPreJogoBz] Titulares iniciais: " + titulares);

                // 1. Se titulares != 11 ou autoEscalar estiver ativo:
                if (titulares < 11 || autoEscalar) {
                    try {
                        Method cuMethod = bzComp.getClass().getDeclaredMethod("cU", int.class);
                        cuMethod.setAccessible(true);
                        // 4 = 4-4-2, 7 = 4-3-3, 9 = 3-5-2, 3 = 4-5-1, 2 = 5-3-2, 5 = 4-4-2(2), etc.
                        int[] formacoes = {4, 7, 9, 3, 2, 5, 6, 8, 10, 11, 1};
                        for (int f : formacoes) {
                            cuMethod.invoke(bzComp, f);
                            titulares = contarTitulares(aeList);
                            if (titulares == 11) {
                                logToFile("[executarPreJogoBz] Formação " + f + " montou os 11 titulares!");
                                break;
                            }
                        }
                    } catch (Throwable t) {
                        logToFile("[executarPreJogoBz] Aviso cU: " + t.getMessage());
                    }
                }

                titulares = contarTitulares(aeList);

                // 2. Se houver mais de 11 titulares: remove excedentes nos slots 1..25
                if (titulares > 11) {
                    logToFile("[executarPreJogoBz] Removendo excedentes (" + titulares + " no campo)...");
                    for (int slot = 25; slot >= 1 && titulares > 11; slot--) {
                        if (slot >= aeList.size()) continue;
                        Object slotAo = aeList.get(slot);
                        if (slotAo != null) {
                            Method xMethod = slotAo.getClass().getMethod("x");
                            Object p = xMethod.invoke(slotAo);
                            if (p != null) {
                                limparSlotAo(slotAo);
                                if (aoList != null) aoList.remove(p);
                                titulares--;
                            }
                        }
                    }
                }

                // 3. Se ainda faltarem titulares (< 11), preenche os slots vazios em 1..25
                titulares = contarTitulares(aeList);
                if (titulares < 11) {
                    logToFile("[executarPreJogoBz] Preenchendo " + (11 - titulares) + " vagas vazias com jogadores disponíveis...");
                    List<Object> disponiveis = new ArrayList<>();

                    // Busca nos reservas (slots 26 em diante)
                    for (int r = 26; r < aeList.size(); r++) {
                        Object rAo = aeList.get(r);
                        if (rAo != null) {
                            try {
                                Method xMethod = rAo.getClass().getMethod("x");
                                Object p = xMethod.invoke(rAo);
                                if (p != null && !disponiveis.contains(p) && (aoList == null || !aoList.contains(p))) {
                                    disponiveis.add(p);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }

                    // Busca na lista geral do clube AF
                    if (afList != null) {
                        for (Object p : afList) {
                            if (p != null && !disponiveis.contains(p) && (aoList == null || !aoList.contains(p))) {
                                disponiveis.add(p);
                            }
                        }
                    }

                    int dispIdx = 0;
                    for (int slot = 1; slot <= 25 && titulares < 11 && dispIdx < disponiveis.size() && slot < aeList.size(); slot++) {
                        Object slotAo = aeList.get(slot);
                        if (slotAo == null) continue;
                        Method xMethod = slotAo.getClass().getMethod("x");
                        if (xMethod.invoke(slotAo) != null) continue; // Slot já ocupado

                        Object cand = disponiveis.get(dispIdx++);
                        atribuirJogadorAoSlot(slotAo, cand, camisaIcon);

                        if (aoList != null && !aoList.contains(cand)) {
                            aoList.add(cand);
                        }
                        titulares++;
                        logToFile("[executarPreJogoBz] Slot " + slot + " preenchido com reserva/disponível. Titulares: " + titulares);
                    }
                }

                titulares = contarTitulares(aeList);
                logToFile("[executarPreJogoBz] Total de titulares antes de iniciar: " + titulares);

                // Redesenha campo
                try {
                    Method csMethod = bzComp.getClass().getDeclaredMethod("cS", int.class);
                    csMethod.setAccessible(true);
                    csMethod.invoke(bzComp, -1);
                    Method ctMethod = bzComp.getClass().getDeclaredMethod("cT", int.class);
                    ctMethod.setAccessible(true);
                    ctMethod.invoke(bzComp, -1);
                } catch (Throwable ignored) {}
                bzComp.repaint();

                // 4. AVANÇO: CLICAR NO BOTÃO JOGAR E INVOCAR MÉTODOS NATIVOS
                Field bnField = bzComp.getClass().getDeclaredField("Bn");
                bnField.setAccessible(true);
                JButton btnJogar = (JButton) bnField.get(bzComp);
                if (btnJogar != null) {
                    logToFile("[executarPreJogoBz] ⚽ Clicando no botão Jogar >> (Bn)...");
                    btnJogar.doClick();
                    for (ActionListener al : btnJogar.getActionListeners()) {
                        try {
                            al.actionPerformed(new ActionEvent(btnJogar, ActionEvent.ACTION_PERFORMED, "Jogar"));
                        } catch (Throwable ignored) {}
                    }
                }

                // Invoca oh() nativo de a.bz
                try {
                    logToFile("[executarPreJogoBz] 🚀 Invocando oh() nativo...");
                    Method ohMethod = bzComp.getClass().getDeclaredMethod("oh");
                    ohMethod.setAccessible(true);
                    ohMethod.invoke(bzComp);
                } catch (Throwable t) {
                    logToFile("[executarPreJogoBz] Aviso oh(): " + t.getMessage());
                }

                // Fallback direto: se o diálogo ub ainda estiver visível e titulares == 11, força W(true) e oi()
                Field ubField = bzComp.getClass().getDeclaredField("ub");
                ubField.setAccessible(true);
                JDialog ub = (JDialog) ubField.get(bzComp);
                if (ub != null && ub.isShowing() && titulares == 11) {
                    logToFile("[executarPreJogoBz] ⚡ Forçando início imediato via W(true) e oi()...");
                    try {
                        Method wMethod = bzComp.getClass().getDeclaredMethod("W", boolean.class);
                        wMethod.setAccessible(true);
                        wMethod.invoke(bzComp, true);

                        Method oiMethod = bzComp.getClass().getDeclaredMethod("oi");
                        oiMethod.setAccessible(true);
                        oiMethod.invoke(bzComp);
                        logToFile("[executarPreJogoBz] ✅ oi() executado com sucesso!");
                    } catch (Throwable t) {
                        logToFile("[executarPreJogoBz] Erro em W/oi direto: " + t.getMessage());
                    }
                }

                // Fecha eventuais popups do JOptionPane (ex: 'Selecione 11 jogadores')
                for (Window w : Window.getWindows()) {
                    if (w instanceof JDialog && w.isShowing() && w != overlay) {
                        Component paneComp = BrasfootAgent.findComponentBySuffix(w, "JOptionPane");
                        if (paneComp != null) {
                            ((JDialog) w).dispose();
                        }
                    }
                }
            } catch (Throwable t) {
                logToFile("[executarPreJogoBz] Erro fatal: " + t.toString());
                t.printStackTrace();
            }
        });
        return true;
    }

    private static void atribuirJogadorAoSlot(Object slotAo, Object jogador, ImageIcon camisa) {
        if (slotAo == null) return;
        try {
            for (Method m : slotAo.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 1 && !m.getParameterTypes()[0].isPrimitive()) {
                    m.invoke(slotAo, jogador);
                    break;
                }
            }
            if (camisa != null) {
                for (Method m : slotAo.getClass().getMethods()) {
                    if (m.getName().equals("h") && m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(ImageIcon.class)) {
                        m.invoke(slotAo, camisa);
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void limparSlotAo(Object slotAo) {
        if (slotAo == null) return;
        try {
            for (Method m : slotAo.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 1 && !m.getParameterTypes()[0].isPrimitive()) {
                    m.invoke(slotAo, new Object[]{null});
                    break;
                }
            }
            for (Method m : slotAo.getClass().getMethods()) {
                if (m.getName().equals("h") && m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(ImageIcon.class)) {
                    m.invoke(slotAo, new Object[]{null});
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static int contarTitulares(ArrayList<?> aeList) {
        int count = 0;
        if (aeList != null) {
            for (int idx = 1; idx <= 25 && idx < aeList.size(); idx++) {
                Object aoObj = aeList.get(idx);
                if (aoObj != null) {
                    try {
                        Method xMethod = aoObj.getClass().getMethod("x");
                        if (xMethod.invoke(aoObj) != null) count++;
                    } catch (Throwable ignored) {}
                }
            }
        }
        return count;
    }

    /**
     * Trata popups do JOptionPane (como 'Selecione 11 jogadores', confirmações de compra, etc.)
     */
    private static boolean tratarJOptionPane(JDialog dialog) {
        Component paneComp = BrasfootAgent.findComponentBySuffix(dialog, "JOptionPane");
        if (paneComp instanceof JOptionPane) {
            JOptionPane pane = (JOptionPane) paneComp;
            Object msg = pane.getMessage();
            String msgStr = msg != null ? msg.toString() : "";

            // Se for aviso de escalar jogadores, fecha o alerta para que a auto-escalação entre em ação
            if (msgStr.contains("11 jogadores") || msgStr.contains("Selecione")) {
                JButton btnOk = findButtonByText(pane, new String[]{"OK", "Confirmar"});
                if (btnOk != null) {
                    btnOk.doClick();
                    return true;
                }
                dialog.dispose();
                return true;
            }

            // Se for proposta de transferência de jogador e estiver configurado para rejeitar
            if (rejeitarPropostas && (msgStr.contains("proposta") || msgStr.contains("aceitar") || msgStr.contains("compra"))) {
                JButton btnNao = findButtonByText(pane, new String[]{"Não", "No", "Recusar", "Rejeitar"});
                if (btnNao != null) {
                    System.out.println("[MOD FÉRIAS] ❌ Rejeitando proposta de compra recebida (clicando 'Não')...");
                    eventos.add("Proposta de transferência recebida e rejeitada automaticamente.");
                    btnNao.doClick();
                    return true;
                }
            }

            // Se for pergunta de marcar amistosos no início da temporada, recusa para prosseguir suavemente
            if (msgStr.toLowerCase().contains("amistos")) {
                JButton btnNao = findButtonByText(pane, new String[]{"Não", "Nao", "No", "Cancelar"});
                if (btnNao != null) {
                    System.out.println("[MOD FÉRIAS] ⚽ Recusando agendamento de amistosos no início da temporada...");
                    btnNao.doClick();
                    return true;
                }
            }

            // Para qualquer outro JOptionPane informativo (notícia, lesão, prêmio): confirma e fecha
            JButton btnConfirm = findButtonByText(pane, new String[]{"OK", "Sim", "Confirmar", "Continuar", "Fechar"});
            if (btnConfirm != null) {
                btnConfirm.doClick();
                return true;
            } else {
                dialog.dispose();
                return true;
            }
        }
        return false;
    }

    /**
     * Clica em 'Continuar >>' na tabela de resultados/classificação da rodada (a.hk)
     */
    private static boolean avancarTabelaRodadaHk(Component hkComp) {
        try {
            verificarERegistrarPartidasDaRodada();
            sincronizarHistoricoComCalendarioOficial();
        } catch (Throwable ignored) {}
        try {
            Object sr = getSRObject();
            if (sr != null) {
                try {
                    int restantes = ((Number) sr.getClass().getMethod("az").invoke(sr)).intValue();
                    if (restantes == 0) {
                        System.out.println("[MOD FÉRIAS] 🏆 Fim de temporada detectado em a.hk! Invocando transição segura...");
                        br.com.brasfootmod.BrasfootAgent.executarTransicaoSeguraFimTemporada(sr);
                        return true;
                    }
                } catch (Throwable ignored) {}
            }

            Field zkField = hkComp.getClass().getDeclaredField("zK");
            zkField.setAccessible(true);
            JButton btnContinuar = (JButton) zkField.get(hkComp);
            if (btnContinuar != null && btnContinuar.isEnabled() && btnContinuar.isShowing()) {
                System.out.println("[MOD FÉRIAS] 📋 Clicando em 'Continuar >>' na tabela da rodada (a.hk)...");
                btnContinuar.doClick();
                return true;
            }

            // Fallback invocando nx() diretamente
            Method nxMethod = hkComp.getClass().getDeclaredMethod("nx");
            nxMethod.setAccessible(true);
            nxMethod.invoke(hkComp);
            return true;
        } catch (Throwable t) {
            if (hkComp instanceof Container) {
                JButton btn = findButtonByText((Container) hkComp, new String[]{"Continuar >>", "Continuar"});
                if (btn != null && btn.isEnabled()) {
                    btn.doClick();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Clica em 'Escalar Time' na tela principal do elenco (a.eg)
     */
    private static boolean avancarElencoEg(Component egComp) {
        if (findActivePreGamePanel() != null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - ultimoCliqueEscalarTime < 1500) {
            return false; // Throttling de 1.5s para não disparar aberturas concorrentes de modal
        }

        try {
            Field huField = egComp.getClass().getDeclaredField("HU");
            huField.setAccessible(true);
            JButton btnEscalar = (JButton) huField.get(egComp);
            if (btnEscalar != null && btnEscalar.isEnabled() && btnEscalar.isShowing()) {
                ultimoCliqueEscalarTime = now;
                System.out.println("[MOD FÉRIAS] 👕 Clicando em 'Escalar Time' no elenco (a.eg)...");
                logToFile("[MOD FÉRIAS] 👕 Clicando em 'Escalar Time' no elenco (a.eg)...");
                SwingUtilities.invokeLater(() -> btnEscalar.doClick());
                return true;
            }

            // Fallback invocando qZ() diretamente apenas se o pré-jogo não estiver ativo
            if (findActivePreGamePanel() == null) {
                Method qZMethod = egComp.getClass().getMethod("qZ");
                ultimoCliqueEscalarTime = now;
                SwingUtilities.invokeLater(() -> {
                    try {
                        qZMethod.invoke(egComp);
                    } catch (Throwable ignored) {}
                });
                return true;
            }
        } catch (Throwable t) {
            if (egComp instanceof Container) {
                JButton btn = findButtonByText((Container) egComp, new String[]{"Escalar Time", "Jogar"});
                if (btn != null && btn.isEnabled()) {
                    ultimoCliqueEscalarTime = now;
                    SwingUtilities.invokeLater(() -> btn.doClick());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Clica em 'Iniciar novo ano >>' na tela de virada de temporada (a.cR)
     */
    private static boolean avancarFimTemporadaCr(Component crComp) {
        if (crComp instanceof Container) {
            JButton btnNovoAno = findButtonByText((Container) crComp, new String[]{"Iniciar novo ano >>", "Iniciar novo ano", "novo ano"});
            if (btnNovoAno != null && btnNovoAno.isEnabled()) {
                System.out.println("[MOD FÉRIAS] 🎆 Clicando em 'Iniciar novo ano >>' (a.cR)...");
                btnNovoAno.doClick();
                return true;
            }
        }
        return false;
    }

    /**
     * Avança após o término de uma partida (sai da tela de jogo a.dV)
     */
    public static void avancarAposPartida(Object match) {
        try {
            if (BrasfootAgent.isPenaltisInProgress()) {
                logToFile("[MOD FÉRIAS] ⏳ Aguardando conclusão dos pênaltis antes de avançar tela...");
                BrasfootAgent.verificarEProcessarPenaltisInstantaneo();
                return;
            }

            logToFile("[MOD FÉRIAS] 🏁 Avançando tela de partida finalizada (a.dV)...");

            // 1. Invoca o finalizador canônico da rodada no motor do Brasfoot (best.s.dJ()) com checagem de cR
            try {
                Class<?> sCls = Class.forName("best.s", true, BrasfootAgent.getGameClassLoader());
                Field cRField = sCls.getDeclaredField("cR");
                cRField.setAccessible(true);
                if (cRField.get(null) != null) {
                    Method dJMethod = sCls.getMethod("dJ");
                    dJMethod.invoke(null);
                    logToFile("[MOD FÉRIAS] ✅ best.s.dJ() invocado com sucesso!");
                }
            } catch (Throwable ignored) {}

            // 2. Se houver botão Continuar/Voltar, clica nele
            if (match instanceof Container) {
                JButton btnContinuar = findButtonByText((Container) match, new String[]{"Continuar", "Voltar", "OK", "Sair", "Próximo"});
                if (btnContinuar != null && btnContinuar.isEnabled()) {
                    btnContinuar.doClick();
                    return;
                }
            }

            // 3. Força transição segura na EDT do Swing para a tela 5 (pós-jogo / classificação)
            SwingUtilities.invokeLater(() -> {
                try {
                    Class<?> SClass = Class.forName("best.S", true, BrasfootAgent.getGameClassLoader());
                    Method aYMethod = SClass.getMethod("aY", int.class);
                    aYMethod.invoke(null, 5); // 5 = pós-jogo / tabela
                    logToFile("[MOD FÉRIAS] 🔄 best.S.aY(5) executado na EDT para desobstruir a.dV");
                } catch (Throwable t2) {
                    try {
                        Class<?> SClass = Class.forName("best.S", true, BrasfootAgent.getGameClassLoader());
                        Method aYMethod = SClass.getMethod("aY", int.class);
                        aYMethod.invoke(null, 6); // fallback para tela 6
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            logToFile("[MOD FÉRIAS] Erro geral em avancarAposPartida: " + t.getMessage());
        }
    }

    /**
     * Registra o resultado de uma partida delegando exclusivamente para a fonte da verdade oficial (c.a.SR.R())
     */
    public static synchronized void registrarPartidaObjeto(Object matchObj, Integer golsCasaOverride, Integer golsForaOverride) {
        sincronizarHistoricoComCalendarioOficial();
    }

    /**
     * Varre as partidas da rodada em c.a.SR.S() delegando para o sincronizador canônico
     */
    public static synchronized void verificarERegistrarPartidasDaRodada() {
        sincronizarHistoricoComCalendarioOficial();
    }

    /**
     * Sincroniza todas as partidas do período de férias consultando o calendário oficial do save (c.a.SR.R())
     * Garante que nenhum jogo disputado fique de fora do relatório e NENHUM jogo seja duplicado.
     */
    public static synchronized void sincronizarHistoricoComCalendarioOficial() {
        try {
            Object srObj = getSRObject();
            if (srObj == null) return;

            Method rMethod = srObj.getClass().getMethod("R");
            List<?> todasRodadas = (List<?>) rMethod.invoke(srObj);
            if (todasRodadas == null || todasRodadas.isEmpty()) return;

            String meuClube = getHumanClubName();
            SimpleDateFormat sdfInt = new SimpleDateFormat("yyyyMMdd");
            String dataInicioStr = dataInicio != null ? sdfInt.format(dataInicio.getTime()) : "00000000";
            String dataFimStr = dataFim != null ? sdfInt.format(dataFim.getTime()) : "99999999";

            Object[] rodadasArray = todasRodadas.toArray();
            for (Object rodada : rodadasArray) {
                if (rodada == null) continue;
                try {
                    Method aMethod = rodada.getClass().getMethod("a");
                    Calendar dataRodada = (Calendar) aMethod.invoke(rodada);
                    if (dataRodada == null) continue;

                    String dStr = sdfInt.format(dataRodada.getTime());
                    // Só processa partidas disputadas dentro da janela de férias
                    if (dStr.compareTo(dataInicioStr) < 0 || dStr.compareTo(dataFimStr) > 0) {
                        continue;
                    }

                    Method hMethod = rodada.getClass().getMethod("h");
                    List<?> partidas = (List<?>) hMethod.invoke(rodada);
                    if (partidas == null || partidas.isEmpty()) continue;

                    Object[] partidasArray = partidas.toArray();
                    for (Object matchObj : partidasArray) {
                        if (matchObj == null) continue;
                        try {
                            Method hcMethod = matchObj.getClass().getMethod("hc");
                            Method hdMethod = matchObj.getClass().getMethod("hd");
                            Object homeClub = hcMethod.invoke(matchObj);
                            Object awayClub = hdMethod.invoke(matchObj);
                            if (homeClub == null || awayClub == null) continue;

                            String homeName = (String) homeClub.getClass().getMethod("getNome").invoke(homeClub);
                            String awayName = (String) awayClub.getClass().getMethod("getNome").invoke(awayClub);
                            if (homeName == null || awayName == null) continue;

                            boolean souCasa = false;
                            boolean souFora = false;
                            try {
                                Method jzHome = homeClub.getClass().getMethod("jZ");
                                Method jzAway = awayClub.getClass().getMethod("jZ");
                                Boolean homeHuman = (Boolean) jzHome.invoke(homeClub);
                                Boolean awayHuman = (Boolean) jzAway.invoke(awayClub);
                                if (Boolean.TRUE.equals(homeHuman)) souCasa = true;
                                if (Boolean.TRUE.equals(awayHuman)) souFora = true;
                            } catch (Throwable ignored) {}

                            if (!souCasa && !souFora) {
                                if (meuClube != null && !meuClube.isEmpty() && !meuClube.equalsIgnoreCase("Seu Clube") && !meuClube.equalsIgnoreCase("Desempregado")) {
                                    if (homeName.trim().equalsIgnoreCase(meuClube.trim())) souCasa = true;
                                    if (awayName.trim().equalsIgnoreCase(meuClube.trim())) souFora = true;
                                }
                            }

                            if (!souCasa && !souFora) continue;

                            // Verifica se a partida foi disputada
                            boolean foiDisputada = false;
                            try {
                                foiDisputada = Boolean.TRUE.equals(matchObj.getClass().getMethod("hS").invoke(matchObj));
                            } catch (Throwable ignored) {}
                            if (!foiDisputada) {
                                try {
                                    foiDisputada = Boolean.TRUE.equals(matchObj.getClass().getMethod("e").invoke(matchObj));
                                } catch (Throwable ignored) {}
                            }
                            if (!foiDisputada) {
                                try {
                                    List<?> lances = (List<?>) matchObj.getClass().getMethod("hE").invoke(matchObj);
                                    if (lances != null && !lances.isEmpty()) {
                                        foiDisputada = true;
                                    }
                                } catch (Throwable ignored) {}
                            }

                            if (!foiDisputada) continue;

                            // Chave única absoluta por jogo (Clubes + Data Oficial da Rodada)
                            String dataPartidaStr = formatCalendar(dataRodada);
                            String chave = homeName.trim().toLowerCase() + "_x_" + awayName.trim().toLowerCase() + "_" + dataPartidaStr;

                            // Prevenção estrita de duplicidade via objeto JVM e chave canônica
                            if (partidasRegistradasObjetos.contains(matchObj) || partidasRegistradasChaves.contains(chave)) {
                                continue; // Já contabilizada anteriormente
                            }
                            partidasRegistradasObjetos.add(matchObj);
                            partidasRegistradasChaves.add(chave);

                            // Recalcula e consolida os gols
                            try {
                                Method hfMethod = matchObj.getClass().getMethod("hF");
                                hfMethod.invoke(matchObj);
                            } catch (Throwable ignored) {}

                            int golsCasa = 0;
                            int golsFora = 0;
                            try {
                                golsCasa = (Integer) matchObj.getClass().getMethod("hu").invoke(matchObj);
                            } catch (Throwable ignored) {}
                            try {
                                golsFora = (Integer) matchObj.getClass().getMethod("hw").invoke(matchObj);
                            } catch (Throwable ignored) {}

                            partidasDisputadas++;
                            int meusGols = souCasa ? golsCasa : golsFora;
                            int golsAdv = souCasa ? golsFora : golsCasa;
                            golsPro += meusGols;
                            golsContra += golsAdv;

                            String tag;
                            if (meusGols > golsAdv) {
                                vitorias++;
                                tag = "🟢 V";
                            } else if (meusGols == golsAdv) {
                                empates++;
                                tag = "🟡 E";
                            } else {
                                derrotas++;
                                tag = "🔴 D";
                            }

                            String registro = String.format("%s: %s %d x %d %s", tag, homeName, golsCasa, golsFora, awayName);
                            historicoPartidas.add(registro);
                            String logMsg = String.format("[MOD FÉRIAS] ⚽ Jogo Sincronizado do Calendário Oficial: %s (%s) [Total: %d]", registro, dataPartidaStr, partidasDisputadas);
                            System.out.println(logMsg);
                            logToFile(logMsg);
                        } catch (Throwable tP) {
                            logToFile("[MOD FÉRIAS] Erro em partida de R(): " + tP.getMessage());
                        }
                    }
                } catch (Throwable tR) {
                    logToFile("[MOD FÉRIAS] Erro em rodada de R(): " + tR.getMessage());
                }
            }
        } catch (Throwable t) {
            logToFile("[MOD FÉRIAS] Erro em sincronizarHistoricoComCalendarioOficial: " + t.toString());
        }
    }

    /**
     * Intercepta o término de uma partida simulada e sincroniza imediatamente
     */
    public static synchronized void registrarResultadoPartida(Object match) {
        sincronizarHistoricoComCalendarioOficial();
    }

    /**
     * Scanner universal de componentes interativos (JButtons, JLabels com ouvintes de clique)
     * Opera com normalização avançada (ignora acentos, caixa alta/baixa, espaços extras e símbolos como >>)
     */
    private static boolean tratarContainerGenerico(Container container) {
        // Se este contêiner contiver o painel pré-jogo (a.bz), delega para a lógica especializada
        Component bz = findBzScreen(container);
        if (bz != null) {
            return executarPreJogoBz(bz);
        }

        String[][] prioridades = {
                {"nao ofertar"},
                {"ver proximo leilao"},
                {"continuar jogo"},
                rejeitarPropostas ? new String[]{"rejeitar todos", "recusar proposta", "rejeitar", "recusar"} : new String[]{},
                {"continuar"},
                {"jogar"},
                {"escalar time"},
                {"iniciar novo ano"},
                {"ok", "confirmar", "fechar", "sim", "voltar"}
        };

        for (String[] grupo : prioridades) {
            for (String alvo : grupo) {
                if (alvo.isEmpty()) continue;
                Component comp = findClickableComponent(container, alvo);
                if (comp != null) {
                    System.out.println("[MOD FÉRIAS] 🖱️ Componente interativo acionado: '" + alvo + "'...");
                    clicarComponente(comp);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Busca recursiva por componentes com texto correspondente ao padrão normalizado
     */
    public static Component findClickableComponent(Container container, String targetNormalized) {
        if (container == null) return null;
        for (Component c : container.getComponents()) {
            if (!c.isShowing() || !c.isEnabled()) continue;

            String txt = "";
            if (c instanceof AbstractButton) {
                txt = ((AbstractButton) c).getText();
            } else if (c instanceof JLabel) {
                txt = ((JLabel) c).getText();
            }

            if (txt != null && !txt.trim().isEmpty()) {
                String norm = normalizar(txt);
                if (norm.equals(targetNormalized) || norm.contains(targetNormalized)) {
                    return c;
                }
            }

            if (c instanceof Container) {
                Component child = findClickableComponent((Container) c, targetNormalized);
                if (child != null) return child;
            }
        }
        return null;
    }

    /**
     * Executa o clique de forma robusta seja em AbstractButton (doClick) ou JLabel (MouseEvent dispatch)
     */
    public static boolean clicarComponente(Component comp) {
        if (comp == null || !comp.isShowing() || !comp.isEnabled()) return false;

        if (comp instanceof AbstractButton) {
            ((AbstractButton) comp).doClick();
            return true;
        }

        boolean clicou = false;
        long time = System.currentTimeMillis();
        int x = comp.getWidth() > 0 ? comp.getWidth() / 2 : 1;
        int y = comp.getHeight() > 0 ? comp.getHeight() / 2 : 1;

        for (MouseListener ml : comp.getMouseListeners()) {
            try {
                MouseEvent mePress = new MouseEvent(comp, MouseEvent.MOUSE_PRESSED, time, 0, x, y, 1, false);
                MouseEvent meRel = new MouseEvent(comp, MouseEvent.MOUSE_RELEASED, time + 1, 0, x, y, 1, false);
                MouseEvent meClick = new MouseEvent(comp, MouseEvent.MOUSE_CLICKED, time + 2, 0, x, y, 1, false);
                ml.mousePressed(mePress);
                ml.mouseReleased(meRel);
                ml.mouseClicked(meClick);
                clicou = true;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        return clicou;
    }

    /**
     * Normaliza strings removendo diacríticos/acentos, símbolos, pontuações e excesso de espaços
     */
    public static String normalizar(String s) {
        if (s == null) return "";
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        return temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .replaceAll("[>><<\\.,;:\\-_\\(\\)\\[\\]\\{\\}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Retorna a instância global de c.a.SR do save do Brasfoot
     */
    public static Object getSRObject() {
        try {
            ClassLoader cl = BrasfootAgent.getGameClassLoader();
            Class<?> cAClass = Class.forName("c.a", true, cl);
            Field srField = cAClass.getDeclaredField("SR");
            srField.setAccessible(true);
            return srField.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Retorna a data exata do calendário interno do jogo a partir de c.a.SR.bb()
     */
    public static Calendar getCurrentGameCalendar() {
        try {
            Object srObj = getSRObject();
            if (srObj == null) return null;

            Method bbMethod = srObj.getClass().getMethod("bb");
            Object res = bbMethod.invoke(srObj);
            if (res instanceof Calendar) {
                return (Calendar) ((Calendar) res).clone();
            }
        } catch (Throwable t) {
            // Ignorado
        }
        return null;
    }

    /**
     * Retorna a rodada/semana atual do jogo a partir de c.a.SR.H()
     */
    public static int getCurrentRound() {
        try {
            Object srObj = getSRObject();
            if (srObj == null) return 1;

            Method hMethod = srObj.getClass().getMethod("H");
            return (Integer) hMethod.invoke(srObj);
        } catch (Throwable t) {
            return 1;
        }
    }

    /**
     * Retorna o nome do clube controlado pelo jogador humano
     */
    /**
     * Retorna o objeto do clube humano (best.ah)
     */
    public static Object getHumanClubObject() {
        try {
            Object srObj = getSRObject();
            if (srObj == null) return null;

            try {
                Method lMethod = srObj.getClass().getMethod("L");
                List<?> coaches = (List<?>) lMethod.invoke(srObj);
                if (coaches != null) {
                    for (Object coach : coaches) {
                        Method isHuman = coach.getClass().getMethod("jZ");
                        Boolean human = (Boolean) isHuman.invoke(coach);
                        if (Boolean.TRUE.equals(human)) {
                            Method getClub = coach.getClass().getMethod("fg");
                            return getClub.invoke(coach);
                        }
                    }
                }
            } catch (Throwable ignored) {}

            try {
                Method pMethod = srObj.getClass().getMethod("P");
                List<?> clubs = (List<?>) pMethod.invoke(srObj);
                if (clubs != null) {
                    for (Object club : clubs) {
                        Method isHuman = club.getClass().getMethod("jZ");
                        Boolean human = (Boolean) isHuman.invoke(club);
                        if (Boolean.TRUE.equals(human)) {
                            return club;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Verifica se uma competição é Campeonato Estadual (best.at com b() == 3 ou classe f.o)
     */
    public static boolean isEstadual(Object comp) {
        if (comp == null) return false;
        try {
            Method bMethod = comp.getClass().getMethod("b");
            int tipo = ((Number) bMethod.invoke(comp)).intValue();
            if (tipo == 3) return true;
        } catch (Throwable ignored) {}

        String name = comp.getClass().getName();
        return name.equals("f.o") || name.endsWith(".o") || name.endsWith("$o");
    }

    /**
     * Verifica se a competição estadual pertence ao mesmo estado do clube fornecido
     */
    public static boolean isEstadualDoClube(Object comp, Object clube) {
        if (!isEstadual(comp)) return false;
        if (clube == null) return true;
        try {
            Method getEstClube = clube.getClass().getMethod("getEstado");
            int estClube = ((Number) getEstClube.invoke(clube)).intValue();

            try {
                Method ggMethod = comp.getClass().getMethod("gg");
                int estComp = ((Number) ggMethod.invoke(comp)).intValue();
                if (estComp >= 0) return estComp == estClube;
            } catch (Throwable ignored) {}

            try {
                Method ykMethod = comp.getClass().getMethod("yk");
                int estComp = ((Number) ykMethod.invoke(comp)).intValue();
                if (estComp >= 0) return estComp == estClube;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return true;
    }

    public static boolean isAfterDay(Calendar c1, Calendar c2) {
        if (c1 == null || c2 == null) return false;
        if (c1.get(Calendar.YEAR) > c2.get(Calendar.YEAR)) return true;
        if (c1.get(Calendar.YEAR) < c2.get(Calendar.YEAR)) return false;
        return c1.get(Calendar.DAY_OF_YEAR) > c2.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Retorna a data da rodada final do Campeonato Estadual DO CLUBE HUMANO na temporada atual.
     * Foca estritamente no Estadual que o clube do jogador disputa (f.o e federação do estado).
     */
    public static Calendar getDataFimEstadual() {
        try {
            Object sr = getSRObject();
            if (sr == null) return null;

            // 1. Verifica flag do save
            try {
                Method jogaEstMethod = sr.getClass().getMethod("isJogaEstadual");
                Boolean jogaEst = (Boolean) jogaEstMethod.invoke(sr);
                if (Boolean.FALSE.equals(jogaEst)) {
                    return null;
                }
            } catch (Throwable ignored) {}

            Object meuClube = getHumanClubObject();
            if (meuClube == null) return null;

            Method rMethod = sr.getClass().getMethod("R");
            List<?> todasRodadas = (List<?>) rMethod.invoke(sr);
            if (todasRodadas == null || todasRodadas.isEmpty()) return null;

            Calendar ultimaDataEstadualClube = null;
            int ultimaRodadaIdx = -1;

            // 1. Encontra a última rodada da fase inicial do estadual do clube humano
            for (int i = 0; i < todasRodadas.size(); i++) {
                Object rodada = todasRodadas.get(i);
                if (rodada == null) continue;
                Method aMethod = rodada.getClass().getMethod("a");
                Calendar dataRodada = (Calendar) aMethod.invoke(rodada);
                if (dataRodada == null) continue;

                Method hMethod = rodada.getClass().getMethod("h");
                List<?> partidas = (List<?>) hMethod.invoke(rodada);
                if (partidas == null || partidas.isEmpty()) continue;

                for (Object match : partidas.toArray()) {
                    if (match == null) continue;
                    Method hcMethod = match.getClass().getMethod("hc");
                    Method hdMethod = match.getClass().getMethod("hd");
                    Object home = hcMethod.invoke(match);
                    Object away = hdMethod.invoke(match);

                    if (home == meuClube || away == meuClube) {
                        Method hyMethod = match.getClass().getMethod("hy");
                        Object comp = hyMethod.invoke(match);
                        if (isEstadualDoClube(comp, meuClube)) {
                            if (ultimaDataEstadualClube == null || dataRodada.after(ultimaDataEstadualClube)) {
                                ultimaDataEstadualClube = (Calendar) dataRodada.clone();
                                ultimaRodadaIdx = i;
                            }
                        }
                    }
                }
            }

            if (ultimaDataEstadualClube == null) return null;

            // 2. Calcula datas adicionais de mata-mata se houver playoff no estadual
            int datasPlayoff = 0;
            try {
                int estId = ((Number) meuClube.getClass().getMethod("getEstado").invoke(meuClube)).intValue();
                List<?> fedList = (List<?>) sr.getClass().getMethod("aE").invoke(sr);
                if (fedList != null) {
                    for (Object f : fedList) {
                        int fEst = ((Number) f.getClass().getMethod("getEstado").invoke(f)).intValue();
                        if (fEst == estId) {
                            Field dsField = f.getClass().getDeclaredField("ds");
                            dsField.setAccessible(true);
                            List<?> divs = (List<?>) dsField.get(f);
                            if (divs != null && !divs.isEmpty()) {
                                Object div = divs.get(0);
                                Object sObj = div.getClass().getMethod("yi").invoke(div);
                                int nTimesMataMata = ((Number) sObj.getClass().getMethod("yX").invoke(sObj)).intValue();
                                Field dvField = sObj.getClass().getDeclaredField("duasVoltasMataMata");
                                dvField.setAccessible(true);
                                boolean[] dv = (boolean[]) dvField.get(sObj);

                                if (nTimesMataMata == 2) {
                                    datasPlayoff = (dv != null && dv.length > 2 && dv[2]) ? 2 : 1;
                                } else if (nTimesMataMata == 4) {
                                    int dSemi = (dv != null && dv.length > 1 && dv[1]) ? 2 : 1;
                                    int dFinal = (dv != null && dv.length > 2 && dv[2]) ? 2 : 1;
                                    datasPlayoff = dSemi + dFinal;
                                } else if (nTimesMataMata == 8) {
                                    int dQuartas = (dv != null && dv.length > 0 && dv[0]) ? 2 : 1;
                                    int dSemi = (dv != null && dv.length > 1 && dv[1]) ? 2 : 1;
                                    int dFinal = (dv != null && dv.length > 2 && dv[2]) ? 2 : 1;
                                    datasPlayoff = dQuartas + dSemi + dFinal;
                                }
                            }
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 3. Se houver mata-mata, avança as datas oficiais de jogos (domingos e terças/quintas)
            if (datasPlayoff > 0 && ultimaRodadaIdx >= 0) {
                int datasEncontradas = 0;
                for (int i = ultimaRodadaIdx + 1; i < todasRodadas.size(); i++) {
                    Object rodada = todasRodadas.get(i);
                    Calendar dataRodada = (Calendar) rodada.getClass().getMethod("a").invoke(rodada);
                    int dow = dataRodada.get(Calendar.DAY_OF_WEEK);
                    if (dow == Calendar.SUNDAY || dow == Calendar.TUESDAY || dow == Calendar.THURSDAY) {
                        datasEncontradas++;
                        if (datasEncontradas == datasPlayoff) {
                            return (Calendar) dataRodada.clone();
                        }
                    }
                }
            }

            return ultimaDataEstadualClube;
        } catch (Throwable t) {
            logToFile("[MOD FÉRIAS] Erro ao buscar data de fim do estadual: " + t.toString());
            return null;
        }
    }

    /**
     * Trava de segurança: verifica se o clube humano já concluiu o estadual e está prestes a disputar competição nacional
     */
    public static boolean isProximaPartidaNaoEstadual(Calendar dataAtual) {
        try {
            Calendar dataFimEst = getDataFimEstadual();
            // Se a data do jogo já é posterior à final do estadual, encerra imediatamente!
            if (dataFimEst != null && dataAtual != null) {
                if (isAfterDay(dataAtual, dataFimEst)) {
                    logToFile("[MOD FÉRIAS] 🛑 Data atual (" + formatCalendar(dataAtual) + ") ultrapassou o fim do estadual (" + formatCalendar(dataFimEst) + "). Fim do Estadual!");
                    return true;
                }
            }

            Object srObj = getSRObject();
            if (srObj == null) return false;

            Object meuClube = getHumanClubObject();
            if (meuClube == null) return false;

            // Verifica partidas da rodada selecionada no momento (S)
            Method sMethod = srObj.getClass().getMethod("S");
            List<?> partidasRodada = (List<?>) sMethod.invoke(srObj);
            if (partidasRodada != null && !partidasRodada.isEmpty()) {
                for (Object match : partidasRodada.toArray()) {
                    if (match == null) continue;
                    Method hcMethod = match.getClass().getMethod("hc");
                    Method hdMethod = match.getClass().getMethod("hd");
                    Object home = hcMethod.invoke(match);
                    Object away = hdMethod.invoke(match);
                    if (home == meuClube || away == meuClube) {
                        Method hyMethod = match.getClass().getMethod("hy");
                        Object comp = hyMethod.invoke(match);
                        if (comp != null) {
                            // Se a competição do jogo NÃO for Estadual
                            if (!isEstadual(comp)) {
                                logToFile("[MOD FÉRIAS] 🛑 Próxima partida do clube é de competição nacional (" + comp.getClass().getSimpleName() + "). Fim do Estadual!");
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logToFile("[MOD FÉRIAS] Erro em isProximaPartidaNaoEstadual: " + t.toString());
        }
        return false;
    }

    public static String getHumanClubName() {
        try {
            Object srObj = getSRObject();
            if (srObj == null) return "Seu Clube";

            // 1. Tenta encontrar pelo treinador humano em L()
            try {
                Method lMethod = srObj.getClass().getMethod("L");
                List<?> coaches = (List<?>) lMethod.invoke(srObj);
                if (coaches != null) {
                    for (Object coach : coaches) {
                        Method isHuman = coach.getClass().getMethod("jZ");
                        Boolean human = (Boolean) isHuman.invoke(coach);
                        if (Boolean.TRUE.equals(human)) {
                            Method getClub = coach.getClass().getMethod("fg");
                            Object club = getClub.invoke(coach);
                            if (club != null) {
                                String nome = (String) club.getClass().getMethod("getNome").invoke(club);
                                if (nome != null && !nome.isEmpty()) return nome;
                            } else {
                                return "Desempregado";
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 2. Fallback: Procura na lista de todos os clubes P() aquele com jZ() == true
            try {
                Method pMethod = srObj.getClass().getMethod("P");
                List<?> clubes = (List<?>) pMethod.invoke(srObj);
                if (clubes != null) {
                    for (Object club : clubes) {
                        Method jzClub = club.getClass().getMethod("jZ");
                        Boolean humanClub = (Boolean) jzClub.invoke(club);
                        if (Boolean.TRUE.equals(humanClub)) {
                            String nome = (String) club.getClass().getMethod("getNome").invoke(club);
                            if (nome != null && !nome.isEmpty()) return nome;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
        return "Seu Clube";
    }

    public static String formatCalendar(Calendar cal) {
        if (cal == null) return "--/--/----";
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        return sdf.format(cal.getTime());
    }

    public static JButton findButtonByText(Container container, String[] targets) {
        for (String target : targets) {
            String normTarget = normalizar(target);
            Component c = findClickableComponent(container, normTarget);
            if (c instanceof JButton) {
                return (JButton) c;
            }
        }
        return null;
    }
}
