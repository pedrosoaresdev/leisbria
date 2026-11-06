package br.com.pandecta.api;

import br.com.pandecta.common.PageResult;
import br.com.pandecta.common.Texts;
import br.com.pandecta.legislation.LegislationService;
import br.com.pandecta.search.Resultado;
import br.com.pandecta.search.SearchService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/legislacao")
public class LegislacaoController {
    private final LegislationService service;
    private final SearchService search;

    public LegislacaoController(LegislationService service, SearchService search) {
        this.service = service;
        this.search = search;
    }

    @GetMapping("/normas")
    public PageResult<Resultado> normas(@RequestParam(defaultValue = "") String q,
                                        @RequestParam(required = false) String tipo,
                                        @RequestParam(defaultValue = "0") int pagina,
                                        @RequestParam(defaultValue = "30") int tamanho) {
        return search.buscarNormas(Texts.userInput(q, 300), Texts.isBlank(tipo) ? null : Texts.userInput(tipo, 60),
                Math.max(0, Math.min(pagina, 300)), Math.max(1, Math.min(tamanho, 50)));
    }

    @GetMapping("/normas/{id}")
    public Map<String, Object> norma(@PathVariable String id) {
        return service.norma(Texts.userInput(id, 200));
    }

    @GetMapping("/resolver")
    public Map<String, Object> resolver(@RequestParam String ref) {
        return service.resolver(Texts.userInput(ref, 300));
    }

    @GetMapping("/dispositivos/{id}")
    public JsonNode dispositivo(@PathVariable String id) {
        return service.dispositivo(Texts.userInput(id, 300));
    }

    @GetMapping("/dispositivos/{id}/historico")
    public List<JsonNode> historico(@PathVariable String id) {
        return service.historico(Texts.userInput(id, 300));
    }
}
