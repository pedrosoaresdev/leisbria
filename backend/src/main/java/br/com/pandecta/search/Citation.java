package br.com.pandecta.search;

/**
 * Referência jurídica reconhecida na consulta do usuário.
 * Todos os campos são opcionais; o parser preenche o que identificar.
 */
public record Citation(
        String normaId,          // id no catálogo (ex.: cf1988), quando identificada
        String normaTipo,        // "Lei", "Decreto", "Lei Complementar"... (referência por número)
        String normaNumero,
        Integer normaAno,
        boolean adct,
        String artigo,           // "5", "103-A", "1036"
        String paragrafo,        // "1", "unico"
        String inciso,           // "LXIII"
        String alinea,           // "a"
        String sumulaNumero,
        String sumulaTribunal,
        boolean sumulaVinculante,
        String temaNumero,
        String temaTribunal,
        String numeroProcessoCnj,
        String acaoControleClasse, // ADI, ADC, ADPF, ADO
        String acaoControleNumero) {

    public boolean temDispositivo() {
        return artigo != null;
    }

    public boolean vazia() {
        return normaId == null && normaNumero == null && artigo == null && sumulaNumero == null
                && temaNumero == null && numeroProcessoCnj == null && acaoControleNumero == null;
    }
}
