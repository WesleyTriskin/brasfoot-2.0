package br.com.brasfootmod.patch;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BrasfootPatchHooks {

    /**
     * Execução 100% segura e thread-safe de a.ho.run() (Timer do pós-jogo a.hk).
     * Despacha para a Swing EDT e captura exceções para que o TimerThread nunca morra.
     */
    public static void runHoSafe(Object hk) {
        try {
            if (hk != null) {
                Method ph = hk.getClass().getDeclaredMethod("ph");
                ph.setAccessible(true);
                java.util.Timer t = (java.util.Timer) ph.invoke(null);
                if (t != null) {
                    t.cancel();
                }
            }
        } catch (Throwable ignored) {}

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Object sr = br.com.brasfootmod.vacation.BrasfootVacationController.getSRObject();
                    if (sr != null) {
                        Method ax = sr.getClass().getMethod("ax");
                        ax.invoke(sr);
                    }
                } catch (Throwable t) {
                    System.err.println("[MOD PATCH] ⚠️ Exceção tratada com segurança em a.ho -> c.a.SR.ax(): " + t.getMessage());
                    t.printStackTrace();
                }
            }
        });
    }

    /**
     * Execução 100% segura e thread-safe de a.cS.run() (Timer do pós-temporada a.cR).
     */
    public static void runCsSafe(Object cr) {
        try {
            if (cr != null) {
                Method ph = cr.getClass().getDeclaredMethod("ph");
                ph.setAccessible(true);
                java.util.Timer t = (java.util.Timer) ph.invoke(null);
                if (t != null) {
                    t.cancel();
                }
            }
        } catch (Throwable ignored) {}

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Object sr = br.com.brasfootmod.vacation.BrasfootVacationController.getSRObject();
                    if (sr != null) {
                        Method v = sr.getClass().getMethod("V");
                        v.invoke(sr);
                    }
                } catch (Throwable t) {
                    System.err.println("[MOD PATCH] ⚠️ Exceção tratada com segurança em a.cS -> c.a.SR.V(): " + t.getMessage());
                    t.printStackTrace();
                }
            }
        });
    }

    /**
     * Guarda anti-NPE em best.S.aZ: garante que gI nunca seja nulo ao ser adicionado ao JFrame cC
     */
    public static Object checkGiNotNull(Object gi) {
        if (gi != null) return gi;
        try {
            System.out.println("[MOD PATCH] 🛡️ gI estava null em best.S.aZ! Prevenindo NullPointerException e recriando components.E...");
            Class<?> compEClass = Class.forName("components.E");
            Constructor<?> ctor = compEClass.getConstructor(String.class, int.class);
            Object fallback = ctor.newInstance(null, 0);
            Method setBg = compEClass.getMethod("setBackground", Color.class);
            setBg.invoke(fallback, new Color(36, 91, 45));
            return fallback;
        } catch (Throwable t) {
            return new javax.swing.JPanel();
        }
    }

    /**
     * Intercepta o texto de assunto na renderização da Caixa de Entrada (b.ar)
     * e garante que títulos de mods ('[Scout]...', '[Tática]...', '[Diretoria]...')
     * nunca venham com o prefixo 'Bem vind@ ao '.
     */
    public static String filterInboxSubject(String text) {
        if (text == null) return "";
        if (text.startsWith("Bem vind@ ao [") || text.startsWith("Bem vindo ao [") || text.startsWith("Bem-vindo ao [")) {
            int idx = text.indexOf('[');
            if (idx >= 0) return text.substring(idx);
        }
        return text;
    }
}
