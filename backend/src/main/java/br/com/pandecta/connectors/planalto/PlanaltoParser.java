package br.com.pandecta.connectors.planalto;

import br.com.pandecta.common.Texts;
import br.com.pandecta.legislation.DispositivoIds;
import br.com.pandecta.search.Roman;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte o HTML compilado do Portal da Legislação (Planalto) em estrutura e dispositivos.
 *
 * Particularidades tratadas:
 *  - redações anteriores aparecem TACHADAS (&lt;strike&gt;/&lt;s&gt;/&lt;del&gt;) → viram versões não vigentes;
 *  - notas "(Redação dada pela…)", "(Incluído pela…)", "(Vide…)" → separadas do texto em notasAlteracao;
 *  - cabeçalhos (Parte, Livro, Título, Capítulo, Seção, Subseção) com o nome na mesma linha ou na seguinte;
 *  - ADCT na mesma página da Constituição (numeração de artigos reinicia);
 *  - vários dispositivos num mesmo parágrafo separados por &lt;br&gt;.
 *
 * O parser é deliberadamente conservador: linhas que não reconhece não viram dispositivos.
 */
public class PlanaltoParser {

    private static final char RISCO_INI = '\u0003';
    private static final char RISCO_FIM = '\u0004';

    private static final Pattern ART = Pattern.compile(
            "^Art(?:igo)?\\.?\\s*(\\d{1,4}(?:\\.\\d{3})?)\\s*(?:º|°|o\\b)?\\s*(?:-\\s*([A-Z]{1,2})\\b)?\\s*[.\\-–—]?\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern PAR = Pattern.compile(
            "^§\\s*(\\d{1,3})\\s*(?:º|°|o\\b)?\\s*(?:-\\s*([A-Z])\\b)?\\s*[.\\-–—]?\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern PAR_UNICO = Pattern.compile(
            "^Par[áa]grafo\\s+[úu]nico\\s*[.\\-–—:]?\\s*(.*)$", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern INC = Pattern.compile(
            "^([IVXLCDM]{1,8})(?:\\s*-\\s*([A-Z])(?=\\s*[-–—]))?\\s*[-–—]\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern ALI = Pattern.compile("^([a-z])(?:-([a-z]))?\\)\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern ITEM = Pattern.compile("^(\\d{1,2})[.)]\\s+(.*)$", Pattern.DOTALL);
    private static final Pattern CABECALHO = Pattern.compile(
            "^(PARTE|LIVRO|T[ÍI]TULO|CAP[ÍI]TULO|SE[ÇC][ÃA]O|SUBSE[ÇC][ÃA]O)\\s+([IVXLCDM]+(?:-[A-Z])?|[ÚU]NIC[OA]|GERAL|ESPECIAL|PRELIMINAR|COMPLEMENTAR)\\b\\.?\\s*[-–—.]?\\s*(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ADCT = Pattern.compile("^ATO\\s+DAS\\s+DISPOSI[ÇC][ÕO]ES\\s+CONSTITUCIONAIS\\s+TRANSIT[ÓO]RIAS", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTA = Pattern.compile(
            "\\((?:Reda[çc][ãa]o dada|Inclu[íi]d[oa]|Revogad[oa]|Vide|Renumerad[oa]|Acrescid[oa]|Vig[êe]ncia|Promulga[çc][ãa]o|Regulamento|Express[ãa]o|Suprimid[oa]|Produ[çc][ãa]o de efeito|Revoga[çc][ãa]o|Execu[çc][ãa]o suspensa|Vetad[oa])[^()]*(?:\\([^()]*\\)[^()]*)*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FIM_TEXTO = Pattern.compile(
            "^(Bras[íi]lia,|Rio de Janeiro,|Este texto n[ãa]o substitui|\\*\\s*$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMENTA = Pattern.compile(
            "^(Institui|Disp[õo]e|Altera|Estabelece|Regulamenta|Aprova|Cria|Define|Revoga|Autoriza|Acrescenta|Modifica|Organiza|Consolida|Promulga|Abre)\\b.*");
    private static final Pattern NORMA_ALTERADORA = Pattern.compile(
            "(Emenda Constitucional(?: de Revis[ãa]o)?|Lei Complementar|Lei|Decreto-Lei|Decreto|Medida Provis[óo]ria)\\s+n[ºo°]?\\s*\\.?\\s*([\\d.]+)(?:,\\s*de\\s*(\\d{4}))?",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, Integer> NIVEL_CAB = Map.of(
            "parte", 0, "livro", 1, "titulo", 2, "capitulo", 3, "secao", 4, "subsecao", 5);

    /** Resultado do parse. Mapas prontos para indexação. */
    public record Resultado(String ementa, String situacao, List<Map<String, Object>> estrutura,
                            List<Map<String, Object>> dispositivos, List<Map<String, Object>> historicoAlteracoes) {}

    private record Linha(String texto, boolean riscada) {}

    // ---------------- estado do parse
    private final String normaId;
    private boolean adct;
    private String artigo, artigoId, paragrafo, paiParagrafoId, inciso, paiIncisoId, alinea, paiAlineaId;
    private String caputAtual = "";
    private final String[] cabecalhos = new String[6];
    private final List<Map<String, Object>> estrutura = new ArrayList<>();
    private final List<Map<String, Object>> pendentesCabecalho = new ArrayList<>();
    private Map<String, Object> cabecalhoAguardandoNome;
    private final List<Map<String, Object>> dispositivos = new ArrayList<>();
    private final Map<String, Integer> ocorrencias = new HashMap<>();
    private Map<String, Object> ultimo;
    private int ordem = 0;

    public PlanaltoParser(String normaId) {
        this.normaId = normaId;
    }

    public Resultado parse(String html) {
        return parse(Jsoup.parse(html));
    }

    /** Usa a detecção de charset do Jsoup (o Planalto frequentemente declara windows-1252 no &lt;meta&gt;). */
    public Resultado parse(byte[] html, String baseUri) throws java.io.IOException {
        return parse(Jsoup.parse(new java.io.ByteArrayInputStream(html), null, baseUri));
    }

    public Resultado parse(Document doc) {
        doc.select("script,style,head,noscript").remove();
        for (Element s : doc.select("strike, s, del")) {
            s.replaceWith(new TextNode(RISCO_INI + s.wholeText() + RISCO_FIM));
        }
        for (Element br : doc.select("br")) br.replaceWith(new TextNode("\n"));

        List<Linha> linhas = new ArrayList<>();
        Elements blocos = doc.select("p, h1, h2, h3, h4, h5, h6, li");
        if (blocos.size() >= 20) {
            for (Element p : blocos) {
                if (!p.select("p").isEmpty() && p.select("p").size() > 1) continue; // contêiner
                adicionarLinhas(linhas, p.wholeText());
            }
        } else {
            adicionarLinhas(linhas, doc.body() == null ? doc.wholeText() : doc.body().wholeText());
        }

        String ementa = null;
        String situacao = "Em vigor (texto compilado)";
        int idx = 0;
        // Cabeçalho do documento até o primeiro artigo/cabeçalho estrutural
        for (; idx < linhas.size(); idx++) {
            String t = linhas.get(idx).texto();
            if (ART.matcher(t).matches() || CABECALHO.matcher(t).matches() || ADCT.matcher(t).find()) break;
            if (ementa == null && EMENTA.matcher(t).matches() && t.length() < 1500) ementa = removerNotas(t).texto;
            if (idx < 15 && t.matches("(?i).*\\(?revogad[oa] pel[oa].*") && t.length() < 300) situacao = "Revogada";
        }
        for (; idx < linhas.size(); idx++) {
            Linha l = linhas.get(idx);
            if (FIM_TEXTO.matcher(l.texto()).find() && artigo != null) break;
            processar(l);
        }
        return new Resultado(ementa, situacao, estrutura, dispositivos, historico());
    }

    private void adicionarLinhas(List<Linha> out, String bruto) {
        for (String parte : bruto.split("\n")) {
            String limpo = parte.replace('\u00A0', ' ').replaceAll("\\s+", " ").strip(); // strip(): trim() apagaria os marcadores de texto riscado
            if (limpo.isEmpty()) continue;
            int riscados = 0, total = 0;
            boolean dentro = false;
            StringBuilder sb = new StringBuilder();
            for (char c : limpo.toCharArray()) {
                if (c == RISCO_INI) { dentro = true; continue; }
                if (c == RISCO_FIM) { dentro = false; continue; }
                sb.append(c);
                if (!Character.isWhitespace(c)) {
                    total++;
                    if (dentro) riscados++;
                }
            }
            String texto = sb.toString().trim();
            if (texto.isEmpty() || total == 0) continue;
            out.add(new Linha(texto, riscados >= total * 0.7));
        }
    }

    private void processar(Linha l) {
        String t = l.texto();
        Matcher m;

        if (ADCT.matcher(t).find()) {
            adct = true;
            artigo = null;
            java.util.Arrays.fill(cabecalhos, null);
            novoCabecalho("titulo", "ADCT", "Ato das Disposições Constitucionais Transitórias");
            return;
        }
        if ((m = CABECALHO.matcher(t)).matches() && !l.riscada()) {
            String tipo = Texts.fold(m.group(1));
            String chave = switch (tipo) {
                case "titulo" -> "titulo";
                case "capitulo" -> "capitulo";
                case "secao" -> "secao";
                case "subsecao" -> "subsecao";
                default -> tipo;
            };
            String rotulo = m.group(1).toUpperCase(Locale.ROOT) + " " + m.group(2).toUpperCase(Locale.ROOT);
            String nome = removerNotas(m.group(3)).texto;
            novoCabecalho(chave, rotulo, nome.isBlank() ? null : nome);
            return;
        }
        if (cabecalhoAguardandoNome != null && !ehDispositivo(t) && t.length() < 250 && !l.riscada()) {
            String nome = removerNotas(t).texto;
            cabecalhoAguardandoNome.put("nome", nome);
            Integer nv = NIVEL_CAB.get((String) cabecalhoAguardandoNome.get("nivel"));
            if (nv != null) cabecalhos[nv] = cabecalhoAguardandoNome.get("rotulo") + " — " + nome;
            cabecalhoAguardandoNome = null;
            return;
        }
        cabecalhoAguardandoNome = null;

        if ((m = ART.matcher(t)).matches()) {
            String num = m.group(1).replace(".", "");
            String letra = m.group(2);
            artigo = letra == null ? num : num + "-" + letra;
            artigoId = DispositivoIds.artigo(normaId, adct, artigo);
            paragrafo = inciso = alinea = null;
            paiParagrafoId = paiIncisoId = paiAlineaId = artigoId;
            String marcador = "Art. " + DispositivoIds.ordinal(artigo) + (num.length() > 1 || letra != null ? "." : "");
            Notas n = removerNotas(m.group(3));
            if (!l.riscada()) caputAtual = n.texto;
            criar(artigoId, "artigo", marcador, n, l.riscada(), null, null, null);
            return;
        }
        if (artigo == null) return; // nada antes do primeiro artigo é dispositivo

        if ((m = PAR_UNICO.matcher(t)).matches()) {
            paragrafo = "unico";
            inciso = alinea = null;
            paiIncisoId = DispositivoIds.paragrafo(artigoId, "unico");
            paiAlineaId = paiIncisoId;
            criar(paiIncisoId, "paragrafo", "Parágrafo único.", removerNotas(m.group(1)), l.riscada(), "unico", null, null);
            return;
        }
        if ((m = PAR.matcher(t)).matches()) {
            paragrafo = m.group(2) == null ? m.group(1) : m.group(1) + "-" + m.group(2);
            inciso = alinea = null;
            paiIncisoId = DispositivoIds.paragrafo(artigoId, paragrafo);
            paiAlineaId = paiIncisoId;
            criar(paiIncisoId, "paragrafo", "§ " + DispositivoIds.ordinal(paragrafo), removerNotas(m.group(3)),
                    l.riscada(), paragrafo, null, null);
            return;
        }
        if ((m = INC.matcher(t)).matches() && Roman.isValid(m.group(1))) {
            inciso = m.group(2) == null ? m.group(1) : m.group(1) + "-" + m.group(2);
            alinea = null;
            paiAlineaId = DispositivoIds.inciso(paiIncisoId, inciso);
            criar(paiAlineaId, "inciso", inciso + " -", removerNotas(m.group(3)), l.riscada(), paragrafo, inciso, null);
            return;
        }
        if ((m = ALI.matcher(t)).matches() && inciso != null) {
            alinea = m.group(2) == null ? m.group(1) : m.group(1) + "-" + m.group(2);
            String id = DispositivoIds.alinea(paiAlineaId, alinea);
            criar(id, "alinea", alinea + ")", removerNotas(m.group(3)), l.riscada(), paragrafo, inciso, alinea);
            return;
        }
        if ((m = ITEM.matcher(t)).matches() && alinea != null) {
            String id = DispositivoIds.item(DispositivoIds.alinea(paiAlineaId, alinea), m.group(1));
            criar(id, "item", m.group(1) + ".", removerNotas(m.group(2)), l.riscada(), paragrafo, inciso, alinea);
            return;
        }
        // Linha de continuação ou nota solta
        if (ultimo != null) {
            Notas n = removerNotas(t);
            if (!n.notas.isEmpty()) acrescentarNotas(ultimo, n.notas);
            String atual = (String) ultimo.get("texto");
            if (!n.texto.isBlank() && atual != null && !atual.matches(".*[;.:]$") && n.texto.length() < 1500
                    && Boolean.valueOf(l.riscada()).equals(!Boolean.TRUE.equals(ultimo.get("vigente")))) {
                ultimo.put("texto", atual + " " + n.texto);
            }
        }
    }

    private boolean ehDispositivo(String t) {
        return ART.matcher(t).matches() || PAR.matcher(t).matches() || PAR_UNICO.matcher(t).matches()
                || (INC.matcher(t).matches() && Roman.isValid(t.split("\\s|-")[0]));
    }

    private void novoCabecalho(String nivel, String rotulo, String nome) {
        Integer nv = NIVEL_CAB.get(nivel);
        if (nv != null) {
            for (int i = nv; i < cabecalhos.length; i++) cabecalhos[i] = null;
            cabecalhos[nv] = nome == null ? rotulo : rotulo + " — " + nome;
        }
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("nivel", nivel);
        h.put("rotulo", rotulo);
        if (nome != null) h.put("nome", nome);
        estrutura.add(h);
        pendentesCabecalho.add(h);
        cabecalhoAguardandoNome = nome == null ? h : null;
    }

    private void criar(String idBase, String nivel, String marcador, Notas n, boolean riscada,
                       String par, String inc, String ali) {
        String id = idBase;
        int vez = ocorrencias.merge(idBase + (riscada ? "#r" : "#v"), 1, Integer::sum);
        Integer versao = null;
        if (riscada) {
            versao = vez;
            id = idBase + "@v" + vez;
        } else if (vez > 1) {
            id = idBase + "~" + vez; // repetição inesperada (ex.: anexos): mantém sem sobrescrever
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", id);
        d.put("normaId", normaId);
        d.put("parte", adct ? "ADCT" : "TEXTO");
        d.put("artigo", artigo);
        try { d.put("artigoNum", Integer.parseInt(artigo.replaceAll("-.*", ""))); } catch (NumberFormatException ignored) { }
        d.put("nivel", nivel);
        d.put("caminho", idBase);
        d.put("marcador", marcador);
        d.put("rotulo", DispositivoIds.rotulo(adct, artigo, par, inc, ali, "item".equals(nivel) ? marcador.replace(".", "") : null));
        d.put("texto", n.texto.isBlank() ? "(sem texto)" : n.texto);
        if (!"artigo".equals(nivel)) d.put("textoCaput", Texts.truncate(caputAtual, 600));
        d.put("hierarquia", hierarquia());
        d.put("ordem", ordem++);
        d.put("vigente", !riscada);
        d.put("versao", riscada ? versao : 0);
        d.put("dispositivoBaseId", idBase);
        if (!n.notas.isEmpty()) acrescentarNotas(d, n.notas);
        dispositivos.add(d);
        if (!riscada) {
            for (Map<String, Object> h : pendentesCabecalho) h.put("antesDe", id);
            pendentesCabecalho.clear();
        }
        ultimo = d;
    }

    @SuppressWarnings("unchecked")
    private void acrescentarNotas(Map<String, Object> d, List<String> notas) {
        String atual = (String) d.getOrDefault("notasAlteracao", "");
        String novo = (atual.isBlank() ? "" : atual + " ") + String.join(" ", notas);
        d.put("notasAlteracao", novo.trim());
        List<String> alteradoras = (List<String>) d.computeIfAbsent("normasAlteradoras", k -> new ArrayList<String>());
        for (String nota : notas) {
            Matcher m = NORMA_ALTERADORA.matcher(nota);
            while (m.find()) {
                String ref = canonico(m.group(1)) + " " + m.group(2).replaceAll("\\.$", "") + (m.group(3) != null ? "/" + m.group(3) : "");
                if (!alteradoras.contains(ref)) alteradoras.add(ref);
            }
        }
    }

    private static String canonico(String tipo) {
        String f = Texts.fold(tipo);
        if (f.startsWith("emenda")) return "EC";
        if (f.startsWith("lei complementar")) return "LC";
        if (f.startsWith("decreto-lei")) return "Decreto-Lei";
        if (f.startsWith("decreto")) return "Decreto";
        if (f.startsWith("medida")) return "MP";
        return "Lei";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> historico() {
        Map<String, Integer> cont = new LinkedHashMap<>();
        for (Map<String, Object> d : dispositivos) {
            Object l = d.get("normasAlteradoras");
            if (l instanceof List<?> lista) for (Object o : lista) cont.merge(String.valueOf(o), 1, Integer::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        cont.forEach((k, v) -> out.add(Map.<String, Object>of("norma", k, "dispositivosAfetados", v)));
        return out;
    }

    private String hierarquia() {
        List<String> partes = new ArrayList<>();
        if (adct && cabecalhos[2] == null) partes.add("ADCT");
        for (String c : cabecalhos) if (c != null) partes.add(c);
        return String.join(" › ", partes);
    }

    private record Notas(String texto, List<String> notas) {}

    private static Notas removerNotas(String t) {
        if (t == null) return new Notas("", List.of());
        List<String> notas = new ArrayList<>();
        Matcher m = NOTA.matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            notas.add(m.group());
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return new Notas(sb.toString().replaceAll("\\s+", " ").trim(), notas);
    }
}
