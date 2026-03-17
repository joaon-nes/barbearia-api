package com.barbearia.api.services;

import com.barbearia.api.models.Agendamento;
import com.barbearia.api.models.Cliente;
import com.barbearia.api.models.Estabelecimento;
import com.barbearia.api.models.Servico;
import com.barbearia.api.models.StatusAgendamento;
import com.barbearia.api.repositories.AgendamentoRepository;
import com.barbearia.api.repositories.ServicoRepository;
import com.barbearia.api.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgendamentoService {

    private final AgendamentoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final ServicoRepository servicoRepository;
    private final EmailService emailService;

    public List<Agendamento> listarTodos() {
        return repository.findAll();
    }

    public List<Agendamento> buscarPorCliente(Long clienteId) {
        return repository.findByClienteId(clienteId);
    }

    public List<Agendamento> buscarPorEstabelecimento(Long estabelecimentoId) {
        return repository.findByEstabelecimentoId(estabelecimentoId);
    }

    @Transactional
    public Agendamento criar(Agendamento agendamento) {
        Servico servico = servicoRepository.findById(agendamento.getServico().getId())
                .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado."));

        Cliente cliente = (Cliente) usuarioRepository.findById(agendamento.getCliente().getId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));

        Estabelecimento estabelecimento = (Estabelecimento) usuarioRepository
                .findById(agendamento.getEstabelecimento().getId())
                .orElseThrow(() -> new IllegalArgumentException("Estabelecimento não encontrado."));

        agendamento.setServico(servico);
        agendamento.setCliente(cliente);
        agendamento.setEstabelecimento(estabelecimento);

        LocalDateTime inicioNovo = agendamento.getDataHoraInicio();
        LocalDateTime fimNovo = inicioNovo.plusMinutes(servico.getDuracaoMinutos());

        LocalDateTime inicioDia = inicioNovo.toLocalDate().atStartOfDay();
        LocalDateTime fimDia = inicioDia.plusDays(1).minusNanos(1);

        List<Agendamento> agendamentosDoDia = repository.buscarAtivosPorEstabelecimentoEDia(
                estabelecimento.getId(), inicioDia, fimDia, StatusAgendamento.CANCELADO);

        for (Agendamento existente : agendamentosDoDia) {
            LocalDateTime inicioExistente = existente.getDataHoraInicio();
            LocalDateTime fimExistente = inicioExistente.plusMinutes(existente.getServico().getDuracaoMinutos());

            if (inicioNovo.isBefore(fimExistente) && fimNovo.isAfter(inicioExistente)) {
                throw new IllegalArgumentException(
                        "Horário indisponível. Este espaço já está ocupado por outro cliente.");
            }
        }

        agendamento.setStatus(StatusAgendamento.AGENDADO);
        return repository.save(agendamento);
    }

    @Transactional
    public Optional<Agendamento> atualizarStatus(Long id, StatusAgendamento novoStatus) {
        return repository.findById(id).map(ag -> {
            ag.setStatus(novoStatus);
            Agendamento salvo = repository.save(ag);

            if (novoStatus == StatusAgendamento.CONCLUIDO && salvo.getCliente() instanceof Cliente) {
                Cliente cliente = (Cliente) salvo.getCliente();
                if (!Boolean.TRUE.equals(cliente.getContaVerificada())) {
                    cliente.setContaVerificada(true);
                    usuarioRepository.save(cliente);
                }
            }
            return salvo;
        });
    }

    @Transactional
    public Optional<Agendamento> avaliar(Long id, Integer nota, String comentario) {
        return repository.findById(id).map(ag -> {
            ag.setNotaAvaliacao(nota);
            ag.setComentarioAvaliacao(comentario);
            ag.setDataAvaliacao(LocalDateTime.now());
            return repository.save(ag);
        });
    }

    @Transactional
    public Optional<Agendamento> responderAvaliacao(Long id, String resposta) {
        return repository.findById(id).map(ag -> {
            ag.setRespostaAvaliacao(resposta);
            ag.setDataResposta(LocalDateTime.now());
            return repository.save(ag);
        });
    }

    @Transactional
    public void eliminar(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public Optional<Agendamento> reagendar(Long id, LocalDateTime novaDataHora) {
        return repository.findById(id).map(agendamento -> {
            LocalDateTime fimNovo = novaDataHora.plusMinutes(agendamento.getServico().getDuracaoMinutos());
            LocalDateTime inicioDia = novaDataHora.toLocalDate().atStartOfDay();
            LocalDateTime fimDia = inicioDia.plusDays(1).minusNanos(1);

            List<Agendamento> agendamentosDoDia = repository.buscarAtivosPorEstabelecimentoEDia(
                    agendamento.getEstabelecimento().getId(), inicioDia, fimDia, StatusAgendamento.CANCELADO);

            for (Agendamento existente : agendamentosDoDia) {
                if (existente.getId().equals(agendamento.getId()))
                    continue;

                LocalDateTime inicioExistente = existente.getDataHoraInicio();
                LocalDateTime fimExistente = inicioExistente.plusMinutes(existente.getServico().getDuracaoMinutos());

                if (novaDataHora.isBefore(fimExistente) && fimNovo.isAfter(inicioExistente)) {
                    throw new IllegalArgumentException("Horário indisponível. Este espaço já está ocupado.");
                }
            }

            agendamento.setDataHoraInicio(novaDataHora);
            return repository.save(agendamento);
        });
    }

    @Transactional
    public Optional<Agendamento> proporReagendamento(Long id, LocalDateTime novaDataHora, String quemSugeriu) {
        return repository.findById(id).map(agendamento -> {
            LocalDateTime fimNovo = novaDataHora.plusMinutes(agendamento.getServico().getDuracaoMinutos());
            LocalDateTime inicioDia = novaDataHora.toLocalDate().atStartOfDay();
            LocalDateTime fimDia = inicioDia.plusDays(1).minusNanos(1);

            List<Agendamento> agendamentosDoDia = repository.buscarAtivosPorEstabelecimentoEDia(
                    agendamento.getEstabelecimento().getId(), inicioDia, fimDia, StatusAgendamento.CANCELADO);

            for (Agendamento existente : agendamentosDoDia) {
                if (existente.getId() != null && existente.getId().equals(agendamento.getId()))
                    continue;

                LocalDateTime inicioExistente = existente.getDataHoraInicio();
                LocalDateTime fimExistente = inicioExistente.plusMinutes(existente.getServico().getDuracaoMinutos());

                if (novaDataHora.isBefore(fimExistente) && fimNovo.isAfter(inicioExistente)) {
                    throw new IllegalArgumentException("O horário entra em conflito com um agendamento existente.");
                }

                if (existente.getDataHoraProposta() != null) {
                    LocalDateTime inicioProposta = existente.getDataHoraProposta();
                    LocalDateTime fimProposta = inicioProposta.plusMinutes(existente.getServico().getDuracaoMinutos());

                    if (novaDataHora.isBefore(fimProposta) && fimNovo.isAfter(inicioProposta)) {
                        throw new IllegalArgumentException(
                                "O horário entra em conflito com uma proposta em negociação.");
                    }
                }
            }

            agendamento.setDataHoraProposta(novaDataHora);
            agendamento.setQuemSugeriuReagendamento(quemSugeriu);
            agendamento.setStatus(StatusAgendamento.REAGENDAMENTO_PENDENTE);
            return repository.save(agendamento);
        });
    }

    @Transactional
    public Optional<Agendamento> confirmarReagendamento(Long id) {
        return repository.findById(id).map(agendamento -> {
            if (agendamento.getDataHoraProposta() != null) {
                agendamento.setDataHoraInicio(agendamento.getDataHoraProposta());
                agendamento.setDataHoraProposta(null);
                agendamento.setQuemSugeriuReagendamento(null);
                agendamento.setStatus(StatusAgendamento.AGENDADO);
            }
            return repository.save(agendamento);
        });
    }

    @Transactional
    public int fecharDia(Long estabelecimentoId, java.time.LocalDate data) {
        LocalDateTime inicioDia = data.atStartOfDay();
        LocalDateTime fimDia = inicioDia.plusDays(1).minusNanos(1);

        List<Agendamento> agendamentos = repository.findByEstabelecimentoId(estabelecimentoId);
        int cancelados = 0;

        for (Agendamento ag : agendamentos) {
            if (!ag.getDataHoraInicio().isBefore(inicioDia) && !ag.getDataHoraInicio().isAfter(fimDia)) {
                if (ag.getStatus() == StatusAgendamento.AGENDADO
                        || ag.getStatus() == StatusAgendamento.REAGENDAMENTO_PENDENTE) {
                    ag.setStatus(StatusAgendamento.CANCELADO);
                    repository.save(ag);
                    cancelados++;

                    try {
                        String assunto = "Aviso Importante: Agendamento Cancelado - "
                                + ag.getEstabelecimento().getNomeBarbearia();
                        String dataFormatada = ag.getDataHoraInicio().toLocalDate().toString();
                        String horaFormatada = ag.getDataHoraInicio().toLocalTime().toString().substring(0, 5);
                        String mensagem = String.format(
                                "Olá %s,\n\nInfelizmente a barbearia %s precisou fechar no dia %s e o seu agendamento das %s foi cancelado.\n\nPor favor, acesse o aplicativo para escolher um novo horário.\n\nPedimos desculpas pelo transtorno.",
                                ag.getCliente().getNome().split(" ")[0], ag.getEstabelecimento().getNomeBarbearia(),
                                dataFormatada, horaFormatada);
                        emailService.enviarEmail(ag.getCliente().getEmail(), assunto, mensagem);
                    } catch (Exception e) {
                        System.err.println("Erro e-mail: " + e.getMessage());
                    }
                }
            }
        }

        Estabelecimento est = (Estabelecimento) usuarioRepository.findById(estabelecimentoId).orElse(null);
        if (est != null) {
            String dias = est.getDiasFechados();
            String dataStr = data.toString();
            if (dias == null || dias.isEmpty()) {
                est.setDiasFechados(dataStr);
            } else if (!dias.contains(dataStr)) {
                est.setDiasFechados(dias + "," + dataStr);
            }
            usuarioRepository.save(est);
        }

        return cancelados;
    }

    @Transactional
    public void reabrirDia(Long estabelecimentoId, java.time.LocalDate data) {
        Estabelecimento est = (Estabelecimento) usuarioRepository.findById(estabelecimentoId).orElse(null);
        if (est != null && est.getDiasFechados() != null) {
            String dataStr = data.toString();
            String novosDias = est.getDiasFechados().replace(dataStr, "").replace(",,", ",").replaceAll("^,|,$", "");
            est.setDiasFechados(novosDias);
            usuarioRepository.save(est);
        }
    }
}