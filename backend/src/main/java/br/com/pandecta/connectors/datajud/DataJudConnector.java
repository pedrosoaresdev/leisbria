package br.com.pandecta.connectors.datajud;

import br.com.pandecta.common.Texts;
import br.com.pandecta.config.AppProperties;
import br.com.pandecta.connectors.ConnectorHttp;
import br.com.pandecta.connectors.SourceConnector;
import br.com.pandecta.connectors.SyncContext;
import br.com.pandecta.connectors.SyncResult;
import br.com.pandecta.connectors.Tribunais;
import br.com.pandecta.search.Indices;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * DataJud — sincronização incremental dos processos atualizados nos tribunais monitorados.
 * Grava a capa/movimentações no índice "processos" e gera eventos de Radar do tipo JULGAMENTO
 * (status JULGADA) quando uma movimentação indica julgamento. Esses eventos não são "publicações":
 * a publicação vem de DJEN/STJ.
 */
@Component
public class DataJudConnector implements SourceConnector {
    public static final String ID = "datajud";
    public static final String NOME = "DataJud — CNJ (API Pública)";

    /** Movimentações que indicam julgamento (pelo nome da Tabela Processual Unificada). Heurística documentada. */
    private static final Pattern JULGAMENTO = Pattern.compile(
            "(?i)(julgad|proced[êe]ncia|improced[êe]ncia|provimento|n[ãa]o[- ]provimento|homologa|extin[çc][ãa]o|acolhimento|rejei[çc][ãa]o de embargos|concess[ãa]o|denega[çc][ãa]o)");

    private final DataJudClient client;
    private final ObjectMapper mapper;
    private final Indices indices;
    private final AppProperties.DataJud cfg;

    public DataJudConnector(DataJudClient client, ObjectMapper mapper, Indices indices, AppProperties props) {
        this.client = client;
        this.mapper = mapper;
        this.indices = indices;
        this.cfg = props.datajud();
    }

    @Override public String id() { return ID; }
    @Override public String nome() { return NOME; }
    @Override public String descricao() {
        return "Metadados processuais (classe, assuntos, órgão julgador, movimentações) dos tribunais "
                + String.join(", ", aliases()).toUpperCase() + ". Consulta por número disponível para todos os tribunais da base pública (o STF não integra o DataJud).";
    }
    @Override public String url() { return "https://datajud-wiki.cnj.jus.br/api-publica/"; }
    @Override public String frequencia() { return "A cada 2 horas (incremental por data de atualização)"; }
    @Override public boolean configurado() { return client.configurado(); }
    @Override public String requisitos() {
        return "Defina DATAJUD_API_KEY com a chave pública vigente publicada pelo CNJ em datajud-wiki.cnj.jus.br/api-publica/acesso. "
                + "Não há contratação; respeite o Termo de Uso da API Pública.";
    }

    private List<String> aliases() {
        return cfg.radarAliases() == null ? List.of() : cfg.radarAliases().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public SyncResult sincronizar(SyncContext ctx) throws Exception {
        SyncResult r = ctx.result();
        int indisponiveis = 0;
        int limitePorTribunal = Math.max(100, cfg.maxPerRun() / Math.max(1, aliases().size()));
        for (String alias : aliases()) {
            String desde = ctx.cursorString("desde:" + alias);
            if (desde == null) desde = Instant.now().minus(1, ChronoUnit.DAYS).toString();
            String maisRecente = desde;
            JsonNode searchAfter = null;
            int lidosTribunal = 0;
            try (SyncContext.Batch proc = ctx.batch(indices.processos()); SyncContext.Batch radar = ctx.batch(indices.radar())) {
                while (lidosTribunal < limitePorTribunal) {
                    ObjectNode q = mapper.createObjectNode();
                    q.put("size", 100);
                    q.putObject("query").putObject("range").putObject("dataHoraUltimaAtualizacao").put("gt", desde);
                    q.putArray("sort").addObject().putObject("@timestamp").put("order", "asc");
                    if (searchAfter != null) q.set("search_after", searchAfter);
                    JsonNode res = client.buscar(alias, q);
                    JsonNode hits = res.path("hits").path("hits");
                    if (!hits.isArray() || hits.isEmpty()) break;
                    for (JsonNode h : hits) {
                        lidosTribunal++;
                        r.lidos++;
                        JsonNode s = h.path("_source");
                        if (s.path("nivelSigilo").asInt(0) > 0) { r.ignorados++; continue; }
                        String atualizado = s.path("dataHoraUltimaAtualizacao").asText("");
                        if (atualizado.compareTo(maisRecente) > 0) maisRecente = atualizado;
                        Map<String, Object> p = client.mapear(s, null);
                        String sigla = String.valueOf(p.get("tribunal"));
                        String id = alias + ":" + s.path("numeroProcesso").asText() + ":" + s.path("grau").asText("");
                        Map<String, Object> doc = new LinkedHashMap<>(p);
                        doc.put("id", id);
                        doc.put("fonte", ID);
                        doc.put("fonteNome", NOME);
                        doc.put("urlOficial", "https://datajud-wiki.cnj.jus.br/api-publica/");
                        doc.put("conector", ID);
                        doc.put("sincronizadoEm", Instant.now().toString());
                        proc.add(id, doc);
                        eventosJulgamento(radar, p, sigla, desde);
                    }
                    searchAfter = hits.get(hits.size() - 1).path("sort");
                    if (searchAfter.isMissingNode() || hits.size() < 100) break;
                    ConnectorHttp.pausa(400);
                }
            } catch (ConnectorHttp.FonteIndisponivelException e) {
                r.erro(alias + ": " + e.getMessage());
                if (++indisponiveis == aliases().size()) r.fonteIndisponivel = true;
            } catch (Exception e) {
                r.erro(alias + ": " + e.getMessage());
            }
            r.cursor.put("desde:" + alias, maisRecente);
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private void eventosJulgamento(SyncContext.Batch radar, Map<String, Object> p, String sigla, String desde) {
        for (Map<String, Object> m : (List<Map<String, Object>>) p.get("movimentos")) {
            String nome = String.valueOf(m.get("nome"));
            String quando = (String) m.get("dataHora");
            if (quando == null || quando.compareTo(desde) <= 0 || !JULGAMENTO.matcher(nome).find()) continue;
            String id = "datajud:" + p.get("numeroProcesso") + ":" + m.get("codigo") + ":" + quando;
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("id", id);
            ev.put("tipoEvento", "JULGAMENTO");
            ev.put("statusPublicacao", "JULGADA");
            ev.put("tribunal", sigla);
            ev.put("uf", Tribunais.uf(sigla));
            ev.put("numeroProcesso", p.get("numeroProcesso"));
            ev.put("titulo", nome + " — " + p.get("classe"));
            ev.put("resumo", "Movimentação registrada no DataJud: " + nome
                    + (m.get("complemento") != null ? " (" + m.get("complemento") + ")" : "") + ". Publicação ainda não localizada.");
            ev.put("orgaoJulgador", p.get("orgaoJulgador"));
            ev.put("assuntos", String.join("; ", (List<String>) p.get("assuntos")));
            ev.put("dataEvento", quando);
            ev.put("horarioDisponivel", quando.length() > 10);
            ev.put("fonte", ID);
            ev.put("fonteNome", NOME);
            ev.put("conector", ID);
            ev.put("sincronizadoEm", Instant.now().toString());
            ev.put("hashConteudo", Texts.sha256(id));
            radar.add(id, ev);
        }
    }
}
