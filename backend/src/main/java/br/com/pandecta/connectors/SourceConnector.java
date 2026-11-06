package br.com.pandecta.connectors;

/**
 * Conector de uma fonte oficial. Cada fonte tem sua própria implementação, isolada das demais:
 * falha em uma fonte não interrompe as outras.
 */
public interface SourceConnector {

    /** Identificador estável (usado em Firestore, índices e na interface). */
    String id();

    String nome();

    String descricao();

    /** Página oficial / documentação da fonte. */
    String url();

    /** Descrição legível da frequência de atualização configurada. */
    String frequencia();

    /** false quando falta configuração obrigatória (ex.: chave de API). */
    boolean configurado();

    /** O que precisa ser configurado ou contratado quando {@link #configurado()} é false. */
    default String requisitos() {
        return null;
    }

    /**
     * Executa uma sincronização incremental.
     *
     * @param ctx contexto com cursor anterior e utilidades de gravação
     */
    SyncResult sincronizar(SyncContext ctx) throws Exception;
}
