package br.com.pandecta.connectors.stj;

import br.com.pandecta.common.Texts;
import br.com.pandecta.config.AppProperties;
import br.com.pandecta.connectors.ConnectorHttp;
import br.com.pandecta.connectors.DecisaoDoc;
import br.com.pandecta.connectors.SourceConnector;
import br.com.pandecta.connectors.SyncContext;
import br.com.pandecta.connectors.SyncResult;
import br.com.pandecta.search.CitationParser;
import br.com.pandecta.search.Indices;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STJ — Portal de Dados Abertos (CKAN). Os conjuntos "espelhos de acórdãos" (um por órgão julgador)
 * publicam arquivos JSON periódicos com ementa, tese, referências legislativas etc.
 *
 * Fluxo: package_search → recursos JSON ainda não importados (mais recentes primeiro) → download para
 * arquivo temporário → leitura em streaming (arquivos podem ser grandes) → indexação.
 * O nome exato dos campos é lido de forma tolerante, pois o STJ pode ajustar o leiaute.
 */
@Component
public class StjDadosAbertosConnector implements SourceConnector {
    private static final Logger log = LoggerFactory.getLogger(StjDadosAbertosConnector.class);
    public static final String ID = "stj-dados-abertos";
    public static final String NOME = "STJ — Portal de Dados Abertos";
    private static final Pattern DATA_BR = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");

    private final ConnectorHttp http;
    private final ObjectMapper mapper;
    private final Indices indices;
    private final AppProperties.Stj cfg;

    public StjDadosAbertosConnector(ConnectorHttp http, ObjectMapper mapper, Indices indices, AppProperties props) {
        this.http = http;
        this.mapper = mapper;
        this.indices = indices;
        this.cfg = props.stj();
    }

    @Override public String id() { return ID; }
    @Override public String nome() { return NOME; }
    @Override public String descricao() {
        return "Espelhos de acórdãos do STJ (Corte Especial, Seções e Turmas): ementa, tese jurídica, tema, referências legislativas e jurisprudência citada.";
    }
    @Override public String url() { return cfg.ckanUrl(); }
    @Override public String frequencia() { return "Diária; importa novos arquivos publicados pelo STJ"; }
    @Override public boolean configurado() { return cfg.ckanUrl() != null && !cfg.ckanUrl().isBlank(); }

    private record Recurso(String id, String url, String nome, String dataset, String criado) {}

    @Override
    public SyncResult sincronizar(SyncContext ctx) throws Exception {
        SyncResult r = ctx.result();
        String api = cfg.ckanUrl() + "/api/3/action/package_search?rows=100&q=" + URLEncoder.encode(cfg.datasetQuery(), StandardCharsets.UTF_8);
        ConnectorHttp.Resposta resp = http.get(api, Map.of("Accept", "application/json"));
        if (resp.status() >= 400) throw new ConnectorHttp.FonteIndisponivelException("CKAN do STJ respondeu HTTP " + resp.status(), resp.status());
        JsonNode result = mapper.readTree(resp.corpo()).path("result");
        List<Recurso> recursos = new ArrayList<>();
        for (JsonNode pkg : result.path("results")) {
            for (JsonNode res : pkg.path("resources")) {
                String formato = res.path("format").asText("").toLowerCase();
                String url = res.path("url").asText("");
                if (!(formato.contains("json") || url.toLowerCase().endsWith(".json"))) continue;
                recursos.add(new Recurso(res.path("id").asText(url), url, res.path("name").asText(""),
                        pkg.path("title").asText(pkg.path("name").asText()), res.path("created").asText("")));
            }
        }
        recursos.sort(Comparator.comparing(Recurso::criado).reversed());
        int processados = 0;
        for (Recurso rec : recursos) {
            if (processados >= cfg.maxResourcesPerRun()) break;
            if (ctx.jaProcessado(rec.id())) continue;
            Path tmp = Files.createTempFile("stj-", ".json");
            try {
                http.download(rec.url(), tmp);
                importarArquivo(tmp, rec, ctx);
                ctx.marcarProcessado(rec.id());
                processados++;
                log.info("STJ: arquivo {} ({}) importado", rec.nome(), rec.dataset());
            } catch (Exception e) {
                r.erro(rec.nome() + ": " + e.getMessage());
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
        r.cursor.put("recursosConhecidos", recursos.size());
        return r;
    }

    private void importarArquivo(Path arquivo, Recurso rec, SyncContext ctx) throws Exception {
        SyncResult r = ctx.result();
        LocalDate limiteRadar = LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(7);
        try (JsonParser p = mapper.getFactory().createParser(arquivo.toFile());
             SyncContext.Batch juris = ctx.batch(indices.jurisprudencia());
             SyncContext.Batch radar = ctx.batch(indices.radar())) {
            JsonToken t = p.nextToken();
            if (t == JsonToken.START_OBJECT) {
                // Alguns arquivos podem envolver a lista num objeto: procura o primeiro array.
                while ((t = p.nextToken()) != null && t != JsonToken.START_ARRAY) { /* avança */ }
            }
            if (t != JsonToken.START_ARRAY) throw new IllegalStateException("Formato inesperado: lista de acórdãos não encontrada");
            while (p.nextToken() == JsonToken.START_OBJECT) {
                JsonNode n = mapper.readTree(p);
                r.lidos++;
                Map<String, Object> d = mapear(n, rec);
                if (d == null) { r.ignorados++; continue; }
                juris.add((String) d.get("id"), d);
                String pub = (String) d.get("dataPublicacao");
                if (pub != null && !LocalDate.parse(pub).isBefore(limiteRadar)) {
                    String tipo = "TEMA_REPETITIVO".equals(d.get("categoria")) ? "RECURSO_REPETITIVO" : "DECISAO_PUBLICADA";
                    Map<String, Object> ev = DecisaoDoc.eventoRadar(d, tipo, pub, false);
                    radar.add((String) ev.get("id"), ev);
                }
            }
        }
    }

    private Map<String, Object> mapear(JsonNode n, Recurso rec) {
        String registro = txt(n, "numeroRegistro", "numero_registro");
        String idOrigem = txt(n, "id", "seqDocumento");
        if (registro == null && idOrigem == null) return null;
        String id = "stj:" + (idOrigem != null ? idOrigem : registro + ":" + txt(n, "dataDecisao"));
        Map<String, Object> d = DecisaoDoc.base(id, "STJ", ID, NOME, ID);
        String classeSigla = txt(n, "siglaClasse");
        String numero = txt(n, "numeroProcesso");
        String cnj = numero == null ? null : CitationParser.normalizarCnj(numero);
        d.put("numeroProcesso", cnj != null ? cnj : (classeSigla != null && numero != null ? classeSigla + " " + numero : numero));
        if (registro != null) d.put("numeroRegistro", registro);
        d.put("classe", txt(n, "descricaoClasse", "classePadronizada", "siglaClasse"));
        if (classeSigla != null) d.put("classeSigla", classeSigla);
        d.put("orgaoJulgador", txt(n, "nomeOrgaoJulgador", "orgaoJulgador"));
        d.put("relator", txt(n, "ministroRelator", "relator"));
        d.put("dataJulgamento", dataCompacta(txt(n, "dataDecisao", "dataJulgamento")));
        d.put("dataPublicacao", dataBr(txt(n, "dataPublicacao")));
        d.put("ementa", txt(n, "ementa"));
        String decisao = txt(n, "decisao");
        if (decisao != null) d.put("resultado", decisao);
        String tese = txt(n, "teseJuridica", "tese");
        if (tese != null) d.put("tese", tese);
        String tema = txt(n, "tema");
        if (tema != null) d.put("tema", tema.replaceAll("\\D+", " ").trim().split(" ")[0]);
        String refs = juntar(n.path("referenciasLegislativas"));
        if (refs != null) d.put("referenciasLegislativas", refs);
        List<String> citados = new ArrayList<>(DecisaoDoc.dispositivosCitados(d.get("ementa") == null ? "" : (String) d.get("ementa")));
        d.put("dispositivosCitados", citados);
        String termos = txt(n, "termosAuxiliares");
        if (termos != null) d.put("assuntos", termos);
        String tipo = Texts.fold(txt(n, "tipoDeDecisao", "tipoDecisao") == null ? "acordao" : txt(n, "tipoDeDecisao", "tipoDecisao"));
        boolean repetitivo = tema != null && !tema.isBlank();
        d.put("tipoDecisao", tipo.contains("monocr") ? "DECISAO_MONOCRATICA" : "ACORDAO");
        d.put("categoria", repetitivo ? "TEMA_REPETITIVO" : (tipo.contains("monocr") ? "DECISAO_MONOCRATICA" : "ACORDAO"));
        d.put("statusPublicacao", d.get("dataPublicacao") != null ? "PUBLICADA" : "JULGADA");
        d.put("urlOficial", registro != null
                ? "https://processo.stj.jus.br/processo/pesquisa/?tipoPesquisa=tipoPesquisaNumeroRegistro&termo=" + registro.replaceAll("\\D", "")
                : cfg.ckanUrl());
        d.put("hashConteudo", Texts.sha256(String.valueOf(d.get("ementa")) + d.get("dataPublicacao")));
        return d;
    }

    private static String txt(JsonNode n, String... campos) {
        for (String c : campos) {
            JsonNode v = n.get(c);
            if (v != null && !v.isNull()) {
                String s = v.isArray() ? juntar(v) : v.asText("").trim();
                if (s != null && !s.isBlank()) return Texts.collapse(s);
            }
        }
        return null;
    }

    private static String juntar(JsonNode arr) {
        if (arr == null || arr.isMissingNode() || arr.isNull()) return null;
        if (!arr.isArray()) return arr.asText(null);
        List<String> l = new ArrayList<>();
        arr.forEach(x -> { if (!x.asText("").isBlank()) l.add(x.asText().trim()); });
        return l.isEmpty() ? null : String.join("\n", l);
    }

    private static String dataCompacta(String v) {
        if (v == null) return null;
        String d = v.replaceAll("\\D", "");
        if (d.length() >= 8 && d.startsWith("19") || d.length() >= 8 && d.startsWith("20")) {
            return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8);
        }
        return dataBr(v);
    }

    /** "DJE        DATA:21/05/2022" → 2022-05-21 */
    private static String dataBr(String v) {
        if (v == null) return null;
        Matcher m = DATA_BR.matcher(v);
        return m.find() ? m.group(3) + "-" + m.group(2) + "-" + m.group(1) : null;
    }
}
