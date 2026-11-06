package br.com.pandecta.search;

import br.com.pandecta.common.Texts;

import java.time.LocalDate;
import java.util.List;

/** Filtros aceitos pela pesquisa. Valores são validados/limitados antes de entrar na consulta. */
public record SearchFilters(
        String aba,
        List<String> tribunais,
        String uf,
        String orgaoJulgador,
        String relator,
        String numeroProcesso,
        String classe,
        String assunto,
        String tipoDecisao,
        String legislacao,
        LocalDate dataJulgamentoInicio,
        LocalDate dataJulgamentoFim,
        LocalDate dataPublicacaoInicio,
        LocalDate dataPublicacaoFim,
        String normaId,
        String normaTipo,
        Boolean somenteVigentes,
        String ordenacao, // relevancia | data | tribunal
        int pagina,
        int tamanho) {

    public SearchFilters {
        aba = aba == null || aba.isBlank() ? "todos" : aba;
        tribunais = tribunais == null ? List.of() : tribunais.stream().map(t -> Texts.userInput(t, 20).toUpperCase()).filter(t -> !t.isBlank()).limit(30).toList();
        uf = clean(uf, 2);
        orgaoJulgador = clean(orgaoJulgador, 200);
        relator = clean(relator, 120);
        numeroProcesso = clean(numeroProcesso, 40);
        classe = clean(classe, 120);
        assunto = clean(assunto, 200);
        tipoDecisao = clean(tipoDecisao, 60);
        legislacao = clean(legislacao, 200);
        normaId = clean(normaId, 60);
        normaTipo = clean(normaTipo, 60);
        ordenacao = ordenacao == null ? "relevancia" : ordenacao;
        pagina = Math.max(0, Math.min(pagina, 400));
        tamanho = tamanho <= 0 ? 20 : Math.min(tamanho, 50);
    }

    private static String clean(String s, int max) {
        if (s == null || s.isBlank()) return null;
        return Texts.userInput(s, max);
    }

    public static SearchFilters simples(String aba, int pagina, int tamanho) {
        return new SearchFilters(aba, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, "relevancia", pagina, tamanho);
    }
}
