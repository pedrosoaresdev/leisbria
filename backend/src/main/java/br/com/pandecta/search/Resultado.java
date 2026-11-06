package br.com.pandecta.search;

import java.util.List;

/**
 * Card de resultado exibido no frontend.
 * Trechos destacados usam os marcadores \u0001 (início) e \u0002 (fim) em vez de HTML,
 * e o frontend os converte em <mark> com segurança (sem dangerouslySetInnerHTML).
 */
public record Resultado(
        String tipo,              // dispositivo | norma | decisao
        String id,
        String titulo,
        String subtitulo,
        String fonte,             // código da fonte (planalto, stj-dados-abertos, djen, datajud, lexml)
        String fonteNome,
        String tribunal,
        String data,
        String trecho,
        List<String> termosEncontrados,
        String relevancia,        // explicação objetiva de por que o item apareceu
        double score,
        String urlOficial,
        boolean documentoOficial, // true: texto vindo da fonte oficial; nunca texto gerado por IA
        String statusPublicacao,  // PUBLICADA | JULGADA (decisões)
        Boolean vigente,          // dispositivos
        String categoria,
        String normaId) {}
