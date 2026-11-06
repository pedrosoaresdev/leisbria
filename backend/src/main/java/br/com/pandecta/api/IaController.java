package br.com.pandecta.api;

import br.com.pandecta.ai.RagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ia")
public class IaController {
    private final RagService rag;

    public IaController(RagService rag) {
        this.rag = rag;
    }

    public record Pergunta(@NotBlank @Size(min = 3, max = 2000) String pergunta) {}

    @PostMapping("/perguntar")
    public Map<String, Object> perguntar(@Valid @RequestBody Pergunta p) {
        return rag.perguntar(p.pergunta());
    }
}
