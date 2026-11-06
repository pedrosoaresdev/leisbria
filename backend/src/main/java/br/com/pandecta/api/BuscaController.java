package br.com.pandecta.api;

import br.com.pandecta.common.PageResult;
import br.com.pandecta.common.Texts;
import br.com.pandecta.legislation.LegislationService;
import br.com.pandecta.search.Citation;
import br.com.pandecta.search.CitationParser;
import br.com.pandecta.search.Resultado;
import br.com.pandecta.search.SearchFilters;
import br.com.pandecta.search.SearchService;
import br.com.pandecta.security.AuthUser;
import br.com.pandecta.security.CurrentUser;
import br.com.pandecta.user.UserDataService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pesquisa unificada: legislação + jurisprudência, com abas, filtros e dispositivo citado. */
@RestController
@RequestMapping("/api")
public class BuscaController {
    private final SearchService search;
    private final CitationParser parser;
    private final LegislationService legislacao;
    private final UserDataService userData;

    public BuscaController(SearchService search, CitationParser parser, LegislationService legislacao, UserDataService userData) {
        this.search = search;
        this.parser = parser;
        this.legislacao = legislacao;
        this.userData = userData;
    }

    @GetMapping("/busca")
    public Map<String, Object> buscar(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "todos") String aba,
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publicacaoFim,
            @CurrentUser(required = false) AuthUser user) {

        String consulta = Texts.userInput(q, 2000);
        Citation citacao = parser.parse(consulta);
        SearchFilters f = new SearchFilters(aba, tribunal, uf, orgaoJulgador, relator, numeroProcesso, classe, assunto,
                tipoDecisao, legislacao, julgamentoInicio, julgamentoFim, publicacaoInicio, publicacaoFim,
                null, null, true, ordenacao, pagina, tamanho);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("consulta", consulta);
        List<String> avisos = new ArrayList<>();

        if (citacao.temDispositivo()) {
            try {
                Map<String, Object> ref = this.legislacao.resolver(consulta);
                out.put("citacao", ref);
                this.legislacao.dispositivoOpcional((String) ref.get("dispositivoId")).ifPresent(d -> out.put("dispositivoCitado", d));
            } catch (Exception e) {
                avisos.add(e.getMessage());
            }
        }

        boolean todos = "todos".equals(f.aba());
        if (todos || SearchService.ABAS_LEGISLACAO.contains(f.aba())) {
            out.put("legislacao", search.buscarDispositivos(consulta, f, citacao));
        }
        if (todos) {
            PageResult<Resultado> normas = search.buscarNormas(consulta, null, 0, 3);
            out.put("normas", normas);
        }
        if (todos || SearchService.ABAS_JURIS.contains(f.aba())) {
            out.put("jurisprudencia", search.buscarJurisprudencia(consulta, f, citacao));
        }
        if ("doutrina".equals(f.aba())) {
            avisos.add("Nenhuma fonte de doutrina licenciada está configurada.");
        }
        if (!Texts.isBlank(consulta)) out.put("abas", search.contarAbas(consulta));
        if (!avisos.isEmpty()) out.put("avisos", avisos);

        if (user != null && !Texts.isBlank(consulta) && pagina == 0) {
            long total = 0;
            if (out.get("legislacao") instanceof PageResult<?> p) total += p.total();
            if (out.get("jurisprudencia") instanceof PageResult<?> p) total += p.total();
            userData.registrarHistoricoAsync(user.uid(), consulta, f.aba(), total);
        }
        return out;
    }
}
