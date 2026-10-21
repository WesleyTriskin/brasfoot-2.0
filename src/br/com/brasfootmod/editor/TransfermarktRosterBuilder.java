package br.com.brasfootmod.editor;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Construtor e Balanceador de Elencos do Brasfoot a partir de dados do Transfermarkt.
 * 
 * Regras:
 * 1. 11 titulares em campo no esquema padrão baseando-se na maior minutagem real.
 * 2. Mais 5 titulares (reservas imediatos do banco de 16 jogadores) selecionados por minutagem.
 * 3. Apenas times da Série A recebem estrela (exatamente 1 estrela para o atleta de linha de maior valor).
 * 4. Preserva 100% das características originais (cr1 e cr2) dos jogadores já existentes no clube
 *    OU existentes no Banco Global de 14.672 jogadores originais do Brasfoot (por TID ou Nome).
 * 5. Atualiza o Treinador Oficial do clube e sua nacionalidade.
 * 6. 100% agnóstico de ClassLoader via reflexão pura.
 */
public class TransfermarktRosterBuilder {

    private static final Set<String> SERIE_A_CLUBS = new HashSet<>(Arrays.asList(
            "flamengo", "palmeiras", "sao paulo", "são paulo", "corinthians",
            "fluminense", "botafogo", "vasco", "vasco da gama",
            "atletico-mg", "atletico mineiro", "atlético-mg", "atlético mineiro",
            "cruzeiro", "gremio", "grêmio", "internacional",
            "bahia", "bragantino", "red bull bragantino",
            "athletico-pr", "athletico paranaense", "atlético-pr", "atletico-pr", "atletico pr",
            "santos", "coritiba", "chapecoense", "mirassol", "remo", "vitoria", "vitória"
    ));

    public static final Map<String, Integer> CLUB_LEVELS = new HashMap<>();
    static {
        // SÉRIE A 2026 (20 clubes - Níveis 16 a 19)
        CLUB_LEVELS.put("botafogorj_bra", 19);
        CLUB_LEVELS.put("flarj", 19);
        CLUB_LEVELS.put("palmeiras", 19);
        CLUB_LEVELS.put("atleticomg_bra", 18);
        CLUB_LEVELS.put("corinthians_bra", 18);
        CLUB_LEVELS.put("flurj", 18);
        CLUB_LEVELS.put("internacional_bra", 18);
        CLUB_LEVELS.put("saopaulo_bra", 18);
        CLUB_LEVELS.put("atleticopr_bra", 17); // Athletico-PR Série A 2026
        CLUB_LEVELS.put("bahia", 17);
        CLUB_LEVELS.put("bragantino_bra", 17);
        CLUB_LEVELS.put("cruzeiro_bra", 17);
        CLUB_LEVELS.put("gremio", 17);
        CLUB_LEVELS.put("santos", 17);         // Santos Série A 2026
        CLUB_LEVELS.put("vasco", 17);
        CLUB_LEVELS.put("chapecoense_bra", 16);// Chapecoense Série A 2026
        CLUB_LEVELS.put("coritiba_bra", 16);   // Coritiba Série A 2026
        CLUB_LEVELS.put("miirassol_sp", 16);   // Mirassol Série A 2026
        CLUB_LEVELS.put("remo", 16);           // Remo Série A 2026
        CLUB_LEVELS.put("vitoria", 16);        // Vitória Série A 2026

        // SÉRIE B 2026 (20 clubes - Níveis 13 a 15)
        CLUB_LEVELS.put("americamg_bra", 15);
        CLUB_LEVELS.put("atleticogo_bra", 15);
        CLUB_LEVELS.put("ceara_bra", 15);
        CLUB_LEVELS.put("criciuma_bra", 15);
        CLUB_LEVELS.put("cuiaba_bra", 15);
        CLUB_LEVELS.put("fortaleza", 15);
        CLUB_LEVELS.put("goias", 15);
        CLUB_LEVELS.put("juventude", 15);
        CLUB_LEVELS.put("sport", 15);
        CLUB_LEVELS.put("avai_bra", 14);
        CLUB_LEVELS.put("londrina_pr", 14);     // Londrina Série B 2026
        CLUB_LEVELS.put("novorinzontino_sp", 14);
        CLUB_LEVELS.put("operario_pr", 14);
        CLUB_LEVELS.put("pontepreta_bra", 14);  // Ponte Preta Série B 2026
        CLUB_LEVELS.put("vilago", 14);
        CLUB_LEVELS.put("athleticclub_mg", 13); // Athletic Club Série B 2026
        CLUB_LEVELS.put("botafogosp_bra", 13);
        CLUB_LEVELS.put("crb_bra", 13);
        CLUB_LEVELS.put("nautico", 13);         // Náutico Série B 2026
        CLUB_LEVELS.put("saobernardo_sp", 13);  // São Bernardo Série B 2026

        // SÉRIE C 2026 (20 clubes - Níveis 10 a 11)
        CLUB_LEVELS.put("amazonas_am", 11);     // Amazonas FC Série C 2026
        CLUB_LEVELS.put("botafogopb_bra", 11);
        CLUB_LEVELS.put("brusquesc_bra", 11);   // Brusque Série C 2026
        CLUB_LEVELS.put("ferroviaria_sp", 11);  // Ferroviária Série C 2026
        CLUB_LEVELS.put("figueirense", 11);
        CLUB_LEVELS.put("guaranisp_bra", 11);   // Guarani Série C 2026
        CLUB_LEVELS.put("ituano_sp", 11);       // Ituano Série C 2026
        CLUB_LEVELS.put("maringapr", 11);       // Maringá Série C 2026
        CLUB_LEVELS.put("paysandu", 11);        // Paysandu Série C 2026
        CLUB_LEVELS.put("santa", 11);           // Santa Cruz Série C 2026
        CLUB_LEVELS.put("voltaredondarj_bra", 11);// Volta Redonda Série C 2026
        CLUB_LEVELS.put("ypiranga_rs", 11);
        CLUB_LEVELS.put("anapolisgo_bra", 10);  // Anápolis Série C 2026
        CLUB_LEVELS.put("barra_sc", 10);        // Barra-SC Série C 2026
        CLUB_LEVELS.put("caxias", 10);
        CLUB_LEVELS.put("confianca_se", 10);
        CLUB_LEVELS.put("florestace", 10);
        CLUB_LEVELS.put("interlimeirasp_bra", 10); // Inter de Limeira Série C 2026
        CLUB_LEVELS.put("itabaiana_se", 10);    // Itabaiana Série C 2026
        CLUB_LEVELS.put("maranhao_ma", 10);     // Maranhão Série C 2026

        // SÉRIE D e demais clubes (Nível <= 9)
        CLUB_LEVELS.put("abcrn_bra", 9);        // ABC Série D 2026
        CLUB_LEVELS.put("csa_bra", 9);          // CSA Série D 2026
        CLUB_LEVELS.put("tombense_mg", 9);      // Tombense Série D 2026
        CLUB_LEVELS.put("retrope_bra", 9);      // Retrô Série D 2026
        CLUB_LEVELS.put("samapaiocorrea_ma", 9);
        CLUB_LEVELS.put("ferroviarioce_bra", 9);
        CLUB_LEVELS.put("aparecidense_go", 9);
        CLUB_LEVELS.put("saojosers_bra", 9);
        CLUB_LEVELS.put("manaus_bra", 9);
        CLUB_LEVELS.put("brasilpelotas_bra", 9);
        CLUB_LEVELS.put("altos_pi", 9);
        CLUB_LEVELS.put("campinensepb_bra", 9);
        CLUB_LEVELS.put("atleticoce_bra", 9);
        CLUB_LEVELS.put("trezepb_bra", 9);
        CLUB_LEVELS.put("parana", 9);
    }

    public static int obterNivelCalibrado(String fileRef, String teamName, int defaultNivel) {
        if (fileRef != null && !fileRef.isEmpty()) {
            String cleanRef = fileRef.toLowerCase().replace(".ban", "").trim();
            if (CLUB_LEVELS.containsKey(cleanRef)) {
                return CLUB_LEVELS.get(cleanRef);
            }
        }
        if (teamName != null && !teamName.isEmpty()) {
            String norm = normalizar(teamName);
            if (norm.equals("flamengo")) return 19;
            if (norm.equals("palmeiras")) return 19;
            if (norm.contains("botafogo") && (norm.contains("rj") || !norm.contains("sp") && !norm.contains("pb"))) return 19;
            if (norm.contains("atletico") && norm.contains("mg")) return 18;
            if (norm.equals("sao paulo")) return 18;
            if (norm.equals("internacional")) return 18;
            if (norm.equals("corinthians")) return 18;
            if (norm.equals("fluminense")) return 18;
            if (norm.contains("athletico") || (norm.contains("atletico") && norm.contains("pr"))) return 17; // Athletico-PR
            if (norm.equals("cruzeiro")) return 17;
            if (norm.equals("gremio")) return 17;
            if (norm.equals("bahia")) return 17;
            if (norm.equals("santos")) return 17;
            if (norm.equals("vasco") || norm.contains("vasco da gama")) return 17;
            if (norm.contains("bragantino")) return 17;
            if (norm.contains("chapecoense")) return 16;
            if (norm.equals("coritiba")) return 16;
            if (norm.equals("mirassol")) return 16;
            if (norm.equals("remo")) return 16;
            if (norm.equals("vitoria")) return 16;
        }
        return defaultNivel;
    }

    private static class OriginalPlayerInfo {
        int cr1;
        int cr2;
        int tid;
        int posicao;
        int lado;

        OriginalPlayerInfo(int c1, int c2, int t, int pos, int l) {
            this.cr1 = c1;
            this.cr2 = c2;
            this.tid = t;
            this.posicao = pos;
            this.lado = l;
        }
    }

    private static class ProcessedPlayer {
        TransfermarktScraper.RawPlayer raw;
        int posicao; // 0=GOL, 1=LAT, 2=ZAG, 3=MEI, 4=ATA
        int lado;    // 0=Dir/Centro, 1=Esq
        int cr1;
        int cr2;
        int pais;
        boolean starter = false;
        boolean star = false;

        ProcessedPlayer(TransfermarktScraper.RawPlayer r, int pos, int l, int c1, int c2, int p) {
            this.raw = r;
            this.posicao = pos;
            this.lado = l;
            this.cr1 = c1;
            this.cr2 = c2;
            this.pais = p;
        }
    }

    // Base Offline Calibrada Sofascore / Perfil Real (Série A e Série B dos 40 clubes)
    private static final Map<String, int[]> CARACTERISTICAS_CALIBRADAS = new HashMap<>();
    private static boolean calibradasLoaded = false;

    private static synchronized void carregarCaracteristicasCalibradasSeNecessario() {
        if (calibradasLoaded) return;
        try {
            InputStream is = null;
            File f1 = new File("C:\\Brasfoot22-23\\caracteristicas_brasil_40_times.json");
            File f2 = new File("caracteristicas_brasil_40_times.json");
            if (f1.exists()) {
                is = new FileInputStream(f1);
            } else if (f2.exists()) {
                is = new FileInputStream(f2);
            } else {
                is = TransfermarktRosterBuilder.class.getResourceAsStream("/caracteristicas_brasil_40_times.json");
            }

            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String currentKey = null;
                Integer firstVal = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("{") || line.startsWith("}")) continue;

                    if (line.contains("\":")) {
                        int quoteStart = line.indexOf('"');
                        int quoteEnd = line.indexOf('"', quoteStart + 1);
                        if (quoteStart != -1 && quoteEnd != -1) {
                            currentKey = line.substring(quoteStart + 1, quoteEnd).trim();
                            firstVal = null;
                        }
                    } else if (currentKey != null) {
                        String numStr = line.replaceAll("[^0-9]", "");
                        if (!numStr.isEmpty()) {
                            int val = Integer.parseInt(numStr);
                            if (firstVal == null) {
                                firstVal = val;
                            } else {
                                CARACTERISTICAS_CALIBRADAS.put(currentKey, new int[]{firstVal, val});
                                currentKey = null;
                                firstVal = null;
                            }
                        }
                    }
                }
                reader.close();
            }
        } catch (Throwable t) {
            System.err.println("[RosterBuilder] Erro carregando caracteristicas calibradas: " + t);
        } finally {
            calibradasLoaded = true;
        }
    }

    // Banco de Dados Global em Memória (14.672 jogadores originais do Brasfoot 22-23)
    private static final Map<Integer, OriginalPlayerInfo> GLOBAL_PLAYERS_BY_TID = new HashMap<>();
    private static final Map<String, OriginalPlayerInfo> GLOBAL_PLAYERS_BY_NAME_AND_POS = new HashMap<>();
    private static boolean globalDbLoaded = false;

    private static synchronized void carregarBancoGlobalSeNecessario() {
        if (globalDbLoaded) return;
        try {
            File bkpDir = new File("C:\\Brasfoot22-23\\teams_backup_original");
            if (!bkpDir.exists() || !bkpDir.isDirectory()) {
                bkpDir = new File("C:\\Brasfoot22-23\\teams");
            }
            File[] files = bkpDir.listFiles((d, n) -> n.toLowerCase().endsWith(".ban"));
            if (files != null) {
                for (File f : files) {
                    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                        Object team = ois.readObject();
                        List<?> jogs = (List<?>) team.getClass().getMethod("getJogadores").invoke(team);
                        if (jogs != null) {
                            for (Object j : jogs) {
                                int tid = (Integer) j.getClass().getMethod("getTid").invoke(j);
                                String nome = (String) j.getClass().getMethod("getNome").invoke(j);
                                int cr1 = (Integer) j.getClass().getMethod("getCr1").invoke(j);
                                int cr2 = (Integer) j.getClass().getMethod("getCr2").invoke(j);
                                int pos = (Integer) j.getClass().getMethod("getPosicao").invoke(j);
                                int l = (Integer) j.getClass().getMethod("getLado").invoke(j);

                                OriginalPlayerInfo info = new OriginalPlayerInfo(cr1, cr2, tid, pos, l);
                                if (tid > 0 && !GLOBAL_PLAYERS_BY_TID.containsKey(tid)) {
                                    GLOBAL_PLAYERS_BY_TID.put(tid, info);
                                }
                                if (nome != null && !nome.trim().isEmpty()) {
                                    String keyNamePos = normalizar(nome) + "_" + pos;
                                    if (!GLOBAL_PLAYERS_BY_NAME_AND_POS.containsKey(keyNamePos)) {
                                        GLOBAL_PLAYERS_BY_NAME_AND_POS.put(keyNamePos, info);
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            globalDbLoaded = true;
        }
    }

    public static boolean aplicarElencoNoTime(Object time, List<TransfermarktScraper.RawPlayer> rawList) {
        return aplicarElencoNoTime(time, rawList, null);
    }

    public static boolean aplicarElencoNoTime(Object time, List<TransfermarktScraper.RawPlayer> rawList, TransfermarktScraper.CoachInfo coach) {
        if (time == null || rawList == null || rawList.isEmpty()) {
            return false;
        }

        carregarCaracteristicasCalibradasSeNecessario();
        carregarBancoGlobalSeNecessario();

        try {
            int clubCountry = 29;
            String teamName = "";
            int teamNivel = 10;
            try {
                clubCountry = (Integer) time.getClass().getMethod("getPais").invoke(time);
                if (clubCountry <= 0) clubCountry = 29;
                teamName = (String) time.getClass().getMethod("getNome").invoke(time);
                teamNivel = (Integer) time.getClass().getMethod("getNivel").invoke(time);
            } catch (Throwable ignored) {}

            String fileRef = "";
            try {
                fileRef = (String) time.getClass().getMethod("getFileRef").invoke(time);
            } catch (Throwable ignored) {}
            int calibratedNivel = obterNivelCalibrado(fileRef, teamName, teamNivel);
            if (calibratedNivel > 0 && clubCountry == 29) {
                try {
                    time.getClass().getMethod("setNivel", int.class).invoke(time, calibratedNivel);
                    teamNivel = calibratedNivel;
                } catch (Throwable ignored) {}
            }

            // Atualiza Treinador se fornecido
            if (coach != null && coach.nome != null && !coach.nome.trim().isEmpty()) {
                try {
                    Method mSetTecnico = time.getClass().getMethod("setTecnico", String.class);
                    mSetTecnico.invoke(time, sanitizarNome(coach.nome));
                    Method mSetTecNac = time.getClass().getMethod("setTecNac", int.class);
                    mSetTecNac.invoke(time, coach.paisCode);
                } catch (Throwable ignored) {}
            }

            // Mapeia jogadores originais do próprio clube
            Map<Integer, OriginalPlayerInfo> origByTid = new HashMap<>();
            Map<String, OriginalPlayerInfo> origByNameAndPos = new HashMap<>();
            try {
                List<?> oldJogadores = (List<?>) time.getClass().getMethod("getJogadores").invoke(time);
                if (oldJogadores != null) {
                    for (Object oj : oldJogadores) {
                        int c1 = (Integer) oj.getClass().getMethod("getCr1").invoke(oj);
                        int c2 = (Integer) oj.getClass().getMethod("getCr2").invoke(oj);
                        int tid = (Integer) oj.getClass().getMethod("getTid").invoke(oj);
                        int pos = (Integer) oj.getClass().getMethod("getPosicao").invoke(oj);
                        int l = (Integer) oj.getClass().getMethod("getLado").invoke(oj);
                        String nome = (String) oj.getClass().getMethod("getNome").invoke(oj);

                        OriginalPlayerInfo info = new OriginalPlayerInfo(c1, c2, tid, pos, l);
                        if (tid > 0) origByTid.put(tid, info);
                        if (nome != null) origByNameAndPos.put(normalizar(nome) + "_" + pos, info);
                    }
                }
            } catch (Throwable ignored) {}

            List<ProcessedPlayer> allGols = new ArrayList<>();
            List<ProcessedPlayer> allLatDir = new ArrayList<>();
            List<ProcessedPlayer> allLatEsq = new ArrayList<>();
            List<ProcessedPlayer> allZags = new ArrayList<>();
            List<ProcessedPlayer> allMeis = new ArrayList<>();
            List<ProcessedPlayer> allAtas = new ArrayList<>();

            for (TransfermarktScraper.RawPlayer r : rawList) {
                if (r.nome == null || r.nome.trim().isEmpty()) continue;

                String pDet = r.posicaoDetalhada != null ? r.posicaoDetalhada.toLowerCase() : "";
                String peStr = r.pe != null ? r.pe.toLowerCase() : "";
                int paisCode = mapearPais(r.nacionalidade, clubCountry);

                int rPos;
                if (pDet.contains("goleiro") || pDet.contains("torwart")) {
                    rPos = 0;
                } else if (pDet.contains("lateral") || pDet.contains("ala") || pDet.contains("verteidiger")) {
                    rPos = 1;
                } else if (pDet.contains("zagueiro") || pDet.contains("innenverteidiger") || pDet.contains("defensor")) {
                    rPos = 2;
                } else if (pDet.contains("volante") || pDet.contains("defensives mittelfeld") || pDet.contains("meio-campo") || pDet.contains("meia") || pDet.contains("mittelfeld")) {
                    rPos = 3;
                } else {
                    rPos = 4;
                }

                // Busca info original de fallback se o pé não constar na página do Transfermarkt
                OriginalPlayerInfo origInfo = null;
                if (r.transfermarktId > 0 && origByTid.containsKey(r.transfermarktId)) {
                    origInfo = origByTid.get(r.transfermarktId);
                } else if (r.nome != null && origByNameAndPos.containsKey(normalizar(r.nome) + "_" + rPos)) {
                    origInfo = origByNameAndPos.get(normalizar(r.nome) + "_" + rPos);
                }
                if (origInfo == null && r.transfermarktId > 0 && GLOBAL_PLAYERS_BY_TID.containsKey(r.transfermarktId)) {
                    origInfo = GLOBAL_PLAYERS_BY_TID.get(r.transfermarktId);
                }
                if (origInfo == null && r.nome != null && GLOBAL_PLAYERS_BY_NAME_AND_POS.containsKey(normalizar(r.nome) + "_" + rPos)) {
                    origInfo = GLOBAL_PLAYERS_BY_NAME_AND_POS.get(normalizar(r.nome) + "_" + rPos);
                }

                // Determinação precisa do lado: 0 = D (Direito/Destro), 1 = E (Esquerdo/Canhoto)
                int ladoDeterminado = 0;
                if (peStr.contains("esquerd") || peStr.contains("canhot") || peStr.contains("link")) {
                    ladoDeterminado = 1;
                } else if (peStr.contains("direit") || peStr.contains("destr") || peStr.contains("recht")) {
                    ladoDeterminado = 0;
                } else if (pDet.contains("esq") || pDet.contains("link")) {
                    ladoDeterminado = 1;
                } else if (pDet.contains("dir") || pDet.contains("recht")) {
                    ladoDeterminado = 0;
                } else if (origInfo != null && origInfo.lado == 1) {
                    ladoDeterminado = 1;
                }

                if (pDet.contains("goleiro") || pDet.contains("torwart")) {
                    allGols.add(new ProcessedPlayer(r, 0, ladoDeterminado, -1, -1, paisCode));
                } else if (pDet.contains("lateral") || pDet.contains("ala") || pDet.contains("verteidiger")) {
                    if (pDet.contains("esq") || pDet.contains("link") || (ladoDeterminado == 1 && !pDet.contains("dir") && !pDet.contains("recht"))) {
                        allLatEsq.add(new ProcessedPlayer(r, 1, 1, -1, -1, paisCode));
                    } else {
                        allLatDir.add(new ProcessedPlayer(r, 1, 0, -1, -1, paisCode));
                    }
                } else if (pDet.contains("zagueiro") || pDet.contains("innenverteidiger") || pDet.contains("defensor")) {
                    allZags.add(new ProcessedPlayer(r, 2, ladoDeterminado, -1, -1, paisCode));
                } else if (pDet.contains("volante") || pDet.contains("defensives mittelfeld") || pDet.contains("meio-campo") || pDet.contains("meia") || pDet.contains("mittelfeld")) {
                    allMeis.add(new ProcessedPlayer(r, 3, ladoDeterminado, -1, -1, paisCode));
                } else {
                    allAtas.add(new ProcessedPlayer(r, 4, ladoDeterminado, -1, -1, paisCode));
                }
            }

            // Critério de Ordenação Prioritário: Minutagem real, desempate por valor de mercado
            Comparator<ProcessedPlayer> minComp = (a, b) -> {
                if (a.raw.minutosJogados != b.raw.minutosJogados) {
                    return Integer.compare(b.raw.minutosJogados, a.raw.minutosJogados);
                }
                return Double.compare(b.raw.valorMercadoEuro, a.raw.valorMercadoEuro);
            };

            allGols.sort(minComp);
            allLatDir.sort(minComp);
            allLatEsq.sort(minComp);
            allZags.sort(minComp);
            allMeis.sort(minComp);
            allAtas.sort(minComp);

            List<ProcessedPlayer> finalSquad = new ArrayList<>();

            // 3 Goleiros
            pegarMelhores(allGols, finalSquad, 3);

            // 4 Laterais (2 Dir, 2 Esq)
            pegarMelhores(allLatDir, finalSquad, 2);
            pegarMelhores(allLatEsq, finalSquad, 2);

            int latsAtuais = contarPosicao(finalSquad, 1);
            if (latsAtuais < 4) {
                pegarMelhores(allLatDir, finalSquad, 4 - latsAtuais);
                latsAtuais = contarPosicao(finalSquad, 1);
                if (latsAtuais < 4) {
                    pegarMelhores(allLatEsq, finalSquad, 4 - latsAtuais);
                }
            }

            // 5 Zagueiros
            pegarMelhores(allZags, finalSquad, 5);

            // 8 Meio-campistas
            pegarMelhores(allMeis, finalSquad, 8);

            // 5 Atacantes
            pegarMelhores(allAtas, finalSquad, 5);

            if (finalSquad.size() < 22) {
                List<ProcessedPlayer> restantes = new ArrayList<>();
                restantes.addAll(allMeis);
                restantes.addAll(allZags);
                restantes.addAll(allAtas);
                restantes.addAll(allGols);
                restantes.sort(minComp);

                for (ProcessedPlayer p : restantes) {
                    if (!finalSquad.contains(p) && finalSquad.size() < 25) {
                        finalSquad.add(p);
                    }
                }
            }

            if (finalSquad.size() < 22) {
                try {
                    List<?> oldJogs = (List<?>) time.getClass().getMethod("getJogadores").invoke(time);
                    if (oldJogs != null) {
                        Set<String> alreadyNames = new HashSet<>();
                        for (ProcessedPlayer pp : finalSquad) {
                            if (pp.raw.nome != null) alreadyNames.add(normalizar(pp.raw.nome));
                        }
                        for (Object oj : oldJogs) {
                            if (finalSquad.size() >= 22) break;
                            String oNome = (String) oj.getClass().getMethod("getNome").invoke(oj);
                            if (oNome == null || alreadyNames.contains(normalizar(oNome))) continue;

                            int oPos = (Integer) oj.getClass().getMethod("getPosicao").invoke(oj);
                            int oLado = (Integer) oj.getClass().getMethod("getLado").invoke(oj);
                            int oIdade = (Integer) oj.getClass().getMethod("getIdade").invoke(oj);
                            int oPais = (Integer) oj.getClass().getMethod("getPais").invoke(oj);
                            int oCr1 = (Integer) oj.getClass().getMethod("getCr1").invoke(oj);
                            int oCr2 = (Integer) oj.getClass().getMethod("getCr2").invoke(oj);
                            int oTid = (Integer) oj.getClass().getMethod("getTid").invoke(oj);

                            TransfermarktScraper.RawPlayer rp = new TransfermarktScraper.RawPlayer();
                            rp.nome = oNome;
                            rp.idade = (oIdade >= 15 && oIdade <= 45) ? oIdade : 25;
                            rp.transfermarktId = oTid;
                            rp.valorMercadoEuro = 150_000.0;
                            rp.posicaoDetalhada = (oPos == 0) ? "Goleiro" : (oPos == 1) ? "Lateral" : (oPos == 2) ? "Zagueiro" : (oPos == 3) ? "Meia" : "Atacante";

                            ProcessedPlayer pp = new ProcessedPlayer(rp, oPos, oLado, oCr1, oCr2, oPais);
                            finalSquad.add(pp);
                            alreadyNames.add(normalizar(oNome));
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (finalSquad.size() < 11) {
                return false;
            }

            while (finalSquad.size() > 26) {
                finalSquad.remove(finalSquad.size() - 1);
            }

            // 1. Atribuição de Características Autênticas (Originais do Clube ou do Banco Global Oficial):
            atribuirCaracteristicasComVariedade(finalSquad, origByTid, origByNameAndPos);

            // 2. Define os 11 Titulares em Campo por Minutagem:
            definirTitular(finalSquad, 0, 1);
            definirTitularLado(finalSquad, 1, 0, 1);
            definirTitularLado(finalSquad, 1, 1, 1);
            int latsTitulares = contarTitularesPos(finalSquad, 1);
            if (latsTitulares < 2) {
                definirTitular(finalSquad, 1, 2 - latsTitulares);
            }
            definirTitular(finalSquad, 2, 2);
            definirTitular(finalSquad, 3, 4);
            definirTitular(finalSquad, 4, 2);

            int totalTitulares = contarTotalTitulares(finalSquad);
            if (totalTitulares < 11) {
                for (ProcessedPlayer p : finalSquad) {
                    if (!p.starter) {
                        p.starter = true;
                        totalTitulares++;
                        if (totalTitulares >= 11) break;
                    }
                }
            }

            // 3. Define mais 5 Titulares (Reservas Imediatos do Banco de 16):
            List<ProcessedPlayer> candidatosBanco = new ArrayList<>();
            for (ProcessedPlayer p : finalSquad) {
                if (!p.starter) {
                    candidatosBanco.add(p);
                }
            }
            candidatosBanco.sort(minComp);

            int extrasAdicionados = 0;
            for (ProcessedPlayer p : candidatosBanco) {
                if (extrasAdicionados < 5) {
                    p.starter = true;
                    extrasAdicionados++;
                }
            }

            // 4. Regra de Estrela: SÓ TIMES DA SÉRIE A TÊM ESTRELAS (E APENAS 1 ESTRELA)
            // CRITÉRIO: Atleta de maior valor de mercado do elenco (qualquer posição, incluindo goleiro)
            boolean isSerieA = isClubeSerieA(teamName, teamNivel);
            for (ProcessedPlayer p : finalSquad) p.star = false;

            if (isSerieA) {
                List<ProcessedPlayer> squadPorValor = new ArrayList<>(finalSquad);
                squadPorValor.sort((a, b) -> Double.compare(b.raw.valorMercadoEuro, a.raw.valorMercadoEuro));
                if (!squadPorValor.isEmpty()) {
                    squadPorValor.get(0).star = true;
                }
            }

            // 5. Ordena: GOL (0) -> LAT (1) -> ZAG (2) -> MEI (3) -> ATA (4), Titulares primeiro
            finalSquad.sort((a, b) -> {
                if (a.posicao != b.posicao) {
                    return Integer.compare(a.posicao, b.posicao);
                }
                if (a.starter != b.starter) {
                    return a.starter ? -1 : 1;
                }
                return minComp.compare(a, b);
            });

            // 6. Instanciação e injeção dinâmica via ClassLoader do objeto time
            ClassLoader cl = time.getClass().getClassLoader();
            Class<?> gClass = Class.forName("e.g", true, cl);
            Constructor<?> ctor = gClass.getDeclaredConstructor();
            ctor.setAccessible(true);

            Method mNome = gClass.getMethod("setNome", String.class);
            Method mPos = gClass.getMethod("setPosicao", int.class);
            Method mLado = gClass.getMethod("setLado", int.class);
            Method mIdade = gClass.getMethod("setIdade", int.class);
            Method mPais = gClass.getMethod("setPais", int.class);
            Method mCr1 = gClass.getMethod("setCr1", int.class);
            Method mCr2 = gClass.getMethod("setCr2", int.class);
            Method mStatus = gClass.getMethod("setStatus", int.class);
            Method mEstrela = gClass.getMethod("setEstrela", boolean.class);
            Method mTid = gClass.getMethod("setTid", int.class);
            Method mSid = gClass.getMethod("setSid", int.class);
            Method mHash = gClass.getMethod("setHash", int.class);

            Random rnd = new Random();
            ArrayList<Object> novosJogadores = new ArrayList<>();
            for (ProcessedPlayer pp : finalSquad) {
                Object j = ctor.newInstance();
                mNome.invoke(j, sanitizarNome(pp.raw.nome));
                mPos.invoke(j, pp.posicao);
                mLado.invoke(j, pp.lado);
                mIdade.invoke(j, pp.raw.idade);
                mPais.invoke(j, pp.pais);
                mCr1.invoke(j, pp.cr1);
                mCr2.invoke(j, pp.cr2);
                mStatus.invoke(j, pp.starter ? 1 : 0);
                mEstrela.invoke(j, pp.star);
                mTid.invoke(j, pp.raw.transfermarktId);
                mSid.invoke(j, 0);
                mHash.invoke(j, rnd.nextInt(900000) + 100000);

                novosJogadores.add(j);
            }

            Method mSetJogadores = time.getClass().getMethod("setJogadores", ArrayList.class);
            mSetJogadores.invoke(time, novosJogadores);

            return true;

        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    public static boolean isClubeSerieA(String teamName, int nivel) {
        if (teamName != null) {
            String norm = normalizar(teamName);
            for (String sa : SERIE_A_CLUBS) {
                if (norm.equals(sa) || norm.contains(sa) || sa.contains(norm)) {
                    return true;
                }
            }
        }
        return nivel >= 16;
    }

    /**
     * Preserva as características originais autênticas de jogadores já existentes
     * no clube ou no banco global oficial de 14.672 jogadores.
     */
    private static void atribuirCaracteristicasComVariedade(List<ProcessedPlayer> squad,
                                                           Map<Integer, OriginalPlayerInfo> origByTid,
                                                           Map<String, OriginalPlayerInfo> origByNameAndPos) {
        int[][] poolGol = { {2, 3}, {2, 1}, {3, 1}, {2, 0}, {3, 0}, {1, 0} };
        int[][] poolLat = { {6, 13}, {11, 7}, {6, 11}, {7, 10}, {13, 8}, {7, 13}, {6, 7}, {11, 4} };
        int[][] poolZag = { {7, 10}, {5, 7}, {10, 5}, {7, 11}, {10, 12}, {7, 13}, {5, 10}, {11, 10} };
        int[][] poolVol = { {10, 11}, {7, 10}, {7, 11}, {10, 12}, {11, 4}, {11, 9} };
        int[][] poolMei = { {4, 11}, {4, 9}, {4, 8}, {11, 8}, {11, 9}, {13, 4}, {8, 9}, {8, 11} };
        int[][] poolPonta = { {13, 8}, {13, 9}, {8, 6}, {13, 6}, {8, 9}, {13, 11} };
        int[][] poolCentro = { {9, 5}, {9, 13}, {9, 8}, {9, 11}, {5, 12}, {9, 0}, {5, 9} };

        Map<Integer, Set<String>> usedByPos = new HashMap<>();
        for (int i = 0; i <= 4; i++) usedByPos.put(i, new HashSet<>());

        // Passo 0: Base Calibrada Offline dos 40 Clubes (Prioridade Máxima - Estilo Sofascore e Perfis Reais)
        for (ProcessedPlayer p : squad) {
            int[] calib = null;
            // 1. Match por TID exato (Transfermarkt ID - precisão absoluta de identidade)
            if (p.raw.transfermarktId > 0) {
                calib = CARACTERISTICAS_CALIBRADAS.get(String.valueOf(p.raw.transfermarktId));
            }
            // 2. Match por Nome + Posição exata
            if (calib == null && p.raw.nome != null) {
                calib = CARACTERISTICAS_CALIBRADAS.get(normalizar(p.raw.nome) + "_" + p.posicao);
            }
            // 3. Match por Nome genérico APENAS se o nome não for ambíguo entre posições diferentes
            if (calib == null && p.raw.nome != null) {
                String norm = normalizar(p.raw.nome);
                boolean temOutraPosicao = false;
                for (int otherPos = 0; otherPos <= 4; otherPos++) {
                    if (otherPos != p.posicao && CARACTERISTICAS_CALIBRADAS.containsKey(norm + "_" + otherPos)) {
                        temOutraPosicao = true;
                        break;
                    }
                }
                if (!temOutraPosicao) {
                    calib = CARACTERISTICAS_CALIBRADAS.get(norm);
                }
            }

            if (calib != null && calib.length == 2) {
                int c1 = calib[0];
                int c2 = calib[1];
                if (isCaracteristicaValidaParaLinhaOuGoleiro(p.posicao, c1, c2)) {
                    p.cr1 = c1;
                    p.cr2 = c2;
                    usedByPos.get(p.posicao).add(p.cr1 + "_" + p.cr2);
                }
            }
        }

        // Passo 1: Busca em ordem: 1º Clube Atual (TID -> Nome+Posição) -> 2º Banco Global Oficial (TID -> Nome+Posição)
        for (ProcessedPlayer p : squad) {
            if (p.cr1 >= 0 && p.cr2 >= 0) continue; // Já calibrado com prioridade máxima

            OriginalPlayerInfo orig = null;
            // 1. Busca no mesmo clube por TID
            if (p.raw.transfermarktId > 0 && origByTid.containsKey(p.raw.transfermarktId)) {
                orig = origByTid.get(p.raw.transfermarktId);
            }
            // 2. Busca no mesmo clube por Nome + Mesma Posição
            if (orig == null && p.raw.nome != null && origByNameAndPos.containsKey(normalizar(p.raw.nome) + "_" + p.posicao)) {
                orig = origByNameAndPos.get(normalizar(p.raw.nome) + "_" + p.posicao);
            }

            // 3. Busca no banco global por TID exato
            if (orig == null && p.raw.transfermarktId > 0 && GLOBAL_PLAYERS_BY_TID.containsKey(p.raw.transfermarktId)) {
                orig = GLOBAL_PLAYERS_BY_TID.get(p.raw.transfermarktId);
            }
            // 4. Busca no banco global por Nome + Mesma Posição (NUNCA cruza posições!)
            if (orig == null && p.raw.nome != null) {
                String keyNamePos = normalizar(p.raw.nome) + "_" + p.posicao;
                if (GLOBAL_PLAYERS_BY_NAME_AND_POS.containsKey(keyNamePos)) {
                    orig = GLOBAL_PLAYERS_BY_NAME_AND_POS.get(keyNamePos);
                }
            }

            // Validação de tipo: integridade de goleiro vs linha
            if (orig != null) {
                if (orig.posicao != p.posicao || !isCaracteristicaValidaParaLinhaOuGoleiro(p.posicao, orig.cr1, orig.cr2)) {
                    orig = null;
                }
            }

            if (orig != null && orig.cr1 >= 0 && orig.cr2 >= 0) {
                p.cr1 = orig.cr1;
                p.cr2 = orig.cr2;
                usedByPos.get(p.posicao).add(p.cr1 + "_" + p.cr2);
            }
        }

        // Passo 2: Para jogadores novos da base não cadastrados no banco global, usa o pool calibrado
        int idxGol = 0, idxLat = 0, idxZag = 0, idxVol = 0, idxMei = 0, idxPonta = 0, idxCentro = 0;

        for (ProcessedPlayer p : squad) {
            if (p.cr1 >= 0 && p.cr2 >= 0) continue; // Já possui características válidas atribuídas

            String pDet = p.raw.posicaoDetalhada != null ? p.raw.posicaoDetalhada.toLowerCase() : "";
            int[][] pool;
            int counter;

            if (p.posicao == 0) {
                pool = poolGol;
                counter = idxGol++;
            } else if (p.posicao == 1) {
                pool = poolLat;
                counter = idxLat++;
            } else if (p.posicao == 2) {
                pool = poolZag;
                counter = idxZag++;
            } else if (p.posicao == 3) {
                if (pDet.contains("volante") || pDet.contains("defensiv")) {
                    pool = poolVol;
                    counter = idxVol++;
                } else {
                    pool = poolMei;
                    counter = idxMei++;
                }
            } else {
                if (pDet.contains("ponta") || pDet.contains("aussen") || pDet.contains("außen")) {
                    pool = poolPonta;
                    counter = idxPonta++;
                } else {
                    pool = poolCentro;
                    counter = idxCentro++;
                }
            }

            Set<String> used = usedByPos.get(p.posicao);
            int[] chosen = null;
            for (int i = 0; i < pool.length; i++) {
                int[] candidate = pool[(counter + i) % pool.length];
                String key = candidate[0] + "_" + candidate[1];
                if (!used.contains(key)) {
                    chosen = candidate;
                    used.add(key);
                    break;
                }
            }

            if (chosen == null) {
                chosen = pool[counter % pool.length];
            }

            p.cr1 = chosen[0];
            p.cr2 = chosen[1];

            // Trava de garantia absoluta para goleiros: nunca ultrapassar índice 3
            if (p.posicao == 0) {
                p.cr1 = Math.min(3, Math.max(0, p.cr1));
                p.cr2 = Math.min(3, Math.max(0, p.cr2));
            }
        }
    }

    public static String normalizar(String s) {
        if (s == null) return "";
        String nfd = java.text.Normalizer.normalize(s.trim().toLowerCase(), java.text.Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}", "");
    }

    private static String sanitizarNome(String n) {
        if (n == null) return "Jogador";
        n = n.trim();
        if (n.length() > 24) {
            String[] parts = n.split(" ");
            if (parts.length > 1) {
                n = parts[0] + " " + parts[parts.length - 1];
            }
            if (n.length() > 24) {
                n = n.substring(0, 24).trim();
            }
        }
        return n;
    }

    public static int mapearPais(String nacStr, int defaultCountry) {
        if (nacStr == null || nacStr.isEmpty()) return defaultCountry;
        String n = normalizar(nacStr);
        if (n.contains("brasil") || n.contains("brazil")) return 29;
        if (n.contains("argentin")) return 11;
        if (n.contains("urugua") || n.contains("uruguay")) return 195;
        if (n.contains("paragua") || n.contains("paraguay")) return 150;
        if (n.contains("chile")) return 42;
        if (n.contains("colomb")) return 46;
        if (n.contains("equador") || n.contains("ecuador")) return 60;
        if (n.contains("venezuel")) return 198;
        if (n.contains("peru")) return 151;
        if (n.contains("boliv")) return 26;
        if (n.contains("holand") || n.contains("netherland") || n.contains("paises baixos") || n.contains("dutch")) return 85;
        if (n.contains("portugal")) return 154;
        if (n.contains("espanh") || n.contains("spain")) return 65;
        if (n.contains("itali")) return 104;
        if (n.contains("franc")) return 72;
        if (n.contains("aleman") || n.contains("german")) return 3;
        if (n.contains("inglat") || n.contains("england")) return 97;
        if (n.contains("belgic") || n.contains("belgium")) return 21;
        if (n.contains("croac") || n.contains("croatia")) return 52;
        if (n.contains("servi") || n.contains("serbia")) return 171;
        if (n.contains("polon") || n.contains("poland")) return 152;
        if (n.contains("sueci") || n.contains("sweden")) return 179;
        if (n.contains("suic") || n.contains("switzerland")) return 180;
        if (n.contains("dinamarc") || n.contains("denmark")) return 54;
        if (n.contains("austri")) return 15;
        if (n.contains("grec") || n.contains("greece")) return 78;
        if (n.contains("russi")) return 162;
        if (n.contains("ucran") || n.contains("ukrain")) return 193;
        if (n.contains("turqu") || n.contains("turkey")) return 192;
        if (n.contains("estados unidos") || n.contains("united states") || n.contains("usa")) return 68;
        if (n.contains("mexic")) return 131;
        if (n.contains("japa") || n.contains("japan")) return 107;
        if (n.contains("corei") || n.contains("korea")) return 49;
        if (n.contains("senegal")) return 169;
        if (n.contains("camaro") || n.contains("cameroon")) return 36;
        if (n.contains("nigeri")) return 141;
        if (n.contains("marroc") || n.contains("morocco")) return 129;
        if (n.contains("gana") || n.contains("ghana")) return 75;
        if (n.contains("angola")) return 5;
        if (n.contains("costa do marfim") || n.contains("ivory coast")) return 50;
        if (n.contains("egito") || n.contains("egypt")) return 57;
        if (n.contains("africa do sul") || n.contains("south africa")) return 1;
        return defaultCountry;
    }

    private static void pegarMelhores(List<ProcessedPlayer> src, List<ProcessedPlayer> dst, int count) {
        int added = 0;
        for (ProcessedPlayer p : src) {
            if (!dst.contains(p) && added < count) {
                dst.add(p);
                added++;
            }
        }
    }

    private static int contarPosicao(List<ProcessedPlayer> list, int pos) {
        int c = 0;
        for (ProcessedPlayer p : list) {
            if (p.posicao == pos) c++;
        }
        return c;
    }

    private static void definirTitular(List<ProcessedPlayer> squad, int pos, int maxTitulares) {
        int count = 0;
        for (ProcessedPlayer p : squad) {
            if (p.posicao == pos && p.starter) count++;
        }
        for (ProcessedPlayer p : squad) {
            if (count >= maxTitulares) break;
            if (p.posicao == pos && !p.starter) {
                p.starter = true;
                count++;
            }
        }
    }

    private static void definirTitularLado(List<ProcessedPlayer> squad, int pos, int lado, int maxTitulares) {
        int count = 0;
        for (ProcessedPlayer p : squad) {
            if (p.posicao == pos && p.lado == lado && p.starter) count++;
        }
        for (ProcessedPlayer p : squad) {
            if (count >= maxTitulares) break;
            if (p.posicao == pos && p.lado == lado && !p.starter) {
                p.starter = true;
                count++;
            }
        }
    }

    private static int contarTitularesPos(List<ProcessedPlayer> squad, int pos) {
        int c = 0;
        for (ProcessedPlayer p : squad) {
            if (p.posicao == pos && p.starter) c++;
        }
        return c;
    }

    private static int contarTotalTitulares(List<ProcessedPlayer> squad) {
        int c = 0;
        for (ProcessedPlayer p : squad) {
            if (p.starter) c++;
        }
        return c;
    }

    public static boolean isCaracteristicaValidaParaLinhaOuGoleiro(int pos, int c1, int c2) {
        if (c1 == c2) return false;
        if (pos == 0) {
            return c1 >= 0 && c1 <= 3 && c2 >= 0 && c2 <= 3;
        } else {
            return c1 >= 4 && c1 <= 13 && c2 >= 4 && c2 <= 13;
        }
    }
}
