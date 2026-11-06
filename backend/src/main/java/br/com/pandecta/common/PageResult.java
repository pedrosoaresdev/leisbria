package br.com.pandecta.common;

import java.util.List;
import java.util.Map;

/** Página de resultados com total e facetas (agregações) opcionais. */
public record PageResult<T>(long total, int pagina, int tamanho, List<T> itens, Map<String, List<Facet>> facetas) {
    public record Facet(String valor, long quantidade) {}

    public static <T> PageResult<T> of(long total, int pagina, int tamanho, List<T> itens) {
        return new PageResult<>(total, pagina, tamanho, itens, Map.of());
    }
}
