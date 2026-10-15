package com.edu.knowledge;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="knowledge.api-enabled",havingValue="true",matchIfMissing=true)
public class KnowledgeController {
    private final RagService service;
    public KnowledgeController(RagService service) {this.service=service;}
    @PostMapping("/api/v1/rag/retrieve") public Map<String,Object> retrieve(@Valid @RequestBody RagContracts.Retrieve request) {return service.retrieve(request);}
    @PostMapping("/api/v1/rag/upsert") public Map<String,Object> upsert(@Valid @RequestBody RagContracts.Upsert request) {return service.upsert(request);}
    @PostMapping("/api/v1/rag/initialize") public Map<String,String> initialize() {service.initialize();return Map.of("status","ok");}
    @RestControllerAdvice
    static class Errors {
        @ExceptionHandler(KnowledgeException.class) ResponseEntity<?> known(KnowledgeException error) {
            return ResponseEntity.status(error.status).body(Map.of("detail",error.getMessage()));
        }
        @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class, org.springframework.http.converter.HttpMessageNotReadableException.class}) ResponseEntity<?> validation() {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail","Invalid knowledge request"));
        }
        @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class) ResponseEntity<?> missing() {
            return ResponseEntity.notFound().build();
        }
        @ExceptionHandler(Exception.class) ResponseEntity<?> unknown() {
            return ResponseEntity.status(503).body(Map.of("detail","Knowledge dependency unavailable"));
        }
    }
}
