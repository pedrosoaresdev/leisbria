package br.com.pandecta.connectors.planalto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture no formato do HTML compilado do Planalto (parágrafos, &lt;strike&gt; para redação anterior,
 * notas de alteração e ADCT). O texto é o da Constituição de 1988 (domínio público, Lei 9.610/98, art. 8º, IV);
 * a redação "anterior" tachada é um exemplo SINTÉTICO de teste, marcado como tal.
 */
class PlanaltoParserTest {

    static final String HTML = """
            <html><head><meta charset="utf-8"></head><body>
            <p>CONSTITUIÇÃO DA REPÚBLICA FEDERATIVA DO BRASIL DE 1988</p>
            <p>PREÂMBULO</p>
            <p>TÍTULO I</p>
            <p>Dos Princípios Fundamentais</p>
            <p>Art. 1º A República Federativa do Brasil, formada pela união indissolúvel dos Estados e Municípios e do Distrito Federal, constitui-se em Estado Democrático de Direito e tem como fundamentos:</p>
            <p>I - a soberania;</p>
            <p>II - a cidadania;</p>
            <p>III - a dignidade da pessoa humana;</p>
            <p>Parágrafo único. Todo o poder emana do povo, que o exerce por meio de representantes eleitos ou diretamente, nos termos desta Constituição.</p>
            <p>TÍTULO II</p>
            <p>Dos Direitos e Garantias Fundamentais</p>
            <p>CAPÍTULO I<br>DOS DIREITOS E DEVERES INDIVIDUAIS E COLETIVOS</p>
            <p>Art. 5º Todos são iguais perante a lei, sem distinção de qualquer natureza, garantindo-se aos brasileiros e aos estrangeiros residentes no País a inviolabilidade do direito à vida, à liberdade, à igualdade, à segurança e à propriedade, nos termos seguintes:</p>
            <p>LXIII - o preso será informado de seus direitos, entre os quais o de permanecer calado, sendo-lhe assegurada a assistência da família e de advogado;</p>
            <p><strike>LXXVIII - TEXTO SINTÉTICO DE TESTE representando redação anterior tachada;</strike></p>
            <p>LXXVIII - a todos, no âmbito judicial e administrativo, são assegurados a razoável duração do processo e os meios que garantam a celeridade de sua tramitação. <a href="#">(Incluído pela Emenda Constitucional nº 45, de 2004)</a></p>
            <p>§ 1º As normas definidoras dos direitos e garantias fundamentais têm aplicação imediata.</p>
            <p>ATO DAS DISPOSIÇÕES CONSTITUCIONAIS TRANSITÓRIAS</p>
            <p>Art. 1º O Presidente da República, o Presidente do Supremo Tribunal Federal e os membros do Congresso Nacional prestarão o compromisso de manter, defender e cumprir a Constituição, no ato e na data de sua promulgação.</p>
            <p>Brasília, 5 de outubro de 1988.</p>
            </body></html>
            """;

    private Map<String, Object> porId(List<Map<String, Object>> l, String id) {
        return l.stream().filter(d -> id.equals(d.get("id"))).findFirst().orElse(null);
    }

    @Test
    void reconheceArtigosIncisosParagrafos() {
        PlanaltoParser.Resultado r = new PlanaltoParser("cf1988").parse(HTML);
        List<Map<String, Object>> d = r.dispositivos();
        assertNotNull(porId(d, "cf1988:art-1"));
        assertNotNull(porId(d, "cf1988:art-1:inc-III"));
        assertNotNull(porId(d, "cf1988:art-1:par-unico"));
        Map<String, Object> lxiii = porId(d, "cf1988:art-5:inc-LXIII");
        assertNotNull(lxiii);
        assertTrue(((String) lxiii.get("texto")).startsWith("o preso será informado"));
        assertEquals("Art. 5º, LXIII", lxiii.get("rotulo"));
        assertEquals("LXIII -", lxiii.get("marcador"));
        assertNotNull(porId(d, "cf1988:art-5:par-1"));
    }

    @Test
    void separaRedacaoAnteriorENotas() {
        List<Map<String, Object>> d = new PlanaltoParser("cf1988").parse(HTML).dispositivos();
        Map<String, Object> atual = porId(d, "cf1988:art-5:inc-LXXVIII");
        Map<String, Object> antiga = porId(d, "cf1988:art-5:inc-LXXVIII@v1");
        assertNotNull(atual);
        assertNotNull(antiga);
        assertEquals(true, atual.get("vigente"));
        assertEquals(false, antiga.get("vigente"));
        assertEquals("cf1988:art-5:inc-LXXVIII", antiga.get("dispositivoBaseId"));
        assertFalse(((String) atual.get("texto")).contains("Incluído"));
        assertTrue(((String) atual.get("notasAlteracao")).contains("Emenda Constitucional nº 45"));
        assertTrue(((List<?>) atual.get("normasAlteradoras")).contains("EC 45/2004"));
    }

    @Test
    void estruturaEAdct() {
        PlanaltoParser.Resultado r = new PlanaltoParser("cf1988").parse(HTML);
        assertTrue(r.estrutura().stream().anyMatch(h -> "TÍTULO II".equals(h.get("rotulo"))
                && "Dos Direitos e Garantias Fundamentais".equals(h.get("nome"))
                && "cf1988:art-5".equals(h.get("antesDe"))));
        assertTrue(r.estrutura().stream().anyMatch(h -> "CAPÍTULO I".equals(h.get("rotulo"))));
        Map<String, Object> adct = porId(r.dispositivos(), "cf1988:adct:art-1");
        assertNotNull(adct, "ADCT reinicia a numeração com prefixo próprio");
        assertTrue(String.valueOf(adct.get("rotulo")).startsWith("ADCT"));
        assertTrue(String.valueOf(porId(r.dispositivos(), "cf1988:art-5:inc-LXIII").get("hierarquia")).contains("TÍTULO II"));
    }
}
