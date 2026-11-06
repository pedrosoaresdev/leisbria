package br.com.pandecta.common;

import org.springframework.http.HttpStatus;

/** Erro de negócio com mensagem segura para exibir ao usuário. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
