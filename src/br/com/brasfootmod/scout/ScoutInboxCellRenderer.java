package br.com.brasfootmod.scout;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Renderizador customizado para a Caixa de Entrada (a.bn) do Brasfoot.
 *
 * Garante que:
 * 1. Mensagens de inteligência do Mod (iniciando com '[Scout]', '[Tática]', '[Comissão Técnica]', etc.)
 *    exibam o assunto exato sem prefixes indesejados (como 'Bem vind@ ao').
 * 2. Mensagens não lidas do mod ganham destaque visual com fonte em negrito e cor dourada.
 * 3. Mensagens nativas do jogo continuam sendo formatadas normalmente usando o dicionário nativo (best.ar.tP).
 */
public class ScoutInboxCellRenderer extends DefaultTableCellRenderer {

    private static String[] tP = null;

    private static String[] getTP() {
        if (tP != null) return tP;
        try {
            Class<?> arClass = Class.forName("best.ar");
            Field tpField = arClass.getDeclaredField("tP");
            tpField.setAccessible(true);
            tP = (String[]) tpField.get(null);
        } catch (Throwable ignored) {}
        return tP;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setBorder(noFocusBorder);
        setForeground(Color.WHITE);
        setBackground(new Color(44, 53, 49)); // Fundo verde escuro característico do Brasfoot
        setFont(new Font("Arial", Font.PLAIN, 11));
        setHorizontalAlignment(SwingConstants.LEFT);
        setIcon(null);
        setText("");

        if (isSelected) {
            setBackground(Color.WHITE);
            setForeground(Color.BLACK);
        }

        if (value != null) {
            try {
                Method getData = value.getClass().getMethod("getData");
                Method vp = value.getClass().getMethod("vp");
                Method vm = value.getClass().getMethod("vm");
                Method vo = value.getClass().getMethod("vo");

                String dataStr = (String) getData.invoke(value);
                String title = (String) vp.invoke(value);
                int type = (Integer) vm.invoke(value);
                boolean read = (Boolean) vo.invoke(value);

                if (!read) {
                    setFont(new Font("Arial", Font.BOLD, 11));
                }

                if (column == 0) {
                    setText(dataStr != null ? dataStr : "");
                } else if (column == 1) {
                    // Trata mensagens personalizadas de mods (Scout, Tática, Comissão Técnica, etc.)
                    if (title != null && (title.startsWith("[") || title.startsWith("Parecer Tático") || title.startsWith("Sugestão Tática"))) {
                        setText(title);
                        if (!read && !isSelected) {
                            setForeground(new Color(255, 215, 0)); // Dourado para mensagens não lidas do mod
                        }
                    } else {
                        // Mensagens nativas do jogo
                        String[] tpArray = getTP();
                        if (tpArray != null && type >= 0 && type < tpArray.length) {
                            String prefix = tpArray[type];
                            String sep = (prefix.endsWith(" ") || prefix.endsWith(":") || (title == null || title.isEmpty())) ? "" : " ";
                            setText(prefix + sep + (title != null ? title : ""));
                        } else {
                            setText(title != null ? title : "");
                        }
                    }
                }
            } catch (Throwable t) {
                setText("");
            }
        }
        return this;
    }
}
