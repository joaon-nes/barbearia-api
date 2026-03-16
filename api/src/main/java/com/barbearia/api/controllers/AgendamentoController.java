package com.barbearia.demo.controllers;

import com.barbearia.demo.models.Agendamento;
import com.barbearia.demo.models.StatusAgendamento;
import com.barbearia.demo.services.AgendamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agendamentos")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AgendamentoController {

    private final AgendamentoService service;

    @GetMapping
    public ResponseEntity<List<Agendamento>> listarTodos() {
        return ResponseEntity.ok(service.listarTodos());
    }

    @PostMapping
    public ResponseEntity<Agendamento> criar(@Valid @RequestBody Agendamento agendamento) {
        return ResponseEntity.ok(service.criar(agendamento));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> atualizarStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.atualizarStatus(id, StatusAgendamento.valueOf(body.get("status")))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/avaliar")
    public ResponseEntity<?> avaliarAgendamento(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.avaliar(id, Integer.parseInt(body.get("nota").toString()), (String) body.get("comentario"))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/responder-avaliacao")
    public ResponseEntity<?> responderAvaliacao(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.responderAvaliacao(id, body.get("resposta"))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}