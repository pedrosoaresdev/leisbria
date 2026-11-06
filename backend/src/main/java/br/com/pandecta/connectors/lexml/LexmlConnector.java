package br.com.pandecta.connectors.lexml;

import br.com.pandecta.common.Texts;
import br.com.pandecta.config.AppProperties;
import br.com.pandecta.connectors.ConnectorHttp;
import br.com.pandecta.connectors.SourceConnector;
import br.com.pandecta.connectors.SyncContext;
import br.com.pandecta.connectors.SyncResult;
import br.com.pandecta.search.Indices;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LexML Brasil — webservice SRU (Search/Retrieval via URL) com metadados Dublin Core de normas
 * federais, estaduais e municipais. Não fornece o texto integral: cada registro aponta para a URN,
 * resolvida em https://www.lexml.gov.br/urn/{urn}. Usado para "Atualizações legislativas" e para a
 * busca de normas por número/ementa além do catálogo com texto integral.
 */
@Component
public class LexmlConnector implements SourceConnector {
    public static final String ID = "lexml";
    public static final String NOME = "LexML Brasil (SRU)";

    /** Consultas CQL incrementais por tipo de norma federal; {ano} é substituído pelo ano corrente. */
    private static final List<String> CONSULTAS = List.of(
            "urn any \"federal\" and urn any \"lei\" and date any \"{ano}\"",
            "urn any \"federal\" and urn any \"lei.complementar\" and date any \"{ano}\"",
            "urn any \"federal\" and urn any \"decreto\" and date any \"{ano}\"",
            "urn any \"federal\" and urn any \"medida.provisoria\" and date any \"{ano}\"",
            "urn any \"federal\" and urn any \"emenda.constitucional\" and date any \"{ano}\"");
    private static final int POR_PAGINA = 100;
    private static final int MAX_PAGINAS = 10;

    private final ConnectorHttp http;
    private final Indices indices;
    private final String sruUrl;

    public LexmlConnector(ConnectorHttp http, Indices indices, AppProperties props) {
        this.http = http;
        this.indices = indices;
        this.sruUrl = props.lexml().sruUrl();
    }

    @Override public String id() { return ID; }
    @Override public String nome() { return NOME; }
    @Override public String descricao() {
        return "Metadados (tipo, número, data, ementa, URN) de leis, leis complementares, decretos, medidas provisórias e emendas constitucionais federais do ano corrente.";
    }
    @Override public String url() { return "https://www.lexml.gov.br/"; }
    @Override public String frequencia() { return "A cada 6 horas"; }
    @Override public boolean configurado() { return sruUrl != null && !sruUrl.isBlank(); }

    @Override
    public SyncResult sincronizar(SyncContext ctx) throws Exception {
        SyncResult r = ctx.result();
        int ano = LocalDate.now(ZoneId.of("America/Sao_Paulo")).getYear();
        try (SyncContext.Batch normas = ctx.batch(indices.normas())) {
            for (String modelo : CONSULTAS) {
                String cql = modelo.replace("{ano}", String.valueOf(ano));
                int inicio = 1;
                for (int pagina = 0; pagina < MAX_PAGINAS; pagina++) {
                    String url = sruUrl + "?operation=searchRetrieve&version=1.1&maximumRecords=" + POR_PAGINA
                            + "&startRecord=" + inicio + "&query=" + URLEncoder.encode(cql, StandardCharsets.UTF_8);
                    ConnectorHttp.Resposta resp = http.get(url, Map.of("Accept", "application/xml,text/xml"));
                    String corpo = resp.texto().trim();
                    if (resp.status() >= 400 || !corpo.startsWith("<?xml") && !corpo.startsWith("<srw") && !corpo.contains("searchRetrieveResponse")) {
                        // O portal pode responder com página de desafio (anti-robô) em vez de XML
                        throw new ConnectorHttp.FonteIndisponivelException("LexML não retornou XML (HTTP " + resp.status() + "); possível bloqueio anti-robô.", resp.status());
                    }
                    Document doc = parseXml(resp.corpo());
                    List<Element> registros = elementos(doc.getDocumentElement(), "recordData");
                    for (Element rec : registros) {
                        r.lidos++;
                        Map<String, Object> n = mapear(rec);
                        if (n == null) { r.ignorados++; continue; }
                        normas.add((String) n.get("id"), n);
                    }
                    String proximo = texto(doc.getDocumentElement(), "nextRecordPosition");
                    if (registros.size() < POR_PAGINA || proximo == null || proximo.isBlank()) break;
                    inicio = Integer.parseInt(proximo.trim());
                    ConnectorHttp.pausa(1000);
                }
            }
        }
        r.cursor.put("ano", ano);
        return r;
    }

    private Map<String, Object> mapear(Element rec) {
        String urn = texto(rec, "urn");
        if (urn == null || urn.isBlank()) return null;
        String tipo = texto(rec, "tipoDocumento");
        String titulo = texto(rec, "title");
        String ementa = texto(rec, "description");
        String data = texto(rec, "date");
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", "lexml:" + urn);
        n.put("tipo", tipo == null ? "Norma" : tipo);
        n.put("esfera", urn.contains(":federal:") ? "Federal" : (urn.contains(":estadual:") ? "Estadual" : "Municipal"));
        String localidade = texto(rec, "localidade");
        if (localidade != null) n.put("localidade", localidade);
        String numero = urn.contains(";") ? urn.substring(urn.lastIndexOf(';') + 1) : null;
        if (numero != null) n.put("numero", numero.replaceAll("[^0-9A-Za-z.-]", ""));
        if (data != null && data.length() >= 4) {
            try { n.put("ano", Integer.parseInt(data.substring(0, 4))); } catch (NumberFormatException ignored) { }
            if (data.matches("\\d{4}-\\d{2}-\\d{2}.*")) n.put("dataPublicacao", data.substring(0, 10));
        }
        n.put("titulo", titulo != null ? Texts.collapse(titulo) : (tipo + " " + numero));
        if (ementa != null) n.put("ementa", Texts.collapse(ementa));
        List<String> assuntos = new ArrayList<>();
        for (Element s : elementos(rec, "subject")) assuntos.add(s.getTextContent().trim());
        if (!assuntos.isEmpty()) n.put("assuntos", assuntos);
        n.put("urn", urn);
        n.put("situacao", "Consultar fonte oficial");
        n.put("textoDisponivel", false);
        n.put("fonte", ID);
        n.put("fonteNome", NOME);
        n.put("urlOficial", "https://www.lexml.gov.br/urn/" + urn);
        n.put("conector", ID);
        n.put("sincronizadoEm", Instant.now().toString());
        n.put("hashConteudo", Texts.sha256(urn + "|" + titulo + "|" + ementa));
        return n;
    }

    private static Document parseXml(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // Proteção contra XXE
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setNamespaceAware(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    /** Elementos descendentes cujo nome local (sem prefixo) é {@code nome}. */
    private static List<Element> elementos(Element raiz, String nome) {
        List<Element> out = new ArrayList<>();
        NodeList todos = raiz.getElementsByTagName("*");
        for (int i = 0; i < todos.getLength(); i++) {
            Node n = todos.item(i);
            String tag = n.getNodeName();
            String local = tag.contains(":") ? tag.substring(tag.indexOf(':') + 1) : tag;
            if (local.equals(nome)) out.add((Element) n);
        }
        return out;
    }

    private static String texto(Element raiz, String nome) {
        List<Element> l = elementos(raiz, nome);
        return l.isEmpty() ? null : l.get(0).getTextContent().trim();
    }
}
