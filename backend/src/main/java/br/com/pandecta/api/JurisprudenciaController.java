package br.com.pandecta.api;

import br.com.pandecta.common.ApiException;
import br.com.pandecta.common.PageResult;
import br.com.pandecta.common.Texts;
import br.com.pandecta.connectors.datajud.DataJudClient;
import br.com.pandecta.search.CitationParser;
import br.com.pandecta.search.Indices;
import br.com.pandecta.search.Resultado;
import br.com.pandecta.search.SearchClient;
import br.com.pandecta.search.SearchFilters;
import br.com.pandecta.search.SearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class JurisprudenciaController {
    private final SearchService search;
    private final SearchClient client;
    private final Indices indices;
    private final CitationParser parser;
    private final DataJudClient datajud;

    public JurisprudenciaController(SearchService search, SearchClient client, Indices indices, CitationParser parser, DataJudClient datajud) {
        this.search = search;
        this.client = client;
        this.indices = indices;
        this.parser = parser;
        this.datajud = datajud;
    }

    @GetMapping("/jurisprudencia")
    public PageResult<Resultado> listar(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "relevancia") String ordenacao,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @RequestParam(required = false) List<String> tribunal,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) String orgaoJulgador,
            @RequestParam(required = false) String relator,
            @RequestParam(required = false) String numeroProcesso,
            @RequestParam(required = false) String classe,
            @RequestParam(required = false) String assunto,
            @RequestParam(required = false) String legislacao,
            @RequestParam(required = false) String tipoDecisao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate julgamentoInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate julgamentoFim,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publicacaoInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publicacaoFim) {
        String consulta = Texts.userInput(q, 1000);
        SearchFilters f = new SearchFilters("jurisprudencia", tribunal, uf, orgaoJulgador, relator, numeroProcesso, classe, assunto,
                tipoDecisao, legislacao, julgamentoInicio, julgamentoFim, publicacaoInicio, publicacaoFim,
                null, null, null, ordenacao, pagina, tamanho);
        return search.buscarJurisprudencia(consulta, f, parser.parse(consulta));
    }

    @GetMapping("/jurisprudencia/{id}")
    public JsonNode decisao(@PathVariable String id) {
        JsonNode d = client.get(indices.jurisprudencia(), Texts.userInput(id, 300))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Decisão não encontrada."));
        ObjectNode o = d.deepCopy();
        o.remove("hashConteudo");
        return o;
    }

    /** Consulta processual em tempo real no DataJud (metadados públicos). */
    @GetMapping("/processos/{numero}")
    public Map<String, Object> processo(@PathVariable String numero) {
        return datajud.consultarProcesso(Texts.userInput(numero, 40));
    }
}
