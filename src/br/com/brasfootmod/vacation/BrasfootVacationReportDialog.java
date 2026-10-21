package br.com.brasfootmod.vacation;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Calendar;
import java.util.List;

/**
 * Janela Modal de Relatório de Retorno das Férias.
 * Apresenta desempenho do time, aproveitamento, placares e eventos do período.
 */
public class BrasfootVacationReportDialog extends JDialog {

    public BrasfootVacationReportDialog(Calendar dataInicio, Calendar dataFim,
                                        boolean interrompido, String motivo,
                                        int jogos, int vitorias, int empates, int derrotas,
                                        int golsPro, int golsContra,
                                        List<String> historicoPartidas,
                                        List<String> eventos) {
        super((JFrame) null, "🛬 Relatório de Retorno das Férias", true);
        setLayout(new BorderLayout());
        setResizable(false);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(new Color(24, 30, 26));
        root.setBorder(new EmptyBorder(18, 20, 18, 20));

        // 1. Cabeçalho
        JPanel pnlHeader = new JPanel(new GridLayout(3, 1, 0, 3));
        pnlHeader.setOpaque(false);

        JLabel lblTitle = new JLabel(interrompido ? "⚠️ RETORNO ANTECIPADO DAS FÉRIAS" : "🛬 BEM-VINDO DE VOLTA, PROFESSOR!");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitle.setForeground(interrompido ? new Color(231, 76, 60) : new Color(46, 204, 113));

        String strInicio = BrasfootVacationController.formatCalendar(dataInicio);
        String strFim = BrasfootVacationController.formatCalendar(dataFim);
        JLabel lblPeriodo = new JLabel(String.format("Período simulado: %s  ➔  %s", strInicio, strFim));
        lblPeriodo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblPeriodo.setForeground(Color.WHITE);

        JLabel lblMotivo = new JLabel("Status: " + motivo);
        lblMotivo.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblMotivo.setForeground(new Color(200, 200, 200));

        pnlHeader.add(lblTitle);
        pnlHeader.add(lblPeriodo);
        pnlHeader.add(lblMotivo);
        root.add(pnlHeader);
        root.add(Box.createVerticalStrut(14));

        // 2. Cards de Estatísticas Resumidas
        JPanel pnlCards = new JPanel(new GridLayout(1, 5, 8, 0));
        pnlCards.setOpaque(false);

        double aproveitamento = jogos > 0 ? ((vitorias * 3.0 + empates) / (jogos * 3.0)) * 100.0 : 0.0;
        int saldoGols = golsPro - golsContra;

        pnlCards.add(criarCard("Partidas", String.valueOf(jogos), Color.WHITE));
        pnlCards.add(criarCard("Vitórias", String.valueOf(vitorias), new Color(46, 204, 113)));
        pnlCards.add(criarCard("Empates", String.valueOf(empates), new Color(241, 196, 15)));
        pnlCards.add(criarCard("Derrotas", String.valueOf(derrotas), new Color(231, 76, 60)));
        pnlCards.add(criarCard("Aproveitamento", String.format("%.1f%%", aproveitamento), new Color(52, 152, 219)));

        root.add(pnlCards);
        root.add(Box.createVerticalStrut(8));

        // Card secundário de gols
        JLabel lblGols = new JLabel(String.format("Gols no período: %d Marcados  |  %d Sofridos  |  Saldo: %+d", golsPro, golsContra, saldoGols));
        lblGols.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblGols.setForeground(new Color(220, 220, 220));
        lblGols.setAlignmentX(Component.CENTER_ALIGNMENT);
        root.add(lblGols);
        root.add(Box.createVerticalStrut(12));

        // 3. Tabela de Partidas Disputadas no Período
        JPanel pnlTabela = new JPanel(new BorderLayout());
        pnlTabela.setOpaque(false);
        pnlTabela.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(50, 70, 55)),
                "⚽ Partidas Disputadas pelo Auxiliar",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(46, 204, 113)
        ));

        String[] colunas = new String[]{"Resultado e Placar da Partida"};
        DefaultTableModel model = new DefaultTableModel(colunas, 0);

        if (historicoPartidas == null || historicoPartidas.isEmpty()) {
            model.addRow(new Object[]{"Nenhuma partida oficial disputada durante este período de férias."});
        } else {
            for (String reg : historicoPartidas) {
                model.addRow(new Object[]{reg});
            }
        }

        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(24);
        table.setBackground(new Color(30, 38, 32));
        table.setForeground(Color.WHITE);
        table.setShowGrid(false);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setHorizontalAlignment(SwingConstants.LEFT);
        renderer.setBackground(new Color(30, 38, 32));
        renderer.setForeground(Color.WHITE);
        table.getColumnModel().getColumn(0).setCellRenderer(renderer);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(540, 140));
        scroll.getViewport().setBackground(new Color(30, 38, 32));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        pnlTabela.add(scroll, BorderLayout.CENTER);
        root.add(pnlTabela);
        root.add(Box.createVerticalStrut(12));

        // 4. Ocorrências / Eventos
        if (eventos != null && !eventos.isEmpty()) {
            JPanel pnlEventos = new JPanel(new BorderLayout());
            pnlEventos.setOpaque(false);
            pnlEventos.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(50, 70, 55)),
                    "📋 Notificações e Ocorrências",
                    TitledBorder.LEFT,
                    TitledBorder.TOP,
                    new Font("Segoe UI", Font.BOLD, 11),
                    new Color(241, 196, 15)
            ));

            DefaultListModel<String> listModel = new DefaultListModel<>();
            for (String ev : eventos) {
                listModel.addElement("• " + ev);
            }
            JList<String> list = new JList<>(listModel);
            list.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            list.setBackground(new Color(30, 38, 32));
            list.setForeground(Color.WHITE);
            JScrollPane scrollEv = new JScrollPane(list);
            scrollEv.setPreferredSize(new Dimension(540, 50));
            scrollEv.setBorder(BorderFactory.createEmptyBorder());
            pnlEventos.add(scrollEv, BorderLayout.CENTER);
            root.add(pnlEventos);
            root.add(Box.createVerticalStrut(14));
        }

        // 5. Botão de Fechar / Reassumir Time
        JPanel pnlBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pnlBotoes.setOpaque(false);

        JButton btnAssumir = new JButton("⚽ Reassumir Comando do Elenco");
        btnAssumir.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnAssumir.setBackground(new Color(46, 204, 113));
        btnAssumir.setForeground(new Color(15, 30, 20));
        btnAssumir.setFocusPainted(false);
        btnAssumir.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnAssumir.addActionListener(e -> dispose());

        pnlBotoes.add(btnAssumir);
        root.add(pnlBotoes);

        add(root, BorderLayout.CENTER);
        pack();
        setSize(580, (eventos != null && !eventos.isEmpty()) ? 520 : 450);
        setLocationRelativeTo(null);
    }

    private JPanel criarCard(String titulo, String valor, Color corValor) {
        JPanel card = new JPanel(new GridLayout(2, 1, 0, 2));
        card.setBackground(new Color(32, 42, 35));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(48, 62, 52), 1),
                new EmptyBorder(6, 4, 6, 4)
        ));

        JLabel lblTit = new JLabel(titulo, SwingConstants.CENTER);
        lblTit.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblTit.setForeground(new Color(180, 180, 180));

        JLabel lblVal = new JLabel(valor, SwingConstants.CENTER);
        lblVal.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblVal.setForeground(corValor);

        card.add(lblTit);
        card.add(lblVal);
        return card;
    }

    public static void mostrarRelatorio(Calendar inicio, Calendar fim, boolean interrompido, String motivo,
                                        int jogos, int vitorias, int empates, int derrotas,
                                        int golsPro, int golsContra,
                                        List<String> historico, List<String> eventos) {
        SwingUtilities.invokeLater(() -> {
            BrasfootVacationReportDialog dlg = new BrasfootVacationReportDialog(
                    inicio, fim, interrompido, motivo, jogos, vitorias, empates, derrotas,
                    golsPro, golsContra, historico, eventos
            );
            dlg.setVisible(true);
        });
    }
}
