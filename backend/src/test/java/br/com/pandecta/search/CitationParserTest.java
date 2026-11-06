package br.com.pandecta.search;

import br.com.pandecta.legislation.NormaCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitationParserTest {
    private final CitationParser parser;

    CitationParserTest() throws Exception {
        parser = new CitationParser(new NormaCatalog(new ObjectMapper()));
    }

    @Test
    void artigoEIncisoSemNorma() {
        Citation c = parser.parse("art. 5º, inciso LXIII");
        assertEquals("5", c.artigo());
        assertEquals("LXIII", c.inciso());
        assertNull(c.normaId());
    }

    @Test
    void artigoComSigla() {
        Citation c = parser.parse("artigo 310 do CPP");
        assertEquals("310", c.artigo());
        assertEquals("cpp1941", c.normaId());
    }

    @Test
    void artigoComMilharEParagrafo() {
        Citation c = parser.parse("art. 1.036, § 1º, do CPC");
        assertEquals("1036", c.artigo());
        assertEquals("1", c.paragrafo());
        assertEquals("cpc2015", c.normaId());
    }

    @Test
    void leiPorNumero() {
        Citation c = parser.parse("art. 33 da Lei 11.343/2006");
        assertEquals("33", c.artigo());
        assertEquals("drogas2006", c.normaId());
        assertEquals(2006, c.normaAno());
    }

    @Test
    void sumulaTemaEProcesso() {
        Citation s = parser.parse("Súmula vinculante 11");
        assertTrue(s.sumulaVinculante());
        assertEquals("11", s.sumulaNumero());
        assertEquals("STF", s.sumulaTribunal());
        assertEquals("7", parser.parse("súmula 7/STJ").sumulaNumero());
        assertEquals("STJ", parser.parse("súmula 7/STJ").sumulaTribunal());
        assertEquals("1031", parser.parse("Tema 1.031").temaNumero());
        assertEquals("0001234-56.2024.8.26.0100", parser.parse("processo 00012345620248260100").numeroProcessoCnj());
    }

    @Test
    void artigoComLetraEParagrafoUnico() {
        Citation c = parser.parse("art. 103-A, parágrafo único, da CF");
        assertEquals("103-A", c.artigo());
        assertEquals("unico", c.paragrafo());
        assertEquals("cf1988", c.normaId());
    }

    @Test
    void perguntaEmLinguagemNaturalNaoInventaCitacao() {
        Citation c = parser.parse("Quais são os direitos de quem teve a conta bancária bloqueada indevidamente?");
        assertTrue(c.vazia());
    }
}
