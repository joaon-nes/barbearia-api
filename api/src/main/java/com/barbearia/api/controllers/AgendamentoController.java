package com.barbearia.api.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.barbearia.api.models.Agendamento;
import com.barbearia.api.models.Barbeiro;
import com.barbearia.api.models.StatusAgendamento;
import com.barbearia.api.models.Usuario;
import com.barbearia.api.services.AgendamentoService;
import com.barbearia.api.repositories.ServicoRepository;
import com.barbearia.api.models.Cliente;
import com.barbearia.api.models.Estabelecimento;
import com.barbearia.api.models.Servico;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
public class AgendamentoController {

    private final AgendamentoService service;
    private final ServicoRepository servicoRepository;

    @Autowired
    private com.barbearia.api.services.PagamentoService pagamentoService;

    @GetMapping
    public ResponseEntity<List<Agendamento>> listarTodos() {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if ("CLIENTE".equals(usuarioLogado.getRole().name())) {
            return ResponseEntity.ok(service.buscarPorCliente(usuarioLogado.getId()));
        } else if ("ESTABELECIMENTO".equals(usuarioLogado.getRole().name())) {
            return ResponseEntity.ok(service.buscarPorEstabelecimento(usuarioLogado.getId()));
        }

        return ResponseEntity.ok(service.listarTodos());
    }

    @GetMapping("/horarios-disponiveis")
    public ResponseEntity<List<String>> horariosDisponiveis(
            @RequestParam Long estabelecimentoId,
            @RequestParam Long servicoId,
            @RequestParam String data,
            @RequestParam Long barbeiroId) {

        return ResponseEntity.ok(service.buscarHorariosDisponiveis(
                estabelecimentoId, servicoId, java.time.LocalDate.parse(data), barbeiroId));
    }

    @PostMapping
    public ResponseEntity<?> criar(@Valid @RequestBody Agendamento agendamento) {
        try {
            Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

            if (!(usuarioLogado instanceof Cliente)) {
                return ResponseEntity.status(403).body(Map.of("erro", "Apenas clientes podem criar agendamentos."));
            }

            agendamento.setId(null);
            agendamento.setCliente((Cliente) usuarioLogado);

            var servicoCompleto = servicoRepository.findById(agendamento.getServico().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado"));

            agendamento.setServico(servicoCompleto);

            Agendamento novo = service.criar(agendamento);
            return ResponseEntity.ok(novo);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            throw new RuntimeException("Erro interno ao criar agendamento.", e);
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> atualizarStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            Agendamento ag = service.buscarPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado."));

            if (!ag.getEstabelecimento().getId().equals(usuarioLogado.getId())
                    && !ag.getCliente().getId().equals(usuarioLogado.getId())) {
                return ResponseEntity.status(403)
                        .body(Map.of("erro", "Acesso negado. Não tem permissão para alterar este agendamento."));
            }

            StatusAgendamento novoStatus = StatusAgendamento.valueOf(body.get("status"));

            if (usuarioLogado instanceof Cliente && novoStatus != StatusAgendamento.CANCELADO) {
                return ResponseEntity.status(403)
                        .body(Map.of("erro", "Clientes só têm permissão para CANCELAR agendamentos."));
            }

            return service.atualizarStatus(id, novoStatus, usuarioLogado.getId())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar atualização de status no banco.", e);
        }
    }

    @PutMapping("/{id}/avaliar")
    public ResponseEntity<?> avaliarAgendamento(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            Agendamento ag = service.buscarPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado."));

            if (!ag.getCliente().getId().equals(usuarioLogado.getId())) {
                return ResponseEntity.status(403)
                        .body(Map.of("erro", "Acesso negado. Apenas o cliente dono do agendamento pode avaliá-lo."));
            }

            String comentarioSeguro = body.get("comentario") != null
                    ? body.get("comentario").toString().replaceAll("<[^>]*>", "")
                    : null;

            return service.avaliar(id, Integer.parseInt(body.get("nota").toString()), comentarioSeguro)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PutMapping("/{id}/responder-avaliacao")
    public ResponseEntity<?> responderAvaliacao(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            Agendamento ag = service.buscarPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado."));

            if (!ag.getEstabelecimento().getId().equals(usuarioLogado.getId())) {
                return ResponseEntity.status(403).body(
                        Map.of("erro", "Acesso negado. Apenas o estabelecimento pode responder a esta avaliação."));
            }

            String respostaSegura = body.get("resposta") != null ? body.get("resposta").replaceAll("<[^>]*>", "")
                    : null;

            return service.responderAvaliacao(id, respostaSegura, usuarioLogado.getId())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        try {
            Agendamento ag = service.buscarPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado."));

            if (!ag.getCliente().getId().equals(usuarioLogado.getId())
                    && !ag.getEstabelecimento().getId().equals(usuarioLogado.getId())) {
                return ResponseEntity.status(403)
                        .body(Map.of("erro", "Não tem permissão para apagar este agendamento."));
            }

            service.eliminar(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            throw new RuntimeException("Erro interno ao eliminar agendamento.", e);
        }
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<?> listarPorCliente(@PathVariable Long clienteId) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!usuarioLogado.getId().equals(clienteId)) {
            return ResponseEntity.status(403).body(Map.of("erro", "Acesso negado às informações deste cliente."));
        }

        List<Agendamento> agendamentos = service.buscarPorCliente(clienteId);

        List<Agendamento> agendamentosSeguros = agendamentos.stream().map(ag -> {
            Agendamento seguro = new Agendamento();
            seguro.setId(ag.getId());
            seguro.setDataHoraInicio(ag.getDataHoraInicio());
            seguro.setDataHoraProposta(ag.getDataHoraProposta());
            seguro.setStatus(ag.getStatus());
            seguro.setNotaAvaliacao(ag.getNotaAvaliacao());
            seguro.setComentarioAvaliacao(ag.getComentarioAvaliacao());
            seguro.setRespostaAvaliacao(ag.getRespostaAvaliacao());
            seguro.setDataAvaliacao(ag.getDataAvaliacao());
            seguro.setDataResposta(ag.getDataResposta());
            seguro.setQuemSugeriuReagendamento(ag.getQuemSugeriuReagendamento());

            seguro.setStatusPagamento(ag.getStatusPagamento());
            seguro.setFormaPagamento(ag.getFormaPagamento());

            if (ag.getServico() != null) {
                Servico servSeguro = new Servico();
                servSeguro.setId(ag.getServico().getId());
                servSeguro.setNome(ag.getServico().getNome());
                servSeguro.setPreco(ag.getServico().getPreco());
                servSeguro.setDuracaoMinutos(ag.getServico().getDuracaoMinutos());
                seguro.setServico(servSeguro);
            }

            if (ag.getBarbeiro() != null) {
                Barbeiro barbSeguro = new Barbeiro();
                barbSeguro.setId(ag.getBarbeiro().getId());
                barbSeguro.setNome(ag.getBarbeiro().getNome());
                seguro.setBarbeiro(barbSeguro);
            }

            if (ag.getEstabelecimento() != null) {
                Estabelecimento estSeguro = new Estabelecimento();
                estSeguro.setId(ag.getEstabelecimento().getId());
                estSeguro.setNomeBarbearia(ag.getEstabelecimento().getNomeBarbearia());
                estSeguro.setTelefone(ag.getEstabelecimento().getTelefone());
                seguro.setEstabelecimento(estSeguro);
            }

            return seguro;
        }).toList();

        return ResponseEntity.ok(agendamentosSeguros);
    }

    @GetMapping("/estabelecimento/{estabelecimentoId}")
    public ResponseEntity<?> listarPorEstabelecimento(@PathVariable Long estabelecimentoId) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<Agendamento> agendamentos = service.buscarPorEstabelecimento(estabelecimentoId);

        if (usuarioLogado.getId().equals(estabelecimentoId)) {
            return ResponseEntity.ok(agendamentos);
        }

        List<Agendamento> agendamentosSeguros = agendamentos.stream().map(ag -> {
            Agendamento seguro = new Agendamento();
            seguro.setId(ag.getId());
            seguro.setDataHoraInicio(ag.getDataHoraInicio());
            seguro.setDataHoraProposta(ag.getDataHoraProposta());
            seguro.setStatus(ag.getStatus());
            seguro.setNotaAvaliacao(ag.getNotaAvaliacao());
            seguro.setComentarioAvaliacao(ag.getComentarioAvaliacao());
            seguro.setRespostaAvaliacao(ag.getRespostaAvaliacao());
            seguro.setDataAvaliacao(ag.getDataAvaliacao());
            seguro.setDataResposta(ag.getDataResposta());

            if (ag.getServico() != null) {
                Servico s = new Servico();
                s.setId(ag.getServico().getId());
                s.setNome(ag.getServico().getNome());
                s.setPreco(ag.getServico().getPreco());
                s.setDuracaoMinutos(ag.getServico().getDuracaoMinutos());
                seguro.setServico(s);
            }

            if (ag.getBarbeiro() != null) {
                Barbeiro b = new Barbeiro();
                b.setId(ag.getBarbeiro().getId());
                b.setNome(ag.getBarbeiro().getNome());
                seguro.setBarbeiro(b);
            }

            if (ag.getCliente() != null) {
                Cliente cliSeguro = new Cliente();
                cliSeguro.setNome(ag.getCliente().getNome().split(" ")[0]);
                cliSeguro.setContaVerificada(ag.getCliente().getContaVerificada());
                seguro.setCliente(cliSeguro);
            }
            return seguro;
        }).toList();

        return ResponseEntity.ok(agendamentosSeguros);
    }

    @PutMapping("/{id}/reagendar")
    public ResponseEntity<?> reagendar(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            Agendamento agendamento = service.buscarPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado."));

            boolean isDono = agendamento.getCliente().getId().equals(usuarioLogado.getId());
            boolean isEstabelecimento = agendamento.getEstabelecimento().getId().equals(usuarioLogado.getId());

            if (!isDono && !isEstabelecimento && !usuarioLogado.getRole().name().equals("ADMIN")) {
                return ResponseEntity.status(403)
                        .body(Map.of("erro", "Acesso negado. Você não tem permissão para alterar este agendamento."));
            }

            java.time.LocalDateTime novaData = java.time.LocalDateTime.parse(body.get("dataHoraInicio"));
            return service.reagendar(id, novaData)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            throw new RuntimeException("Erro interno ao tentar reagendar.", e);
        }
    }

    @PutMapping("/{id}/propor-reagendamento")
    public ResponseEntity<?> proporReagendamento(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            Agendamento agendamento = service.buscarPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado."));

            boolean isDono = agendamento.getCliente().getId().equals(usuarioLogado.getId());
            boolean isEstabelecimento = agendamento.getEstabelecimento().getId().equals(usuarioLogado.getId());

            if (!isDono && !isEstabelecimento && !usuarioLogado.getRole().name().equals("ADMIN")) {
                return ResponseEntity.status(403).body(Map.of("erro", "Acesso negado."));
            }

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
            throw new RuntimeException("Erro no servidor ao processar reagendamento.", e);
        }
    }

    @PutMapping("/{id}/confirmar-reagendamento")
    public ResponseEntity<?> confirmarReagendamento(@PathVariable Long id) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            Agendamento agendamento = service.buscarPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado."));

            boolean isDono = agendamento.getCliente().getId().equals(usuarioLogado.getId());
            boolean isEstabelecimento = agendamento.getEstabelecimento().getId().equals(usuarioLogado.getId());

            if (!isDono && !isEstabelecimento && !usuarioLogado.getRole().name().equals("ADMIN")) {
                return ResponseEntity.status(403).body(Map.of("erro", "Acesso negado."));
            }

            String autorDaProposta = agendamento.getQuemSugeriuReagendamento();
            if (usuarioLogado instanceof Cliente && "CLIENTE".equalsIgnoreCase(autorDaProposta)) {
                return ResponseEntity.status(403)
                        .body(Map.of("erro", "Você não pode aprovar a sua própria sugestão. Aguarde a barbearia."));
            }
            if (usuarioLogado instanceof Estabelecimento && "ESTABELECIMENTO".equalsIgnoreCase(autorDaProposta)) {
                return ResponseEntity.status(403)
                        .body(Map.of("erro", "Você não pode aprovar a sua própria sugestão. Aguarde o cliente."));
            }

            return service.confirmarReagendamento(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            throw new RuntimeException("Erro no servidor ao confirmar reagendamento.", e);
        }
    }

    @PostMapping("/estabelecimento/{estabelecimentoId}/fechar-dia")
    public ResponseEntity<?> fecharDia(@PathVariable Long estabelecimentoId, @RequestBody Map<String, String> body) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!usuarioLogado.getId().equals(estabelecimentoId)) {
            return ResponseEntity.status(403)
                    .body(Map.of("erro", "Não autorizado a fechar dia deste estabelecimento."));
        }

        try {
            java.time.LocalDate data = java.time.LocalDate.parse(body.get("data"));
            int cancelados = service.fecharDia(estabelecimentoId, data);

            return ResponseEntity.ok(Map.of(
                    "mensagem",
                    "Dia fechado! " + cancelados + " agendamentos foram cancelados e notificados.",
                    "cancelados", cancelados));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao fechar o dia.", e);
        }
    }

    @PostMapping("/estabelecimento/{estabelecimentoId}/reabrir-dia")
    public ResponseEntity<?> reabrirDia(@PathVariable Long estabelecimentoId, @RequestBody Map<String, String> body) {
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!usuarioLogado.getId().equals(estabelecimentoId)) {
            return ResponseEntity.status(403)
                    .body(Map.of("erro", "Não autorizado a reabrir dia deste estabelecimento."));
        }

        try {
            java.time.LocalDate data = java.time.LocalDate.parse(body.get("data"));
            service.reabrirDia(estabelecimentoId, data);
            return ResponseEntity.ok(Map.of("mensagem", "Dia reaberto para novos agendamentos!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao reabrir o dia.", e);
        }
    }

    @PostMapping("/{id}/pagar")
    public ResponseEntity<?> solicitarPagamento(@PathVariable Long id) {
        Usuario usuarioLogado = (Usuario) org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        try {
            Agendamento ag = service.buscarPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado."));

            if (!ag.getCliente().getId().equals(usuarioLogado.getId())) {
                return ResponseEntity.status(403).body(Map.of("erro", "Acesso negado."));
            }

            String urlCheckout = pagamentoService.gerarLinkDePagamento(ag);
            return ResponseEntity.ok(Map.of("checkoutUrl", urlCheckout));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao solicitar pagamento.", e);
        }
    }
}