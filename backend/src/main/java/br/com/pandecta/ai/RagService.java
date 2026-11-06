package br.com.pandecta.ai;

import br.com.pandecta.common.Texts;
import br.com.pandecta.legislation.LegislationService;
import br.com.pandecta.legislation.NormaCatalog;
import br.com.pandecta.search.Citation;
import br.com.pandecta.search.CitationParser;
import br.com.pandecta.search.Indices;
import br.com.pandecta.search.SearchClient;
import br.com.pandecta.search.SearchFilters;
import br.com.pandecta.search.SearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * IA jurídica com RAG (Retrieval-Augmented Generation):
 *  1. Interpretação: o modelo identifica assuntos, termos de busca e possíveis dispositivos (apenas como pistas).
 *  2. Recuperação: busca na base oficial (OpenSearch); pistas de dispositivos só entram se EXISTIREM no índice.
 *  3. Geração: o modelo recebe apenas as fontes numeradas [S1..Sn] e responde em JSON com seções separadas.
 *  4. Verificação: referências inexistentes e menções a leis/artigos/processos que não constam das fontes são
 *     removidas e listadas como "informação não confirmada". O texto literal exibido vem do índice, nunca do modelo.
 */
@Service
public class RagService {
    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final String SISTEMA_ANALISE = """
            Você auxilia uma ferramenta de pesquisa jurídica brasileira. Analise a pergunta e responda SOMENTE com JSON:
            {"assuntos": [até 6 temas jurídicos curtos], "termosBusca": [até 10 termos/expressões técnicas para busca textual],
             "referencias": [até 8 objetos {"norma": sigla ou "Lei 0000/AAAA", "artigo": "número"} que PODEM ser pertinentes],
             "ramos": [ramos do Direito]}
            As referências são apenas pistas de busca e serão conferidas na base oficial; não as invente por preencher.
            """;

    private static final String SISTEMA_RESPOSTA = """
            Você é um assistente de pesquisa jurídica brasileira. Regras obrigatórias:
            1. Use EXCLUSIVAMENTE as fontes fornecidas, identificadas como [S1], [S2]… Não use conhecimento externo para citar
               leis, artigos, súmulas, temas, números de processo, ementas ou decisões.
            2. Toda afirmação jurídica deve terminar com a(s) referência(s) [S#] que a sustentam.
            3. Nunca cite dispositivo, lei, súmula, tema ou processo que não apareça literalmente nas fontes.
            4. Se as fontes não bastarem para uma conclusão segura, diga isso e marque "suficiente": false.
            5. Não transcreva o texto da lei: ele é exibido à parte, literalmente, pela plataforma.
            6. Linguagem clara, objetiva, em português do Brasil. Não dê parecer conclusivo sobre caso concreto.
            Responda SOMENTE com JSON no formato:
            {"suficiente": true|false,
             "motivoInsuficiencia": "texto ou vazio",
             "resumo": "síntese objetiva da resposta, com [S#]",
             "itens": [{"fonte": "S#", "relacao": "por que esta fonte é pertinente à pergunta (1-2 frases)"}],
             "entendimentoJurisprudencial": "síntese do que as DECISÕES fornecidas indicam, com [S#], ou vazio se não houver decisões",
             "interpretacao": "raciocínio interpretativo que conecta fatos e normas, com [S#]",
             "naoConfirmado": ["pontos relevantes que as fontes NÃO permitem confirmar"]}
            """;

    private final LlmClient llm;
    private final SearchService search;
    private final SearchClient client;
    private final Indices indices;
    private final CitationParser parser;
    private final NormaCatalog catalogo;
    private final ObjectMapper mapper;
    private final int maxFontes;

    public RagService(LlmClient llm, SearchService search, SearchClient client, Indices indices,
                      CitationParser parser, NormaCatalog catalogo, ObjectMapper mapper,
                      br.com.pandecta.config.AppProperties props) {
        this.llm = llm;
        this.search = search;
        this.client = client;
        this.indices = indices;
        this.parser = parser;
        this.catalogo = catalogo;
        this.mapper = mapper;
        this.maxFontes = Math.max(4, Math.min(props.ai().maxSources(), 25));
    }

    private record Fonte(String ref, String tipo, String id, String normaId, String titulo, String trechoLiteral,
                         String textoParaModelo, String fonteNome, String urlOficial, String data) {}

    public Map<String, Object> perguntar(String perguntaBruta) {
        String pergunta = Texts.userInput(perguntaBruta, 2000);
        Map<String, Object> out = new LinkedHashMap<>();
        if (!llm.habilitado()) {
            out.put("habilitada", false);
            out.put("mensagem", "A análise com IA não está configurada (ANTHROPIC_API_KEY). Os resultados de legislação e jurisprudência abaixo continuam disponíveis.");
            return out;
        }
        out.put("habilitada", true);
        List<String> avisos = new ArrayList<>();

        // 1. Interpretação
        List<String> termos = new ArrayList<>();
        List<String> assuntos = new ArrayList<>();
        List<String[]> pistas = new ArrayList<>();
        try {
            JsonNode a = llm.json(llm.completar(SISTEMA_ANALISE, pergunta, 600));
            a.path("assuntos").forEach(x -> { if (assuntos.size() < 6) assuntos.add(Texts.userInput(x.asText(), 80)); });
            a.path("termosBusca").forEach(x -> { if (termos.size() < 10) termos.add(Texts.userInput(x.asText(), 80)); });
            a.path("referencias").forEach(x -> { if (pistas.size() < 8) pistas.add(new String[]{x.path("norma").asText(""), x.path("artigo").asText("")}); });
        } catch (Exception e) {
            log.warn("Análise da pergunta falhou: {}", e.getMessage());
            avisos.add("A etapa de interpretação da pergunta falhou; a busca usou apenas o texto original.");
        }
        out.put("assuntos", assuntos);

        // 2. Recuperação
        List<Fonte> fontes = recuperar(pergunta, termos, pistas);
        if (fontes.isEmpty()) {
            out.put("suficiente", false);
            out.put("motivoInsuficiencia", "Nenhum dispositivo legal ou decisão pertinente foi encontrado na base sincronizada.");
            out.put("fontes", List.of());
            out.put("avisos", avisos);
            out.put("modelo", llm.modelo());
            out.put("geradoEm", Instant.now().toString());
            return out;
        }

        // 3. Geração
        StringBuilder ctx = new StringBuilder("PERGUNTA:\n").append(pergunta).append("\n\nFONTES:\n");
        for (Fonte f : fontes) {
            ctx.append("[").append(f.ref()).append("] (").append(f.tipo().equals("dispositivo") ? "LEGISLAÇÃO" : "DECISÃO").append(") ")
                    .append(f.titulo()).append("\n").append(f.textoParaModelo()).append("\n\n");
        }
        JsonNode r;
        try {
            r = llm.json(llm.completar(SISTEMA_RESPOSTA, ctx.toString(), 2500));
        } catch (Exception e) {
            log.warn("Geração falhou: {}", e.getMessage());
            out.put("suficiente", false);
            out.put("motivoInsuficiencia", "O serviço de IA não respondeu. As fontes recuperadas estão listadas abaixo para consulta direta.");
            out.put("fontes", fontesSaida(fontes));
            out.put("avisos", avisos);
            out.put("modelo", llm.modelo());
            out.put("geradoEm", Instant.now().toString());
            return out;
        }

        // 4. Verificação
        List<String> textos = new ArrayList<>();
        for (Fonte f : fontes) textos.add(f.titulo() + "\n" + f.textoParaModelo());
        CitationVerifier v = new CitationVerifier(textos);
        List<String> naoConfirmado = new ArrayList<>();
        r.path("naoConfirmado").forEach(x -> naoConfirmado.add(Texts.userInput(x.asText(), 600)));
        Set<String> refsInvalidas = new LinkedHashSet<>();
        for (String campo : List.of("resumo", "entendimentoJurisprudencial", "interpretacao")) {
            CitationVerifier.Resultado res = v.verificar(r.path(campo).asText(""));
            out.put(campo, res.texto());
            res.removidas().forEach(frase -> naoConfirmado.add("Trecho removido por citar referência ausente nas fontes: \"" + Texts.truncate(frase, 300) + "\""));
            refsInvalidas.addAll(res.refsInvalidas());
        }
        List<Map<String, String>> itens = new ArrayList<>();
        Set<String> refsValidas = new LinkedHashSet<>();
        fontes.forEach(f -> refsValidas.add(f.ref()));
        for (JsonNode it : r.path("itens")) {
            String ref = it.path("fonte").asText("").replace("[", "").replace("]", "");
            if (!refsValidas.contains(ref)) { refsInvalidas.add(ref); continue; }
            CitationVerifier.Resultado rel = v.verificar(it.path("relacao").asText(""));
            itens.add(Map.of("fonte", ref, "relacao", rel.texto()));
        }
        if (!refsInvalidas.isEmpty()) avisos.add("Referências inexistentes geradas pela IA foram descartadas: " + String.join(", ", refsInvalidas));
        boolean suficiente = r.path("suficiente").asBoolean(false);
        out.put("suficiente", suficiente);
        if (!suficiente) out.put("motivoInsuficiencia", Texts.userInput(r.path("motivoInsuficiencia").asText(""), 600));
        out.put("itens", itens);
        out.put("naoConfirmado", naoConfirmado);
        out.put("fontes", fontesSaida(fontes));
        out.put("avisos", avisos);
        out.put("modelo", llm.modelo());
        out.put("geradoEm", Instant.now().toString());
        return out;
    }

    private List<Fonte> recuperar(String pergunta, List<String> termos, List<String[]> pistas) {
        String consulta = pergunta + (termos.isEmpty() ? "" : " " + String.join(" ", termos));
        Citation citacao = parser.parse(pergunta);
        Map<String, Fonte> porId = new LinkedHashMap<>();
        int n = 1;

        // Dispositivos citados explicitamente na pergunta ou sugeridos como pista — somente se existirem no índice
        List<String> ids = new ArrayList<>();
        if (citacao.temDispositivo()) ids.addAll(LegislationService.candidatos(citacao.normaId() != null ? citacao.normaId() : "cf1988", citacao));
        for (String[] p : pistas) {
            Citation c = parser.parse("art. " + p[1] + " " + p[0]);
            if (c.temDispositivo() && c.normaId() != null) ids.add(LegislationService.candidatos(c.normaId(), c).get(0));
        }
        for (String id : ids) {
            if (porId.size() >= maxFontes / 3) break;
            if (porId.containsKey(id)) continue;
            Optional<JsonNode> d = client.get(indices.dispositivos(), id);
            if (d.isPresent()) porId.put(id, fonteDispositivo("S" + n++, d.get()));
        }

        SearchFilters fLeg = SearchFilters.simples("legislacao", 0, 8);
        for (var r : search.buscarDispositivos(consulta, fLeg, citacao).itens()) {
            if (porId.size() >= (maxFontes * 2) / 3) break;
            if (porId.containsKey(r.id())) continue;
            Optional<JsonNode> d = client.get(indices.dispositivos(), r.id());
            if (d.isPresent()) porId.put(r.id(), fonteDispositivo("S" + n++, d.get()));
        }
        SearchFilters fJur = SearchFilters.simples("jurisprudencia", 0, 8);
        for (var r : search.buscarJurisprudencia(consulta, fJur, citacao).itens()) {
            if (porId.size() >= maxFontes) break;
            if (porId.containsKey(r.id())) continue;
            Optional<JsonNode> d = client.get(indices.jurisprudencia(), r.id());
            if (d.isPresent()) porId.put(r.id(), fonteDecisao("S" + n++, d.get()));
        }
        return new ArrayList<>(porId.values());
    }

    private Fonte fonteDispositivo(String ref, JsonNode d) {
        String titulo = d.path("normaTitulo").asText() + " — " + d.path("rotulo").asText();
        String texto = d.path("texto").asText();
        String contexto = d.has("textoCaput") && !d.path("textoCaput").asText().isBlank()
                ? "Caput do artigo: " + d.path("textoCaput").asText() + "\nDispositivo: " + texto : texto;
        return new Fonte(ref, "dispositivo", d.path("id").asText(), d.path("normaId").asText(), titulo, texto,
                Texts.truncate(contexto, 2500), d.path("fonteNome").asText(), d.path("urlOficial").asText(null), null);
    }

    private Fonte fonteDecisao(String ref, JsonNode d) {
        String titulo = (d.path("tribunal").asText() + " — " + d.path("classe").asText("") + " " + d.path("numeroProcesso").asText("")).trim();
        StringBuilder t = new StringBuilder();
        if (!d.path("relator").asText("").isBlank()) t.append("Relator: ").append(d.path("relator").asText()).append(". ");
        if (!d.path("dataJulgamento").asText("").isBlank()) t.append("Julgado em ").append(d.path("dataJulgamento").asText()).append(". ");
        if (!d.path("tese").asText("").isBlank()) t.append("\nTese: ").append(d.path("tese").asText());
        String ementa = d.path("ementa").asText("");
        if (!ementa.isBlank()) t.append("\nEmenta: ").append(ementa);
        else t.append("\nTrecho: ").append(Texts.truncate(d.path("inteiroTeor").asText(""), 3000));
        String literal = !ementa.isBlank() ? ementa : Texts.truncate(d.path("inteiroTeor").asText(""), 800);
        return new Fonte(ref, "decisao", d.path("id").asText(), null, titulo, Texts.truncate(literal, 1500),
                Texts.truncate(t.toString(), 4000), d.path("fonteNome").asText(), d.path("urlOficial").asText(null),
                d.path("dataPublicacao").asText(d.path("dataJulgamento").asText(null)));
    }

    private List<Map<String, Object>> fontesSaida(List<Fonte> fontes) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Fonte f : fontes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ref", f.ref());
            m.put("tipo", f.tipo());
            m.put("id", f.id());
            if (f.normaId() != null) m.put("normaId", f.normaId());
            m.put("titulo", f.titulo());
            m.put("trechoLiteral", f.trechoLiteral());
            m.put("fonteNome", f.fonteNome());
            if (f.urlOficial() != null) m.put("urlOficial", f.urlOficial());
            if (f.data() != null) m.put("data", f.data());
            out.add(m);
        }
        return out;
    }
}
