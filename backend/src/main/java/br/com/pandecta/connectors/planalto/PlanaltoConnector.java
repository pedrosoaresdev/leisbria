package br.com.pandecta.connectors.planalto;

import br.com.pandecta.common.Texts;
import br.com.pandecta.connectors.ConnectorHttp;
import br.com.pandecta.connectors.SourceConnector;
import br.com.pandecta.connectors.SyncContext;
import br.com.pandecta.connectors.SyncResult;
import br.com.pandecta.legislation.NormaCatalog;
import br.com.pandecta.search.Indices;
import br.com.pandecta.search.SearchClient;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portal da Legislação do Planalto. Não há API: o conector baixa o HTML compilado de cada norma do
 * catálogo (sources/catalogo-normas.json), interpreta com o {@link PlanaltoParser} e reindexa a norma
 * somente quando o conteúdo muda (hash). Acesso educado: uma norma por vez, com pausa entre downloads.
 */
@Component
public class PlanaltoConnector implements SourceConnector {
    private static final Logger log = LoggerFactory.getLogger(PlanaltoConnector.class);
    public static final String ID = "planalto";
    public static final String NOME = "Portal da Legislação — Planalto";

    private final ConnectorHttp http;
    private final NormaCatalog catalogo;
    private final Indices indices;
    private final SearchClient search;

    public PlanaltoConnector(ConnectorHttp http, NormaCatalog catalogo, Indices indices, SearchClient search) {
        this.http = http;
        this.catalogo = catalogo;
        this.indices = indices;
        this.search = search;
    }

    @Override public String id() { return ID; }
    @Override public String nome() { return NOME; }
    @Override public String descricao() {
        return "Texto integral compilado (redação atual, redações anteriores tachadas e notas de alteração) das "
                + catalogo.todas().size() + " normas do catálogo: Constituição, códigos e leis principais.";
    }
    @Override public String url() { return "https://www.planalto.gov.br/ccivil_03/"; }
    @Override public String frequencia() { return "Diária (madrugada); reindexa só normas alteradas"; }
    @Override public boolean configurado() { return true; }

    @Override
    public SyncResult sincronizar(SyncContext ctx) throws Exception {
        SyncResult r = ctx.result();
        int falhasRede = 0;
        for (NormaCatalog.Entrada e : catalogo.todas()) {
            r.lidos++;
            try {
                ConnectorHttp.Resposta resp = http.get(e.url(), Map.of("Accept", "text/html"));
                if (resp.status() == 404) {
                    r.erro(e.id() + ": URL não encontrada (404) — revise o catálogo: " + e.url());
                    continue;
                }
                if (resp.status() >= 400) {
                    r.erro(e.id() + ": HTTP " + resp.status());
                    continue;
                }
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resp.corpo()));
                if (hash.equals(ctx.cursorString("hash:" + e.id()))) {
                    r.ignorados++;
                    r.cursor.put("hash:" + e.id(), hash);
                    continue;
                }
                PlanaltoParser.Resultado res = new PlanaltoParser(e.id()).parse(resp.corpo(), e.url());
                if (res.dispositivos().size() < 3) {
                    r.erro(e.id() + ": nenhum dispositivo reconhecido (layout da página mudou?)");
                    continue;
                }
                indexar(ctx, e, res, hash);
                r.cursor.put("hash:" + e.id(), hash);
                log.info("Planalto: {} → {} dispositivos", e.id(), res.dispositivos().size());
            } catch (ConnectorHttp.FonteIndisponivelException ex) {
                r.erro(e.id() + ": " + ex.getMessage());
                if (++falhasRede >= 3) {
                    r.fonteIndisponivel = true;
                    break;
                }
            } catch (Exception ex) {
                r.erro(e.id() + ": " + ex.getMessage());
            }
            ConnectorHttp.pausa(1500);
        }
        // preserva hashes de normas não processadas nesta execução
        ctx.cursorAnterior().forEach(r.cursor::putIfAbsent);
        return r;
    }

    private void indexar(SyncContext ctx, NormaCatalog.Entrada e, PlanaltoParser.Resultado res, String hash) {
        String agora = Instant.now().toString();
        // Remove versões anteriores dos dispositivos desta norma (evita restos de redações que deixaram de existir)
        ObjectNode q = search.mapper().createObjectNode();
        q.putObject("query").putObject("term").put("normaId", e.id());
        search.deleteByQuery(indices.dispositivos(), q);

        String tituloNorma = e.apelido().isBlank() ? e.titulo() : e.apelido();
        try (SyncContext.Batch b = ctx.batch(indices.dispositivos())) {
            for (Map<String, Object> d : res.dispositivos()) {
                d.put("normaTitulo", tituloNorma);
                d.put("normaSiglas", e.siglas());
                d.put("normaTipo", e.tipo());
                d.put("fonte", ID);
                d.put("fonteNome", NOME);
                d.put("urlOficial", e.url());
                d.put("conector", ID);
                d.put("sincronizadoEm", agora);
                d.put("hashConteudo", Texts.sha256(String.valueOf(d.get("texto"))));
                b.add((String) d.get("id"), d);
            }
        }
        Map<String, Object> norma = new LinkedHashMap<>();
        norma.put("id", e.id());
        norma.put("tipo", e.tipo());
        norma.put("esfera", "Federal");
        norma.put("localidade", "Brasil");
        norma.put("numero", e.numero());
        norma.put("ano", e.ano());
        norma.put("titulo", e.titulo());
        norma.put("apelido", e.apelido());
        norma.put("siglas", e.siglas());
        norma.put("ementa", res.ementa());
        norma.put("dataPublicacao", e.dataPublicacao());
        norma.put("situacao", res.situacao());
        norma.put("textoDisponivel", true);
        norma.put("totalDispositivos", res.dispositivos().size());
        norma.put("estrutura", res.estrutura());
        norma.put("historicoAlteracoes", res.historicoAlteracoes());
        norma.put("fonte", ID);
        norma.put("fonteNome", NOME);
        norma.put("urlOficial", e.url());
        norma.put("conector", ID);
        norma.put("sincronizadoEm", agora);
        norma.put("hashConteudo", hash);
        search.bulkIndex(indices.normas(), List.<Map.Entry<String, Object>>of(Map.entry(e.id(), (Object) norma)));
        search.refresh(indices.dispositivos());
    }
}
