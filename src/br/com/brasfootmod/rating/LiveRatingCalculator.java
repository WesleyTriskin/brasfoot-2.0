package br.com.brasfootmod.rating;

import java.awt.Color;
import java.util.ArrayList;

public class LiveRatingCalculator {

    public static final Color COLOR_EXCELLENT = new Color(39, 174, 96);   // >= 8.0 (Verde)
    public static final Color COLOR_GOOD = new Color(46, 204, 113);        // 7.0 - 7.9 (Verde claro)
    public static final Color COLOR_AVERAGE = new Color(243, 156, 18);     // 6.0 - 6.9 (Amarelo ouro)
    public static final Color COLOR_POOR = new Color(230, 126, 34);        // 5.0 - 5.9 (Laranja)
    public static final Color COLOR_BAD = new Color(231, 76, 60);          // < 5.0 (Vermelho)

    public static String getPosName(int pos) {
        switch (pos) {
            case 1: return "G";
            case 2: return "L";
            case 3: return "Z";
            case 4: return "V";
            case 5: return "M";
            case 6: return "A";
            default: return "J";
        }
    }

    public static Color getRatingColor(double rating) {
        if (rating >= 8.0) return COLOR_EXCELLENT;
        if (rating >= 7.0) return COLOR_GOOD;
        if (rating >= 6.0) return COLOR_AVERAGE;
        if (rating >= 5.0) return COLOR_POOR;
        return COLOR_BAD;
    }

    /**
     * Calcula dinamicamente a classificação/nota do jogador com base nos eventos da partida.
     */
    public static double calculateRating(best.I match, int teamIdx, best.F player) {
        if (match == null || player == null) return 6.0;

        int minute = 1;
        try {
            minute = Math.max(1, Math.min(90, match.hr()));
        } catch (Throwable ignored) {
            minute = 45;
        }

        int teamGoals = 0;
        int oppGoals = 0;
        try {
            teamGoals = (teamIdx == 0) ? match.hM() : match.hN();
            oppGoals = (teamIdx == 0) ? match.hN() : match.hM();
        } catch (Throwable ignored) {}

        int scoreDiff = teamGoals - oppGoals;

        // 1. Nota Base pelo placar e tempo decorrido
        double rating;
        if (teamGoals > oppGoals) {
            if (minute <= 60) rating = 6.0;
            else if (minute <= 90) rating = 6.7;
            else rating = 7.2;
        } else if (teamGoals < oppGoals) {
            if (minute <= 30) rating = 5.0;
            else if (minute <= 60) rating = 5.2;
            else if (minute <= 90) rating = 5.5;
            else rating = 6.0;
        } else {
            if (minute <= 30) rating = 5.5;
            else if (minute <= 60) rating = 5.8;
            else if (minute <= 90) rating = 6.2;
            else rating = 6.8;
        }

        // 2. Bônus por diferença de gols
        int pos = player.getPosicao();
        if (scoreDiff > 0) {
            rating += 0.6;
            if (pos == 6 || pos == 5) rating += 0.5; // Meias e atacantes que geram gols
        } else if (scoreDiff < 0) {
            rating -= 0.6;
        }

        // 3. Eventos individuais do jogador na partida
        components.s stats = null;
        try {
            stats = player.gB();
        } catch (Throwable ignored) {}

        if (stats != null) {
            rating += stats.tx() * 1.5;   // +1.5 por gol marcado
            rating += stats.cD() * 0.4;   // +0.4 por assistência
            rating -= stats.tz() * 0.2;   // -0.2 por cartão amarelo
            rating -= stats.tA() * 0.8;   // -0.8 por expulsão
            rating += stats.y() * 0.1;    // passes e desarmes positivos
        }

        // 4. Ajustes de Goleiro (Posição 1)
        if (pos == 1) {
            rating -= 0.8;
            if (oppGoals == 0) {
                rating += 1.0; // Clean sheet
            } else if (oppGoals == 1) {
                rating -= 0.5;
            } else if (oppGoals <= 3) {
                rating -= 1.0;
            } else {
                rating -= 1.8;
            }

            if (stats != null) {
                rating += stats.tI() * 1.2; // Defesas difíceis realizadas
            }
        }

        // 5. Ajustes Defensivos (Zagueiros e Laterais: pos 2 e 3)
        if (pos == 2 || pos == 3) {
            if (oppGoals == 0) {
                rating += 0.8; // Não sofrer gols valoriza a defesa
            } else if (oppGoals >= 2) {
                rating -= oppGoals * 0.2; // Gols sofridos penalizam a defesa
            }
        }

        // 6. Capitão
        try {
            if (Boolean.TRUE.equals(player.ff())) {
                rating += 0.4;
            }
        } catch (Throwable ignored) {}

        // 7. Ponderação gradual por minutos jogados (início do jogo estabiliza perto de 6.0)
        if (minute < 15) {
            double factor = Math.max(0.25, minute / 15.0);
            rating = 6.0 + (rating - 6.0) * factor;
        }

        // 8. Limites entre 2.0 e 10.0 e arredondamento em 1 casa decimal
        rating = Math.max(2.0, Math.min(10.0, rating));
        return Math.round(rating * 10.0) / 10.0;
    }
}
