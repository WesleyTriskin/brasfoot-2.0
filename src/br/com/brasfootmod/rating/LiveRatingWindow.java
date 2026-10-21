package br.com.brasfootmod.rating;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class LiveRatingWindow extends JFrame {

    private static LiveRatingWindow instance;

    private JLabel lblPlacar;
    private JLabel lblStatus;
    private JComboBox<MatchItem> cmbPartidas;
    private JTable tblHome;
    private JTable tblAway;
    private DefaultTableModel modelHome;
    private DefaultTableModel modelAway;
    private JTabbedPane tabs;
    private Timer refreshTimer;
    private JCheckBox chkAlwaysOnTop;
    private boolean isUpdatingCombo = false;

    private static class MatchItem {
        final best.I match;
        final String label;

        MatchItem(best.I match, String label) {
            this.match = match;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static synchronized void toggle() {
        if (instance == null) {
            instance = new LiveRatingWindow();
        }
        if (instance.isVisible()) {
            instance.setVisible(false);
        } else {
            instance.setLocationRelativeTo(null);
            instance.setVisible(true);
            instance.atualizar();
        }
    }

    public static synchronized void abrirSeNaoAberto() {
        if (instance == null) {
            instance = new LiveRatingWindow();
        }
        if (!instance.isVisible()) {
            instance.setLocationRelativeTo(null);
            instance.setVisible(true);
            instance.atualizar();
        }
    }

    public LiveRatingWindow() {
        super("⭐ Classificação dos Jogadores ao Vivo (Mod Brasfoot)");
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(820, 540);
        setMinimumSize(new Dimension(680, 420));
        setLayout(new BorderLayout());

        // Header Panel
        JPanel pnlHeader = new JPanel();
        pnlHeader.setLayout(new BoxLayout(pnlHeader, BoxLayout.Y_AXIS));
        pnlHeader.setBackground(new Color(24, 30, 42));
        pnlHeader.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JPanel pnlTopRow = new JPanel(new BorderLayout(10, 0));
        pnlTopRow.setOpaque(false);

        JLabel lblPartida = new JLabel("Partida: ");
        lblPartida.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblPartida.setForeground(Color.LIGHT_GRAY);

        cmbPartidas = new JComboBox<>();
        cmbPartidas.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cmbPartidas.setBackground(Color.WHITE);
        cmbPartidas.addActionListener(e -> {
            if (!isUpdatingCombo) {
                atualizarTabelas();
            }
        });

        chkAlwaysOnTop = new JCheckBox("Sempre no topo", true);
        chkAlwaysOnTop.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkAlwaysOnTop.setForeground(Color.LIGHT_GRAY);
        chkAlwaysOnTop.setOpaque(false);
        chkAlwaysOnTop.addActionListener(e -> setAlwaysOnTop(chkAlwaysOnTop.isSelected()));
        setAlwaysOnTop(true);

        JPanel pnlSelector = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        pnlSelector.setOpaque(false);
        pnlSelector.add(lblPartida);
        pnlSelector.add(cmbPartidas);

        pnlTopRow.add(pnlSelector, BorderLayout.CENTER);
        pnlTopRow.add(chkAlwaysOnTop, BorderLayout.EAST);

        lblPlacar = new JLabel("Aguardando início de partida...", SwingConstants.CENTER);
        lblPlacar.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblPlacar.setForeground(new Color(255, 215, 0));
        lblPlacar.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
        lblPlacar.setAlignmentX(Component.CENTER_ALIGNMENT);

        pnlHeader.add(pnlTopRow);
        pnlHeader.add(lblPlacar);
        add(pnlHeader, BorderLayout.NORTH);

        // Center Tables
        String[] cols = {"Pos", "Jogador", "Gols", "Assists", "Cartão", "Nota"};

        modelHome = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        modelAway = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        tblHome = createStyledTable(modelHome);
        tblAway = createStyledTable(modelAway);

        tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.addTab("Time Mandante", new JScrollPane(tblHome));
        tabs.addTab("Time Visitante", new JScrollPane(tblAway));

        add(tabs, BorderLayout.CENTER);

        // Bottom Status Panel
        JPanel pnlBottom = new JPanel(new BorderLayout());
        pnlBottom.setBackground(new Color(24, 30, 42));
        pnlBottom.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));

        lblStatus = new JLabel("Atualização dinâmica em tempo real (Pressione F3 para abrir/fechar)");
        lblStatus.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblStatus.setForeground(Color.LIGHT_GRAY);

        JButton btnAtualizar = new JButton("🔄 Atualizar");
        btnAtualizar.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnAtualizar.setFocusable(false);
        btnAtualizar.addActionListener(e -> atualizar());

        pnlBottom.add(lblStatus, BorderLayout.CENTER);
        pnlBottom.add(btnAtualizar, BorderLayout.EAST);
        add(pnlBottom, BorderLayout.SOUTH);

        // Timer de auto-atualização a cada 1 segundo enquanto visível
        refreshTimer = new Timer(1000, e -> {
            if (isVisible()) {
                atualizar();
            }
        });
        refreshTimer.start();
    }

    private JTable createStyledTable(DefaultTableModel model) {
        JTable tbl = new JTable(model);
        tbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tbl.setRowHeight(25);
        tbl.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        tbl.getTableHeader().setBackground(new Color(44, 62, 80));
        tbl.getTableHeader().setForeground(Color.WHITE);
        tbl.setGridColor(new Color(230, 230, 230));

        // Renderizador colorido para a coluna de Classificação/Nota (coluna 5)
        tbl.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setHorizontalAlignment(SwingConstants.CENTER);
                c.setFont(new Font("Segoe UI", Font.BOLD, 13));
                if (value != null) {
                    try {
                        double r = Double.parseDouble(value.toString());
                        c.setForeground(LiveRatingCalculator.getRatingColor(r));
                    } catch (Throwable ignored) {
                        c.setForeground(Color.BLACK);
                    }
                }
                return c;
            }
        });

        // Alinhamento central para Pos, Gols, Assists, Cartão
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        tbl.getColumnModel().getColumn(0).setCellRenderer(center);
        tbl.getColumnModel().getColumn(2).setCellRenderer(center);
        tbl.getColumnModel().getColumn(3).setCellRenderer(center);
        tbl.getColumnModel().getColumn(4).setCellRenderer(center);

        tbl.getColumnModel().getColumn(0).setPreferredWidth(45);
        tbl.getColumnModel().getColumn(1).setPreferredWidth(210);
        tbl.getColumnModel().getColumn(2).setPreferredWidth(60);
        tbl.getColumnModel().getColumn(3).setPreferredWidth(60);
        tbl.getColumnModel().getColumn(4).setPreferredWidth(95);
        tbl.getColumnModel().getColumn(5).setPreferredWidth(85);

        return tbl;
    }

    public void atualizar() {
        try {
            Object matchComp = br.com.brasfootmod.BrasfootAgent.findActiveMatch();
            if (matchComp == null) {
                matchComp = br.com.brasfootmod.BrasfootAgent.findActiveHalftimeDialog();
            }

            if (matchComp == null) {
                lblPlacar.setText("Nenhuma partida em andamento no momento");
                lblStatus.setText("As notas são calculadas dinamicamente durante os jogos.");
                modelHome.setRowCount(0);
                modelAway.setRowCount(0);
                return;
            }

            List<MatchItem> matches = getAvailableMatches(matchComp);
            if (matches.isEmpty()) {
                lblPlacar.setText("Carregando dados da partida...");
                return;
            }

            // Atualiza ComboBox de partidas preservando seleção se possível
            best.I selectedMatch = null;
            MatchItem curItem = (MatchItem) cmbPartidas.getSelectedItem();
            if (curItem != null) {
                selectedMatch = curItem.match;
            }

            isUpdatingCombo = true;
            try {
                cmbPartidas.removeAllItems();
                MatchItem toSelect = null;
                for (MatchItem mi : matches) {
                    cmbPartidas.addItem(mi);
                    if (selectedMatch != null && mi.match == selectedMatch) {
                        toSelect = mi;
                    }
                }
                if (toSelect != null) {
                    cmbPartidas.setSelectedItem(toSelect);
                } else if (cmbPartidas.getItemCount() > 0) {
                    cmbPartidas.setSelectedIndex(0);
                }
            } finally {
                isUpdatingCombo = false;
            }

            atualizarTabelas();

        } catch (Throwable ignored) {
        }
    }

    private void atualizarTabelas() {
        MatchItem item = (MatchItem) cmbPartidas.getSelectedItem();
        if (item == null || item.match == null) return;

        best.I matchData = item.match;
        String homeName = matchData.hc() != null ? matchData.hc().getNome() : "Mandante";
        String awayName = matchData.hd() != null ? matchData.hd().getNome() : "Visitante";
        int homeGoals = matchData.hM();
        int awayGoals = matchData.hN();
        int minute = Math.max(1, Math.min(90, matchData.hr()));

        lblPlacar.setText(String.format("%s  %d x %d  %s   |   %d' min", homeName, homeGoals, awayGoals, awayName, minute));
        tabs.setTitleAt(0, homeName);
        tabs.setTitleAt(1, awayName);

        // Atualiza tabelas
        updateTeamTable(modelHome, matchData, 0, matchData.hl());
        updateTeamTable(modelAway, matchData, 1, matchData.hm());

        // Calcula MVP e candidato à substituição
        encontrarDestaques(matchData);
    }

    private void updateTeamTable(DefaultTableModel model, best.I matchData, int teamIdx, ArrayList<?> players) {
        model.setRowCount(0);
        if (players == null) return;

        for (int i = 0; i < players.size(); i++) {
            Object obj = players.get(i);
            if (!(obj instanceof best.F)) continue;
            best.F p = (best.F) obj;

            String pos = LiveRatingCalculator.getPosName(p.getPosicao());
            String nome = p.getNome();
            int gols = 0;
            int assists = 0;
            String cartao = "-";

            components.s s = p.gB();
            if (s != null) {
                gols = s.tx();
                assists = s.cD();
                if (s.tA() > 0) cartao = "🟥 Vermelho";
                else if (s.tz() > 0) cartao = "🟨 Amarelo";
            }

            double rating = LiveRatingCalculator.calculateRating(matchData, teamIdx, p);
            model.addRow(new Object[]{pos, nome, gols > 0 ? String.valueOf(gols) : "-", assists > 0 ? String.valueOf(assists) : "-", cartao, String.format("%.1f", rating)});
        }
    }

    private void encontrarDestaques(best.I matchData) {
        best.F mvp = null;
        double maxRating = -1.0;
        best.F pior = null;
        double minRating = 99.0;

        ArrayList<?> all = new ArrayList<>();
        if (matchData.hl() != null) all.addAll(matchData.hl());
        if (matchData.hm() != null) all.addAll(matchData.hm());

        for (Object o : all) {
            if (!(o instanceof best.F)) continue;
            best.F p = (best.F) o;
            int teamIdx = (matchData.hl() != null && matchData.hl().contains(p)) ? 0 : 1;
            double r = LiveRatingCalculator.calculateRating(matchData, teamIdx, p);
            if (r > maxRating) {
                maxRating = r;
                mvp = p;
            }
            if (r < minRating) {
                minRating = r;
            }
        }

        if (mvp != null && pior != null) {
            lblStatus.setText(String.format("🌟 Melhor em Campo: %s (%.1f)   |   ⚠️ Sugestão de Substituição: %s (%.1f)",
                    mvp.getNome(), maxRating, pior.getNome(), minRating));
        }
    }

    private List<MatchItem> getAvailableMatches(Object comp) {
        List<MatchItem> res = new ArrayList<>();
        if (comp == null) return res;

        try {
            if (comp.getClass().getName().endsWith(".dV")) {
                Method qi = comp.getClass().getMethod("qi");
                ArrayList<?> vK = (ArrayList<?>) qi.invoke(comp);
                if (vK != null) {
                    for (Object v : vK) {
                        if (v == null) continue;
                        Method tR = v.getClass().getMethod("tR");
                        best.I m = (best.I) tR.invoke(v);
                        if (m != null) {
                            boolean isUserClub = false;
                            try {
                                Method ei = v.getClass().getMethod("ei");
                                isUserClub = Boolean.TRUE.equals(ei.invoke(v));
                            } catch (Throwable ignored) {}

                            String hName = m.hc() != null ? m.hc().getNome() : "Mandante";
                            String aName = m.hd() != null ? m.hd().getNome() : "Visitante";
                            String label = (isUserClub ? "⭐ " : "") + hName + " x " + aName;
                            MatchItem item = new MatchItem(m, label);
                            if (isUserClub) {
                                res.add(0, item); // Partida do clube do usuário fica sempre no topo
                            } else {
                                res.add(item);
                            }
                        }
                    }
                }
            } else if (comp.getClass().getName().endsWith(".cD")) {
                Field dbField = comp.getClass().getDeclaredField("Db");
                dbField.setAccessible(true);
                Object v = dbField.get(comp);
                if (v != null) {
                    Method tR = v.getClass().getMethod("tR");
                    best.I m = (best.I) tR.invoke(v);
                    if (m != null) {
                        String hName = m.hc() != null ? m.hc().getNome() : "Mandante";
                        String aName = m.hd() != null ? m.hd().getNome() : "Visitante";
                        res.add(new MatchItem(m, "⭐ " + hName + " x " + aName));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return res;
    }
}
