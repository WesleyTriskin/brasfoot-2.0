package br.com.brasfootmod.rating;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Controlador do Melhor da Partida (MVP) pós-jogo.
 * - Identifica automaticamente o craque do jogo com base nas notas, gols e assistências.
 * - Destaca o jogador (goleiro, zagueiro, lateral, meia ou atacante, mandante ou visitante)
 *   na tabela de atletas com estrela única ao lado do nome e nota dourada limpa e visível.
 * - Sem banner invasivo, preservando a estética 100% nativa e limpa do Brasfoot.
 */
public class MatchMvpController {

    public static class MvpInfo {
        public Object player;          // best.F
        public String playerName = "";
        public String teamName = "";
        public Icon teamCrest = null;
        public Color teamColor = Color.WHITE;
        public double rating = 0.0;
        public int goals = 0;
        public int assists = 0;
        public boolean isHome = false;
        public boolean isWinner = false;

        public String getFormattedRating() {
            if (rating >= 9.95) return "10";
            return String.format(Locale.US, "%.1f", rating).replace('.', ',');
        }
    }

    private static volatile MvpInfo currentMvp = null;
    private static volatile ImageIcon starIcon = null;

    public static ImageIcon getStarIcon(ClassLoader cl) {
        if (starIcon != null) return starIcon;
        try {
            if (cl != null) {
                java.net.URL url = cl.getResource("aicons/estrela.png");
                if (url != null) {
                    starIcon = new ImageIcon(url);
                    return starIcon;
                }
            }
        } catch (Throwable ignored) {}
        try {
            java.net.URL url = MatchMvpController.class.getResource("/aicons/estrela.png");
            if (url != null) {
                starIcon = new ImageIcon(url);
                return starIcon;
            }
        } catch (Throwable ignored) {}
        return starIcon;
    }

    public static MvpInfo getCurrentMvp() {
        return currentMvp;
    }

    /**
     * Calcula o MVP para a tela a.hk atualmente exibida
     */
    public static MvpInfo computeMvp(Component hkComp) {
        if (hkComp == null) return null;
        try {
            Class<?> hkClass = hkComp.getClass();

            // Partida ativa (KY ou KS)
            Field kyField = hkClass.getDeclaredField("KY");
            kyField.setAccessible(true);
            Object match = kyField.get(hkComp);
            if (match == null) {
                Field ksField = hkClass.getDeclaredField("KS");
                ksField.setAccessible(true);
                match = ksField.get(hkComp);
            }

            // Nomes dos times e placar
            String homeName = "Mandante";
            String awayName = "Visitante";
            Icon homeCrest = null;
            Icon awayCrest = null;
            boolean homeWon = false;
            boolean awayWon = false;

            if (match != null) {
                try {
                    Method hcM = match.getClass().getMethod("hc");
                    Method hdM = match.getClass().getMethod("hd");
                    Object homeTeam = hcM.invoke(match);
                    Object awayTeam = hdM.invoke(match);
                    if (homeTeam != null) {
                        Method getNomeM = homeTeam.getClass().getMethod("getNome");
                        homeName = (String) getNomeM.invoke(homeTeam);
                        try {
                            Method kPM = homeTeam.getClass().getMethod("kP");
                            homeCrest = (Icon) kPM.invoke(homeTeam);
                        } catch (Throwable ignored) {}
                    }
                    if (awayTeam != null) {
                        Method getNomeM = awayTeam.getClass().getMethod("getNome");
                        awayName = (String) getNomeM.invoke(awayTeam);
                        try {
                            Method kPM = awayTeam.getClass().getMethod("kP");
                            awayCrest = (Icon) kPM.invoke(awayTeam);
                        } catch (Throwable ignored) {}
                    }
                    Method huM = match.getClass().getMethod("hu");
                    Method hwM = match.getClass().getMethod("hw");
                    int homeScore = (int) huM.invoke(match);
                    int awayScore = (int) hwM.invoke(match);
                    if (homeScore > awayScore) homeWon = true;
                    else if (awayScore > homeScore) awayWon = true;
                } catch (Throwable ignored) {}
            }

            // Fallback nos labels zO e zP
            if (homeName.equals("Mandante")) {
                try {
                    Field zoF = hkClass.getDeclaredField("zO");
                    zoF.setAccessible(true);
                    JLabel zoLbl = (JLabel) zoF.get(hkComp);
                    if (zoLbl != null && zoLbl.getText() != null) homeName = zoLbl.getText().trim();
                } catch (Throwable ignored) {}
            }
            if (awayName.equals("Visitante")) {
                try {
                    Field zpF = hkClass.getDeclaredField("zP");
                    zpF.setAccessible(true);
                    JLabel zpLbl = (JLabel) zpF.get(hkComp);
                    if (zpLbl != null && zpLbl.getText() != null) awayName = zpLbl.getText().trim();
                } catch (Throwable ignored) {}
            }

            // Jogadores Mandante (KP) e Visitante (KQ)
            Field kpF = hkClass.getDeclaredField("KP");
            kpF.setAccessible(true);
            ArrayList<?> homeItems = (ArrayList<?>) kpF.get(hkComp);

            Field kqF = hkClass.getDeclaredField("KQ");
            kqF.setAccessible(true);
            ArrayList<?> awayItems = (ArrayList<?>) kqF.get(hkComp);

            MvpInfo best = null;

            if (homeItems != null) {
                for (Object item : homeItems) {
                    MvpInfo candidate = evaluatePlayer(item, homeName, homeCrest, true, homeWon);
                    best = pickBetter(best, candidate);
                }
            }

            if (awayItems != null) {
                for (Object item : awayItems) {
                    MvpInfo candidate = evaluatePlayer(item, awayName, awayCrest, false, awayWon);
                    best = pickBetter(best, candidate);
                }
            }

            currentMvp = best;
            return best;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private static MvpInfo evaluatePlayer(Object itemObj, String teamName, Icon crest, boolean isHome, boolean isWinner) {
        if (itemObj == null) return null;
        try {
            Method fbM = itemObj.getClass().getMethod("fb");
            Object player = fbM.invoke(itemObj);
            if (player == null) return null;

            Method gkM = player.getClass().getMethod("gk");
            double r = (double) gkM.invoke(player);
            if (r < 2.0) return null; // Sem nota (jogou pouco ou não entrou)

            Method getNomeM = player.getClass().getMethod("getNome");
            String pName = (String) getNomeM.invoke(player);

            int goals = 0;
            int assists = 0;
            try {
                Method gbM = player.getClass().getMethod("gB");
                Object stats = gbM.invoke(player);
                if (stats != null) {
                    try {
                        Method tuM = stats.getClass().getMethod("tu");
                        goals = (int) tuM.invoke(stats);
                    } catch (Throwable ignored) {}
                    try {
                        Method cdM = stats.getClass().getMethod("cD");
                        assists = (int) cdM.invoke(stats);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            MvpInfo info = new MvpInfo();
            info.player = player;
            info.playerName = pName != null ? pName : "Jogador";
            info.teamName = teamName;
            info.teamCrest = crest;
            info.rating = r;
            info.goals = goals;
            info.assists = assists;
            info.isHome = isHome;
            info.isWinner = isWinner;
            return info;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static MvpInfo pickBetter(MvpInfo a, MvpInfo b) {
        if (a == null) return b;
        if (b == null) return a;

        // 1. Maior nota
        if (b.rating > a.rating + 0.001) return b;
        if (a.rating > b.rating + 0.001) return a;

        // 2. Empate de nota -> Maior número de gols
        if (b.goals > a.goals) return b;
        if (a.goals > b.goals) return a;

        // 3. Maior número de assistências
        if (b.assists > a.assists) return b;
        if (a.assists > b.assists) return a;

        // 4. Time vencedor
        if (b.isWinner && !a.isWinner) return b;
        if (a.isWinner && !b.isWinner) return a;

        // 5. Time mandante
        if (b.isHome && !a.isHome) return b;
        return a;
    }

    /**
     * Injeta os renderizadores de MVP nas tabelas de atletas do pós-jogo (a.hk)
     * - Lr: Tabela do time Mandante (KP)
     * - Ls: Tabela do time Visitante (KQ)
     * - DG: Tabela central de eventos (KO)
     */
    public static void injectMvpUi(Component hkComp) {
        if (!(hkComp instanceof Container)) return;
        Container hk = (Container) hkComp;

        // Evita reinjeção se já configurado
        if ("MvpUiInjected".equals(hk.getName())) {
            refreshMvpUi(hk);
            return;
        }
        hk.setName("MvpUiInjected");

        try {
            Class<?> hkClass = hkComp.getClass();
            ClassLoader cl = hkClass.getClassLoader();
            getStarIcon(cl);

            // 1. Calcula MVP inicial
            computeMvp(hk);

            // 2. Custom Renderer em TODAS as tabelas de jogadores (Mandante = Lr, Visitante = Ls)
            Class<?> compIClass = Class.forName("components.i", true, cl);

            for (String fieldName : new String[]{"Lr", "Ls", "DG"}) {
                try {
                    Field f = hkClass.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    JTable table = (JTable) f.get(hk);
                    if (table != null) {
                        TableCellRenderer origRenderer = table.getDefaultRenderer(compIClass);
                        if (!(origRenderer instanceof MvpTableCellRenderer)) {
                            table.setDefaultRenderer(compIClass, new MvpTableCellRenderer(origRenderer));
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 3. Listener no seletor de partidas da rodada (va)
            try {
                Field vaF = hkClass.getDeclaredField("va");
                vaF.setAccessible(true);
                JComboBox<?> vaBox = (JComboBox<?>) vaF.get(hk);
                if (vaBox != null) {
                    vaBox.addItemListener(e -> {
                        if (e.getStateChange() == ItemEvent.SELECTED) {
                            SwingUtilities.invokeLater(() -> refreshMvpUi(hk));
                        }
                    });
                }
            } catch (Throwable ignored) {}

            // Atualiza exibição inicial
            refreshMvpUi(hk);
            hk.validate();
            hk.repaint();
            System.out.println("[MOD MVP] 🏆 Melhor da Partida injetado com sucesso nas tabelas de atletas!");
        } catch (Throwable t) {
            System.err.println("[MOD MVP] Erro ao injetar interface do Melhor da Partida: " + t);
            t.printStackTrace();
        }
    }

    /**
     * Recalcula MVP e força repaint de todas as tabelas
     */
    public static void refreshMvpUi(Container hk) {
        computeMvp(hk);

        try {
            for (String fieldName : new String[]{"Lr", "Ls", "DG"}) {
                Field f = hk.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                JTable table = (JTable) f.get(hk);
                if (table != null) table.repaint();
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Renderizador customizado que decora o b.aD original destacando o MVP
     * - Coluna 0 (Nome): Exibe a estrela única ao lado do nome do craque em ouro suave.
     * - Coluna 1 (Ícones): Deixa nativo (preserva gols e assistências).
     * - Coluna 2 (Nota): Exibe a nota limpa e 100% visível em ouro brilhante (sem ícone para não truncar).
     */
    public static class MvpTableCellRenderer implements TableCellRenderer {
        private final TableCellRenderer delegate;

        public MvpTableCellRenderer(TableCellRenderer delegate) {
            this.delegate = delegate;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!(c instanceof JLabel)) return c;
            JLabel lbl = (JLabel) c;

            MvpInfo mvp = currentMvp;
            if (mvp == null || mvp.player == null) return lbl;

            Object player = getPlayerFromItem(value);
            if (player == null || player != mvp.player) {
                return lbl;
            }

            // === DESTAQUE DO MELHOR DA PARTIDA (MVP) ===
            ImageIcon sIcon = starIcon != null ? starIcon : getStarIcon(table.getClass().getClassLoader());

            if (column == 2) {
                // Coluna da Nota (width 25px): NUNCA colocar ícone para não truncar em '...'!
                // Exibe a nota com clareza absoluta em dourado ouro
                lbl.setIcon(null);
                lbl.setForeground(new Color(255, 215, 0)); // Dourado Ouro Brilhante
                lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
            } else if (column == 0) {
                // Coluna do Nome (width 180px): Estrela única ao lado do nome do craque
                if (sIcon != null) {
                    lbl.setIcon(sIcon);
                    lbl.setHorizontalTextPosition(SwingConstants.RIGHT);
                }
                lbl.setForeground(new Color(255, 238, 150)); // Ouro suave
                lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
            }
            // Coluna 1: Não alterada, preserva ícones nativos de gols e assistências do jogo

            return lbl;
        }

        private Object getPlayerFromItem(Object itemObj) {
            if (itemObj == null) return null;
            try {
                Method fbM = itemObj.getClass().getMethod("fb");
                return fbM.invoke(itemObj);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
