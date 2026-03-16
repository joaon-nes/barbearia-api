package com.barbearia.demo.controllers;

import com.barbearia.demo.models.Agendamento;
import com.barbearia.demo.models.StatusAgendamento;
import com.barbearia.demo.models.Usuario;
import com.barbearia.demo.repositories.AgendamentoRepository;
import com.barbearia.demo.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agendamentos")
@CrossOrigin(origins = "*")
public class AgendamentoController {

    @Autowired
    private AgendamentoRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<List<Agendamento>> listarTodos() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody Agendamento agendamento) {
        agendamento.setStatus(StatusAgendamento.AGENDADO);
        return ResponseEntity.ok(repository.save(agendamento));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> atualizarStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String novoStatus = body.get("status");
        return repository.findById(id).map(ag -> {
            ag.setStatus(StatusAgendamento.valueOf(novoStatus));
            Agendamento salvo = repository.save(ag);

            if (novoStatus.equals("CONCLUIDO")) {
                Usuario cliente = salvo.getCliente();
                if (!Boolean.TRUE.equals(cliente.getContaVerificada())) {
                    cliente.setContaVerificada(true);
                    usuarioRepository.save(cliente);
                }
            }

            return ResponseEntity.ok(salvo);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/avaliar")
    public ResponseEntity<?> avaliarAgendamento(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return repository.findById(id).map(ag -> {
            ag.setNotaAvaliacao(Integer.parseInt(body.get("nota").toString()));
            ag.setComentarioAvaliacao((String) body.get("comentario"));
            ag.setDataAvaliacao(java.time.LocalDateTime.now().toString());
            return ResponseEntity.ok(repository.save(ag));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/responder-avaliacao")
    public ResponseEntity<?> responderAvaliacao(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return repository.findById(id).map(ag -> {
            ag.setRespostaAvaliacao(body.get("resposta"));
            ag.setDataResposta(java.time.LocalDateTime.now().toString());
            return ResponseEntity.ok(repository.save(ag));
        }).orElse(ResponseEntity.notFound().build());
    }
}