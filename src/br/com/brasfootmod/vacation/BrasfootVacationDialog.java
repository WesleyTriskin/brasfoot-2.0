package br.com.brasfootmod.vacation;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Calendar;
import java.util.Date;

/**
 * Janela Modal de Configuração para o Treinador Sair de Férias.
 */
public class BrasfootVacationDialog extends JDialog {

    private final JRadioButton rb1Semana;
    private final JRadioButton rb2Semanas;
    private final JRadioButton rb1Mes;
    private final JRadioButton rb3Meses;
    private final JRadioButton rbFimEstadual;
    private final JRadioButton rbFimTemporada;
    private final JRadioButton rbDataEspecifica;
    private final JSpinner spinnerData;

    private final JCheckBox chkAutoEscalacao;
    private final JCheckBox chkRejeitarPropostas;
    private final JCheckBox chkPararZ4;

    private final Calendar dataAtualJogo;
    private final Calendar dataFimEstadual;

    public BrasfootVacationDialog(JFrame parent) {
        super(parent, "🏖️ Mod Brasfoot — Sair de Férias", true);
        setLayout(new BorderLayout());
        setResizable(false);

        dataAtualJogo = BrasfootVacationController.getCurrentGameCalendar() != null
                ? BrasfootVacationController.getCurrentGameCalendar()
                : Calendar.getInstance();

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(new Color(24, 30, 26));
        root.setBorder(new EmptyBorder(16, 20, 16, 20));

        // 1. Cabeçalho
        JPanel pnlHeader = new JPanel(new GridLayout(2, 1, 0, 4));
        pnlHeader.setOpaque(false);

        JLabel lblTitulo = new JLabel("🏖️ CENTRAL DE FÉRIAS DO TREINADOR");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitulo.setForeground(new Color(46, 204, 113));

        String clube = BrasfootVacationController.getHumanClubName();
        String strDataAtual = BrasfootVacationController.formatCalendar(dataAtualJogo);
        int rodada = BrasfootVacationController.getCurrentRound();
        JLabel lblSub = new JLabel(String.format("Clube: %s  |  Data interna do jogo: %s (Semana %d)", clube, strDataAtual, rodada));
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(220, 220, 220));

        pnlHeader.add(lblTitulo);
        pnlHeader.add(lblSub);
        root.add(pnlHeader);
        root.add(Box.createVerticalStrut(14));

        // 2. Duração das Férias
        JPanel pnlDuracao = new JPanel();
        pnlDuracao.setLayout(new BoxLayout(pnlDuracao, BoxLayout.Y_AXIS));
        pnlDuracao.setOpaque(false);
        pnlDuracao.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(50, 70, 55)),
                "📅 Período de Retorno (Baseado no Calendário do Jogo)",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(46, 204, 113)
        ));

        dataFimEstadual = BrasfootVacationController.getDataFimEstadual();
        boolean temEstadualValido = dataFimEstadual != null && dataAtualJogo.before(dataFimEstadual);

        rb1Semana = criarRadio("1 Semana (Próxima rodada do calendário)", false);
        rb2Semanas = criarRadio("2 Semanas (~2 a 3 jogos)", false);
        rb1Mes = criarRadio("1 Mês (~5 a 8 jogos)", !temEstadualValido);
        rb3Meses = criarRadio("3 Meses (~15 jogos)", false);

        // Opção inteligente: Até o Fim do Estadual (selecionada por padrão se o estadual estiver em andamento!)
        if (dataFimEstadual != null) {
            String strFimEst = BrasfootVacationController.formatCalendar(dataFimEstadual);
            if (temEstadualValido) {
                rbFimEstadual = criarRadio("🏆 Até o Fim do Estadual (" + strFimEst + ")", true);
                rbFimEstadual.setEnabled(true);
            } else {
                rbFimEstadual = criarRadio("Até o Fim do Estadual (Encerrado em " + strFimEst + ")", false);
                rbFimEstadual.setEnabled(false);
                rbFimEstadual.setForeground(new Color(130, 130, 130));
            }
        } else {
            rbFimEstadual = criarRadio("Até o Fim do Estadual (Sem estaduais no save)", false);
            rbFimEstadual.setEnabled(false);
            rbFimEstadual.setForeground(new Color(130, 130, 130));
        }

        rbFimTemporada = criarRadio("Até o Fim da Temporada Atual (" + dataAtualJogo.get(Calendar.YEAR) + ")", false);
        rbDataEspecifica = criarRadio("Escolher Data Específica de Retorno no Jogo:", false);

        ButtonGroup group = new ButtonGroup();
        group.add(rb1Semana);
        group.add(rb2Semanas);
        group.add(rb1Mes);
        group.add(rb3Meses);
        group.add(rbFimEstadual);
        group.add(rbFimTemporada);
        group.add(rbDataEspecifica);

        pnlDuracao.add(rb1Semana);
        pnlDuracao.add(rb2Semanas);
        pnlDuracao.add(rb1Mes);
        pnlDuracao.add(rb3Meses);
        pnlDuracao.add(rbFimEstadual);
        pnlDuracao.add(rbFimTemporada);

        // Seletor de data específica
        JPanel pnlDataEsp = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        pnlDataEsp.setOpaque(false);
        pnlDataEsp.add(rbDataEspecifica);

        Calendar minCal = (Calendar) dataAtualJogo.clone();
        minCal.add(Calendar.DAY_OF_YEAR, 1);
        SpinnerDateModel dateModel = new SpinnerDateModel(minCal.getTime(), minCal.getTime(), null, Calendar.DAY_OF_YEAR);
        spinnerData = new JSpinner(dateModel);
        spinnerData.setEditor(new JSpinner.DateEditor(spinnerData, "dd/MM/yyyy"));
        spinnerData.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        spinnerData.setEnabled(false);
        pnlDataEsp.add(spinnerData);

        rbDataEspecifica.addActionListener(e -> spinnerData.setEnabled(rbDataEspecifica.isSelected()));
        rb1Semana.addActionListener(e -> spinnerData.setEnabled(false));
        rb2Semanas.addActionListener(e -> spinnerData.setEnabled(false));
        rb1Mes.addActionListener(e -> spinnerData.setEnabled(false));
        rb3Meses.addActionListener(e -> spinnerData.setEnabled(false));
        rbFimEstadual.addActionListener(e -> spinnerData.setEnabled(false));
        rbFimTemporada.addActionListener(e -> spinnerData.setEnabled(false));

        pnlDuracao.add(pnlDataEsp);
        root.add(pnlDuracao);
        root.add(Box.createVerticalStrut(14));

        // 3. Diretrizes para a Comissão Técnica
        JPanel pnlDiretrizes = new JPanel();
        pnlDiretrizes.setLayout(new BoxLayout(pnlDiretrizes, BoxLayout.Y_AXIS));
        pnlDiretrizes.setOpaque(false);
        pnlDiretrizes.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(50, 70, 55)),
                "⚙️ Instruções para o Auxiliar Técnico",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(46, 204, 113)
        ));

        chkAutoEscalacao = criarCheck("Escalar automaticamente os melhores jogadores disponíveis por força/moral", true);
        chkRejeitarPropostas = criarCheck("Rejeitar automaticamente todas as propostas de transferência de jogadores", true);
        chkPararZ4 = criarCheck("Interromper férias imediatamente se o clube entrar na zona de rebaixamento (Z4)", true);

        pnlDiretrizes.add(chkAutoEscalacao);
        pnlDiretrizes.add(chkRejeitarPropostas);
        pnlDiretrizes.add(chkPararZ4);

        root.add(pnlDiretrizes);
        root.add(Box.createVerticalStrut(18));

        // 4. Botões de Ação
        JPanel pnlBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        pnlBotoes.setOpaque(false);

        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnCancelar.setBackground(new Color(60, 60, 60));
        btnCancelar.setForeground(Color.WHITE);
        btnCancelar.setFocusPainted(false);
        btnCancelar.addActionListener(e -> dispose());

        JButton btnViajar = new JButton("✈️ Partir para as Férias");
        btnViajar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnViajar.setBackground(new Color(46, 204, 113));
        btnViajar.setForeground(new Color(15, 30, 20));
        btnViajar.setFocusPainted(false);
        btnViajar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnViajar.addActionListener(e -> confirmarEIniciar());

        pnlBotoes.add(btnCancelar);
        pnlBotoes.add(btnViajar);
        root.add(pnlBotoes);

        add(root, BorderLayout.CENTER);
        pack();
        setSize(540, 520);
        setLocationRelativeTo(parent);
    }

    private JRadioButton criarRadio(String texto, boolean selecionado) {
        JRadioButton rb = new JRadioButton(texto, selecionado);
        rb.setOpaque(false);
        rb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        rb.setForeground(Color.WHITE);
        rb.setFocusPainted(false);
        return rb;
    }

    private JCheckBox criarCheck(String texto, boolean selecionado) {
        JCheckBox chk = new JCheckBox(texto, selecionado);
        chk.setOpaque(false);
        chk.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chk.setForeground(new Color(230, 230, 230));
        chk.setFocusPainted(false);
        return chk;
    }

    private void confirmarEIniciar() {
        Calendar dataAlvo = (Calendar) dataAtualJogo.clone();
        boolean pararEstadual = false;

        if (rbFimEstadual != null && rbFimEstadual.isSelected()) {
            pararEstadual = true;
            if (dataFimEstadual != null) {
                dataAlvo = (Calendar) dataFimEstadual.clone();
                // Avança 1 dia além da rodada final projetada para garantir que o último jogo seja simulado
                dataAlvo.add(Calendar.DAY_OF_YEAR, 1);
            } else {
                dataAlvo.add(Calendar.MONTH, 3);
            }
        } else if (rb1Semana.isSelected()) {
            dataAlvo.add(Calendar.WEEK_OF_YEAR, 1);
        } else if (rb2Semanas.isSelected()) {
            dataAlvo.add(Calendar.WEEK_OF_YEAR, 2);
        } else if (rb1Mes.isSelected()) {
            dataAlvo.add(Calendar.MONTH, 1);
        } else if (rb3Meses.isSelected()) {
            dataAlvo.add(Calendar.MONTH, 3);
        } else if (rbFimTemporada.isSelected()) {
            dataAlvo.set(Calendar.MONTH, Calendar.DECEMBER);
            dataAlvo.set(Calendar.DAY_OF_MONTH, 31);
            dataAlvo.set(Calendar.HOUR_OF_DAY, 23);
        } else if (rbDataEspecifica.isSelected()) {
            Date d = (Date) spinnerData.getValue();
            dataAlvo.setTime(d);
        }

        boolean rej = chkRejeitarPropostas.isSelected();
        boolean z4 = chkPararZ4.isSelected();
        boolean auto = chkAutoEscalacao.isSelected();

        dispose();

        // Inicia as férias no controlador
        BrasfootVacationController.iniciarFerias(dataAlvo, rej, z4, auto, pararEstadual);
    }

    public static void abrir(JFrame parent) {
        SwingUtilities.invokeLater(() -> {
            BrasfootVacationDialog dlg = new BrasfootVacationDialog(parent);
            if (dlg.dataAtualJogo != null) {
                dlg.setVisible(true);
            }
        });
    }
}
