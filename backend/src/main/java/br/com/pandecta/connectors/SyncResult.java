package br.com.pandecta.connectors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Totais e erros de uma execução de sincronização. Mutável durante a execução. */
public class SyncResult {
    public int lidos;
    public int gravados;
    public int ignorados;
    public int duplicados;
    public final List<String> erros = new ArrayList<>();
    /** Estado a ser lembrado para a próxima execução incremental (datas, páginas, ids). */
    public final Map<String, Object> cursor = new HashMap<>();
    /** true quando a fonte não respondeu (indisponível), diferente de erro de processamento. */
    public boolean fonteIndisponivel;

    public void erro(String msg) {
        if (erros.size() < 50) erros.add(msg.length() > 500 ? msg.substring(0, 500) : msg);
    }

    @Override
    public String toString() {
        return "lidos=" + lidos + " gravados=" + gravados + " ignorados=" + ignorados
                + " duplicados=" + duplicados + " erros=" + erros.size();
    }
}
