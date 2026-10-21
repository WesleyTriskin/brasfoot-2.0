package br.com.brasfootmod;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import br.com.brasfootmod.vacation.BrasfootVacationController;
import br.com.brasfootmod.vacation.BrasfootVacationDialog;

/**
 * Java Agent para o Brasfoot 22-23.
 * - Simulação Instantânea de Partidas (com auto-intervalo).
 * - Filtro de Busca por Nota Média (<6.5, 6.5-7.5, >7.5).
 * - Menus In-Game e Painel de Estatísticas.
 */
public class BrasfootAgent {


    private static final String GAME_PATH = "C:\\Brasfoot22-23";
    private static boolean modoSimulacaoInstantanea = false;

    public static boolean isModoSimulacaoInstantanea() {
        return modoSimulacaoInstantanea;
    }
    private static boolean simulandoAgora = false;
    public static volatile ClassLoader gameClassLoader = null;

    public static ClassLoader getGameClassLoader() {
        if (gameClassLoader != null) return gameClassLoader;
        for (Window w : Window.getWindows()) {
            if (w == null) continue;
            ClassLoader cl = findClassLoaderFromComponent(w);
            if (cl != null) {
                gameClassLoader = cl;
                return cl;
            }
        }
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        if (ccl != null) {
            try {
                Class.forName("c.a", false, ccl);
                gameClassLoader = ccl;
                return ccl;
            } catch (Throwable ignored) {}
        }
        return BrasfootAgent.class.getClassLoader();
    }

    private static ClassLoader findClassLoaderFromComponent(Component comp) {
        if (comp == null) return null;
        ClassLoader cl = comp.getClass().getClassLoader();
        if (cl != null) {
            try {
                Class.forName("c.a", false, cl);
                return cl;
            } catch (Throwable ignored) {}
        }
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                ClassLoader childCl = findClassLoaderFromComponent(child);
                if (childCl != null) return childCl;
            }
        }
        return null;
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("==================================================");
        System.out.println("  ⚽ Brasfoot 22-23 Mod Agent Inicializado! ⚽");
        System.out.println("==================================================");

        // 0. Instala o Bytecode Transformer para thread-safety e proteção anti-NPE (best.S, a.ho, a.cS)
        try {
            inst.addTransformer(new br.com.brasfootmod.patch.BrasfootClassTransformer(), true);
            System.out.println("[MOD] 🛡️ BrasfootClassTransformer registrado com sucesso!");
        } catch (Throwable t) {
            System.err.println("[MOD] Erro ao registrar transformer: " + t.getMessage());
            t.printStackTrace();
        }

        // 1. Hook de Teclado Global:
        //    F2 -> Painel de Estatísticas
        //    F8 -> Finalizar Partida Agora
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                if (e.getKeyCode() == KeyEvent.VK_F4) {
                    abrirDialogoFerias();
                    return true;
                } else if (e.getKeyCode() == KeyEvent.VK_F8) {
                    executarFinalizacaoManual();
                    return true;
                }
            }
            return false;
        });

        // 2. Injeta Menu e Filtros nas janelas e caixas de diálogo do Brasfoot
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == WindowEvent.WINDOW_OPENED) {
                WindowEvent we = (WindowEvent) event;
                Window win = we.getWindow();
                if (win instanceof JDialog) {
                    JDialog dlg = (JDialog) win;
                    if (simulandoAgora || modoSimulacaoInstantanea || br.com.brasfootmod.vacation.BrasfootVacationController.isEmFerias()) {
                        if (interceptarEFecharPenaltiModal(dlg)) {
                            return;
                        }
                        if (interceptarEFecharIntervaloModal(dlg)) {
                            return;
                        }
                    }
                    injectIntoDialog(dlg);
                } else if (win instanceof JFrame) {
                    injectIntoFrame((JFrame) win);
                }
            }
        }, AWTEvent.WINDOW_EVENT_MASK);

        // 3. Monitor em Tempo Real para Simulação Instantânea e Telas do Jogo (a cada 50ms)
        new javax.swing.Timer(50, e -> {
            // Injeta temporadas 2026 e 2027 na tela de configurações de novo jogo (a.hU)
            verificarEInjetarTelaInicioHU();

            // Injeta Melhor da Partida (MVP) na tela de pós-jogo (a.hk)
            verificarEInjetarMelhorDaPartidaHk();

            // Monitor e recuperação de fim de temporada em a.hk (virada de ano e tela 6)
            verificarERecuperarFimDeTemporadaHk();

            // Injeta Inteligência de Mercado / Scout (Opção 1 - Passiva via Caixa de Entrada)
            try {
                br.com.brasfootmod.scout.ScoutController.checkPeriodicScoutUpdate();
            } catch (Throwable ignored) {}

            // Injeta Sugestão de Formação Tática (Assistente Técnico - Janeiro e Julho via Inbox)
            try {
                br.com.brasfootmod.tactics.TacticalAnalysisController.checkPeriodicTacticalReport();
            } catch (Throwable ignored) {}

            // Injeta Botão 'Rotacionar Elenco' na tela de escolha do time/escalação (a.bz)
            try {
                br.com.brasfootmod.lineup.LineupRotationController.checkAndInjectRotationButton();
            } catch (Throwable ignored) {}

            // Injeta Objetivos de Temporada & Cobrança da Diretoria (Janeiro, Julho e Dezembro via Inbox)
            try {
                br.com.brasfootmod.goals.SeasonGoalsController.checkPeriodicGoalsUpdate();
            } catch (Throwable ignored) {}

            // Garante que menus e botões de atualização estejam sempre injetados e ativos
            try {
                verificarEInjetarJanelasAbertas();
            } catch (Throwable ignored) {}

            boolean autoSimAtiva = modoSimulacaoInstantanea || br.com.brasfootmod.vacation.BrasfootVacationController.isEmFerias();

            Object match = findActiveMatch();
            if (match instanceof JComponent) {
                final JComponent compMatch = (JComponent) match;
                if (compMatch.getClientProperty("ModFimDeJogoClickListener") == null) {
                    compMatch.putClientProperty("ModFimDeJogoClickListener", Boolean.TRUE);
                    compMatch.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if (isMatchFinished(compMatch)) {
                                br.com.brasfootmod.vacation.BrasfootVacationController.avancarAposPartida(compMatch);
                            }
                        }
                    });
                }
            }

            if (autoSimAtiva) {
                // Durante Simulação Instantânea Automática OU Modo Férias:
                verificarEProcessarPenaltisInstantaneo();
                Object halftime = findActiveHalftimeDialog();

                if (halftime != null) {
                    fecharIntervalo(halftime);
                } else if (match != null) {
                    if (!isMatchFinished(match)) {
                        simularPartidaCompleta(match);
                    } else {
                        br.com.brasfootmod.vacation.BrasfootVacationController.avancarAposPartida(match);
                    }
                }
            } else {
                verificarEDestravarPenaltisOrfaos();
            }

            if (!simulandoAgora && !autoSimAtiva) {
                // Em partidas normais jogadas pelo usuário, garante que o intervalo está ATIVADO nativamente (verJanelaSubs = 1)
                // e que o modo rápido nativo (bK) está DESLIGADO (bK = false)
                // para que o jogo pause aos 45 minutos e abra a tela de formação/substituição (a.cD) normalmente sem fechar
                try {
                    Object sr = br.com.brasfootmod.vacation.BrasfootVacationController.getSRObject();
                    if (sr != null) {
                        Method getSubs = sr.getClass().getMethod("getVerJanelaSubs");
                        int val = ((Number) getSubs.invoke(sr)).intValue();
                        if (val != 1) {
                            Method setSubs = sr.getClass().getMethod("setVerJanelaSubs", int.class);
                            setSubs.invoke(sr, 1);
                        }

                        Method bDMethod = sr.getClass().getMethod("bD");
                        boolean bKVal = ((Boolean) bDMethod.invoke(sr)).booleanValue();
                        if (bKVal) {
                            Method hMethod = sr.getClass().getMethod("h", boolean.class);
                            hMethod.invoke(sr, false);
                        }
                    }

                    // Sincroniza também c.a.vM() (Options global do Brasfoot)
                    ClassLoader cl = getGameClassLoader();
                    if (cl != null) {
                        Class<?> cAClass = Class.forName("c.a", true, cl);
                        Method vMMethod = cAClass.getMethod("vM");
                        Object vm = vMMethod.invoke(null);
                        if (vm != null) {
                            Method getOptSubs = vm.getClass().getMethod("getVerJanelaSubs");
                            int optVal = ((Number) getOptSubs.invoke(vm)).intValue();
                            if (optVal != 1) {
                                Method setOptSubs = vm.getClass().getMethod("setVerJanelaSubs", int.class);
                                setOptSubs.invoke(vm, 1);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    /**
     * Detecta a tela de configurações iniciais de novo jogo (a.hU) e injeta os anos 2026 e 2027
     */
    
    /**
     * Detecta a tela de pós-jogo (a.hk) e injeta o Melhor da Partida (MVP)
     */
    public static void verificarEInjetarMelhorDaPartidaHk() {
        for (Window w : Window.getWindows()) {
            if (w == null || !w.isShowing()) continue;
            Component hkComp = findComponentBySuffix(w, ".hk");
            if (hkComp == null || !hkComp.isShowing()) continue;
            try {
                br.com.brasfootmod.rating.MatchMvpController.injectMvpUi(hkComp);
            } catch (Throwable ignored) {}
        }
    }

    private static long tempoInicioWaitCursor = 0;

    /**
     * Monitor e recuperação automática para tela de pós-jogo no fim de temporada (a.hk).
     * Evita congelamento no último jogo do ano (ex: Mundial de Clubes) e
     * garante a transição segura para a tela de Premiação (a.cR - tela 6) e novo ano.
     */
    public static void verificarERecuperarFimDeTemporadaHk() {
        for (Window w : Window.getWindows()) {
            if (w == null || !w.isShowing()) continue;
            Component hkComp = findComponentBySuffix(w, ".hk");
            if (hkComp == null || !hkComp.isShowing()) continue;

            try {
                Object sr = br.com.brasfootmod.vacation.BrasfootVacationController.getSRObject();
                if (sr == null) continue;

                Method azMethod = sr.getClass().getMethod("az");
                int restantes = ((Number) azMethod.invoke(sr)).intValue();

                Field zkField = hkComp.getClass().getDeclaredField("zK");
                zkField.setAccessible(true);
                JButton btnContinuar = (JButton) zkField.get(hkComp);

                if (restantes == 0) {
                    if (btnContinuar != null && !"ModFimTemporadaHkHook".equals(btnContinuar.getName())) {
                        btnContinuar.setName("ModFimTemporadaHkHook");

                        btnContinuar.addActionListener(e -> {
                            System.out.println("[MOD] 🏆 Botão 'Continuar >>' acionado no FIM DA TEMPORADA! Executando transição protegida para tela 6...");
                            br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] 🏆 Botão 'Continuar >>' acionado no FIM DA TEMPORADA! Executando transição protegida para tela 6...");
                            SwingUtilities.invokeLater(() -> executarTransicaoSeguraFimTemporada(sr));
                        });
                    }

                    if (btnContinuar != null && btnContinuar.getCursor() != null &&
                        btnContinuar.getCursor().getType() == Cursor.WAIT_CURSOR) {

                        long now = System.currentTimeMillis();
                        if (tempoInicioWaitCursor == 0) {
                            tempoInicioWaitCursor = now;
                        } else if (now - tempoInicioWaitCursor > 1200) {
                            System.out.println("[MOD] ⚠️ Jogo travado em a.hk com WAIT_CURSOR no fim de temporada! Destravando e forçando avanço para Premiação...");
                            br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] ⚠️ Destravando a.hk (WAIT_CURSOR detectado) e avançando para premiação...");
                            tempoInicioWaitCursor = 0;
                            btnContinuar.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                            executarTransicaoSeguraFimTemporada(sr);
                        }
                    } else {
                        tempoInicioWaitCursor = 0;
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Executa com segurança total a virada de temporada (al, afQ.zw e transição para tela 6)
     */
    public static void executarTransicaoSeguraFimTemporada(Object sr) {
        if (sr == null) return;
        try {
            System.out.println("[MOD] 🏆 Iniciando transição segura de virada de ano...");
            br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] 🏆 Iniciando transição segura de virada de ano...");

            // 1. Marca bk = true (temporada finalizada)
            try {
                Field bkField = sr.getClass().getDeclaredField("bk");
                bkField.setAccessible(true);
                bkField.setBoolean(sr, true);
            } catch (Throwable ignored) {}

            // 2. Chama premiações de técnicos humanos (c.a.afQ.zw())
            try {
                ClassLoader cl = getGameClassLoader();
                Class<?> caClass = Class.forName("c.a", true, cl);
                Field afQField = caClass.getDeclaredField("afQ");
                afQField.setAccessible(true);
                Object afQ = afQField.get(null);
                if (afQ != null) {
                    Method zwMethod = afQ.getClass().getMethod("zw");
                    zwMethod.invoke(afQ);
                }
            } catch (Throwable t) {
                System.err.println("[MOD] Aviso em afQ.zw: " + t.getMessage());
            }

            // 3. Executa al() (reseta o ano, atualiza idades/contratos e gera novos calendários)
            try {
                Method alMethod = sr.getClass().getMethod("al");
                alMethod.invoke(sr);
                System.out.println("[MOD] ✅ Temporada resetada com sucesso via al()!");
            } catch (Throwable t) {
                System.err.println("[MOD] Erro em al(): " + t.getMessage());
                t.printStackTrace();
            }

            // 4. Abre a tela de premiação e troféus (tela 6 = a.cR)
            try {
                ClassLoader cl = getGameClassLoader();
                Class<?> sClass = Class.forName("best.S", true, cl);
                Method ayMethod = sClass.getMethod("aY", int.class);
                ayMethod.invoke(null, 6);
                System.out.println("[MOD] 🏆 Tela de premiação 6 (a.cR) aberta com sucesso!");
            } catch (Throwable t) {
                System.err.println("[MOD] Erro ao abrir tela 6: " + t.getMessage());
                t.printStackTrace();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void verificarEInjetarTelaInicioHU() {
        for (Window w : Window.getWindows()) {
            if (w == null || !w.isShowing()) continue;
            Component huComp = findComponentBySuffix(w, ".hU");
            if (huComp == null) continue;

            if ("ModInjected_HU_2026".equals(huComp.getName())) {
                continue;
            }
            huComp.setName("ModInjected_HU_2026");

            try {
                System.out.println("[MOD] 📅 Tela de Configurações Iniciais (a.hU) detectada! Injetando temporadas 2026 e 2027...");
                br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] 📅 Tela de Configurações Iniciais (a.hU) detectada! Injetando temporadas 2026 e 2027...");

                Field afAField = huComp.getClass().getDeclaredField("afA");
                afAField.setAccessible(true);
                JComboBox<?> afA = (JComboBox<?>) afAField.get(huComp);

                Field bwField = huComp.getClass().getDeclaredField("Bw");
                bwField.setAccessible(true);
                JButton btnIniciar = (JButton) bwField.get(huComp);

                Field afzField = huComp.getClass().getDeclaredField("afz");
                afzField.setAccessible(true);
                JCheckBox chkCopaMundo = (JCheckBox) afzField.get(huComp);

                Field miField = huComp.getClass().getDeclaredField("Mi");
                miField.setAccessible(true);
                JCheckBox chkEstadual = (JCheckBox) miField.get(huComp);

                Field mjField = huComp.getClass().getDeclaredField("Mj");
                mjField.setAccessible(true);
                JCheckBox chkIntClubes = (JCheckBox) mjField.get(huComp);

                Field mhField = huComp.getClass().getDeclaredField("Mh");
                mhField.setAccessible(true);
                JCheckBox chkSelecoes = (JCheckBox) mhField.get(huComp);

                if (afA != null) {
                    DefaultComboBoxModel model = (DefaultComboBoxModel) afA.getModel();
                    model.removeAllElements();
                    model.addElement("2026");
                    model.addElement("2027");
                    afA.setSelectedItem("2026");

                    afA.addItemListener(e -> {
                        if (e.getStateChange() == ItemEvent.SELECTED) {
                            String sel = String.valueOf(afA.getSelectedItem());
                            if ("2026".equals(sel)) {
                                if (chkCopaMundo != null) {
                                    chkCopaMundo.setText("Usar grupos da Copa do Mundo 2026");
                                    chkCopaMundo.setEnabled(true);
                                    chkCopaMundo.setSelected(true);
                                }
                            } else if ("2027".equals(sel)) {
                                if (chkCopaMundo != null) {
                                    chkCopaMundo.setText("Usar grupos da Copa do Mundo (Sem Copa em 2027)");
                                    chkCopaMundo.setSelected(false);
                                    chkCopaMundo.setEnabled(false);
                                }
                            } else if ("2023".equals(sel)) {
                                if (chkCopaMundo != null) {
                                    chkCopaMundo.setText("Usar grupos da Copa do Mundo (Sem Copa em 2023)");
                                    chkCopaMundo.setSelected(false);
                                    chkCopaMundo.setEnabled(false);
                                }
                            } else {
                                if (chkCopaMundo != null) {
                                    chkCopaMundo.setText("Usar grupos da Copa do Mundo 2022");
                                    chkCopaMundo.setEnabled(true);
                                }
                            }
                        }
                    });

                    if (chkCopaMundo != null) {
                        chkCopaMundo.setText("Usar grupos da Copa do Mundo 2026");
                        chkCopaMundo.setEnabled(true);
                        chkCopaMundo.setSelected(true);
                    }
                    if (chkIntClubes != null) {
                        chkIntClubes.setSelected(true);
                    }
                    if (chkSelecoes != null) {
                        chkSelecoes.setSelected(true);
                    }
                }

                if (btnIniciar != null) {
                    for (ActionListener al : btnIniciar.getActionListeners()) {
                        btnIniciar.removeActionListener(al);
                    }

                    btnIniciar.addActionListener(ev -> {
                        btnIniciar.setCursor(new Cursor(Cursor.WAIT_CURSOR));
                        String anoStr = afA != null ? String.valueOf(afA.getSelectedItem()) : "2026";
                        int anoInicio = 2026;
                        try {
                            anoInicio = Integer.parseInt(anoStr);
                        } catch (Throwable ignored) {
                            anoInicio = 2026;
                        }

                        try {
                            ClassLoader cl = huComp.getClass().getClassLoader();
                        if (cl != null) gameClassLoader = cl;
                        else cl = getGameClassLoader();
                            Class<?> caClass = Class.forName("c.a", true, cl);
                            Field srField = caClass.getDeclaredField("SR");
                            srField.setAccessible(true);
                            Object sr = srField.get(null);

                            // 1. Define o ano de início no save
                            Method iMethod = sr.getClass().getMethod("I", int.class);
                            iMethod.invoke(sr, anoInicio);

                            // 2. Define anoinicio no aq
                            Class<?> aqClass = Class.forName("best.aq", true, cl);
                            Method fMethod = aqClass.getMethod("f", String.class, int.class);
                            if (anoInicio == 2023) {
                                fMethod.invoke(null, "anoinicio", 1);
                            } else if (anoInicio == 2026) {
                                fMethod.invoke(null, "anoinicio", 2);
                            } else if (anoInicio == 2027) {
                                fMethod.invoke(null, "anoinicio", 3);
                            } else {
                                fMethod.invoke(null, "anoinicio", 0);
                            }

                            // 3. Define opções selecionadas
                            if (chkEstadual != null) {
                                Method jogaEst = sr.getClass().getMethod("setJogaEstadual", boolean.class);
                                jogaEst.invoke(sr, chkEstadual.isSelected());
                            }
                            if (chkIntClubes != null) {
                                Method jogaInt = sr.getClass().getMethod("setJogaIntClubes", boolean.class);
                                jogaInt.invoke(sr, chkIntClubes.isSelected());
                            }
                            if (chkSelecoes != null) {
                                Method jogaSel = sr.getClass().getMethod("setJogaSelecoesAll", boolean.class);
                                jogaSel.invoke(sr, chkSelecoes.isSelected());
                            }

                            // 4. Define UsarGruposReaisCopa e ciclos ANTES do dg()!
                            boolean usarCopaReal = (chkCopaMundo != null ? chkCopaMundo.isSelected() : true);
                            if (anoInicio == 2026) {
                                usarCopaReal = true; // Em 2026 grupos reais são indispensáveis no ano 1
                            }
                            try {
                                Method setCopa = sr.getClass().getMethod("setUsarGruposReaisCopa", boolean.class);
                                setCopa.invoke(sr, usarCopaReal);
                            } catch (Throwable ignored) {}
                            try {
                                Method vMMethod = caClass.getMethod("vM");
                                Object opts = vMMethod.invoke(null);
                                if (opts != null) {
                                    opts.getClass().getMethod("setUsarGruposReaisCopa", boolean.class).invoke(opts, usarCopaReal);
                                }
                            } catch (Throwable ignored) {}

                            // 5. Ajuste dos ciclos de competições de seleções ANTES de chamar dg()!
                            if (anoInicio == 2026) {
                                try {
                                    Field blField = sr.getClass().getDeclaredField("bl");
                                    blField.setAccessible(true);
                                    blField.setInt(sr, 1); // Copa do Mundo no ano 1 (2026)

                                    Field bmField = sr.getClass().getDeclaredField("bm");
                                    bmField.setAccessible(true);
                                    bmField.setInt(sr, 3); // Eurocopa em 2028

                                    Field bnField = sr.getClass().getDeclaredField("bn");
                                    bnField.setAccessible(true);
                                    bnField.setInt(sr, 3); // Copa América em 2028
                                } catch (Throwable ignored) {}
                            } else if (anoInicio == 2027) {
                                try {
                                    Method ruMethod = sr.getClass().getMethod("rU");
                                    ruMethod.invoke(sr);
                                } catch (Throwable ignored) {}

                                try {
                                    Field blField = sr.getClass().getDeclaredField("bl");
                                    blField.setAccessible(true);
                                    blField.setInt(sr, 4); // Copa do Mundo em 2030

                                    Field bmField = sr.getClass().getDeclaredField("bm");
                                    bmField.setAccessible(true);
                                    bmField.setInt(sr, 2); // Eurocopa em 2028

                                    Field bnField = sr.getClass().getDeclaredField("bn");
                                    bnField.setAccessible(true);
                                    bnField.setInt(sr, 2); // Copa América em 2028
                                } catch (Throwable ignored) {}
                            }

                            // 6. Validação de pelo menos 1 liga selecionada
                            Method bjMethod = sr.getClass().getMethod("bj");
                            boolean temLiga = (Boolean) bjMethod.invoke(sr);

                            if (temLiga) {
                                // Inicialização do save: best.p.dg()
                                Class<?> pClass = Class.forName("best.p", true, cl);
                                Method dgMethod = pClass.getMethod("dg");
                                dgMethod.invoke(null);

                                // 7. Avança para a tela de escolha de clube: best.S.aY(1)
                                Class<?> sClass = Class.forName("best.S", true, cl);
                                Method ayMethod = sClass.getMethod("aY", int.class);
                                ayMethod.invoke(null, 1);

                                System.out.println("[MOD] 🚀 Save inicializado com sucesso para o ano de: " + anoInicio);
                                br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] 🚀 Save inicializado com sucesso para o ano de: " + anoInicio);
                            } else {
                                JOptionPane.showMessageDialog(null, "Selecione pelo menos uma liga", "", JOptionPane.ERROR_MESSAGE);
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                            java.io.StringWriter sw = new java.io.StringWriter();
                            t.printStackTrace(new java.io.PrintWriter(sw));
                            br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] Erro ao iniciar jogo com ano customizado:\n" + sw.toString());
                        } finally {
                            btnIniciar.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                        }
                    });
                }

                huComp.validate();
                huComp.repaint();
                System.out.println("[MOD] ✅ Temporadas 2026 e 2027 injetadas com sucesso em a.hU!");
            } catch (Throwable ex) {
                System.err.println("[MOD] Erro ao injetar em a.hU: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    /**
     * Injeta o Filtro de Nota Média e a coluna 'Nota' na tela de busca de jogadores (a.gx)
     */
    private static void injectIntoDialog(JDialog dialog) {
        SwingUtilities.invokeLater(() -> {
            try {
                Component gxComp = findComponentBySuffix(dialog, ".gx");
                if (gxComp == null) return;

                // Evita reinjetar caso o componente já tenha sido modificado
                if (gxComp.getName() != null && gxComp.getName().contains("ModInjected")) {
                    return;
                }
                gxComp.setName("ModInjected_" + System.currentTimeMillis());

                System.out.println("[MOD] Tela de busca detectada (a.gx)! Injetando filtro e coluna de nota média...");

                // Obtém referências aos campos internos de a.gx via reflexão
                Field yYField = gxComp.getClass().getDeclaredField("yY");
                yYField.setAccessible(true);
                JButton btnProcurar = (JButton) yYField.get(gxComp);

                Field kfField = gxComp.getClass().getDeclaredField("Kf");
                kfField.setAccessible(true);
                JButton btnReset = (JButton) kfField.get(gxComp);

                Field ukField = gxComp.getClass().getDeclaredField("uK");
                ukField.setAccessible(true);

                Field zjField = gxComp.getClass().getDeclaredField("zj");
                zjField.setAccessible(true);
                JTable table = (JTable) zjField.get(gxComp);

                Field zhField = gxComp.getClass().getDeclaredField("zh");
                zhField.setAccessible(true);
                JLabel lblCount = (JLabel) zhField.get(gxComp);

                // Componentes do filtro de nota média
                JLabel lblNota = new JLabel("Nota Média:");
                lblNota.setFont(new Font("Segoe UI", Font.BOLD, 12));
                lblNota.setForeground(Color.WHITE);

                String[] tiers = new String[]{"Qualquer", "< 6.5", "6.5 - 7.5", "> 7.5"};
                JComboBox<String> cmbNota = new JComboBox<>(tiers);
                cmbNota.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                cmbNota.setBackground(Color.WHITE);

                // Posição: colocado entre 'Resetar filtros' (x=10..140) e 'Procurar' (x=340..470)
                // Evita sobrepor a contagem de jogadores 'zh' (que fica em x=510..800)
                int btnY = 250;
                if (btnReset != null && btnReset.getY() > 0) {
                    btnY = btnReset.getY();
                }

                Class<?> aClass = Class.forName("components.a", true, gxComp.getClass().getClassLoader());
                Constructor<?> constr = aClass.getConstructor(int.class, int.class, int.class, int.class);
                Object constrLabel = constr.newInstance(145, btnY, 75, 30);
                Object constrCombo = constr.newInstance(225, btnY, 105, 30);

                Method addMethod = gxComp.getClass().getMethod("add", Component.class, Object.class);
                addMethod.invoke(gxComp, lblNota, constrLabel);
                addMethod.invoke(gxComp, cmbNota, constrCombo);

                // Garante que a coluna 'Nota' seja adicionada à tabela
                garantirColunaNota(table, gxComp, ukField);

                // Mantém cópia dos resultados originais da busca para poder filtrar dinamicamente
                List<Object> originalResults = new ArrayList<>();

                // Hook no botão "Procurar": executa após a busca original do jogo
                btnProcurar.addActionListener(e -> {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            ArrayList<?> uK = (ArrayList<?>) ukField.get(gxComp);
                            originalResults.clear();
                            if (uK != null) {
                                originalResults.addAll(uK);
                            }
                            garantirColunaNota(table, gxComp, ukField);
                            aplicarFiltroNota(cmbNota, originalResults, uK, table, lblCount);
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                        }
                    });
                });

                // Se o usuário mudar o dropdown de Nota Média, filtra imediatamente os resultados existentes
                cmbNota.addActionListener(e -> {
                    try {
                        ArrayList<?> uK = (ArrayList<?>) ukField.get(gxComp);
                        garantirColunaNota(table, gxComp, ukField);
                        aplicarFiltroNota(cmbNota, originalResults, uK, table, lblCount);
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                    }
                });

                // Ao clicar em "Resetar filtros", volta o dropdown de nota para "Qualquer"
                btnReset.addActionListener(e -> {
                    cmbNota.setSelectedIndex(0);
                    originalResults.clear();
                });

                gxComp.validate();
                gxComp.repaint();
                System.out.println("[MOD] ✅ Filtro de nota média e coluna de tabela injetados com sucesso!");
            } catch (Throwable ex) {
                System.err.println("[MOD] Erro ao injetar filtro de nota na busca: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    /**
     * Adiciona a coluna 'Nota' à tabela JTable de jogadores, caso ainda não exista.
     */
    private static void garantirColunaNota(JTable table, Component gxComp, Field ukField) {
        if (table == null) return;
        TableColumnModel colModel = table.getColumnModel();

        // Se já existe uma coluna com título "Nota", não adiciona duplicada
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            if ("Nota".equals(colModel.getColumn(i).getHeaderValue())) {
                return;
            }
        }

        // Cria a coluna com modelIndex = 0 (b.D.getValueAt sempre retorna a instância best.F do jogador)
        TableColumn notaCol = new TableColumn(0, 42);
        notaCol.setHeaderValue("Nota");
        notaCol.setPreferredWidth(42);
        notaCol.setMinWidth(35);
        notaCol.setMaxWidth(60);

        // Renderer customizado com alinhamento central e destaque amarelo ao selecionar
        notaCol.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(CENTER);
                if (isSelected) {
                    setBackground(Color.YELLOW);
                    setForeground(Color.BLACK);
                } else {
                    setBackground(Color.WHITE);
                    setForeground(Color.BLACK);
                }
                if (value != null) {
                    setText(formatarNota(value));
                } else {
                    setText("--");
                }
                return this;
            }
        });

        table.addColumn(notaCol);

        // Move a coluna para a posição antes de 'Idade' (logo após GC - Gols no Campeonato)
        try {
            int notaIdx = colModel.getColumnCount() - 1;
            for (int i = 0; i < colModel.getColumnCount(); i++) {
                if ("Idade".equals(colModel.getColumn(i).getHeaderValue())) {
                    table.moveColumn(notaIdx, i);
                    break;
                }
            }
        } catch (Throwable ignored) {
        }

        // Listener de clique no cabeçalho da coluna para ordenar os jogadores por nota
        JTableHeader header = table.getTableHeader();
        if (header != null && header.getClientProperty("ModHeaderListen") == null) {
            header.putClientProperty("ModHeaderListen", Boolean.TRUE);
            header.addMouseListener(new MouseAdapter() {
                private boolean asc = false;
                @SuppressWarnings({"rawtypes", "unchecked"})
                @Override
                public void mouseClicked(MouseEvent e) {
                    int col = table.columnAtPoint(e.getPoint());
                    if (col >= 0 && col < table.getColumnCount()) {
                        TableColumn tc = table.getColumnModel().getColumn(col);
                        if ("Nota".equals(tc.getHeaderValue())) {
                            asc = !asc;
                            try {
                                ArrayList uK = (ArrayList) ukField.get(gxComp);
                                if (uK != null && !uK.isEmpty()) {
                                    if (table.getRowSorter() != null) {
                                        table.getRowSorter().setSortKeys(null);
                                    }
                                    Collections.sort(uK, (a, b) -> {
                                        double nA = calcularNota(a);
                                        double nB = calcularNota(b);
                                        return asc ? Double.compare(nA, nB) : Double.compare(nB, nA);
                                    });
                                    if (table.getModel() instanceof AbstractTableModel) {
                                        ((AbstractTableModel) table.getModel()).fireTableDataChanged();
                                    }
                                    table.repaint();
                                }
                            } catch (Throwable ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * Calcula a nota média do jogador de qualquer time (temporada atual ou carreira).
     * Invoca 'gn()' em tempo de execução para que o Brasfoot processe o histórico de partidas.
     */
    public static double calcularNota(Object player) {
        if (player == null) return 0.0;
        try {
            // 1. Invoca gn() para calcular a nota média da temporada atual
            Method gnMethod = player.getClass().getMethod("gn");
            Object resSeason = gnMethod.invoke(player);
            if (resSeason instanceof String) {
                String s = (String) resSeason;
                if (!s.equals("--") && !s.trim().isEmpty()) {
                    return Double.parseDouble(s.replace(",", "."));
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            // 2. Se não jogou na temporada atual, verifica nota da carreira gx()
            Method gxMethod = player.getClass().getMethod("gx");
            Object resCareer = gxMethod.invoke(player);
            if (resCareer instanceof String) {
                String s = (String) resCareer;
                if (!s.equals("--") && !s.trim().isEmpty()) {
                    return Double.parseDouble(s.replace(",", "."));
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            // 3. Fallback para F() se já preenchido
            Method fMethod = player.getClass().getMethod("F");
            double fVal = (Double) fMethod.invoke(player);
            if (fVal >= 2.0) {
                return fVal;
            }
        } catch (Throwable ignored) {
        }

        return 0.0;
    }

    /**
     * Formata a nota média para exibição amigável na tabela
     */
    public static String formatarNota(Object player) {
        double nota = calcularNota(player);
        if (nota < 2.0) {
            return "--";
        }
        return String.format(Locale.US, "%.2f", nota);
    }

    /**
     * Aplica a filtragem por nota média na lista de resultados da busca
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void aplicarFiltroNota(JComboBox<String> cmbNota, List<Object> originalResults,
                                         ArrayList uK, JTable table, JLabel lblCount) {
        if (originalResults == null || originalResults.isEmpty() || uK == null) return;

        String tier = (String) cmbNota.getSelectedItem();
        if (tier == null || "Qualquer".equals(tier)) {
            uK.clear();
            uK.addAll(originalResults);
        } else {
            ArrayList filtrados = new ArrayList();
            for (Object player : originalResults) {
                double nota = calcularNota(player);

                if ("< 6.5".equals(tier)) {
                    if (nota >= 2.0 && nota < 6.5) {
                        filtrados.add(player);
                    }
                } else if ("6.5 - 7.5".equals(tier)) {
                    if (nota >= 6.5 && nota <= 7.5) {
                        filtrados.add(player);
                    }
                } else if ("> 7.5".equals(tier)) {
                    if (nota > 7.5) {
                        filtrados.add(player);
                    }
                }
            }
            uK.clear();
            uK.addAll(filtrados);
        }

        // Atualiza tabela e contador de jogadores
        if (table.getModel() instanceof AbstractTableModel) {
            ((AbstractTableModel) table.getModel()).fireTableDataChanged();
        }
        lblCount.setText(uK.size() + (uK.size() == 1 ? " jogador encontrado" : " jogadores encontrados"));
        if (table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
        table.repaint();
    }

    public static Component findComponentBySuffix(Component comp, String suffix) {
        if (comp == null) return null;
        if (comp.getClass().getName().endsWith(suffix)) {
            if (gameClassLoader == null) {
                gameClassLoader = comp.getClass().getClassLoader();
            }
            return comp;
        }
        if (comp instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane) comp;
            if (sp.getViewport() != null) {
                Component view = sp.getViewport().getView();
                Component found = findComponentBySuffix(view, suffix);
                if (found != null) return found;
            }
        }
        if (comp instanceof RootPaneContainer) {
            Component content = ((RootPaneContainer) comp).getContentPane();
            Component found = findComponentBySuffix(content, suffix);
            if (found != null) return found;
        }
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                Component found = findComponentBySuffix(child, suffix);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static Object findActiveMatch() {
        for (Window w : Window.getWindows()) {
            if (w.isShowing()) {
                Component match = findComponentBySuffix(w, ".dV");
                if (match != null) return match;
            }
        }
        return null;
    }

    public static Object findActiveHalftimeDialog() {
        for (Window w : Window.getWindows()) {
            if (w.isShowing()) {
                Component cD = findComponentBySuffix(w, ".cD");
                if (cD != null) return cD;
            }
        }
        return null;
    }

    public static boolean isPenaltisInProgress() {
        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing()) {
                if (findComponentBySuffix(w, ".bg") != null ||
                    findComponentBySuffix(w, ".C") != null ||
                    findComponentBySuffix(w, ".gh") != null ||
                    findComponentBySuffix(w, ".gh_0") != null ||
                    findComponentBySuffix(w, ".gb") != null) {
                    return true;
                }
            }
        }
        try {
            Class<?> dvCls = Class.forName("a.dV", true, getGameClassLoader());
            Field gyField = dvCls.getDeclaredField("GY");
            gyField.setAccessible(true);
            List<?> gy = (List<?>) gyField.get(null);
            if (gy != null && !gy.isEmpty()) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isMatchFinished(Object match) {
        if (match == null) return false;
        if (isPenaltisInProgress()) return false;
        try {
            Field guField = match.getClass().getDeclaredField("GU");
            guField.setAccessible(true);
            boolean gu = guField.getBoolean(match);
            if (!gu) return false;

            try {
                Field gyField = match.getClass().getDeclaredField("GY");
                gyField.setAccessible(true);
                List<?> gy = (List<?>) gyField.get(null);
                if (gy != null && !gy.isEmpty()) {
                    return false;
                }
            } catch (Throwable ignored) {}
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void fecharIntervalo(Object cD) {
        if (cD == null) return;
        // Se NÃO estiver simulando e NÃO estiver em férias, NUNCA fecha o intervalo (mantém nativo!)
        if (!simulandoAgora && !br.com.brasfootmod.vacation.BrasfootVacationController.isEmFerias()) {
            return;
        }
        try {
            System.out.println("[MOD] ⏸️ Intervalo detectado! Avançando direto para o 2º tempo sem pausa...");
            try {
                br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] ⏸️ Intervalo detectado! Avançando direto para o 2º tempo sem pausa...");
            } catch (Throwable ignored) {}

            try {
                Method pbMethod = cD.getClass().getDeclaredMethod("pb");
                pbMethod.setAccessible(true);
                pbMethod.invoke(cD);
            } catch (Throwable t1) {
                try {
                    Field dmField = cD.getClass().getDeclaredField("Dm");
                    dmField.setAccessible(true);
                    JButton btnVoltar = (JButton) dmField.get(cD);
                    if (btnVoltar != null) {
                        btnVoltar.doClick();
                    }
                } catch (Throwable ignored) {}
            }

            if (cD instanceof Window) {
                ((Window) cD).dispose();
            } else {
                try {
                    Field ubField = cD.getClass().getDeclaredField("ub");
                    ubField.setAccessible(true);
                    JDialog ub = (JDialog) ubField.get(cD);
                    if (ub != null) ub.dispose();
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            System.err.println("[MOD] Erro ao fechar intervalo: " + t.getMessage());
        }
    }

    public static boolean interceptarEFecharIntervaloModal(Window w) {
        if (w == null) return false;
        // Se NÃO estiver simulando e NÃO estiver em férias, NUNCA intercepta o intervalo
        if (!simulandoAgora && !br.com.brasfootmod.vacation.BrasfootVacationController.isEmFerias()) {
            return false;
        }
        try {
            Component cD = findComponentBySuffix(w, ".cD");
            if (cD == null) cD = findComponentBySuffix(w, ".cd_0");
            if (cD != null) {
                fecharIntervalo(cD);
                w.dispose();
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void executarFinalizacaoManual() {
        Object match = findActiveMatch();
        if (match == null) {
            JOptionPane.showMessageDialog(null,
                    "Nenhuma partida em andamento no momento.\nAbra ou inicie uma partida para finalizar!",
                    "Mod Brasfoot", JOptionPane.WARNING_MESSAGE);
            return;
        }

        simulandoAgora = true;
        Object halftime = findActiveHalftimeDialog();
        if (halftime != null) {
            fecharIntervalo(halftime);
        }

        simularPartidaCompleta(match);
    }

    public static void simularPartidaCompleta(Object match) {
        if (match == null || isMatchFinished(match)) return;
        simulandoAgora = true;

        SwingUtilities.invokeLater(() -> {
            int subsOriginal = -1;
            Object sr = null;
            try {
                sr = br.com.brasfootmod.vacation.BrasfootVacationController.getSRObject();
                if (sr != null) {
                    Method getSubs = sr.getClass().getMethod("getVerJanelaSubs");
                    subsOriginal = ((Number) getSubs.invoke(sr)).intValue();
                    Method setSubs = sr.getClass().getMethod("setVerJanelaSubs", int.class);
                    setSubs.invoke(sr, 0); // 0 = desativa tela de substituições no intervalo nativamente
                }
            } catch (Throwable ignored) {}

            try {
                System.out.println("[MOD] ⚡ Simulando partida completa direto ao fim (sem parada no intervalo)...");
                Class<?> dvClass = match.getClass();
                Method qaMethod = dvClass.getMethod("qa");
                Field guField = dvClass.getDeclaredField("GU");
                guField.setAccessible(true);

                try {
                    Field goField = dvClass.getDeclaredField("GO");
                    goField.setAccessible(true);
                    javax.swing.Timer go = (javax.swing.Timer) goField.get(match);
                    if (go != null) {
                        go.stop();
                    }
                } catch (Throwable ignored) {
                }

                // Se houver algum diálogo de intervalo remanescente, fecha imediatamente
                Object htInicial = findActiveHalftimeDialog();
                if (htInicial != null) {
                    fecharIntervalo(htInicial);
                }

                int count = 0;
                while (!guField.getBoolean(match) && count < 250) {
                    qaMethod.invoke(match);
                    count++;

                    Object halftime = findActiveHalftimeDialog();
                    if (halftime != null) {
                        fecharIntervalo(halftime);
                    }

                    verificarEProcessarPenaltisInstantaneo();
                }

                // Processa pênaltis imediatamente caso o jogo tenha ido para decisão por penalidades
                for (int p = 0; p < 15; p++) {
                    if (verificarEProcessarPenaltisInstantaneo()) {
                        break;
                    }
                }

                System.out.println("[MOD] ✅ Partida finalizada com sucesso em " + count + " ticks instantâneos!");
                try {
                    br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] ✅ Partida finalizada com sucesso em " + count + " ticks instantâneos (direto ao fim)!");
                    
                    // Conclui a rodada oficialmente no motor do jogo
                    try {
                        Class<?> sCls = Class.forName("best.s", true, getGameClassLoader());
                        Method dJMethod = sCls.getMethod("dJ");
                        dJMethod.invoke(null);
                    } catch (Throwable ignored) {}

                    if (br.com.brasfootmod.vacation.BrasfootVacationController.isEmFerias()) {
                        br.com.brasfootmod.vacation.BrasfootVacationController.sincronizarHistoricoComCalendarioOficial();
                        br.com.brasfootmod.vacation.BrasfootVacationController.avancarAposPartida(match);
                    }
                } catch (Throwable t) {
                    try {
                        br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] Erro ao registrar partida pós-simulação: " + t.getMessage());
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ex) {
                System.err.println("[MOD] Erro na simulação: " + ex.getMessage());
            } finally {
                // Restaura configuração de intervalo nativo e modo rápido caso não esteja em férias
                if (!br.com.brasfootmod.vacation.BrasfootVacationController.isEmFerias()) {
                    try {
                        Object srAtual = sr != null ? sr : br.com.brasfootmod.vacation.BrasfootVacationController.getSRObject();
                        if (srAtual != null) {
                            Method setSubs = srAtual.getClass().getMethod("setVerJanelaSubs", int.class);
                            setSubs.invoke(srAtual, 1); // 1 = nativo com intervalo
                            Method hMethod = srAtual.getClass().getMethod("h", boolean.class);
                            hMethod.invoke(srAtual, false); // restaura bK = false
                        }
                    } catch (Throwable ignored) {}
                }
                simulandoAgora = false;
            }
        });
    }

    /**
     * Intercepta e fecha diálogos de pênaltis (escolha de batedor a.gh_0/a.gh, animação a.gb, ordem a.C, disputa a.bg)
     */
    /**
     * Intercepta e fecha diálogos de pênaltis (escolha de batedor a.gh_0/a.gh, animação a.gb, ordem a.C, disputa a.bg)
     */
    public static boolean interceptarEFecharPenaltiModal(Window w) {
        if (w == null) return false;
        boolean deveInterceptar = simulandoAgora 
                || br.com.brasfootmod.vacation.BrasfootVacationController.isEmFerias()
                || modoSimulacaoInstantanea
                || isOrphanPenaltyDialog(w);

        // Se NÃO estiver simulando, NÃO estiver em férias e NÃO for diálogo órfão preso, mantém nativo
        if (!deveInterceptar) {
            return false;
        }
        try {
            // 1. Escolha do batedor de pênalti em jogo (a.gh_0 / a.gh / "Penalty - escolha do batedor")
            Component ghComp = findComponentBySuffix(w, ".gh");
            if (ghComp == null) ghComp = findComponentBySuffix(w, ".gh_0");
            if (ghComp == null && w instanceof Container) {
                ghComp = br.com.brasfootmod.vacation.BrasfootVacationController.findClickableComponent((Container) w, "escolha do batedor");
            }
            if (ghComp != null) {
                br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] 🥅 Batedor de pênalti (a.gh) interceptado! Confirmando...");
                System.out.println("[MOD] 🥅 Batedor de pênalti (a.gh) interceptado! Confirmando...");
                try {
                    Method orMethod = ghComp.getClass().getDeclaredMethod("oR");
                    orMethod.setAccessible(true);
                    orMethod.invoke(ghComp);
                } catch (Throwable t1) {
                    try {
                        Field zsField = ghComp.getClass().getDeclaredField("zS");
                        zsField.setAccessible(true);
                        JTable table = (JTable) zsField.get(ghComp);
                        if (table != null && table.getRowCount() > 0) {
                            table.setRowSelectionInterval(0, 0);
                        }
                    } catch (Throwable ignored) {}
                    try {
                        Field vqField = ghComp.getClass().getDeclaredField("vq");
                        vqField.setAccessible(true);
                        JButton btn = (JButton) vqField.get(ghComp);
                        if (btn != null) btn.doClick();
                    } catch (Throwable ignored) {}
                }
                w.dispose();
                return true;
            }

            // 2. Animação de pênalti em jogo (a.gb)
            Component gbComp = findComponentBySuffix(w, ".gb");
            if (gbComp != null) {
                br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] 🥅 Animação de pênalti (a.gb) interceptada! Fechando imediatamente...");
                System.out.println("[MOD] 🥅 Animação de pênalti (a.gb) interceptada! Fechando imediatamente...");
                w.dispose();
                return true;
            }

            // 3. Tela de confirmação da ordem de batedores (a.C)
            Component cComp = findComponentBySuffix(w, ".C");
            if (cComp != null) {
                try {
                    Field vqField = cComp.getClass().getDeclaredField("vq");
                    vqField.setAccessible(true);
                    JButton btnJogar = (JButton) vqField.get(cComp);
                    if (btnJogar != null) {
                        System.out.println("[MOD] 🥅 Ordem de batedores (a.C) confirmada automaticamente...");
                        btnJogar.doClick();
                    }
                } catch (Throwable ignored) {}
                w.dispose();
                return true;
            }

            // 4. Tela de animação das cobranças de pênaltis (a.bg)
            Component bgComp = findComponentBySuffix(w, ".bg");
            if (bgComp != null) {
                simularPenaltisInstantaneamente(bgComp);
                w.dispose();
                return true;
            }
        } catch (Throwable t) {
            br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] Erro ao interceptar pênalti: " + t.getMessage());
        }
        return false;
    }

    /**
     * Detecta se um diálogo de pênalti está travado/órfão na tela
     * (por exemplo, quando a tela de pós-jogo a.hk já está no fundo)
     */
    public static boolean isOrphanPenaltyDialog(Window w) {
        if (w == null) return false;
        Component bgComp = findComponentBySuffix(w, ".bg");
        if (bgComp == null) {
            bgComp = findComponentBySuffix(w, ".C");
        }
        if (bgComp == null) return false;

        for (Window rootW : Window.getWindows()) {
            if (rootW != null && rootW.isShowing() && rootW instanceof JFrame) {
                if (findComponentBySuffix(rootW, ".hk") != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Varredura de segurança para destravar diálogos de pênaltis órfãos
     */
    public static void verificarEDestravarPenaltisOrfaos() {
        for (Window w : Window.getWindows()) {
            if (w == null || !w.isShowing()) continue;
            if (isOrphanPenaltyDialog(w)) {
                Component bgComp = findComponentBySuffix(w, ".bg");
                if (bgComp != null) {
                    try {
                        br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] 🚨 Diálogo de pênaltis órfão detectado sobre a.hk! Destravando imediatamente...");
                    } catch (Throwable ignored) {}
                    simularPenaltisInstantaneamente(bgComp);
                    w.dispose();
                } else {
                    w.dispose();
                }
            }
        }
    }

    /**
     * Detecta e processa telas de pênaltis instantaneamente em modo oculto
     */
    public static boolean verificarEProcessarPenaltisInstantaneo() {
        for (Window w : Window.getWindows()) {
            if (w == null || !w.isShowing()) continue;
            if (interceptarEFecharPenaltiModal(w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Executa a disputa de pênaltis de forma 100% segura na EDT, simulando todas as cobranças
     * e avançando o vencedor sem travar animações na tela
     */
    public static void simularPenaltisInstantaneamente(Component bgComp) {
        if (bgComp == null) return;
        Runnable r = () -> executarSimulacaoPenaltisEDT(bgComp);
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(r);
            } catch (Throwable t) {
                SwingUtilities.invokeLater(r);
            }
        }
    }

    private static void executarSimulacaoPenaltisEDT(Component bgComp) {
        try {
            System.out.println("[MOD] 🥅 Disputa de pênaltis (a.bg) detectada: concluindo instantaneamente na EDT...");
            try {
                br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] 🥅 Disputa de pênaltis (a.bg) detectada: concluindo instantaneamente na EDT...");
            } catch (Throwable ignored) {}

            Window window = SwingUtilities.getWindowAncestor(bgComp);
            if (window != null) {
                window.setVisible(false);
            }

            Field ubField = bgComp.getClass().getDeclaredField("ub");
            ubField.setAccessible(true);
            JDialog ub = (JDialog) ubField.get(bgComp);
            if (ub != null) {
                ub.setVisible(false);
            }

            Field zcField = bgComp.getClass().getDeclaredField("zC");
            zcField.setAccessible(true);

            Field zkField = bgComp.getClass().getDeclaredField("zK");
            zkField.setAccessible(true);
            JButton zK = (JButton) zkField.get(bgComp);

            Method nLMethod = bgComp.getClass().getDeclaredMethod("nL");
            nLMethod.setAccessible(true);

            Method nMMethod = bgComp.getClass().getDeclaredMethod("nM");
            nMMethod.setAccessible(true);

            // Cancela timer ativo nativo
            try {
                java.util.Timer timer = (java.util.Timer) zcField.get(bgComp);
                if (timer != null) timer.cancel();
            } catch (Throwable ignored) {}

            int maxKicks = 60;
            while ((zK == null || !zK.isVisible()) && maxKicks-- > 0) {
                try {
                    java.util.Timer t1 = (java.util.Timer) zcField.get(bgComp);
                    if (t1 != null) t1.cancel();
                } catch (Throwable ignored) {}

                nLMethod.invoke(bgComp);

                try {
                    java.util.Timer t2 = (java.util.Timer) zcField.get(bgComp);
                    if (t2 != null) t2.cancel();
                } catch (Throwable ignored) {}

                nMMethod.invoke(bgComp);
            }

            try {
                java.util.Timer timerFinal = (java.util.Timer) zcField.get(bgComp);
                if (timerFinal != null) timerFinal.cancel();
            } catch (Throwable ignored) {}

            if (zK != null) {
                zK.doClick();
            }
            if (ub != null) {
                ub.dispose();
            }
            if (window != null) {
                window.dispose();
            }
            System.out.println("[MOD] ✅ Decisão por pênaltis finalizada com sucesso!");
            try {
                br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] ✅ Decisão por pênaltis finalizada com sucesso!");
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            System.err.println("[MOD] Erro ao simular pênaltis: " + t.getMessage());
            try {
                br.com.brasfootmod.vacation.BrasfootVacationController.logToFile("[MOD] Erro ao simular pênaltis: " + t.getMessage());
            } catch (Throwable ignored) {}
            try {
                Window window = SwingUtilities.getWindowAncestor(bgComp);
                if (window != null) window.dispose();
            } catch (Throwable ignored) {}
        }
    }

    private static long ultimoCheckJanelas = 0;
    private static void verificarEInjetarJanelasAbertas() {
        long now = System.currentTimeMillis();
        if (now - ultimoCheckJanelas < 1000) return;
        ultimoCheckJanelas = now;

        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing() && w instanceof JFrame) {
                JFrame f = (JFrame) w;
                String name = f.getClass().getName();
                if (name.equals("d.n") || name.equals("a.gq")) {
                    JMenuBar mb = f.getJMenuBar();
                    if (mb == null || mb.getClientProperty("ModInjected") == null) {
                        injectIntoFrame(f);
                    }
                }
            }
        }
    }

    private static void injectIntoFrame(JFrame frame) {
        if (frame == null) return;
        SwingUtilities.invokeLater(() -> {
            try {
                JMenuBar menuBar = frame.getJMenuBar();
                if (menuBar == null) {
                    menuBar = new JMenuBar();
                    frame.setJMenuBar(menuBar);
                }

                // Remove componentes anteriores do mod para evitar duplicações
                for (int i = menuBar.getComponentCount() - 1; i >= 0; i--) {
                    Component c = menuBar.getComponent(i);
                    if (c instanceof JMenu) {
                        String t = ((JMenu) c).getText();
                        if (t != null && (t.contains("Mod Brasfoot") || t.contains("Transfermarkt"))) {
                            menuBar.remove(c);
                        }
                    } else if (c instanceof JButton) {
                        String t = ((JButton) c).getText();
                        if (t != null && (t.contains("Transfermarkt") || t.contains("Atualizar"))) {
                            menuBar.remove(c);
                        }
                    }
                }

                menuBar.putClientProperty("ModInjected", Boolean.TRUE);

                String frameClass = frame.getClass().getName();
                boolean isEditorClubes = frameClass.equals("d.n");

                if (isEditorClubes) {
                    // Ajusta altura do frame do editor para não cortar botões inferiores
                    if (frame.getRootPane().getClientProperty("EditorHeightAdjusted") == null) {
                        frame.getRootPane().putClientProperty("EditorHeightAdjusted", Boolean.TRUE);
                        frame.setSize(frame.getWidth(), frame.getHeight() + 32);
                    }

                    // ==========================================
                    // 1. TELA DO EDITOR DE CLUBES (d.n)
                    // ==========================================
                    JMenu menuEditor = new JMenu("🌐 Transfermarkt & Divisões");
                    menuEditor.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    menuEditor.setForeground(new Color(0, 102, 204));

                    JMenuItem itemSyncCurrent = new JMenuItem("⚡ Atualizar Este Clube (Transfermarkt)");
                    itemSyncCurrent.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    itemSyncCurrent.setForeground(new Color(39, 174, 96));
                    itemSyncCurrent.addActionListener(e -> br.com.brasfootmod.editor.TransfermarktEditorSync.atualizarClubeNoEditor(frame));
                    menuEditor.add(itemSyncCurrent);

                    JMenuItem itemRestoreCurrent = new JMenuItem("↩️ Restaurar Este Clube (Fallback)");
                    itemRestoreCurrent.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                    itemRestoreCurrent.setForeground(new Color(192, 57, 43));
                    itemRestoreCurrent.addActionListener(e -> br.com.brasfootmod.editor.TransfermarktEditorSync.restaurarEsteClubeNoEditor(frame));
                    menuEditor.add(itemRestoreCurrent);

                    menuEditor.addSeparator();

                    JMenuItem itemSyncAll = new JMenuItem("🇧🇷 Atualizar Times do Brasil (Transfermarkt)");
                    itemSyncAll.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    itemSyncAll.setForeground(new Color(0, 102, 204));
                    itemSyncAll.addActionListener(e -> br.com.brasfootmod.editor.TransfermarktEditorSync.syncAllBrazilianClubs(frame));
                    menuEditor.add(itemSyncAll);

                    JMenuItem itemCalibrateDivisions = new JMenuItem("🏆 Calibrar Forças / Divisões (Séries A, B e C)");
                    itemCalibrateDivisions.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    itemCalibrateDivisions.setForeground(new Color(211, 84, 0));
                    itemCalibrateDivisions.addActionListener(e -> br.com.brasfootmod.editor.TransfermarktEditorSync.calibrarDivisoesBrasil(frame));
                    menuEditor.add(itemCalibrateDivisions);

                    JMenuItem itemDeduplicate = new JMenuItem("🧹 Deletar Jogadores Repetidos (Base Completa)");
                    itemDeduplicate.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                    itemDeduplicate.setForeground(new Color(230, 126, 34));
                    itemDeduplicate.addActionListener(e -> br.com.brasfootmod.editor.TransfermarktEditorSync.executarDeduplicacaoBaseCompleta(frame));
                    menuEditor.add(itemDeduplicate);

                    JMenuItem itemRestoreFallback = new JMenuItem("↩️ Restaurar Elencos Originais (Fallback)");
                    itemRestoreFallback.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                    itemRestoreFallback.setForeground(new Color(192, 57, 43));
                    itemRestoreFallback.addActionListener(e -> br.com.brasfootmod.editor.TransfermarktEditorSync.restaurarFallback(frame));
                    menuEditor.add(itemRestoreFallback);

                    menuBar.add(menuEditor);

                    // BOTÕES DIRETOS E VISÍVEIS NA BARRA SUPERIOR (100% CLICÁVEIS)
                    menuBar.add(Box.createHorizontalStrut(10));

                    JButton btnSyncCurrent = new JButton("⚡ Atualizar Este Clube (TM)");
                    btnSyncCurrent.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    btnSyncCurrent.setForeground(Color.WHITE);
                    btnSyncCurrent.setBackground(new Color(39, 174, 96));
                    btnSyncCurrent.setOpaque(true);
                    btnSyncCurrent.setContentAreaFilled(true);
                    btnSyncCurrent.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(30, 130, 70), 1),
                            BorderFactory.createEmptyBorder(3, 10, 3, 10)));
                    btnSyncCurrent.setToolTipText("Atualiza o clube atualmente aberto com dados reais do Transfermarkt");
                    btnSyncCurrent.setFocusPainted(false);
                    btnSyncCurrent.setFocusable(false);
                    btnSyncCurrent.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    btnSyncCurrent.addActionListener(e -> br.com.brasfootmod.editor.TransfermarktEditorSync.atualizarClubeNoEditor(frame));
                    btnSyncCurrent.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mousePressed(java.awt.event.MouseEvent e) {
                            if (SwingUtilities.isLeftMouseButton(e)) {
                                br.com.brasfootmod.editor.TransfermarktEditorSync.atualizarClubeNoEditor(frame);
                            }
                        }
                    });
                    menuBar.add(btnSyncCurrent);

                    menuBar.add(Box.createHorizontalStrut(6));

                    JButton btnSyncAll = new JButton("🇧🇷 Atualizar Times do Brasil");
                    btnSyncAll.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    btnSyncAll.setForeground(Color.WHITE);
                    btnSyncAll.setBackground(new Color(0, 102, 204));
                    btnSyncAll.setOpaque(true);
                    btnSyncAll.setContentAreaFilled(true);
                    btnSyncAll.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(0, 70, 150), 1),
                            BorderFactory.createEmptyBorder(3, 10, 3, 10)));
                    btnSyncAll.setToolTipText("Sincroniza todos os clubes brasileiros (Séries A, B e C) via Transfermarkt");
                    btnSyncAll.setFocusPainted(false);
                    btnSyncAll.setFocusable(false);
                    btnSyncAll.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    btnSyncAll.addActionListener(e -> br.com.brasfootmod.editor.TransfermarktEditorSync.syncAllBrazilianClubs(frame));
                    btnSyncAll.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mousePressed(java.awt.event.MouseEvent e) {
                            if (SwingUtilities.isLeftMouseButton(e)) {
                                br.com.brasfootmod.editor.TransfermarktEditorSync.syncAllBrazilianClubs(frame);
                            }
                        }
                    });
                    menuBar.add(btnSyncAll);

                } else {
                    // ==========================================
                    // 2. TELA PRINCIPAL DO JOGO (a.gq, a.hk, etc.)
                    // ==========================================
                    JMenu menuMod = new JMenu("⚙️ Mod Brasfoot");
                    menuMod.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    menuMod.setForeground(new Color(0, 102, 204));

                    JMenuItem itemFerias = new JMenuItem("🏖️ Sair de Férias (F4)");
                    itemFerias.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    itemFerias.setForeground(new Color(39, 174, 96));
                    itemFerias.addActionListener(e -> abrirDialogoFerias());
                    menuMod.add(itemFerias);

                    menuMod.addSeparator();

                    JCheckBoxMenuItem itemAutoSim = new JCheckBoxMenuItem("⚡ Simulação Instantânea (Automática)", modoSimulacaoInstantanea);
                    itemAutoSim.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                    itemAutoSim.addActionListener(e -> {
                        modoSimulacaoInstantanea = itemAutoSim.isSelected();
                        JOptionPane.showMessageDialog(frame,
                                "Simulação Instantânea Automática: " + (modoSimulacaoInstantanea ? "LIGADA\n(As partidas finalizarão no mesmo segundo em que abrirem)" : "DESLIGADA\n(Você assiste à partida normalmente)"),
                                "Mod Brasfoot", JOptionPane.INFORMATION_MESSAGE);
                    });
                    menuMod.add(itemAutoSim);

                    JMenuItem itemSimNow = new JMenuItem("⚡ Finalizar Partida Agora (F8)");
                    itemSimNow.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    itemSimNow.addActionListener(e -> executarFinalizacaoManual());
                    menuMod.add(itemSimNow);

                    menuBar.add(menuMod);
                }

                frame.validate();
                frame.repaint();
            } catch (Exception e) {
                System.err.println("[MOD] Erro ao injetar no frame: " + e.getMessage());
            }
        });
    }

    public static void abrirDialogoFerias() {
        if (BrasfootVacationController.isEmFerias()) {
            int op = JOptionPane.showConfirmDialog(null,
                    "O Treinador já está em Modo Férias no momento!\nDeseja interromper e reassumir o clube agora?",
                    "Mod Férias Brasfoot", JOptionPane.YES_NO_OPTION);
            if (op == JOptionPane.YES_OPTION) {
                BrasfootVacationController.cancelarFeriasManual();
            }
        } else {
            BrasfootVacationDialog.abrir(null);
        }
    }


}
