package br.com.pandecta.connectors.djen;

import br.com.pandecta.common.Texts;
import br.com.pandecta.config.AppProperties;
import br.com.pandecta.connectors.ConnectorHttp;
import br.com.pandecta.connectors.DecisaoDoc;
import br.com.pandecta.connectors.SourceConnector;
import br.com.pandecta.connectors.SyncContext;
import br.com.pandecta.connectors.SyncResult;
import br.com.pandecta.search.CitationParser;
import br.com.pandecta.search.Indices;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Diário de Justiça Eletrônico Nacional (DJEN) — API pública de comunicações processuais
 * (comunicaapi.pje.jus.br/api/v1/comunicacao). Importa apenas os tipos de documento configurados
 * (acórdãos, sentenças, decisões), que viram jurisprudência com status PUBLICADA e eventos no Radar.
 *
 * LGPD: listas de destinatários (partes e advogados) NÃO são armazenadas. O texto publicado é mantido
 * por ser publicação oficial, mas pode conter nomes; não é usado para perfis de pessoas.
 *
 * Observação: a data informada pela API é a de DISPONIBILIZAÇÃO no diário; a publicação considera-se
 * feita no primeiro dia útil seguinte (Lei 11.419/2006, art. 4º, § 3º).
 */
@Component
public class DjenConnector implements SourceConnector {
    public static final String ID = "djen";
    public static final String NOME = "Diário de Justiça Eletrônico Nacional (DJEN)";

    private final ConnectorHttp http;
    private final ObjectMapper mapper;
    private final Indices indices;
    private final AppProperties.Djen cfg;

    public DjenConnector(ConnectorHttp http, ObjectMapper mapper, Indices indices, AppProperties props) {
        this.http = http;
        this.mapper = mapper;
        this.indices = indices;
        this.cfg = props.djen();
    }

    @Override public String id() { return ID; }
    @Override public String nome() { return NOME; }
    @Override public String descricao() {
        return "Acórdãos, sentenças e decisões disponibilizados no DJEN pelos tribunais " + String.join(", ", tribunais()) + ".";
    }
    @Override public String url() { return "https://comunica.pje.jus.br/"; }
    @Override public String frequencia() { return "A cada 30 minutos (dia corrente e anterior)"; }
    @Override public boolean configurado() { return !tribunais().isEmpty(); }

    private List<String> tribunais() {
        return cfg.tribunais() == null ? List.of() : cfg.tribunais().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public SyncResult sincronizar(SyncContext ctx) throws Exception {
        SyncResult r = ctx.result();
        Set<String> tipos = cfg.tiposDocumento().stream().map(Texts::fold).map(String::trim).collect(Collectors.toSet());
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        int falhas = 0, tentativas = 0;
        try (SyncContext.Batch juris = ctx.batch(indices.jurisprudencia()); SyncContext.Batch radar = ctx.batch(indices.radar())) {
            for (String trib : tribunais()) {
                for (LocalDate dia : List.of(hoje.minusDays(1), hoje)) {
                    tentativas++;
                    try {
                        importarDia(trib, dia, tipos, r, juris, radar);
                    } catch (ConnectorHttp.FonteIndisponivelException e) {
                        falhas++;
                        r.erro(trib + " " + dia + ": " + e.getMessage());
                    }
                }
            }
        }
        if (tentativas > 0 && falhas == tentativas) r.fonteIndisponivel = true;
        r.cursor.put("ultimoDia", hoje.toString());
        return r;
    }

    private void importarDia(String trib, LocalDate dia, Set<String> tipos, SyncResult r,
                             SyncContext.Batch juris, SyncContext.Batch radar) throws Exception {
        for (int pagina = 1; pagina <= cfg.maxPagesPerTribunal(); pagina++) {
            String url = cfg.baseUrl() + "/comunicacao?siglaTribunal=" + enc(trib)
                    + "&dataDisponibilizacaoInicio=" + dia + "&dataDisponibilizacaoFim=" + dia
                    + "&pagina=" + pagina + "&itensPorPagina=100";
            ConnectorHttp.Resposta resp = http.get(url, Map.of("Accept", "application/json"));
            if (resp.status() >= 400) {
                r.erro(trib + " " + dia + ": HTTP " + resp.status());
                return;
            }
            JsonNode body = mapper.readTree(resp.corpo());
            JsonNode itens = body.path("items");
            if (!itens.isArray() || itens.isEmpty()) return;
            for (JsonNode it : itens) {
                r.lidos++;
                String tipoDoc = it.path("tipoDocumento").asText("");
                if (!tipos.contains(Texts.fold(tipoDoc).trim())) { r.ignorados++; continue; }
                if (!it.path("ativo").asBoolean(true)) { r.ignorados++; continue; }
                Map<String, Object> d = mapear(it, trib, tipoDoc);
                juris.add((String) d.get("id"), d);
                Map<String, Object> ev = DecisaoDoc.eventoRadar(d, "DECISAO_PUBLICADA", (String) d.get("dataPublicacao"), false);
                radar.add((String) ev.get("id"), ev);
            }
            if (itens.size() < 100) return;
            ConnectorHttp.pausa(800);
        }
    }

    private Map<String, Object> mapear(JsonNode it, String trib, String tipoDoc) {
        String sigla = it.path("siglaTribunal").asText(trib);
        String id = "djen:" + it.path("id").asText(it.path("hash").asText());
        Map<String, Object> d = DecisaoDoc.base(id, sigla, ID, NOME, ID);
        String numero = it.path("numeroprocessocommascara").asText("");
        if (numero.isBlank()) numero = String.valueOf(CitationParser.normalizarCnj(it.path("numero_processo").asText("")));
        d.put("numeroProcesso", numero);
        d.put("classe", it.path("nomeClasse").asText(null));
        d.put("orgaoJulgador", it.path("nomeOrgao").asText(null));
        String tf = Texts.fold(tipoDoc);
        String categoria = tf.startsWith("acordao") || tf.startsWith("ementa") ? "ACORDAO"
                : tf.startsWith("sentenca") ? "SENTENCA" : "DECISAO_MONOCRATICA";
        d.put("tipoDecisao", categoria);
        String classeFold = Texts.fold(it.path("nomeClasse").asText(""));
        boolean controle = classeFold.contains("inconstitucionalidade") || classeFold.contains("declaratoria de constitucionalidade")
                || classeFold.contains("descumprimento de preceito");
        d.put("categoria", controle ? "CONTROLE_CONCENTRADO" : categoria);
        d.put("statusPublicacao", "PUBLICADA");
        String data = it.path("data_disponibilizacao").asText(null);
        d.put("dataPublicacao", data != null && data.length() >= 10 ? data.substring(0, 10) : null);
        String texto = Texts.plain(it.path("texto").asText(""));
        d.put("inteiroTeor", texto);
        String ementa = DecisaoDoc.extrairEmenta(texto);
        if (ementa != null) d.put("ementa", ementa);
        d.put("dispositivosCitados", DecisaoDoc.dispositivosCitados(texto));
        String link = it.path("link").asText("");
        // "link" aponta para o documento quando o tribunal o informa; caso contrário, o portal do DJEN.
        d.put("urlOficial", link.startsWith("http") ? link : "https://comunica.pje.jus.br/");
        d.put("hashConteudo", Texts.sha256(texto));
        return d;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
