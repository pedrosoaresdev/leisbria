package br.com.pandecta.common;

public class SearchUnavailableException extends RuntimeException {
    public SearchUnavailableException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public SearchUnavailableException(String msg) {
        super(msg);
    }
}
