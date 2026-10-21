package br.com.brasfootmod.editor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente de extração de dados de elencos, treinadores e minutagem do Transfermarkt.
 */
public class TransfermarktScraper {

    public static class RawPlayer {
        public String nome = "";
        public int transfermarktId = 0;
        public String posicaoDetalhada = "";
        public String pe = "";
        public int idade = 25;
        public String nacionalidade = "Brasil";
        public double valorMercadoEuro = 0.0;
        public String valorFormatado = "";
        public int minutosJogados = 0;
    }

    public static class CoachInfo {
        public String nome = "";
        public String nacionalidade = "Brasil";
        public int paisCode = 29;
    }

    private static final Map<String, String> CLUB_SLUGS = new LinkedHashMap<>();
    private static final Map<String, String> FILE_REF_SLUGS = new LinkedHashMap<>();

    static {
        // ==========================================
        // SÉRIE A (20 CLUBES OFICIAIS 2026)
        // ==========================================
        CLUB_SLUGS.put("flamengo", "flamengo-rio-de-janeiro/kader/verein/614");
        CLUB_SLUGS.put("palmeiras", "se-palmeiras-sao-paulo/kader/verein/1023");
        CLUB_SLUGS.put("sao paulo", "sao-paulo-fc/kader/verein/585");
        CLUB_SLUGS.put("corinthians", "corinthians-sao-paulo/kader/verein/199");
        CLUB_SLUGS.put("fluminense", "fluminense-football-club/kader/verein/2462");

        CLUB_SLUGS.put("botafogo", "botafogo-rio-de-janeiro/kader/verein/537");
        CLUB_SLUGS.put("botafogo rj", "botafogo-rio-de-janeiro/kader/verein/537");
        CLUB_SLUGS.put("botafogo fr", "botafogo-rio-de-janeiro/kader/verein/537");
        CLUB_SLUGS.put("botafogo f r", "botafogo-rio-de-janeiro/kader/verein/537");
        CLUB_SLUGS.put("botafogo de futebol e regatas", "botafogo-rio-de-janeiro/kader/verein/537");

        CLUB_SLUGS.put("vasco", "vasco-da-gama-rio-de-janeiro/kader/verein/978");
        CLUB_SLUGS.put("vasco da gama", "vasco-da-gama-rio-de-janeiro/kader/verein/978");
        CLUB_SLUGS.put("atletico-mg", "clube-atletico-mineiro/kader/verein/330");
        CLUB_SLUGS.put("atletico mg", "clube-atletico-mineiro/kader/verein/330");
        CLUB_SLUGS.put("atletico mineiro", "clube-atletico-mineiro/kader/verein/330");
        CLUB_SLUGS.put("cruzeiro", "ec-cruzeiro-belo-horizonte/kader/verein/609");
        CLUB_SLUGS.put("gremio", "gremio-porto-alegre/kader/verein/210");
        CLUB_SLUGS.put("internacional", "sc-internacional-porto-alegre/kader/verein/6600");
        CLUB_SLUGS.put("bahia", "esporte-clube-bahia/kader/verein/10010");
        CLUB_SLUGS.put("athletico-pr", "athletico-paranaense/kader/verein/679");
        CLUB_SLUGS.put("athletico pr", "athletico-paranaense/kader/verein/679");
        CLUB_SLUGS.put("athletico paranaense", "athletico-paranaense/kader/verein/679");
        CLUB_SLUGS.put("atletico-pr", "athletico-paranaense/kader/verein/679");
        CLUB_SLUGS.put("atletico pr", "athletico-paranaense/kader/verein/679");
        CLUB_SLUGS.put("atletico paranaense", "athletico-paranaense/kader/verein/679");
        CLUB_SLUGS.put("red bull bragantino", "red-bull-bragantino/kader/verein/8793");
        CLUB_SLUGS.put("rb bragantino", "red-bull-bragantino/kader/verein/8793");
        CLUB_SLUGS.put("bragantino", "red-bull-bragantino/kader/verein/8793");
        CLUB_SLUGS.put("santos", "santos-fc/kader/verein/221");
        CLUB_SLUGS.put("chapecoense", "associacao-chapecoense-de-futebol/kader/verein/17776");
        CLUB_SLUGS.put("coritiba", "coritiba-fc/kader/verein/776");
        CLUB_SLUGS.put("coritiba pr", "coritiba-fc/kader/verein/776");
        CLUB_SLUGS.put("mirassol", "mirassol-futebol-clube-sp-/kader/verein/3876");
        CLUB_SLUGS.put("mirassol sp", "mirassol-futebol-clube-sp-/kader/verein/3876");
        CLUB_SLUGS.put("remo", "clube-do-remo-pa-/kader/verein/10997");
        CLUB_SLUGS.put("remo pa", "clube-do-remo-pa-/kader/verein/10997");
        CLUB_SLUGS.put("clube do remo", "clube-do-remo-pa-/kader/verein/10997");
        CLUB_SLUGS.put("vitoria", "esporte-clube-vitoria/kader/verein/2125");
        CLUB_SLUGS.put("vitoria ba", "esporte-clube-vitoria/kader/verein/2125");

        // ==========================================
        // SÉRIE B (20 CLUBES OFICIAIS 2026)
        // ==========================================
        CLUB_SLUGS.put("america-mg", "america-futebol-clube-mg-/kader/verein/2863");
        CLUB_SLUGS.put("america mg", "america-futebol-clube-mg-/kader/verein/2863");
        CLUB_SLUGS.put("america mineiro", "america-futebol-clube-mg-/kader/verein/2863");
        CLUB_SLUGS.put("atletico-go", "atletico-goianiense/kader/verein/15172");
        CLUB_SLUGS.put("atletico go", "atletico-goianiense/kader/verein/15172");
        CLUB_SLUGS.put("atletico goianiense", "atletico-goianiense/kader/verein/15172");
        CLUB_SLUGS.put("ceara", "ceara-sporting-club/kader/verein/2029");
        CLUB_SLUGS.put("ceara sc", "ceara-sporting-club/kader/verein/2029");
        CLUB_SLUGS.put("criciuma", "criciuma-esporte-clube/kader/verein/7178");
        CLUB_SLUGS.put("cuiaba", "cuiaba-ec-mt-/kader/verein/28022");
        CLUB_SLUGS.put("cuiaba ec", "cuiaba-ec-mt-/kader/verein/28022");
        CLUB_SLUGS.put("fortaleza", "fortaleza-esporte-clube/kader/verein/10870");
        CLUB_SLUGS.put("goias", "goias-esporte-clube/kader/verein/3197");
        CLUB_SLUGS.put("goias ec", "goias-esporte-clube/kader/verein/3197");
        CLUB_SLUGS.put("juventude", "esporte-clube-juventude/kader/verein/10492");
        CLUB_SLUGS.put("sport", "sport-club-do-recife/kader/verein/8718");
        CLUB_SLUGS.put("sport recife", "sport-club-do-recife/kader/verein/8718");
        CLUB_SLUGS.put("avai", "avai-fc-sc-/kader/verein/2035");
        CLUB_SLUGS.put("londrina", "londrina-esporte-clube-pr-/kader/verein/1693");
        CLUB_SLUGS.put("londrina ec", "londrina-esporte-clube-pr-/kader/verein/1693");
        CLUB_SLUGS.put("novorizontino", "gremio-novorizontino-sp-/kader/verein/37474");
        CLUB_SLUGS.put("gremio novorizontino", "gremio-novorizontino-sp-/kader/verein/37474");
        CLUB_SLUGS.put("operario-pr", "operario-fec/kader/verein/27214");
        CLUB_SLUGS.put("operario pr", "operario-fec/kader/verein/27214");
        CLUB_SLUGS.put("operario ferroviario", "operario-fec/kader/verein/27214");
        CLUB_SLUGS.put("ponte preta", "associacao-atletica-ponte-preta/kader/verein/1134");
        CLUB_SLUGS.put("vila nova", "vila-nova-fc/kader/verein/5677");
        CLUB_SLUGS.put("vila nova go", "vila-nova-fc/kader/verein/5677");
        CLUB_SLUGS.put("athletic club", "athletic-club/kader/verein/64918");
        CLUB_SLUGS.put("athletic mg", "athletic-club/kader/verein/64918");
        CLUB_SLUGS.put("botafogo-sp", "botafogo-fc-sp-/kader/verein/9030");
        CLUB_SLUGS.put("botafogo sp", "botafogo-fc-sp-/kader/verein/9030");
        CLUB_SLUGS.put("crb", "crb/kader/verein/11449");
        CLUB_SLUGS.put("nautico", "clube-nautico-capibaribe/kader/verein/2646");
        CLUB_SLUGS.put("sao bernardo", "sao-bernardo-fc-sp-/kader/verein/16439");
        CLUB_SLUGS.put("sao bernardo fc", "sao-bernardo-fc-sp-/kader/verein/16439");
        CLUB_SLUGS.put("sao bernardo sp", "sao-bernardo-fc-sp-/kader/verein/16439");

        // ==========================================
        // SÉRIE C (20 CLUBES OFICIAIS 2026)
        // ==========================================
        CLUB_SLUGS.put("amazonas", "amazonas-fc/kader/verein/87727");
        CLUB_SLUGS.put("amazonas fc", "amazonas-fc/kader/verein/87727");
        CLUB_SLUGS.put("botafogo-pb", "botafogo-fc-pb-/kader/verein/17964");
        CLUB_SLUGS.put("botafogo pb", "botafogo-fc-pb-/kader/verein/17964");
        CLUB_SLUGS.put("brusque", "brusque-fc-sc-/kader/verein/14390");
        CLUB_SLUGS.put("ferroviaria", "ferroviaria-sao-paulo/kader/verein/15882");
        CLUB_SLUGS.put("ferroviaria sp", "ferroviaria-sao-paulo/kader/verein/15882");
        CLUB_SLUGS.put("figueirense", "figueirense-futebol-clube/kader/verein/4064");
        CLUB_SLUGS.put("guarani", "guarani-fc/kader/verein/1755");
        CLUB_SLUGS.put("guarani sp", "guarani-fc/kader/verein/1755");
        CLUB_SLUGS.put("ituano", "ituano-fc/kader/verein/4773");
        CLUB_SLUGS.put("ituano sp", "ituano-fc/kader/verein/4773");
        CLUB_SLUGS.put("maringa", "maringa-fc/kader/verein/33003");
        CLUB_SLUGS.put("maringa fc", "maringa-fc/kader/verein/33003");
        CLUB_SLUGS.put("paysandu", "paysandu-sc/kader/verein/6347");
        CLUB_SLUGS.put("santa cruz", "santa-cruz-futebol-clube-pe-/kader/verein/1785");
        CLUB_SLUGS.put("santa cruz pe", "santa-cruz-futebol-clube-pe-/kader/verein/1785");
        CLUB_SLUGS.put("volta redonda", "volta-redonda-futebol-clube-rj-/kader/verein/4176");
        CLUB_SLUGS.put("ypiranga", "ypiranga-futebol-clube-rs-/kader/verein/16869");
        CLUB_SLUGS.put("ypiranga rs", "ypiranga-futebol-clube-rs-/kader/verein/16869");
        CLUB_SLUGS.put("anapolis", "anapolis-futebol-clube/kader/verein/17568");
        CLUB_SLUGS.put("anapolis go", "anapolis-futebol-clube/kader/verein/17568");
        CLUB_SLUGS.put("barra", "barra-futebol-clube-sc-/kader/verein/52519");
        CLUB_SLUGS.put("barra sc", "barra-futebol-clube-sc-/kader/verein/52519");
        CLUB_SLUGS.put("barra fc", "barra-futebol-clube-sc-/kader/verein/52519");
        CLUB_SLUGS.put("caxias", "ser-caxias-do-sul/kader/verein/9141");
        CLUB_SLUGS.put("confianca", "ad-confianca/kader/verein/3280");
        CLUB_SLUGS.put("floresta", "floresta-esporte-clube-ce-/kader/verein/65477");
        CLUB_SLUGS.put("inter de limeira", "aa-internacional-de-limeira-sp-/kader/verein/308");
        CLUB_SLUGS.put("internacional de limeira", "aa-internacional-de-limeira-sp-/kader/verein/308");
        CLUB_SLUGS.put("itabaiana", "associacao-olimpica-de-itabaiana-se-/kader/verein/8547");
        CLUB_SLUGS.put("maranhao", "maranhao-atletico-clube-ma-/kader/verein/34293");
        CLUB_SLUGS.put("maranhao ac", "maranhao-atletico-clube-ma-/kader/verein/34293");

        // ==========================================
        // DEMAIS CLUBES TRADICIONAIS / SÉRIE D
        // ==========================================
        CLUB_SLUGS.put("abc", "abc-fc/kader/verein/7209");
        CLUB_SLUGS.put("abc rn", "abc-fc/kader/verein/7209");
        CLUB_SLUGS.put("csa", "csa/kader/verein/18545");
        CLUB_SLUGS.put("csa al", "csa/kader/verein/18545");
        CLUB_SLUGS.put("sampaio correa", "sampaio-correa-fc-ma-/kader/verein/3319");
        CLUB_SLUGS.put("sampaio correa ma", "sampaio-correa-fc-ma-/kader/verein/3319");
        CLUB_SLUGS.put("ferroviario", "ferroviario-atletico-clube-ce-/kader/verein/11931");
        CLUB_SLUGS.put("ferroviario ce", "ferroviario-atletico-clube-ce-/kader/verein/11931");
        CLUB_SLUGS.put("tombense", "tombense-futebol-clube-mg-/kader/verein/3234");
        CLUB_SLUGS.put("retro", "retro-fc-brasil/kader/verein/76156");
        CLUB_SLUGS.put("retro pe", "retro-fc-brasil/kader/verein/76156");
        CLUB_SLUGS.put("altos", "aa-altos/kader/verein/62794");
        CLUB_SLUGS.put("altos pi", "aa-altos/kader/verein/62794");
        CLUB_SLUGS.put("aparecidense", "aa-aparecidense/kader/verein/25209");
        CLUB_SLUGS.put("sao jose rs", "ec-sao-jose-rs-/kader/verein/7535");
        CLUB_SLUGS.put("sao jose-rs", "ec-sao-jose-rs-/kader/verein/7535");
        CLUB_SLUGS.put("america rn", "america-fc-rn-/kader/verein/1751");
        CLUB_SLUGS.put("america-rn", "america-fc-rn-/kader/verein/1751");

        // ==========================================
        // MAPEAMENTO DIRETO POR NOME DO ARQUIVO .BAN
        // ==========================================
        // Série A
        FILE_REF_SLUGS.put("flarj", "flamengo-rio-de-janeiro/kader/verein/614");
        FILE_REF_SLUGS.put("palmeiras", "se-palmeiras-sao-paulo/kader/verein/1023");
        FILE_REF_SLUGS.put("saopaulo_bra", "sao-paulo-fc/kader/verein/585");
        FILE_REF_SLUGS.put("corinthians_bra", "corinthians-sao-paulo/kader/verein/199");
        FILE_REF_SLUGS.put("flurj", "fluminense-football-club/kader/verein/2462");
        FILE_REF_SLUGS.put("fluminense_bra", "fluminense-football-club/kader/verein/2462");
        FILE_REF_SLUGS.put("botafogorj_bra", "botafogo-rio-de-janeiro/kader/verein/537");
        FILE_REF_SLUGS.put("vasco", "vasco-da-gama-rio-de-janeiro/kader/verein/978");
        FILE_REF_SLUGS.put("vasco_bra", "vasco-da-gama-rio-de-janeiro/kader/verein/978");
        FILE_REF_SLUGS.put("atleticomg_bra", "clube-atletico-mineiro/kader/verein/330");
        FILE_REF_SLUGS.put("cruzeiro_bra", "ec-cruzeiro-belo-horizonte/kader/verein/609");
        FILE_REF_SLUGS.put("gremio", "gremio-porto-alegre/kader/verein/210");
        FILE_REF_SLUGS.put("internacional_bra", "sc-internacional-porto-alegre/kader/verein/6600");
        FILE_REF_SLUGS.put("bahia", "esporte-clube-bahia/kader/verein/10010");
        FILE_REF_SLUGS.put("bahia_bra", "esporte-clube-bahia/kader/verein/10010");
        FILE_REF_SLUGS.put("bragantino_bra", "red-bull-bragantino/kader/verein/8793");
        FILE_REF_SLUGS.put("atleticopr_bra", "athletico-paranaense/kader/verein/679");
        FILE_REF_SLUGS.put("santos", "santos-fc/kader/verein/221");
        FILE_REF_SLUGS.put("santos_bra", "santos-fc/kader/verein/221");
        FILE_REF_SLUGS.put("chapecoense_bra", "associacao-chapecoense-de-futebol/kader/verein/17776");
        FILE_REF_SLUGS.put("chapecoense_sc", "associacao-chapecoense-de-futebol/kader/verein/17776");
        FILE_REF_SLUGS.put("coritiba_bra", "coritiba-fc/kader/verein/776");
        FILE_REF_SLUGS.put("miirassol_sp", "mirassol-futebol-clube-sp-/kader/verein/3876");
        FILE_REF_SLUGS.put("mirassol_sp", "mirassol-futebol-clube-sp-/kader/verein/3876");
        FILE_REF_SLUGS.put("remo", "clube-do-remo-pa-/kader/verein/10997");
        FILE_REF_SLUGS.put("vitoria", "esporte-clube-vitoria/kader/verein/2125");

        // Série B
        FILE_REF_SLUGS.put("americamg_bra", "america-futebol-clube-mg-/kader/verein/2863");
        FILE_REF_SLUGS.put("atleticogo_bra", "atletico-goianiense/kader/verein/15172");
        FILE_REF_SLUGS.put("ceara_bra", "ceara-sporting-club/kader/verein/2029");
        FILE_REF_SLUGS.put("criciuma_bra", "criciuma-esporte-clube/kader/verein/7178");
        FILE_REF_SLUGS.put("cuiaba_bra", "cuiaba-ec-mt-/kader/verein/28022");
        FILE_REF_SLUGS.put("cuiaba_mt", "cuiaba-ec-mt-/kader/verein/28022");
        FILE_REF_SLUGS.put("fortaleza", "fortaleza-esporte-clube/kader/verein/10870");
        FILE_REF_SLUGS.put("fortaleza_bra", "fortaleza-esporte-clube/kader/verein/10870");
        FILE_REF_SLUGS.put("goias", "goias-esporte-clube/kader/verein/3197");
        FILE_REF_SLUGS.put("goias_bra", "goias-esporte-clube/kader/verein/3197");
        FILE_REF_SLUGS.put("juventude", "esporte-clube-juventude/kader/verein/10492");
        FILE_REF_SLUGS.put("juventude_bra", "esporte-clube-juventude/kader/verein/10492");
        FILE_REF_SLUGS.put("sport", "sport-club-do-recife/kader/verein/8718");
        FILE_REF_SLUGS.put("sport_bra", "sport-club-do-recife/kader/verein/8718");
        FILE_REF_SLUGS.put("avai_bra", "avai-fc-sc-/kader/verein/2035");
        FILE_REF_SLUGS.put("londrina_pr", "londrina-esporte-clube-pr-/kader/verein/1693");
        FILE_REF_SLUGS.put("novorinzontino_sp", "gremio-novorizontino-sp-/kader/verein/37474");
        FILE_REF_SLUGS.put("novorizontinosp_bra", "gremio-novorizontino-sp-/kader/verein/37474");
        FILE_REF_SLUGS.put("operario_pr", "operario-fec/kader/verein/27214");
        FILE_REF_SLUGS.put("pontepreta_bra", "associacao-atletica-ponte-preta/kader/verein/1134");
        FILE_REF_SLUGS.put("vilago", "vila-nova-fc/kader/verein/5677");
        FILE_REF_SLUGS.put("vilanova_go", "vila-nova-fc/kader/verein/5677");
        FILE_REF_SLUGS.put("athleticclub_mg", "athletic-club/kader/verein/64918");
        FILE_REF_SLUGS.put("botafogosp_bra", "botafogo-fc-sp-/kader/verein/9030");
        FILE_REF_SLUGS.put("crb_bra", "crb/kader/verein/11449");
        FILE_REF_SLUGS.put("nautico", "clube-nautico-capibaribe/kader/verein/2646");
        FILE_REF_SLUGS.put("nautico_bra", "clube-nautico-capibaribe/kader/verein/2646");
        FILE_REF_SLUGS.put("saobernardo_sp", "sao-bernardo-fc-sp-/kader/verein/16439");

        // Série C
        FILE_REF_SLUGS.put("amazonas_am", "amazonas-fc/kader/verein/87727");
        FILE_REF_SLUGS.put("amazonas_bra", "amazonas-fc/kader/verein/87727");
        FILE_REF_SLUGS.put("botafogopb_bra", "botafogo-fc-pb-/kader/verein/17964");
        FILE_REF_SLUGS.put("brusquesc_bra", "brusque-fc-sc-/kader/verein/14390");
        FILE_REF_SLUGS.put("brusque_sc", "brusque-fc-sc-/kader/verein/14390");
        FILE_REF_SLUGS.put("ferroviaria_sp", "ferroviaria-sao-paulo/kader/verein/15882");
        FILE_REF_SLUGS.put("ferroviariasp_bra", "ferroviaria-sao-paulo/kader/verein/15882");
        FILE_REF_SLUGS.put("figueirense", "figueirense-futebol-clube/kader/verein/4064");
        FILE_REF_SLUGS.put("figueirense_sc", "figueirense-futebol-clube/kader/verein/4064");
        FILE_REF_SLUGS.put("guaranisp_bra", "guarani-fc/kader/verein/1755");
        FILE_REF_SLUGS.put("guarani_bra", "guarani-fc/kader/verein/1755");
        FILE_REF_SLUGS.put("ituano_sp", "ituano-fc/kader/verein/4773");
        FILE_REF_SLUGS.put("maringapr", "maringa-fc/kader/verein/33003");
        FILE_REF_SLUGS.put("paysandu", "paysandu-sc/kader/verein/6347");
        FILE_REF_SLUGS.put("paysandu_bra", "paysandu-sc/kader/verein/6347");
        FILE_REF_SLUGS.put("santa", "santa-cruz-futebol-clube-pe-/kader/verein/1785");
        FILE_REF_SLUGS.put("santacruzpe_bra", "santa-cruz-futebol-clube-pe-/kader/verein/1785");
        FILE_REF_SLUGS.put("voltaredondarj_bra", "volta-redonda-futebol-clube-rj-/kader/verein/4176");
        FILE_REF_SLUGS.put("voltaredonda_bra", "volta-redonda-futebol-clube-rj-/kader/verein/4176");
        FILE_REF_SLUGS.put("ypiranga_rs", "ypiranga-futebol-clube-rs-/kader/verein/16869");
        FILE_REF_SLUGS.put("ypirangars_bra", "ypiranga-futebol-clube-rs-/kader/verein/16869");
        FILE_REF_SLUGS.put("anapolisgo_bra", "anapolis-futebol-clube/kader/verein/17568");
        FILE_REF_SLUGS.put("barra_sc", "barra-futebol-clube-sc-/kader/verein/52519");
        FILE_REF_SLUGS.put("caxias", "ser-caxias-do-sul/kader/verein/9141");
        FILE_REF_SLUGS.put("confianca_se", "ad-confianca/kader/verein/3280");
        FILE_REF_SLUGS.put("florestace", "floresta-esporte-clube-ce-/kader/verein/65477");
        FILE_REF_SLUGS.put("interlimeirasp_bra", "aa-internacional-de-limeira-sp-/kader/verein/308");
        FILE_REF_SLUGS.put("itabaiana_se", "associacao-olimpica-de-itabaiana-se-/kader/verein/8547");
        FILE_REF_SLUGS.put("maranhao_ma", "maranhao-atletico-clube-ma-/kader/verein/34293");

        // Outros / Série D
        FILE_REF_SLUGS.put("abcrn_bra", "abc-fc/kader/verein/7209");
        FILE_REF_SLUGS.put("csa_bra", "csa/kader/verein/18545");
        FILE_REF_SLUGS.put("csa", "csa/kader/verein/18545");
        FILE_REF_SLUGS.put("samapaiocorrea_ma", "sampaio-correa-fc-ma-/kader/verein/3319");
        FILE_REF_SLUGS.put("sampaiocorrea_ma", "sampaio-correa-fc-ma-/kader/verein/3319");
        FILE_REF_SLUGS.put("ferroviarioce_bra", "ferroviario-atletico-clube-ce-/kader/verein/11931");
        FILE_REF_SLUGS.put("tombense_mg", "tombense-futebol-clube-mg-/kader/verein/3234");
        FILE_REF_SLUGS.put("retrope_bra", "retro-fc-brasil/kader/verein/76156");
        FILE_REF_SLUGS.put("altos_pi", "aa-altos/kader/verein/62794");
        FILE_REF_SLUGS.put("aparecidense_go", "aa-aparecidense/kader/verein/25209");
        FILE_REF_SLUGS.put("saojosers_bra", "ec-sao-jose-rs-/kader/verein/7535");
        FILE_REF_SLUGS.put("americarn_bra", "america-fc-rn-/kader/verein/1751");
        FILE_REF_SLUGS.put("americar_bra", "america-fc-rn-/kader/verein/1751");
    }

    public static String normalizarNomeClube(String n) {
        if (n == null) return "";
        String nfd = java.text.Normalizer.normalize(n.trim().toLowerCase(), java.text.Normalizer.Form.NFD);
        String clean = nfd.replaceAll("\\p{M}", "");
        clean = clean.replace("(", " ").replace(")", " ").replace("-", " ").replace("/", " ").replace(".", " ");
        clean = clean.replaceAll("\\s+", " ").trim();
        return clean;
    }

    public static boolean temSlugMapeado(String clubName) {
        return temSlugMapeado(clubName, null);
    }

    public static boolean temSlugMapeado(String clubName, String fileRef) {
        if (fileRef != null && !fileRef.trim().isEmpty()) {
            String f = fileRef.trim().toLowerCase().replace(".ban", "");
            if (FILE_REF_SLUGS.containsKey(f)) return true;
        }
        String norm = normalizarNomeClube(clubName);
        return CLUB_SLUGS.containsKey(norm);
    }

    public static String obterUrlKaderClube(String clubName) {
        return obterUrlKaderClube(clubName, null);
    }

    public static String obterUrlKaderClube(String clubName, String fileRef) {
        if (fileRef != null && !fileRef.trim().isEmpty()) {
            String f = fileRef.trim().toLowerCase().replace(".ban", "");
            if (FILE_REF_SLUGS.containsKey(f)) {
                return "https://www.transfermarkt.com.br/" + FILE_REF_SLUGS.get(f);
            }
        }
        String norm = normalizarNomeClube(clubName);
        if (CLUB_SLUGS.containsKey(norm)) {
            return "https://www.transfermarkt.com.br/" + CLUB_SLUGS.get(norm);
        }
        return buscarUrlViaSearch(clubName);
    }

    private static String buscarUrlViaSearch(String clubName) {
        try {
            // Adiciona contexto 'brasil futebol' para evitar que clubes homônimos internacionais sejam selecionados
            String q = URLEncoder.encode(clubName + " brasil futebol", "UTF-8");
            String searchUrl = "https://www.transfermarkt.com.br/schnellsuche/ergebnis/schnellsuche?query=" + q;
            String html = fetchHtml(searchUrl);
            if (html != null) {
                Pattern p = Pattern.compile("<td class=\"hauptlink\"><a title=\"[^\"]*\" href=\"(/[^/]+/startseite/verein/(\\d+))\">");
                Matcher m = p.matcher(html);
                if (m.find()) {
                    String path = m.group(1).replace("/startseite/verein/", "/kader/verein/");
                    return "https://www.transfermarkt.com.br" + path;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static List<RawPlayer> baixarElencoTransfermarkt(String clubUrl) {
        List<RawPlayer> list = new ArrayList<>();
        if (clubUrl == null || clubUrl.isEmpty()) return list;

        // Garante que usamos a visão detalhada /plus/1 para capturar pé preferido e posições específicas
        if (!clubUrl.contains("/plus/1")) {
            clubUrl = clubUrl.replaceAll("/+$", "") + "/plus/1";
        }

        String html = fetchHtml(clubUrl);
        if (html == null || html.isEmpty()) return list;

        String[] chunks = html.split("<td class=\"posrela\">");
        if (chunks.length <= 1) return list;

        for (int i = 1; i < chunks.length; i++) {
            String c = chunks[i];
            try {
                // Captura nome mesmo quando houver spans de capitão ou suspensão/lesão dentro da tag <a>
                Pattern pNome = Pattern.compile("<a href=\"([^\"]*/profil/spieler/(\\d+)[^\"]*)\">(.*?)</a>", Pattern.DOTALL);
                Matcher mNome = pNome.matcher(c);
                if (!mNome.find()) continue;

                int tmId = 0;
                try {
                    tmId = Integer.parseInt(mNome.group(2));
                } catch (Throwable ignored) {}
                String nome = mNome.group(3).replaceAll("<[^>]*>", "").replace("&nbsp;", " ").trim();

                List<String> cells = new ArrayList<>();
                Pattern pCell = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);
                Matcher mCell = pCell.matcher(c);
                while (mCell.find()) {
                    cells.add(mCell.group(1).trim());
                }

                String posDetalhada = "";
                int idade = 25;
                String nacionalidade = "Brasil";
                double valorEuro = 0.0;
                String valorStr = "Consulte";

                if (cells.size() > 2) {
                    posDetalhada = cleanText(cells.get(2));
                }

                // Extração precisa da idade em todos os formatos do Transfermarkt:
                // 1) Visão estendida /plus/1: data e idade entre parênteses, ex: "21/08/1995 (31)"
                Pattern pIdadeParen = Pattern.compile("<td[^>]*class=\"[^\"]*zentriert[^\"]*\"[^>]*>[^<]*\\((\\d{2})\\)[^<]*</td>");
                Matcher mIdadeParen = pIdadeParen.matcher(c);
                if (mIdadeParen.find()) {
                    try {
                        idade = Integer.parseInt(mIdadeParen.group(1).trim());
                    } catch (Throwable ignored) {}
                } else {
                    // 2) Visão padrão: apenas o número de 2 dígitos, ex: "31"
                    Pattern pIdadeSimple = Pattern.compile("<td[^>]*class=\"[^\"]*zentriert[^\"]*\"[^>]*>\\s*(\\d{2})\\s*</td>");
                    Matcher mIdadeSimple = pIdadeSimple.matcher(c);
                    if (mIdadeSimple.find()) {
                        try {
                            idade = Integer.parseInt(mIdadeSimple.group(1).trim());
                        } catch (Throwable ignored) {}
                    } else {
                        // 3) Fallback por data de nascimento completa: "DD/MM/AAAA" -> calcula idade
                        Pattern pDataNasc = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");
                        Matcher mDataNasc = pDataNasc.matcher(c);
                        if (mDataNasc.find()) {
                            try {
                                int anoNasc = Integer.parseInt(mDataNasc.group(3));
                                if (anoNasc >= 1970 && anoNasc <= 2012) {
                                    idade = 2026 - anoNasc;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }

                Pattern pNac = Pattern.compile("<img[^>]*class=\"[^\"]*flaggenrahmen[^\"]*\"[^>]*title=\"([^\"]+)\"|<img[^>]*title=\"([^\"]+)\"[^>]*class=\"[^\"]*flaggenrahmen[^\"]*\"");
                Matcher mNac = pNac.matcher(c);
                if (mNac.find()) {
                    nacionalidade = mNac.group(1) != null ? mNac.group(1).trim() : mNac.group(2).trim();
                }

                // Captura o pé preferido autêntico do jogador (direito, esquerdo, ambidestro)
                Pattern pPe = Pattern.compile("<td class=\"zentriert\">\\s*(direito|esquerdo|ambidestro|canhoto|destro|links|rechts|beidfüßig)\\s*</td>", Pattern.CASE_INSENSITIVE);
                Matcher mPe = pPe.matcher(c);
                String pe = mPe.find() ? mPe.group(1).trim().toLowerCase() : "";

                // Extrai valor de mercado real independente de tags internas (como <a href=...>)
                Pattern pValor = Pattern.compile("<td class=\"rechts hauptlink\">(.*?)</td>", Pattern.DOTALL);
                Matcher mValor = pValor.matcher(c);
                if (mValor.find()) {
                    valorStr = cleanText(mValor.group(1));
                    valorEuro = parseValorMercado(valorStr);
                }

                RawPlayer rp = new RawPlayer();
                rp.nome = nome;
                rp.transfermarktId = tmId;
                rp.posicaoDetalhada = posDetalhada;
                rp.pe = pe;
                rp.idade = (idade >= 15 && idade <= 45) ? idade : 25;
                rp.nacionalidade = nacionalidade;
                rp.valorMercadoEuro = valorEuro;
                rp.valorFormatado = valorStr;

                list.add(rp);
            } catch (Throwable ignored) {}
        }

        // Busca e cruza a minutagem real dos atletas a partir de /leistungsdaten/
        try {
            String statsUrl = clubUrl.replace("/plus/1", "").replace("/kader/verein/", "/leistungsdaten/verein/");
            String statsHtml = fetchHtml(statsUrl);
            if (statsHtml != null && !statsHtml.isEmpty()) {
                Map<Integer, Integer> minsMap = new HashMap<>();
                Pattern pLink = Pattern.compile("href=\"[^\"]*/profil/spieler/(\\d+)\"[^>]*>([^<]+)</a>");
                Matcher mLink = pLink.matcher(statsHtml);
                while (mLink.find()) {
                    int pid = Integer.parseInt(mLink.group(1));
                    int end = mLink.end();
                    int subEnd = Math.min(statsHtml.length(), end + 1200);
                    String snippet = statsHtml.substring(end, subEnd);
                    Pattern pMin = Pattern.compile("<td class=\"rechts[^\"]*\">([^<]+)</td>");
                    Matcher mMin = pMin.matcher(snippet);
                    if (mMin.find()) {
                        String num = mMin.group(1).replaceAll("[^0-9]", "").trim();
                        if (!num.isEmpty()) {
                            int val = Integer.parseInt(num);
                            if (!minsMap.containsKey(pid) || val > minsMap.get(pid)) {
                                minsMap.put(pid, val);
                            }
                        }
                    }
                }

                for (RawPlayer rp : list) {
                    if (minsMap.containsKey(rp.transfermarktId)) {
                        rp.minutosJogados = minsMap.get(rp.transfermarktId);
                    }
                }
            }
        } catch (Throwable ignored) {}

        return list;
    }

    /**
     * Baixa os dados do Treinador Principal do clube via página de funcionários (/mitarbeiter/verein/).
     */
    public static CoachInfo baixarTreinadorTransfermarkt(String clubUrl, int defaultCountry) {
        CoachInfo info = new CoachInfo();
        if (clubUrl == null || clubUrl.isEmpty()) return info;

        try {
            String mitarbeiterUrl = clubUrl.replace("/plus/1", "").replace("/kader/verein/", "/mitarbeiter/verein/");
            String html = fetchHtml(mitarbeiterUrl);
            if (html != null && !html.isEmpty()) {
                Pattern pName = Pattern.compile("href=\"[^\"]*/profil/trainer/\\d+\"[^>]*>([^<]+)</a>");
                Matcher mName = pName.matcher(html);
                if (mName.find()) {
                    info.nome = mName.group(1).replaceAll("<[^>]*>", "").replace("&nbsp;", " ").trim();
                    int end = mName.end();
                    String snippet = html.substring(end, Math.min(html.length(), end + 1500));
                    Pattern pCountry = Pattern.compile("<img[^>]*class=\"[^\"]*flaggenrahmen[^\"]*\"[^>]*title=\"([^\"]+)\"|<img[^>]*title=\"([^\"]+)\"[^>]*class=\"[^\"]*flaggenrahmen[^\"]*\"");
                    Matcher mCountry = pCountry.matcher(snippet);
                    if (mCountry.find()) {
                        String c = mCountry.group(1) != null ? mCountry.group(1).trim() : mCountry.group(2).trim();
                        info.nacionalidade = c;
                        info.paisCode = TransfermarktRosterBuilder.mapearPais(c, defaultCountry);
                    }
                }
            }
        } catch (Throwable ignored) {}

        return info;
    }

    private static String fetchHtml(String urlStr) {
        return fetchHtmlWithRedirects(urlStr, 0);
    }

    private static String fetchHtmlWithRedirects(String urlStr, int hops) {
        if (hops > 4 || urlStr == null || urlStr.isEmpty()) return null;
        for (int tentativa = 1; tentativa <= 2; tentativa++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(12000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
                conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7");
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
                conn.setRequestProperty("Sec-Ch-Ua", "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"");
                conn.setRequestProperty("Sec-Ch-Ua-Mobile", "?0");
                conn.setRequestProperty("Sec-Ch-Ua-Platform", "\"Windows\"");
                conn.setRequestProperty("Sec-Fetch-Dest", "document");
                conn.setRequestProperty("Sec-Fetch-Mode", "navigate");
                conn.setRequestProperty("Sec-Fetch-Site", "none");
                conn.setRequestProperty("Sec-Fetch-User", "?1");
                conn.setRequestProperty("Upgrade-Insecure-Requests", "1");

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                        || code == 307 || code == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    if (newUrl != null && !newUrl.isEmpty()) {
                        if (!newUrl.startsWith("http")) {
                            newUrl = "https://www.transfermarkt.com.br" + newUrl;
                        }
                        return fetchHtmlWithRedirects(newUrl, hops + 1);
                    }
                }

                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    br.close();
                    return sb.toString();
                } else if (code == 429) {
                    Thread.sleep(1200);
                }
            } catch (Throwable t) {
                try { Thread.sleep(500); } catch (Throwable ignored) {}
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Throwable ignored) {}
                }
            }
        }
        return null;
    }

    private static String cleanText(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").replaceAll("&[^;]+;", " ").trim();
    }

    private static double parseValorMercado(String str) {
        if (str == null || str.trim().isEmpty() || str.equals("-")) return 0.0;
        try {
            Pattern p = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)");
            Matcher m = p.matcher(str);
            if (m.find()) {
                double val = Double.parseDouble(m.group(1).replace(",", "."));
                String lower = str.toLowerCase();
                if (lower.contains("mil") && !lower.contains("milh")) {
                    return val * 1_000.0;
                } else if (lower.contains("mi") || lower.contains("milh")) {
                    return val * 1_000_000.0;
                } else if (lower.contains("k") || lower.contains("th")) {
                    return val * 1_000.0;
                }
                return val;
            }
        } catch (Throwable ignored) {}
        return 0.0;
    }
}
