package br.com.pandecta.legislation;

import br.com.pandecta.common.ApiException;
import br.com.pandecta.search.Citation;
import br.com.pandecta.search.CitationParser;
import br.com.pandecta.search.Indices;
import br.com.pandecta.search.SearchClient;
import br.com.pandecta.search.SearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LegislationService {
    private final SearchClient client;
    private final Indices indices;
    private final CitationParser parser;
    private final NormaCatalog catalogo;
    private final ObjectMapper mapper;

    public LegislationService(SearchClient client, Indices indices, CitationParser parser, NormaCatalog catalogo) {
        this.client = client;
        this.indices = indices;
        this.parser = parser;
        this.catalogo = catalogo;
        this.mapper = client.mapper();
    }

    /** Norma com todos os dispositivos (vigentes e redações anteriores), em ordem. */
    @Cacheable(value = "norma", key = "#id")
    public Map<String, Object> norma(String id) {
        JsonNode norma = client.get(indices.normas(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Norma não encontrada ou ainda não sincronizada."));
        List<JsonNode> dispositivos = new ArrayList<>();
        JsonNode after = null;
        while (true) {
            ObjectNode q = mapper.createObjectNode();
            q.put("size", 2000);
            q.putObject("query").putObject("term").put("normaId", id);
            q.putArray("sort").addObject().putObject("ordem").put("order", "asc");
            q.putArray("_source").add("id").add("normaId").add("nivel").add("marcador").add("rotulo").add("artigo")
                    .add("texto").add("hierarquia").add("vigente").add("versao").add("dispositivoBaseId").add("notasAlteracao").add("ordem");
            if (after != null) q.set("search_after", after);
            JsonNode res = client.search(indices.dispositivos(), q);
            JsonNode hits = res.path("hits").path("hits");
            if (!hits.isArray() || hits.isEmpty()) break;
            hits.forEach(h -> dispositivos.add(h.path("_source")));
            if (hits.size() < 2000) break;
            after = hits.get(hits.size() - 1).path("sort");
        }
        ObjectNode n = norma.deepCopy();
        n.remove("hashConteudo");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("norma", n);
        out.put("dispositivos", dispositivos);
        return out;
    }

    public JsonNode dispositivo(String id) {
        return client.get(indices.dispositivos(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Dispositivo não encontrado."));
    }

    public Optional<JsonNode> dispositivoOpcional(String id) {
        return client.get(indices.dispositivos(), id);
    }

    /** Redações anteriores (tachadas na fonte) de um dispositivo vigente. */
    public List<JsonNode> historico(String id) {
        ObjectNode q = mapper.createObjectNode();
        q.put("size", 50);
        ArrayNode f = q.putObject("query").putObject("bool").putArray("filter");
        SearchService.term(f, "dispositivoBaseId", id);
        SearchService.term(f, "vigente", false);
        q.putArray("sort").addObject().putObject("versao").put("order", "asc");
        List<JsonNode> out = new ArrayList<>();
        client.search(indices.dispositivos(), q).path("hits").path("hits").forEach(h -> {
            ObjectNode s = h.path("_source").deepCopy();
            s.put("rotuloVersao", "Redação anterior " + s.path("versao").asInt());
            out.add(s);
        });
        return out;
    }

    /**
     * Resolve uma referência ("art. 5º, LXIII", "art. 310 do CPP", "art. 33 da Lei 11.343/2006") no id do
     * dispositivo. Sem norma indicada, assume a Constituição Federal. Se o inciso/parágrafo exato não
     * existir, devolve o artigo e indica exato=false.
     */
    public Map<String, Object> resolver(String ref) {
        Citation c = parser.parse(ref);
        if (!c.temDispositivo()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Informe um artigo, por exemplo: art. 5º, LXIII ou art. 310 do CPP.");
        }
        String normaId = c.normaId();
        if (normaId == null && c.normaNumero() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "O texto integral de " + c.normaTipo() + " " + c.normaNumero()
                    + (c.normaAno() != null ? "/" + c.normaAno() : "") + " ainda não está no catálogo. Consulte a norma pela busca de legislação.");
        }
        if (normaId == null) normaId = "cf1988";
        List<String> candidatos = candidatos(normaId, c);
        for (int i = 0; i < candidatos.size(); i++) {
            Optional<JsonNode> d = client.get(indices.dispositivos(), candidatos.get(i));
            if (d.isPresent()) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("normaId", normaId);
                out.put("dispositivoId", candidatos.get(i));
                out.put("rotulo", d.get().path("rotulo").asText());
                out.put("exato", i == 0);
                return out;
            }
        }
        String nome = catalogo.porId(normaId).map(NormaCatalog.Entrada::apelido).orElse(normaId);
        throw new ApiException(HttpStatus.NOT_FOUND, "Dispositivo não encontrado em " + nome + " (a norma pode ainda não ter sido sincronizada).");
    }

    /** Do mais específico ao artigo. */
    public static List<String> candidatos(String normaId, Citation c) {
        List<String> out = new ArrayList<>();
        String art = DispositivoIds.artigo(normaId, c.adct(), c.artigo());
        String base = c.paragrafo() != null ? DispositivoIds.paragrafo(art, c.paragrafo()) : art;
        if (c.inciso() != null) {
            String inc = DispositivoIds.inciso(base, c.inciso());
            if (c.alinea() != null) out.add(DispositivoIds.alinea(inc, c.alinea()));
            out.add(inc);
        }
        if (!base.equals(art)) out.add(base);
        out.add(art);
        return out;
    }
}
