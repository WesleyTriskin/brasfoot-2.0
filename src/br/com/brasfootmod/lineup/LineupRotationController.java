package br.com.brasfootmod.lineup;

import br.com.brasfootmod.BrasfootAgent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Controlador para Rotacionar Elenco na Tela de Escalação (a.bz).
 *
 * Funcionalidade:
 * - Adiciona o botão 'Rotacionar Elenco' na barra superior do Banco de Reservas.
 * - Com um clique, escala automaticamente os reservas com MENOR FORÇA do elenco como titulares
 *   na formação tática armada.
 * - Os titulares habituais (maior força) são poupados e posicionados no banco de reservas
 *   como opções para o decorrer da partida.
 * - Atualiza instantaneamente a prancheta de campo, o banco e a lista de jogadores disponíveis.
 */
public class LineupRotationController {

    private static final String PROP_INJECTED = "ModLineupRotationInjected";

    private static void log(String msg) {
        System.out.println("[MOD ROTACIONAR] " + msg);
    }

    /**
     * Verifica janelas ativas e injeta o botão na tela de escalação (a.bz)
     */
    public static void checkAndInjectRotationButton() {
        try {
            for (Window w : Window.getWindows()) {
                if (w == null || !w.isShowing()) continue;

                Component bzComp = BrasfootAgent.findComponentBySuffix(w, ".bz");
                if (bzComp == null) {
                    bzComp = BrasfootAgent.findComponentBySuffix(w, ".bz_0");
                }
                if (bzComp == null || !bzComp.isShowing()) continue;

                if (bzComp instanceof JComponent) {
                    JComponent jc = (JComponent) bzComp;
                    if (jc.getClientProperty(PROP_INJECTED) != null) {
                        continue;
                    }
                    jc.putClientProperty(PROP_INJECTED, Boolean.TRUE);
                }

                injectButtonIntoLineupScreen(bzComp);
            }
        } catch (Throwable t) {
            // Silencioso em caso de erro na varredura periódica
        }
    }

    /**
     * Injeta o botão com estilo nativo e posicionamento preciso no container Bp de a.bz
     */
    private static void injectButtonIntoLineupScreen(final Component bzComp) {
        SwingUtilities.invokeLater(() -> {
            try {
                Class<?> bzClass = bzComp.getClass();
                ClassLoader cl = bzClass.getClassLoader();

                Field bpField = bzClass.getDeclaredField("Bp");
                bpField.setAccessible(true);
                Container bpContainer = (Container) bpField.get(bzComp);
                if (bpContainer == null) return;

                // Cria o botão estilizado com tema moderno e profissional
                final JButton btnRotate = new JButton("Rotacionar Elenco");
                btnRotate.setFont(new Font("Arial", Font.BOLD, 11));
                btnRotate.setForeground(Color.WHITE);
                btnRotate.setBackground(new Color(24, 76, 120)); // Azul tático escuro
                btnRotate.setFocusPainted(false);
                btnRotate.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(15, 23, 42), 1),
                        BorderFactory.createEmptyBorder(2, 6, 2, 6)
                ));
                btnRotate.setCursor(new Cursor(Cursor.HAND_CURSOR));
                btnRotate.setToolTipText("Escalar os reservas de menor força como titulares e poupar os principais no banco.");

                // Efeito hover dinâmico
                btnRotate.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        btnRotate.setBackground(new Color(36, 102, 160));
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        btnRotate.setBackground(new Color(24, 76, 120));
                    }
                });

                // Ação de clique: executa a rotação de elenco
                btnRotate.addActionListener(e -> {
                    btnRotate.setEnabled(false);
                    try {
                        executeSquadRotation(bzComp);
                    } finally {
                        btnRotate.setEnabled(true);
                    }
                });

                // Constraint components.a(x, y, w, h)
                // Posicionado em x=845, y=0, w=175, h=20 (canto direito da barra do Banco de Reservas)
                Class<?> compAClass = Class.forName("components.a", true, cl);
                Constructor<?> compAConst = compAClass.getConstructor(int.class, int.class, int.class, int.class);
                Object constraint = compAConst.newInstance(845, 0, 175, 20);

                bpContainer.add(btnRotate, constraint, 0);
                bpContainer.revalidate();
                bpContainer.repaint();

                log("🔄 Botão 'Rotacionar Elenco' injetado com sucesso na tela de escalação!");
            } catch (Throwable t) {
                log("Erro ao injetar botão de rotação: " + t.getMessage());
            }
        });
    }

    /**
     * Executa a rotação do elenco:
     * - Titulares: jogadores com MENOR FORÇA para cada posição da formação ativa.
     * - Banco: titulares habituais (MAIOR FORÇA) poupados no banco.
     */
    public static void executeSquadRotation(Component bzComp) {
        try {
            Class<?> bzClass = bzComp.getClass();
            ClassLoader cl = bzClass.getClassLoader();

            // 1. Obter referências aos componentes e coleções de a.bz
            Field bfField = bzClass.getDeclaredField("Bf");
            bfField.setAccessible(true);
            JComboBox<?> cmbBf = (JComboBox<?>) bfField.get(bzComp);

            Field aeField = bzClass.getDeclaredField("AE");
            aeField.setAccessible(true);
            ArrayList<?> aeList = (ArrayList<?>) aeField.get(bzComp);

            Field afField = bzClass.getDeclaredField("AF");
            afField.setAccessible(true);
            ArrayList<?> afList = (ArrayList<?>) afField.get(bzComp);

            Field aoField = bzClass.getDeclaredField("AO");
            aoField.setAccessible(true);
            @SuppressWarnings("rawtypes")
            ArrayList aoList = (ArrayList) aoField.get(bzComp);

            Field alField = bzClass.getDeclaredField("AL");
            alField.setAccessible(true);
            ImageIcon clubShirt = (ImageIcon) alField.get(bzComp);

            if (afList == null || afList.isEmpty() || aeList == null || aeList.isEmpty()) {
                return;
            }

            Class<?> aqClass = Class.forName("best.aq", true, cl);
            Field sjField = aqClass.getField("sJ");
            int[][] sJ = (int[][]) sjField.get(null);
            Field seField = aqClass.getField("sE");
            final int[][] sE = (int[][]) seField.get(null);

            // 2. DETECTAR FORMAÇÃO ATIVA DO USUÁRIO NO CAMPO:
            // Varre os slots de campo (1..25) ANTES de limpar para capturar a formação exata
            // que o usuário escolheu (ex: 4-4-2, 4-3-3, 3-5-2 ou posições customizadas arrastadas).
            ArrayList<Integer> starterSlotsList = new ArrayList<>();
            for (int i = 1; i <= 25 && i < aeList.size(); i++) {
                Object slotObj = aeList.get(i);
                if (slotObj != null) {
                    Method xMethod = slotObj.getClass().getMethod("x");
                    Object curPlayer = xMethod.invoke(slotObj);
                    if (curPlayer != null) {
                        starterSlotsList.add(i);
                    }
                }
            }

            // Se o campo não tiver exatamente 11 jogadores (ex: aberto em branco), usa o JComboBox ou 4-4-2
            if (starterSlotsList.size() != 11) {
                starterSlotsList.clear();
                int formationIndex = (cmbBf != null) ? cmbBf.getSelectedIndex() : 0;
                if (formationIndex <= 0 || formationIndex >= sJ.length) {
                    // Padrão do jogo quando indefinido: 4-4-2 clássico (índice 4 em sJ)
                    formationIndex = 4;
                }
                for (int slotId : sJ[formationIndex]) {
                    starterSlotsList.add(slotId);
                }
            }

            // Ordena os slots titulares por setor tático (Goleiro 0 -> Lateral 1 -> Zagueiro 2 -> Meio-campo 3 -> Atacante 4)
            // Isso permite que o motor nativo (best.ah.a) escale com precisão cada posição sem conflito
            Collections.sort(starterSlotsList, new Comparator<Integer>() {
                @Override
                public int compare(Integer s1, Integer s2) {
                    int p1 = (s1 >= 0 && s1 < sE.length) ? sE[s1][0] : 0;
                    int p2 = (s2 >= 0 && s2 < sE.length) ? sE[s2][0] : 0;
                    return Integer.compare(p1, p2);
                }
            });

            // 3. Filtrar elenco disponível (desconsiderando lesionados/suspensos: getStatus() == 0)
            ArrayList<Object> available = new ArrayList<>();
            for (Object p : afList) {
                if (p == null) continue;
                try {
                    Method statusMethod = p.getClass().getMethod("getStatus");
                    int st = (Integer) statusMethod.invoke(p);
                    if (st == 0) {
                        available.add(p);
                    }
                } catch (Throwable t) {
                    available.add(p);
                }
            }

            if (available.size() < 11) {
                available.clear();
                available.addAll(afList);
            }

            // 4. Ordenar available por FORÇA CRESCENTE (menor força primeiro -> RESERVAS)
            Collections.sort(available, new Comparator<Object>() {
                @Override
                public int compare(Object p1, Object p2) {
                    int f1 = getForca(p1);
                    int f2 = getForca(p2);
                    return Integer.compare(f1, f2);
                }
            });

            // 5. Limpar titulares e slots de banco (0..36)
            aoList.clear();
            for (int i = 0; i <= 36 && i < aeList.size(); i++) {
                Object slotObj = aeList.get(i);
                if (slotObj != null) {
                    Method vgMethod = slotObj.getClass().getMethod("vg");
                    vgMethod.invoke(slotObj);
                }
            }

            // 6. Escalar os 11 titulares mantendo a formação exata do usuário
            Class<?> ahClass = Class.forName("best.ah", true, cl);
            Method ahPickMethod = ahClass.getMethod("a", ArrayList.class, int.class, boolean.class, boolean.class);
            Class<?> fClass = Class.forName("best.F", true, cl);

            int titularesEscalados = 0;
            for (int slotIndex : starterSlotsList) {
                Object player = ahPickMethod.invoke(null, available, slotIndex, false, false);
                if (player == null) {
                    player = ahPickMethod.invoke(null, available, slotIndex, true, false);
                }
                if (player == null && !available.isEmpty()) {
                    player = available.get(0);
                }
                if (player != null && slotIndex < aeList.size()) {
                    Object slotObj = aeList.get(slotIndex);

                    // slot.h(AL) - camisa
                    Method hMethod = slotObj.getClass().getMethod("h", ImageIcon.class);
                    hMethod.invoke(slotObj, clubShirt);

                    // slot.a(player) - jogador
                    Method aMethod = slotObj.getClass().getMethod("a", fClass);
                    aMethod.invoke(slotObj, player);

                    available.remove(player);
                    aoList.add(player);
                    titularesEscalados++;
                }
            }

            // 7. Para o BANCO (slots 26..36):
            // Ordena os jogadores restantes por FORÇA DECRESCENTE (maior força primeiro)
            // Assim, os titulares habituais descansados viram opções de luxo no banco!
            Collections.sort(available, new Comparator<Object>() {
                @Override
                public int compare(Object p1, Object p2) {
                    int f1 = getForca(p1);
                    int f2 = getForca(p2);
                    return Integer.compare(f2, f1); // Decrescente
                }
            });

            Field siField = aqClass.getField("sI");
            int[] sI = (int[]) siField.get(null);

            int benchSlot = 26;
            for (int k = 0; k < sI.length && benchSlot <= 36; k++) {
                Object player = ahPickMethod.invoke(null, available, sI[k], true, false);
                if (player != null && benchSlot < aeList.size()) {
                    Object slotObj = aeList.get(benchSlot);

                    Method hMethod = slotObj.getClass().getMethod("h", ImageIcon.class);
                    hMethod.invoke(slotObj, clubShirt);

                    Method aMethod = slotObj.getClass().getMethod("a", fClass);
                    aMethod.invoke(slotObj, player);

                    available.remove(player);
                    aoList.add(player);
                    benchSlot++;
                }
            }

            // Limpa slots de banco não preenchidos
            for (int b = benchSlot; b <= 36 && b < aeList.size(); b++) {
                Object slotObj = aeList.get(b);
                if (slotObj != null) {
                    Method ejMethod = slotObj.getClass().getMethod("ej", int.class);
                    ejMethod.invoke(slotObj, b);
                }
            }

            // 8. Atualizar as renderizações visuais nativas de a.bz
            Method cSMethod = bzClass.getDeclaredMethod("cS", int.class);
            cSMethod.setAccessible(true);
            cSMethod.invoke(bzComp, -1);

            Method cTMethod = bzClass.getDeclaredMethod("cT", int.class);
            cTMethod.setAccessible(true);
            cTMethod.invoke(bzComp, -1);

            Method oeMethod = bzClass.getMethod("oe");
            oeMethod.invoke(bzComp);

            bzComp.repaint();

            log("✅ Elenco rotacionado com sucesso! " + titularesEscalados + " titulares reservas escalados.");
        } catch (Throwable t) {
            log("Erro ao rotacionar elenco: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static int getForca(Object player) {
        if (player == null) return 0;
        try {
            Method fiMethod = player.getClass().getMethod("fi");
            return (Integer) fiMethod.invoke(player);
        } catch (Throwable t) {
            return 0;
        }
    }
}
