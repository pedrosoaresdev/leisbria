package br.com.pandecta.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> validacao(Exception e) {
        String msg = "Parâmetros inválidos.";
        if (e instanceof MethodArgumentNotValidException m && m.getBindingResult().getFieldError() != null) {
            var fe = m.getBindingResult().getFieldError();
            msg = "Campo '" + fe.getField() + "': " + fe.getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(Map.of("erro", msg));
    }

    @ExceptionHandler(SearchUnavailableException.class)
    public ResponseEntity<Map<String, String>> buscaIndisponivel(SearchUnavailableException e) {
        log.error("Motor de busca indisponível: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("erro", "O motor de busca está temporariamente indisponível. Tente novamente em instantes."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> generico(Exception e) {
        String ref = UUID.randomUUID().toString().substring(0, 8);
        log.error("Erro inesperado [ref={}]", ref, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("erro", "Erro interno. Referência: " + ref));
    }
}
