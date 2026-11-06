package br.com.pandecta.security;

/**
 * Usuário autenticado (token do Firebase validado no servidor).
 *
 * @param authTimeEpochSec momento do último login efetivo (claim auth_time), usado para exigir login recente
 *                         em operações sensíveis como troca de senha.
 */
public record AuthUser(String uid, String nome, boolean admin, long authTimeEpochSec, String provider) {
    public static final String REQUEST_ATTR = "pandecta.authUser";

    public boolean loginRecente(long maxSegundos) {
        return (System.currentTimeMillis() / 1000L) - authTimeEpochSec <= maxSegundos;
    }
}
