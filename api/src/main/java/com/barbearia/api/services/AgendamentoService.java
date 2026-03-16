package com.barbearia.demo.services;

import com.barbearia.demo.models.Agendamento;
import com.barbearia.demo.models.Cliente;
import com.barbearia.demo.models.Servico;
import com.barbearia.demo.models.StatusAgendamento;
import com.barbearia.demo.repositories.AgendamentoRepository;
import com.barbearia.demo.repositories.ServicoRepository;
import com.barbearia.demo.repositories.UsuarioRepository;
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

    public List<Agendamento> listarTodos() {
        return repository.findAll();
    }

    @Transactional
    public Agendamento criar(Agendamento agendamento) {
        Servico servico = servicoRepository.findById(agendamento.getServico().getId())
                .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado"));

        LocalDateTime inicioNovo = agendamento.getDataHoraInicio();
        LocalDateTime fimNovo = inicioNovo.plusMinutes(servico.getDuracaoMinutos());

        LocalDateTime inicioDia = inicioNovo.toLocalDate().atStartOfDay();
        LocalDateTime fimDia = inicioDia.plusDays(1).minusNanos(1);

        List<Agendamento> agendamentosDoDia = repository.buscarAtivosPorEstabelecimentoEDia(
                agendamento.getEstabelecimento().getId(), inicioDia, fimDia);

        for (Agendamento existente : agendamentosDoDia) {
            LocalDateTime inicioExistente = existente.getDataHoraInicio();
            LocalDateTime fimExistente = inicioExistente.plusMinutes(existente.getServico().getDuracaoMinutos());

            if (inicioNovo.isBefore(fimExistente) && fimNovo.isAfter(inicioExistente)) {
                throw new IllegalArgumentException(
                        "Horário indisponível. Este espaço já está ocupado por outro agendamento.");
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
}