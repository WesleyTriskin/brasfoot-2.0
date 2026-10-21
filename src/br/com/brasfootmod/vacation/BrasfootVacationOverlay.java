package br.com.brasfootmod.vacation;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Calendar;

/**
 * Overlay flutuante exibido no topo da tela enquanto o treinador está de férias.
 * Permite acompanhar o avanço das datas, resultados de jogos e interromper a qualquer momento.
 */
public class BrasfootVacationOverlay extends JWindow {

    private final JLabel lblStatus;
    private final JLabel lblDatas;
    private final JLabel lblUltimoJogo;
    private final JProgressBar progressBar;

    public BrasfootVacationOverlay() {
        setAlwaysOnTop(true);
        setLayout(new BorderLayout());

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(20, 36, 25), 0, getHeight(), new Color(12, 22, 15));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(46, 204, 113));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 16, 16);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BorderLayout(15, 8));
        panel.setBorder(new EmptyBorder(10, 18, 10, 18));

        // Painel Esquerdo com Ícone e Título
        JPanel leftPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        leftPanel.setOpaque(false);

        lblStatus = new JLabel("🏖️ MODO FÉRIAS ATIVADO — Auxiliar Técnico no Comando");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStatus.setForeground(new Color(46, 204, 113));

        lblDatas = new JLabel("📅 Data: Calculando... ➔ Retorno: ...");
        lblDatas.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblDatas.setForeground(Color.WHITE);

        leftPanel.add(lblStatus);
        leftPanel.add(lblDatas);

        // Painel Central com Barra de Progresso e Último Jogo
        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        centerPanel.setOpaque(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 11));
        progressBar.setForeground(new Color(46, 204, 113));
        progressBar.setBackground(new Color(30, 45, 35));
        progressBar.setBorder(BorderFactory.createEmptyBorder());

        lblUltimoJogo = new JLabel("Último resultado: Aguardando...");
        lblUltimoJogo.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblUltimoJogo.setForeground(new Color(241, 196, 15));

        centerPanel.add(progressBar);
        centerPanel.add(lblUltimoJogo);

        // Painel Direito com Botão de Interrupção
        JButton btnCancelar = new JButton("⏹️ Assumir Time Agora");
        btnCancelar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCancelar.setBackground(new Color(231, 76, 60));
        btnCancelar.setForeground(Color.WHITE);
        btnCancelar.setFocusPainted(false);
        btnCancelar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCancelar.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btnCancelar.addActionListener(e -> BrasfootVacationController.cancelarFeriasManual());

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(btnCancelar, BorderLayout.EAST);

        add(panel, BorderLayout.CENTER);
        setSize(780, 72);

        // Posiciona no topo central da tela principal
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenSize.width - 780) / 2, 25);
    }

    public void atualizarStatus(Calendar atual, Calendar retorno, String ultimoResultado, int porcentagem) {
        SwingUtilities.invokeLater(() -> {
            String strAtual = BrasfootVacationController.formatCalendar(atual);
            String strFim = BrasfootVacationController.formatCalendar(retorno);

            lblDatas.setText(String.format("📅 Data Atual: %s ➔ Retorno: %s", strAtual, strFim));
            lblUltimoJogo.setText("Último resultado: " + ultimoResultado);
            progressBar.setValue(porcentagem);
            progressBar.setString(porcentagem + "% concluído");
            repaint();
        });
    }
}
