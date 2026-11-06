package br.com.pandecta.connectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TribunaisTest {

    @Test
    void aliasDataJudPeloNumeroCnj() {
        assertEquals("tjsp", Tribunais.aliasDataJud("00012345620248260100"));   // J=8, TR=26
        assertEquals("tjdft", Tribunais.aliasDataJud("00012345620248070001"));  // J=8, TR=07
        assertEquals("tjmg", Tribunais.aliasDataJud("00012345620248130024"));   // J=8, TR=13
        assertEquals("trf4", Tribunais.aliasDataJud("50012345620244047000"));   // J=4, TR=04
        assertEquals("trt2", Tribunais.aliasDataJud("10012345620245020001"));   // J=5, TR=02
        assertEquals("tst", Tribunais.aliasDataJud("10012345620245000000"));    // J=5, TR=00
        assertEquals("stj", Tribunais.aliasDataJud("00012345620243000000"));    // J=3
        assertEquals("tre-rj", Tribunais.aliasDataJud("00012345620246190000")); // J=6, TR=19
        assertNull(Tribunais.aliasDataJud("00012345620241000000"));             // J=1 (STF) fora do DataJud
    }

    @Test
    void segmentoEUf() {
        assertEquals("ESTADUAL", Tribunais.segmento("TJSP"));
        assertEquals("SP", Tribunais.uf("TJSP"));
        assertEquals("DF", Tribunais.uf("TJDFT"));
        assertEquals("TRABALHO", Tribunais.segmento("TRT15"));
        assertEquals("MG", Tribunais.uf("TRE-MG"));
        assertEquals("MILITAR", Tribunais.segmento("TJMSP"));
    }
}
