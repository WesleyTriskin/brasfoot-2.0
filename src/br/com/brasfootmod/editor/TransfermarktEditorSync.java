package br.com.brasfootmod.editor;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;


import br.com.brasfootmod.BrasfootAgent;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gerenciador de Sincronização de Elencos entre Transfermarkt e Brasfoot 22-23.
 */
public class TransfermarktEditorSync {

    private static final String TEAMS_DIR = "C:\\Brasfoot22-23\\teams";
    private static final String BACKUP_DIR = "C:\\Brasfoot22-23\\teams_backup_original";

    private static class ClubBanFile {
        File file;
        Object time;
        String nome;
        int pais;
        String fileRef;

        ClubBanFile(File f, Object t, String n, int p, String fr) {
            this.file = f;
            this.time = t;
            this.nome = n;
            this.pais = p;
            this.fileRef = fr;
        }
    }

    private static class CustomObjectInputStream extends ObjectInputStream {
        private final ClassLoader classLoader;

        public CustomObjectInputStream(InputStream in, ClassLoader cl) throws IOException {
            super(in);
            this.classLoader = cl;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String name = desc.getName();
            ClassLoader[] loaders = {
                classLoader,
                BrasfootAgent.getGameClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                TransfermarktEditorSync.class.getClassLoader()
            };
            for (ClassLoader l : loaders) {
                if (l != null) {
                    try {
                        return Class.forName(name, false, l);
                    } catch (ClassNotFoundException ignored) {}
                }
            }
            return super.resolveClass(desc);
        }
    }

    public static void syncAllBrazilianClubs(Component parent) {
        int confirm = JOptionPane.showConfirmDialog(parent,
                "Deseja atualizar todos os elencos e treinadores dos clubes brasileiros (Série A & Série B) via Transfermarkt?\n\n" +
                "• 11 titulares em campo + 5 reservas imediatos (16 titulares reais)\n" +
                "• Minutagem e valores de mercado oficiais do Transfermarkt\n" +
                "• Atualização do Treinador oficial e nacionalidade\n" +
                "• Características autênticas preservadas do Banco Oficial do Brasfoot\n" +
                "• Apenas times da Série A recebem 1 estrela (atleta de linha mais valioso)\n" +
                "• Elencos balanceados em 22 a 26 jogadores",
                "Atualizar Elencos do Brasil (Transfermarkt)",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        File dir = new File(TEAMS_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            JOptionPane.showMessageDialog(parent, "Pasta teams não encontrada: " + TEAMS_DIR, "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File[] banFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".ban"));
        if (banFiles == null || banFiles.length == 0) {
            JOptionPane.showMessageDialog(parent, "Nenhum arquivo .ban encontrado em teams.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ClassLoader cl = (parent != null) ? parent.getClass().getClassLoader() : BrasfootAgent.getGameClassLoader();
        final ClassLoader clFinal = (cl != null) ? cl : Thread.currentThread().getContextClassLoader();

        Frame owner = (parent instanceof Frame) ? (Frame) parent : (Frame) SwingUtilities.getWindowAncestor(parent);
        JDialog progressDlg = new JDialog(owner, "Atualizando Elencos...", true);
        progressDlg.setSize(500, 250);
        progressDlg.setLocationRelativeTo(parent);
        progressDlg.setLayout(new BorderLayout());
        progressDlg.setResizable(false);
        progressDlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 25, 20, 25));
        content.setBackground(new Color(245, 247, 250));

        JLabel lblTitle = new JLabel("🌐 Atualização em Lote - Clubes do Brasil");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTitle.setForeground(new Color(33, 37, 41));
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblStatus = new JLabel("Identificando clubes brasileiros mapeados...");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(new Color(108, 117, 125));
        lblStatus.setBorder(new EmptyBorder(8, 0, 12, 0));
        lblStatus.setAlignmentX(Component.LEFT_ALIGNMENT);

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(440, 26));
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblDetail = new JLabel("Conectando ao banco de dados do Transfermarkt...");
        lblDetail.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblDetail.setForeground(new Color(100, 100, 100));
        lblDetail.setBorder(new EmptyBorder(10, 0, 10, 0));
        lblDetail.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnCancelar.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(lblTitle);
        content.add(lblStatus);
        content.add(progressBar);
        content.add(lblDetail);
        content.add(btnCancelar);

        progressDlg.add(content, BorderLayout.CENTER);

        SwingWorker<Void, Object> worker = new SwingWorker<Void, Object>() {
            int successCount = 0;
            int totalProcessed = 0;

            @Override
            protected Void doInBackground() throws Exception {
                // Passo 1: Identificação rápida dos clubes em background sem congelar a interface
                List<ClubBanFile> brazilianClubs = new ArrayList<>();
                for (File bf : banFiles) {
                    if (isCancelled()) return null;
                    String cleanRef = bf.getName().replace(".ban", "").toLowerCase().trim();
                    if (TransfermarktScraper.temSlugMapeado(null, cleanRef)) {
                        try {
                            Object team = carregarTime(bf, clFinal);
                            if (team != null) {
                                String nome = (String) team.getClass().getMethod("getNome").invoke(team);
                                int pais = (Integer) team.getClass().getMethod("getPais").invoke(team);
                                String fileRef = cleanRef;
                                try {
                                    String fr = (String) team.getClass().getMethod("getFileRef").invoke(team);
                                    if (fr != null && !fr.trim().isEmpty()) fileRef = fr.trim();
                                } catch (Throwable ignored) {}
                                brazilianClubs.add(new ClubBanFile(bf, team, nome, pais, fileRef));
                            }
                        } catch (Throwable ignored) {}
                    }
                }

                if (brazilianClubs.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("Nenhum clube brasileiro encontrado.");
                    });
                    return null;
                }

                final int totalClubs = brazilianClubs.size();
                SwingUtilities.invokeLater(() -> {
                    progressBar.setMaximum(totalClubs);
                    progressBar.setValue(0);
                    lblStatus.setText(String.format("Iniciando atualização de %d clubes brasileiros...", totalClubs));
                });

                // Passo 2: Itera e atualiza os elencos via Transfermarkt
                for (int i = 0; i < brazilianClubs.size(); i++) {
                    if (isCancelled()) break;

                    ClubBanFile cbf = brazilianClubs.get(i);
                    totalProcessed++;
                    String clubName = cbf.nome;

                    final int idx = i;
                    final int sCount = successCount;
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(idx + 1);
                        lblStatus.setText(String.format("Processando (%d de %d): %s", idx + 1, totalClubs, clubName));
                        lblDetail.setText(String.format("Atualizados com sucesso: %d | Baixando dados...", sCount));
                    });

                    try {
                        String url = TransfermarktScraper.obterUrlKaderClube(clubName, cbf.fileRef);
                        if (url != null) {
                            List<TransfermarktScraper.RawPlayer> squad = TransfermarktScraper.baixarElencoTransfermarkt(url);
                            if (squad != null && squad.size() >= 11) {
                                TransfermarktScraper.CoachInfo coach = TransfermarktScraper.baixarTreinadorTransfermarkt(url, 29);

                                Object freshTime = carregarTime(cbf.file, clFinal);
                                Object timeToUse = (freshTime != null) ? freshTime : cbf.time;

                                boolean ok = TransfermarktRosterBuilder.aplicarElencoNoTime(timeToUse, squad, coach);
                                if (ok) {
                                    salvarTime(cbf.file, timeToUse);
                                    cbf.time = timeToUse;
                                    successCount++;

                                    // Se este clube estiver aberto na tela de edição (d.n), atualiza imediatamente em memória
                                    atualizarJanelaEditorAbertaSeAplicavel(cbf);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }

                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }

                // Passo 3: Passada global única de de-duplicação
                if (!isCancelled()) {
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("Finalizando: Removendo duplicatas entre clubes...");
                        lblDetail.setText("Otimizando integridade de todos os elencos...");
                    });
                    try {
                        executarDeduplicacaoSilenciosa(clFinal);
                    } catch (Throwable ignored) {}
                }
                return null;
            }

            @Override
            protected void done() {
                progressDlg.dispose();
                recarregarTodasJanelasEditorAbertas();

                if (isCancelled()) {
                    JOptionPane.showMessageDialog(parent,
                            "Atualização cancelada pelo usuário.\nProcessados: " + totalProcessed + "\nAtualizados com sucesso: " + successCount,
                            "Cancelado", JOptionPane.WARNING_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(parent,
                            "🎉 Atualização Concluída com Sucesso!\n\n" +
                            "• Clubes nacionais processados: " + totalProcessed + "\n" +
                            "• Clubes atualizados via Transfermarkt: " + successCount + "\n\n" +
                            "Todos os elencos contam com 11 titulares em campo,\n" +
                            "5 reservas imediatos (16 titulares totais por minutagem),\n" +
                            "treinadores oficiais e idades reais atualizadas,\n" +
                            "características autênticas de goleiro/linha e lado correto (D/E).",
                            "Transfermarkt Mod Sync", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        };

        btnCancelar.addActionListener(e -> {
            worker.cancel(true);
            btnCancelar.setEnabled(false);
            btnCancelar.setText("Cancelando...");
        });

        worker.execute();
        progressDlg.setVisible(true);
    }

    private static void atualizarJanelaEditorAbertaSeAplicavel(ClubBanFile cbf) {
        SwingUtilities.invokeLater(() -> {
            try {
                for (Window w : Window.getWindows()) {
                    if (w != null && w.getClass().getName().equals("d.n")) {
                        JFrame frameDn = (JFrame) w;
                        Method mWIC = frameDn.getClass().getMethod("wI");
                        Object time = mWIC.invoke(frameDn);
                        if (time != null) {
                            String nome = (String) time.getClass().getMethod("getNome").invoke(time);
                            if (cbf.nome.equalsIgnoreCase(nome)) {
                                ArrayList<?> newJogs = (ArrayList<?>) cbf.time.getClass().getMethod("getJogadores").invoke(cbf.time);
                                time.getClass().getMethod("setJogadores", ArrayList.class).invoke(time, newJogs);

                                String tec = (String) cbf.time.getClass().getMethod("getTecnico").invoke(cbf.time);
                                int tecNac = (Integer) cbf.time.getClass().getMethod("getTecNac").invoke(cbf.time);
                                time.getClass().getMethod("setTecnico", String.class).invoke(time, tec);
                                time.getClass().getMethod("setTecNac", int.class).invoke(time, tecNac);

                                try {
                                    Field fMS = frameDn.getClass().getDeclaredField("MS");
                                    fMS.setAccessible(true);
                                    JLabel lblMS = (JLabel) fMS.get(frameDn);
                                    if (lblMS != null && tec != null) {
                                        lblMS.setText(tec);
                                    }
                                } catch (Throwable ignored) {}

                                try {
                                    int nivel = (Integer) cbf.time.getClass().getMethod("getNivel").invoke(cbf.time);
                                    time.getClass().getMethod("setNivel", int.class).invoke(time, nivel);
                                    Field fVb = frameDn.getClass().getDeclaredField("Vb");
                                    fVb.setAccessible(true);
                                    JLabel lblVb = (JLabel) fVb.get(frameDn);
                                    if (lblVb != null) {
                                        lblVb.setText(Integer.toString(nivel));
                                    }
                                } catch (Throwable ignored) {}

                                Method mWO = frameDn.getClass().getMethod("wO");
                                JTable table = (JTable) mWO.invoke(frameDn);
                                if (table != null && table.getModel() instanceof AbstractTableModel) {
                                    ((AbstractTableModel) table.getModel()).fireTableDataChanged();
                                }
                                frameDn.validate();
                                frameDn.repaint();
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void recarregarTodasJanelasEditorAbertas() {
        SwingUtilities.invokeLater(() -> {
            try {
                for (Window w : Window.getWindows()) {
                    if (w != null && w.getClass().getName().equals("d.n")) {
                        JFrame frameDn = (JFrame) w;
                        Method mWIC = frameDn.getClass().getMethod("wI");
                        Object time = mWIC.invoke(frameDn);
                        if (time != null) {
                            String fileRef = (String) time.getClass().getMethod("getFileRef").invoke(time);
                            File targetFile = new File(TEAMS_DIR, fileRef + ".ban");
                            if (targetFile.exists()) {
                                Object reloaded = carregarTime(targetFile, frameDn.getClass().getClassLoader());
                                if (reloaded != null) {
                                    ArrayList<?> newJogs = (ArrayList<?>) reloaded.getClass().getMethod("getJogadores").invoke(reloaded);
                                    time.getClass().getMethod("setJogadores", ArrayList.class).invoke(time, newJogs);

                                    String tec = (String) reloaded.getClass().getMethod("getTecnico").invoke(reloaded);
                                    int tecNac = (Integer) reloaded.getClass().getMethod("getTecNac").invoke(reloaded);
                                    time.getClass().getMethod("setTecnico", String.class).invoke(time, tec);
                                    time.getClass().getMethod("setTecNac", int.class).invoke(time, tecNac);

                                    try {
                                        Field fMS = frameDn.getClass().getDeclaredField("MS");
                                        fMS.setAccessible(true);
                                        JLabel lblMS = (JLabel) fMS.get(frameDn);
                                        if (lblMS != null && tec != null) {
                                            lblMS.setText(tec);
                                        }
                                    } catch (Throwable ignored) {}

                                    try {
                                        int nivel = (Integer) reloaded.getClass().getMethod("getNivel").invoke(reloaded);
                                        time.getClass().getMethod("setNivel", int.class).invoke(time, nivel);
                                        Field fVb = frameDn.getClass().getDeclaredField("Vb");
                                        fVb.setAccessible(true);
                                        JLabel lblVb = (JLabel) fVb.get(frameDn);
                                        if (lblVb != null) {
                                            lblVb.setText(Integer.toString(nivel));
                                        }
                                    } catch (Throwable ignored) {}

                                    Method mWO = frameDn.getClass().getMethod("wO");
                                    JTable table = (JTable) mWO.invoke(frameDn);
                                    if (table != null && table.getModel() instanceof AbstractTableModel) {
                                        ((AbstractTableModel) table.getModel()).fireTableDataChanged();
                                    }
                                    frameDn.validate();
                                    frameDn.repaint();
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void atualizarClubeNoEditor(JFrame frameDn) {
        if (frameDn == null || !frameDn.getClass().getName().equals("d.n")) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JFrame && w.getClass().getName().equals("d.n") && w.isShowing()) {
                    frameDn = (JFrame) w;
                    break;
                }
            }
        }

        if (frameDn == null || !frameDn.getClass().getName().equals("d.n")) {
            int op = JOptionPane.showConfirmDialog(frameDn,
                    "A tela de edição de clube individual (Editor de Equipes) não está aberta no momento.\n" +
                    "Deseja atualizar todos os clubes brasileiros via Transfermarkt agora?",
                    "Atualização Transfermarkt",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (op == JOptionPane.YES_OPTION) {
                syncAllBrazilianClubs(frameDn);
            }
            return;
        }

        final JFrame targetFrame = frameDn;
        try {
            Method mWIC = targetFrame.getClass().getMethod("wI");
            Object time = mWIC.invoke(targetFrame);
            if (time == null) {
                // Tenta carregar o clube selecionado na tabela de clubes (wO)
                try {
                    Method mWO = targetFrame.getClass().getMethod("wO");
                    JTable tableTeams = (JTable) mWO.invoke(targetFrame);
                    if (tableTeams != null) {
                        int row = tableTeams.getSelectedRow();
                        if (row < 0 && tableTeams.getRowCount() > 0) {
                            row = 0;
                            tableTeams.setRowSelectionInterval(0, 0);
                        }
                        if (row >= 0) {
                            int modelIdx = tableTeams.convertRowIndexToModel(row);
                            if (tableTeams.getModel() != null) {
                                Method mEt = tableTeams.getModel().getClass().getMethod("et", int.class);
                                mEt.invoke(tableTeams.getModel(), modelIdx);
                                time = mWIC.invoke(targetFrame);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (time == null) {
                JOptionPane.showMessageDialog(targetFrame,
                        "Por favor, selecione um clube na lista do editor antes de atualizar.",
                        "Selecione um Clube", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            Method mGetNome = time.getClass().getMethod("getNome");
            String clubName = (String) mGetNome.invoke(time);
            String fileRef = null;
            try {
                fileRef = (String) time.getClass().getMethod("getFileRef").invoke(time);
            } catch (Throwable ignored) {}

            String url = TransfermarktScraper.obterUrlKaderClube(clubName, fileRef);
            if (url == null) {
                JOptionPane.showMessageDialog(targetFrame, "Clube não encontrado no Transfermarkt: " + clubName, "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            targetFrame.setCursor(new Cursor(Cursor.WAIT_CURSOR));

            final Object timeObj = time;
            final String clubNameFinal = clubName;
            new Thread(() -> {
                try {
                    List<TransfermarktScraper.RawPlayer> squad = TransfermarktScraper.baixarElencoTransfermarkt(url);
                    TransfermarktScraper.CoachInfo coach = TransfermarktScraper.baixarTreinadorTransfermarkt(url, 29);

                    if (squad == null || squad.size() < 11) {
                        SwingUtilities.invokeLater(() -> {
                            targetFrame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                            JOptionPane.showMessageDialog(targetFrame, "Não foi possível extrair o elenco completo do Transfermarkt.", "Erro", JOptionPane.ERROR_MESSAGE);
                        });
                        return;
                    }

                    boolean ok = TransfermarktRosterBuilder.aplicarElencoNoTime(timeObj, squad, coach);
                    if (ok) {
                        try {
                            String fRef = (String) timeObj.getClass().getMethod("getFileRef").invoke(timeObj);
                            if (fRef != null && !fRef.isEmpty()) {
                                File targetFile = new File(TEAMS_DIR, fRef.trim() + ".ban");
                                salvarTime(targetFile, timeObj);
                                // Deduplicação executada no background thread sem bloquear a interface Swing
                                deletarDuplicatasDeOutrosClubes(targetFile, timeObj, targetFrame.getClass().getClassLoader());
                            }
                        } catch (Throwable ignored) {}

                        SwingUtilities.invokeLater(() -> {
                            try {
                                // Atualização nativa total da interface do editor de clubes
                                Method mWE = targetFrame.getClass().getMethod("wE");
                                mWE.invoke(targetFrame);
                            } catch (Throwable ignored) {}

                            try {
                                Method mWO = targetFrame.getClass().getMethod("wO");
                                JTable table = (JTable) mWO.invoke(targetFrame);
                                if (table != null && table.getModel() instanceof AbstractTableModel) {
                                    ((AbstractTableModel) table.getModel()).fireTableDataChanged();
                                }
                            } catch (Throwable ignored) {}

                            try {
                                Method mWJ = targetFrame.getClass().getMethod("wJ");
                                JTable tableJogs = (JTable) mWJ.invoke(targetFrame);
                                if (tableJogs != null && tableJogs.getModel() instanceof AbstractTableModel) {
                                    ((AbstractTableModel) tableJogs.getModel()).fireTableDataChanged();
                                }
                            } catch (Throwable ignored) {}

                            try {
                                Field fMS = targetFrame.getClass().getDeclaredField("MS");
                                fMS.setAccessible(true);
                                JLabel lblMS = (JLabel) fMS.get(targetFrame);
                                if (lblMS != null && coach != null && coach.nome != null) {
                                    lblMS.setText(coach.nome);
                                }

                                int curNivel = (Integer) timeObj.getClass().getMethod("getNivel").invoke(timeObj);
                                Field fVb = targetFrame.getClass().getDeclaredField("Vb");
                                fVb.setAccessible(true);
                                JLabel lblVb = (JLabel) fVb.get(targetFrame);
                                if (lblVb != null) {
                                    lblVb.setText(Integer.toString(curNivel));
                                }

                                targetFrame.validate();
                                targetFrame.repaint();

                                int totalJogs = 0;
                                try {
                                    List<?> jogs = (List<?>) timeObj.getClass().getMethod("getJogadores").invoke(timeObj);
                                    if (jogs != null) totalJogs = jogs.size();
                                } catch (Throwable ignored) {}

                                String coachDesc = (coach != null && coach.nome != null && !coach.nome.isEmpty())
                                        ? "\n• Treinador: " + coach.nome + " (" + coach.nacionalidade + ")"
                                        : "";

                                JOptionPane.showMessageDialog(targetFrame,
                                        "✅ Elenco do " + clubNameFinal + " atualizado via Transfermarkt!\n\n" +
                                        "• Total de atletas no elenco: " + totalJogs + "\n" +
                                        "• Titulares (11 em campo + 5 reservas): 16 selecionados por minutagem\n" +
                                        "• Características autênticas preservadas do Banco Oficial do Brasfoot" +
                                        coachDesc + "\n" +
                                        "• Estrela: " + (TransfermarktRosterBuilder.isClubeSerieA(clubNameFinal, 18) ? "1 estrela (Série A)" : "0 (Série B)") + "\n" +
                                        "• Clique no botão 'Confirmar' do editor para salvar as alterações.",
                                        "Clube Atualizado", JOptionPane.INFORMATION_MESSAGE);
                            } catch (Throwable t) {
                                t.printStackTrace();
                            } finally {
                                targetFrame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                            }
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            targetFrame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                            JOptionPane.showMessageDialog(targetFrame, "Falha ao aplicar elenco balanceado no clube.", "Erro", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                } catch (Throwable t) {
                    SwingUtilities.invokeLater(() -> {
                        targetFrame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                        t.printStackTrace();
                        JOptionPane.showMessageDialog(targetFrame, "Erro ao atualizar clube no editor: " + t.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();

        } catch (Throwable t) {
            targetFrame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            t.printStackTrace();
            JOptionPane.showMessageDialog(targetFrame, "Erro ao atualizar clube no editor: " + t.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void restaurarEsteClubeNoEditor(JFrame frameDn) {
        try {
            Method mWIC = frameDn.getClass().getMethod("wI");
            Object time = mWIC.invoke(frameDn);
            if (time == null) {
                JOptionPane.showMessageDialog(frameDn, "Nenhum clube carregado no editor.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String fileRef = (String) time.getClass().getMethod("getFileRef").invoke(time);
            String clubName = (String) time.getClass().getMethod("getNome").invoke(time);

            File bkpFile = new File(BACKUP_DIR, fileRef + ".ban");
            if (!bkpFile.exists()) {
                JOptionPane.showMessageDialog(frameDn, "Arquivo de backup não encontrado para este clube: " + bkpFile.getName(), "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int op = JOptionPane.showConfirmDialog(frameDn,
                    "Deseja restaurar o elenco original de " + clubName + " a partir do backup de fallback?",
                    "Restaurar " + clubName,
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (op != JOptionPane.YES_OPTION) return;

            File targetFile = new File(TEAMS_DIR, fileRef + ".ban");
            Files.copy(bkpFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            Object restoredTeam = carregarTime(targetFile, frameDn.getClass().getClassLoader());
            if (restoredTeam != null) {
                ArrayList<?> restoredJog = (ArrayList<?>) restoredTeam.getClass().getMethod("getJogadores").invoke(restoredTeam);
                time.getClass().getMethod("setJogadores", ArrayList.class).invoke(time, restoredJog);

                String tec = (String) restoredTeam.getClass().getMethod("getTecnico").invoke(restoredTeam);
                int tecNac = (Integer) restoredTeam.getClass().getMethod("getTecNac").invoke(restoredTeam);
                time.getClass().getMethod("setTecnico", String.class).invoke(time, tec);
                time.getClass().getMethod("setTecNac", int.class).invoke(time, tecNac);

                try {
                    Field fMS = frameDn.getClass().getDeclaredField("MS");
                    fMS.setAccessible(true);
                    JLabel lblMS = (JLabel) fMS.get(frameDn);
                    if (lblMS != null && tec != null) {
                        lblMS.setText(tec);
                    }
                } catch (Throwable ignored) {}

                Method mWO = frameDn.getClass().getMethod("wO");
                JTable table = (JTable) mWO.invoke(frameDn);
                if (table != null && table.getModel() instanceof AbstractTableModel) {
                    ((AbstractTableModel) table.getModel()).fireTableDataChanged();
                }

                frameDn.validate();
                frameDn.repaint();

                JOptionPane.showMessageDialog(frameDn,
                        "✅ Elenco original de " + clubName + " restaurado com sucesso!\n" +
                        "Foram recarregados " + restoredJog.size() + " atletas originais.",
                        "Restaurado", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(frameDn, "Erro ao restaurar clube: " + t.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void restaurarFallback(Component parent) {
        File bkp = new File(BACKUP_DIR);
        if (!bkp.exists() || !bkp.isDirectory()) {
            JOptionPane.showMessageDialog(parent, "Pasta de backup original não encontrada:\n" + BACKUP_DIR, "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int op = JOptionPane.showConfirmDialog(parent,
                "Tem certeza que deseja restaurar TODOS os arquivos de elencos (.ban)\n" +
                "para o estado original de fallback?",
                "Restaurar Elencos Originais (Fallback)",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (op != JOptionPane.YES_OPTION) return;

        File[] files = bkp.listFiles((d, n) -> n.toLowerCase().endsWith(".ban"));
        if (files == null || files.length == 0) {
            JOptionPane.showMessageDialog(parent, "Nenhum arquivo de backup encontrado.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int count = 0;
        for (File f : files) {
            try {
                File target = new File(TEAMS_DIR, f.getName());
                Files.copy(f.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                count++;
            } catch (Throwable ignored) {}
        }

        recarregarTodasJanelasEditorAbertas();

        JOptionPane.showMessageDialog(parent,
                "✅ Fallback Restaurado com Sucesso!\n\n" +
                count + " arquivos de times (.ban) foram restaurados para a versão original.\n" +
                "Se houver janelas do editor abertas, os elencos já foram recarregados.",
                "Fallback Concluído", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void calibrarDivisoesBrasil(Component parent) {
        int confirm = JOptionPane.showConfirmDialog(parent,
                "Deseja calibrar a força dos times brasileiros de acordo com as divisões oficiais de 2026 (Séries A, B e C)?\n\n" +
                "• Série A (20 times): Forças 16 a 19 (Athletico-PR, Botafogo, Flamengo, Palmeiras, Santos, Coritiba, Remo, Chapecoense, etc.)\n" +
                "• Série B (20 times): Forças 13 a 15 (Fortaleza, Juventude, Cuiabá, Criciúma, Atlético-GO, Sport, Ponte Preta, Londrina, etc.)\n" +
                "• Série C (20 times): Forças 10 a 11 (Amazonas, Ferroviária, Paysandu, Volta Redonda, Santa Cruz, Inter de Limeira, Barra-SC, etc.)\n" +
                "• Série D / Estaduais: Forças <= 9 (ABC, CSA, Tombense, Retrô, Sampaio Corrêa, etc.)\n\n" +
                "Isso garante que ao iniciar um novo jogo no Brasfoot, os 60 clubes oficiais entrem exatamente em suas respectivas divisões de 2026.",
                "Calibrar Forças / Divisões Nacionais 2026",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        File dir = new File(TEAMS_DIR);
        int updated = 0;
        ClassLoader cl = parent != null ? parent.getClass().getClassLoader() : TransfermarktEditorSync.class.getClassLoader();
        for (Map.Entry<String, Integer> entry : TransfermarktRosterBuilder.CLUB_LEVELS.entrySet()) {
            File f = new File(dir, entry.getKey() + ".ban");
            if (!f.exists()) continue;

            Object team = carregarTime(f, cl);
            if (team != null) {
                try {
                    int oldNivel = (Integer) team.getClass().getMethod("getNivel").invoke(team);
                    int newNivel = entry.getValue();
                    if (oldNivel != newNivel) {
                        team.getClass().getMethod("setNivel", int.class).invoke(team, newNivel);
                        salvarTime(f, team);
                        updated++;
                    }
                } catch (Throwable ignored) {}
            }
        }

        recarregarTodasJanelasEditorAbertas();

        JOptionPane.showMessageDialog(parent,
                "✅ Forças dos clubes brasileiros calibradas com sucesso!\n\n" +
                "• " + updated + " clubes atualizados com novas forças de divisão.\n" +
                "• Séries A, B e C estão 100% alinhadas com o cenário real.",
                "Sucesso", JOptionPane.INFORMATION_MESSAGE);
    }

    public static Object carregarTime(File file, ClassLoader cl) {
        try (CustomObjectInputStream ois = new CustomObjectInputStream(new FileInputStream(file), cl)) {
            return ois.readObject();
        } catch (Throwable t) {
            return null;
        }
    }

    public static void salvarTime(File file, Object time) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(time);
        }
    }

    /**
     * Remove todos os jogadores do clube recém-atualizado que existam em qualquer outro
     * clube do jogo (por TID ou nome), garantindo que não haja repetições entre clubes.
     */
    public static int deletarDuplicatasDeOutrosClubes(File currentFile, Object currentTime, ClassLoader cl) {
        if (currentTime == null) return 0;
        try {
            List<?> jogs = (List<?>) currentTime.getClass().getMethod("getJogadores").invoke(currentTime);
            if (jogs == null || jogs.isEmpty()) return 0;

            Set<Integer> currentTids = new HashSet<>();
            Set<String> currentNames = new HashSet<>();

            for (Object j : jogs) {
                int tid = (Integer) j.getClass().getMethod("getTid").invoke(j);
                String nome = (String) j.getClass().getMethod("getNome").invoke(j);
                if (tid > 0) {
                    currentTids.add(tid);
                }
                if (nome != null && !nome.trim().isEmpty()) {
                    currentNames.add(TransfermarktRosterBuilder.normalizar(nome));
                }
            }

            File dir = new File(TEAMS_DIR);
            File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".ban"));
            if (files == null) return 0;

            int removedTotal = 0;
            String curName = currentFile != null ? currentFile.getName().toLowerCase() : "";

            for (File f : files) {
                if (f.getName().toLowerCase().equals(curName)) continue;

                Object otherTeam = carregarTime(f, cl);
                if (otherTeam == null) continue;

                List<?> otherJogs = (List<?>) otherTeam.getClass().getMethod("getJogadores").invoke(otherTeam);
                if (otherJogs == null || otherJogs.isEmpty()) continue;

                List<Object> toRemove = new ArrayList<>();
                for (Object oj : otherJogs) {
                    int otid = (Integer) oj.getClass().getMethod("getTid").invoke(oj);
                    String onome = (String) oj.getClass().getMethod("getNome").invoke(oj);
                    boolean match = false;
                    if (otid > 0 && currentTids.contains(otid)) {
                        match = true;
                    } else if (onome != null && !onome.trim().isEmpty()) {
                        String normO = TransfermarktRosterBuilder.normalizar(onome);
                        if (currentNames.contains(normO) && (normO.length() > 5 || normO.contains(" "))) {
                            match = true;
                        }
                    }

                    if (match) {
                        toRemove.add(oj);
                    }
                }

                if (!toRemove.isEmpty()) {
                    otherJogs.removeAll(toRemove);
                    salvarTime(f, otherTeam);
                    removedTotal += toRemove.size();
                    System.out.println("[DEDUPLICACAO] Removido(s) " + toRemove.size() + " atleta(s) de " + f.getName() + " que agora pertencem a " + curName);
                }
            }

            return removedTotal;
        } catch (Throwable t) {
            t.printStackTrace();
            return 0;
        }
    }

    /**
     * Varre todos os arquivos .ban em teams/ e remove duplicatas de TID de forma silenciosa (para uso em lote).
     */
    public static int executarDeduplicacaoSilenciosa(ClassLoader cl) {
        File teamsDir = new File(TEAMS_DIR);
        File[] files = teamsDir.listFiles((d, n) -> n.toLowerCase().endsWith(".ban"));
        if (files == null || files.length == 0) return 0;

        Map<Integer, List<Object[]>> occurrencesByTid = new HashMap<>();
        Map<File, Object> loadedTeams = new HashMap<>();

        for (File f : files) {
            Object team = carregarTime(f, cl);
            if (team != null) {
                loadedTeams.put(f, team);
                try {
                    String teamName = (String) team.getClass().getMethod("getNome").invoke(team);
                    List<?> jogs = (List<?>) team.getClass().getMethod("getJogadores").invoke(team);
                    if (jogs != null) {
                        for (Object j : jogs) {
                            int tid = (Integer) j.getClass().getMethod("getTid").invoke(j);
                            String nome = (String) j.getClass().getMethod("getNome").invoke(j);
                            if (tid > 0) {
                                occurrencesByTid.computeIfAbsent(tid, k -> new ArrayList<>())
                                        .add(new Object[]{f, team, j, tid, nome, teamName, f.lastModified()});
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        Set<String> priorityClubs = new HashSet<>();
        for (String ref : TransfermarktRosterBuilder.CLUB_LEVELS.keySet()) {
            priorityClubs.add(ref.toLowerCase() + ".ban");
        }

        int removedCount = 0;
        Set<File> modifiedFiles = new HashSet<>();

        for (Map.Entry<Integer, List<Object[]>> entry : occurrencesByTid.entrySet()) {
            List<Object[]> occs = entry.getValue();
            if (occs.size() > 1) {
                occs.sort((o1, o2) -> {
                    File f1 = (File) o1[0];
                    File f2 = (File) o2[0];
                    long lm1 = (Long) o1[6];
                    long lm2 = (Long) o2[6];
                    boolean p1 = priorityClubs.contains(f1.getName().toLowerCase());
                    boolean p2 = priorityClubs.contains(f2.getName().toLowerCase());
                    if (p1 && !p2) return -1;
                    if (!p1 && p2) return 1;
                    return Long.compare(lm2, lm1);
                });

                for (int i = 1; i < occs.size(); i++) {
                    Object[] rem = occs.get(i);
                    File remFile = (File) rem[0];
                    Object remTeam = rem[1];
                    Object remPlayer = rem[2];
                    try {
                        List<?> jogs = (List<?>) remTeam.getClass().getMethod("getJogadores").invoke(remTeam);
                        if (jogs != null && jogs.remove(remPlayer)) {
                            removedCount++;
                            modifiedFiles.add(remFile);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        for (File f : modifiedFiles) {
            Object team = loadedTeams.get(f);
            if (team != null) {
                try {
                    salvarTime(f, team);
                } catch (Throwable ignored) {}
            }
        }

        return removedCount;
    }

    /**
     * Varre todos os arquivos .ban em teams/ e remove duplicatas de TID em toda a base de dados.
     */
    public static void executarDeduplicacaoBaseCompleta(Component parent) {
        int confirm = JOptionPane.showConfirmDialog(parent,
                "Deseja escanear TODOS os clubes da base de dados (.ban) e remover jogadores repetidos com o mesmo ID?\n\n" +
                "• Mantém cada atleta exclusivamente no seu clube mais recente/atualizado\n" +
                "• Elimina repetições em todos os clubes nacionais e internacionais\n" +
                "• Deixa a base 100% íntegra para novas partidas e scouts",
                "Remover Jogadores Repetidos (Base Completa)",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        ClassLoader cl = parent != null ? parent.getClass().getClassLoader() : TransfermarktEditorSync.class.getClassLoader();
        int removedCount = executarDeduplicacaoSilenciosa(cl);
        recarregarTodasJanelasEditorAbertas();

        JOptionPane.showMessageDialog(parent,
                "✅ De-duplicação Concluída com Sucesso!\n\n" +
                "• Instâncias duplicadas deletadas: " + removedCount + "\n\n" +
                "Todos os atletas agora possuem registro único na base.",
                "Limpeza Concluída", JOptionPane.INFORMATION_MESSAGE);
    }
}
