package br.com.pandecta.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitationVerifierTest {

    private final CitationVerifier v = new CitationVerifier(List.of(
            "Constituição Federal — Art. 5º, LXII\na prisão de qualquer pessoa e o local onde se encontre serão comunicados imediatamente ao juiz competente",
            "Código de Processo Penal — Art. 310\nApós receber o auto de prisão em flagrante..."));

    @Test
    void mantemFrasesComCitacoesPresentesNasFontes() {
        var r = v.verificar("A prisão deve ser comunicada ao juiz (art. 5º, LXII) [S1]. O art. 310 do CPP trata da audiência [S2].");
        assertTrue(r.removidas().isEmpty());
        assertTrue(r.texto().contains("[S1]"));
        assertTrue(r.texto().contains("[S2]"));
    }

    @Test
    void removeFraseComArtigoOuLeiInexistenteNasFontes() {
        var r = v.verificar("A comunicação é obrigatória [S1]. Conforme o art. 999 da Lei 12.345/2010, há multa [S2].");
        assertEquals(1, r.removidas().size());
        assertFalse(r.texto().contains("999"));
    }

    @Test
    void removeReferenciaDeFonteInexistente() {
        var r = v.verificar("Há direito à comunicação [S1][S7].");
        assertEquals(List.of("[S7]"), r.refsInvalidas());
        assertFalse(r.texto().contains("S7"));
    }

    @Test
    void removeNumeroDeProcessoNaoPresente() {
        var r = v.verificar("Ver o processo 0001234-56.2024.8.26.0100 [S1].");
        assertEquals(1, r.removidas().size());
    }
}
