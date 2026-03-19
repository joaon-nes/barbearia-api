package com.barbearia.api.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.barbearia.api.models.Agendamento;
import com.barbearia.api.models.StatusAgendamento;
import com.barbearia.api.services.AgendamentoService;
import com.barbearia.api.repositories.ServicoRepository;
import com.barbearia.api.repositories.UsuarioRepository;
import com.barbearia.api.models.Cliente;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
public class AgendamentoController {

    private final AgendamentoService service;
    private final ServicoRepository servicoRepository;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<List<Agendamento>> listarTodos() {
        return ResponseEntity.ok(service.listarTodos());
    }

    @GetMapping("/horarios-disponiveis")
    public ResponseEntity<List<String>> horariosDisponiveis(
            @RequestParam Long estabelecimentoId,
            @RequestParam Long servicoId,
            @RequestParam String data) {
        return ResponseEntity
                .ok(service.buscarHorariosDisponiveis(estabelecimentoId, servicoId, java.time.LocalDate.parse(data)));
    }

    @PostMapping
    public ResponseEntity<?> criar(@Valid @RequestBody Agendamento agendamento) {
        try {
            var servicoCompleto = servicoRepository.findById(agendamento.getServico().getId())
                    .orElseThrow(() -> new RuntimeException("Serviço não encontrado"));

            var usuario = usuarioRepository.findById(agendamento.getCliente().getId())
                    .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

            if (!(usuario instanceof Cliente)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("erro", "O ID fornecido não pertence a um Cliente válido."));
            }

            agendamento.setCliente((Cliente) usuario);
            agendamento.setServico(servicoCompleto);

            Agendamento novo = service.criar(agendamento);

            return ResponseEntity.ok(novo);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("erro", "Erro interno: " + e.getMessage()));
        }
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

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Agendamento>> listarPorCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(service.buscarPorCliente(clienteId));
    }

    @GetMapping("/estabelecimento/{estabelecimentoId}")
    public ResponseEntity<List<Agendamento>> listarPorEstabelecimento(@PathVariable Long estabelecimentoId) {
        return ResponseEntity.ok(service.buscarPorEstabelecimento(estabelecimentoId));
    }

    @PutMapping("/{id}/reagendar")
    public ResponseEntity<?> reagendar(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            java.time.LocalDateTime novaData = java.time.LocalDateTime.parse(body.get("dataHoraInicio"));
            return service.reagendar(id, novaData)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PutMapping("/{id}/propor-reagendamento")
    public ResponseEntity<?> proporReagendamento(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String dataHoraStr = body.get("dataHoraProposta").toString();
            if (dataHoraStr.length() > 19) {
                dataHoraStr = dataHoraStr.substring(0, 19);
            } else if (dataHoraStr.length() == 16) {
                dataHoraStr += ":00";
            }

            java.time.LocalDateTime novaData = java.time.LocalDateTime.parse(dataHoraStr);
            String autor = body.get("quemSugeriu").toString();

            return service.proporReagendamento(id, novaData, autor)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", "Erro no servidor: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/confirmar-reagendamento")
    public ResponseEntity<?> confirmarReagendamento(@PathVariable Long id) {
        try {
            return service.confirmarReagendamento(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", "Erro no servidor: " + e.getMessage()));
        }
    }

    @PostMapping("/estabelecimento/{estabelecimentoId}/fechar-dia")
    public ResponseEntity<?> fecharDia(@PathVariable Long estabelecimentoId, @RequestBody Map<String, String> body) {
        try {
            java.time.LocalDate data = java.time.LocalDate.parse(body.get("data"));
            int cancelados = service.fecharDia(estabelecimentoId, data);

            return ResponseEntity.ok(Map.of(
                    "mensagem",
                    "Dia fechado! " + cancelados + " agendamentos foram cancelados e notificados.",
                    "cancelados", cancelados));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", "Erro ao fechar o dia: " + e.getMessage()));
        }
    }

    @PostMapping("/estabelecimento/{estabelecimentoId}/reabrir-dia")
    public ResponseEntity<?> reabrirDia(@PathVariable Long estabelecimentoId, @RequestBody Map<String, String> body) {
        try {
            java.time.LocalDate data = java.time.LocalDate.parse(body.get("data"));
            service.reabrirDia(estabelecimentoId, data);
            return ResponseEntity.ok(Map.of("mensagem", "Dia reaberto para novos agendamentos!"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", "Erro ao reabrir o dia: " + e.getMessage()));
        }
    }
}