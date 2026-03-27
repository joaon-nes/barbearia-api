package com.barbearia.api.services;

import com.barbearia.api.models.Agendamento;
import com.barbearia.api.models.Cliente;
import com.barbearia.api.models.Estabelecimento;
import com.barbearia.api.models.Servico;
import com.barbearia.api.models.StatusAgendamento;
import com.barbearia.api.models.StatusPagamento;
import com.barbearia.api.models.Usuario;
import com.barbearia.api.repositories.AgendamentoRepository;
import com.barbearia.api.repositories.BarbeiroRepository;
import com.barbearia.api.repositories.ServicoRepository;
import com.barbearia.api.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgendamentoService {

    private final AgendamentoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final ServicoRepository servicoRepository;
    private final EmailService emailService;
    private final BarbeiroRepository barbeiroRepository;

    @Scheduled(cron = "0 0 8 * * *")
    public void enviarLembretes() {
        LocalDateTime inicioAmanha = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime fimAmanha = LocalDate.now().plusDays(1).atTime(23, 59, 59);

        List<Agendamento> agendaAmanha = repository.findByDataHoraInicioBetweenAndStatus(
                inicioAmanha, fimAmanha, StatusAgendamento.AGENDADO);

        for (Agendamento ag : agendaAmanha) {
            try {
                String mensagem = String.format(
                        "Olá %s,\n\nLembrete amigável: Você tem um agendamento marcado para amanhã (%s) às %s na barbearia %s.\n\nContamos com a sua presença!",
                        ag.getCliente().getNome().split(" ")[0],
                        ag.getDataHoraInicio().toLocalDate().toString(),
                        ag.getDataHoraInicio().toLocalTime().toString().substring(0, 5),
                        ag.getEstabelecimento().getNomeBarbearia());

                emailService.enviarEmail(ag.getCliente().getEmail(), "Lembrete do seu Agendamento", mensagem);
            } catch (Exception ignored) {
            }
        }
    }

    public List<Agendamento> listarTodos() {
        return repository.findAll();
    }

    public Optional<Agendamento> buscarPorId(Long id) {
        return repository.findById(id);
    }

    public List<Agendamento> buscarPorCliente(Long clienteId) {
        return repository.findByClienteId(clienteId);
    }

    public List<Agendamento> buscarPorEstabelecimento(Long estabelecimentoId) {
        return repository.findByEstabelecimentoId(estabelecimentoId);
    }

    @Transactional
    public Agendamento criar(Agendamento agendamento) {
        if (agendamento.getDataHoraInicio().isBefore(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")))) {
            throw new IllegalArgumentException("Não é possível agendar um horário no passado.");
        }

        Servico servico = servicoRepository.findById(agendamento.getServico().getId())
                .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado."));

        Usuario usuarioCliente = usuarioRepository.findById(agendamento.getCliente().getId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));
        if (!(usuarioCliente instanceof Cliente)) {
            throw new IllegalArgumentException("O ID informado não pertence a um Cliente válido.");
        }
        Cliente cliente = (Cliente) usuarioCliente;

        int agendamentosAtivos = repository.countByClienteIdAndStatus(cliente.getId(), StatusAgendamento.AGENDADO);
        int limitePermitido = Boolean.TRUE.equals(cliente.getContaVerificada()) ? 3 : 1;

        if (agendamentosAtivos >= limitePermitido) {
            String msgExtra = limitePermitido == 1
                    ? " Após comparecer ao seu primeiro corte, o seu limite aumentará para 3."
                    : "";
            throw new IllegalArgumentException("Já possui " + agendamentosAtivos +
                    " agendamentos ativos. Conclua-os ou cancele-os antes de marcar um novo." + msgExtra);
        }

        Usuario usuarioEstabelecimento = usuarioRepository.findById(agendamento.getEstabelecimento().getId())
                .orElseThrow(() -> new IllegalArgumentException("Estabelecimento não encontrado."));
        if (!(usuarioEstabelecimento instanceof Estabelecimento)) {
            throw new IllegalArgumentException("O ID informado não pertence a um Estabelecimento válido.");
        }
        Estabelecimento estabelecimento = (Estabelecimento) usuarioEstabelecimento;

        String diasFechados = estabelecimento.getDiasFechados();
        String dataAgendamento = agendamento.getDataHoraInicio().toLocalDate().toString();

        if (diasFechados != null && !diasFechados.isBlank()) {
            List<String> fechados = Arrays.asList(diasFechados.split(","));
            if (fechados.contains(dataAgendamento)) {
                throw new IllegalArgumentException("O estabelecimento está fechado neste dia.");
            }
        }

        if (agendamento.getBarbeiro() == null || agendamento.getBarbeiro().getId() == null) {
            throw new IllegalArgumentException("É obrigatório selecionar um barbeiro.");
        }
        com.barbearia.api.models.Barbeiro barbeiro = barbeiroRepository.findById(agendamento.getBarbeiro().getId())
                .orElseThrow(() -> new IllegalArgumentException("Barbeiro não encontrado."));

        agendamento.setServico(servico);
        agendamento.setCliente(cliente);
        agendamento.setEstabelecimento(estabelecimento);
        agendamento.setBarbeiro(barbeiro);

        LocalDateTime inicioNovo = agendamento.getDataHoraInicio();
        LocalDateTime fimNovo = inicioNovo.plusMinutes(servico.getDuracaoMinutos());

        LocalDateTime inicioDia = inicioNovo.toLocalDate().atStartOfDay();
        LocalDateTime fimDia = inicioDia.plusDays(1).minusNanos(1);

        List<Agendamento> agendamentosDoDia = repository.buscarAtivosPorBarbeiroEDia(
                agendamento.getBarbeiro().getId(), inicioDia, fimDia);

        for (Agendamento existente : agendamentosDoDia) {
            LocalDateTime inicioExistente = existente.getDataHoraInicio();
            LocalDateTime fimExistente = inicioExistente.plusMinutes(existente.getServico().getDuracaoMinutos());

            if (inicioNovo.isBefore(fimExistente) && fimNovo.isAfter(inicioExistente)) {
                throw new IllegalArgumentException(
                        "Horário indisponível. Este profissional já tem marcação nesta hora.");
            }
        }

        agendamento.setStatus(StatusAgendamento.AGENDADO);
        agendamento.setStatusPagamento(StatusPagamento.PENDENTE);
        Agendamento salvo = repository.save(agendamento);

        try {
            String dataFormatada = inicioNovo.toLocalDate().toString();
            String dataPt = dataFormatada.split("-")[2] + "/" + dataFormatada.split("-")[1] + "/"
                    + dataFormatada.split("-")[0];
            String horaFormatada = inicioNovo.toLocalTime().toString().substring(0, 5);

            String assuntoCliente = "Confirmação de Agendamento - " + estabelecimento.getNomeBarbearia();
            String mensagemCliente = String.format(
                    "Olá %s,\n\nO seu agendamento foi confirmado com sucesso!\n\nDetalhes:\n📍 Barbearia: %s\n👨‍🦱 Profissional: %s\n✂️ Serviço: %s\n📅 Data: %s\n⏰ Hora: %s\n\nAgradecemos a preferência!",
                    cliente.getNome().split(" ")[0], estabelecimento.getNomeBarbearia(), barbeiro.getNome(),
                    servico.getNome(), dataPt,
                    horaFormatada);
            emailService.enviarEmail(cliente.getEmail(), assuntoCliente, mensagemCliente);

            String nomeBarbearia = estabelecimento.getNomeBarbearia() != null ? estabelecimento.getNomeBarbearia()
                    : estabelecimento.getNome();
            String assuntoEst = "Novo Agendamento: " + cliente.getNome().split(" ")[0] + " - " + dataPt + " às "
                    + horaFormatada;
            String mensagemEst = String.format(
                    "Olá %s,\n\nVocê tem um novo agendamento!\n\n👤 Cliente: %s\n👨‍🦱 Profissional: %s\n✂️ Serviço: %s\n📅 Data: %s\n⏰ Hora: %s",
                    nomeBarbearia, cliente.getNome(), barbeiro.getNome(), servico.getNome(), dataPt, horaFormatada);
            emailService.enviarEmail(estabelecimento.getEmail(), assuntoEst, mensagemEst);

        } catch (Exception e) {
            System.err.println("Aviso: Falha ao enviar e-mail de notificação - " + e.getMessage());
        }

        return salvo;
    }

    public List<String> buscarHorariosDisponiveis(Long estabelecimentoId, Long servicoId, java.time.LocalDate data,
            Long barbeiroId) {
        Usuario user = usuarioRepository.findById(estabelecimentoId).orElseThrow();
        if (!(user instanceof Estabelecimento))
            return Collections.emptyList();
        Estabelecimento est = (Estabelecimento) user;

        if (est.getDiasFechados() != null
                && Arrays.asList(est.getDiasFechados().split(",")).contains(data.toString())) {
            return Collections.emptyList();
        }

        Servico servico = servicoRepository.findById(servicoId).orElseThrow();
        int duracao = servico.getDuracaoMinutos();
        String[] nomesDias = { "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo" };
        String diaDaSemana = nomesDias[data.getDayOfWeek().getValue() - 1];
        String hrJson = est.getHorariosFuncionamento();
        if (hrJson == null || hrJson.isBlank())
            return Collections.emptyList();

        List<String> slotsDisponiveis = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, String>> horariosDaSemana = mapper.readValue(hrJson,
                    new TypeReference<List<Map<String, String>>>() {
                    });
            Map<String, String> configDia = horariosDaSemana.stream()
                    .filter(h -> diaDaSemana.equalsIgnoreCase(h.get("dia"))).findFirst().orElse(null);

            if (configDia == null)
                return Collections.emptyList();

            List<Agendamento> ocupados = repository.buscarAtivosPorBarbeiroEDia(
                    barbeiroId, data.atStartOfDay(), data.atTime(23, 59, 59));

            gerarSlotsNoTurno(data, configDia.get("abertura1"), configDia.get("fechamento1"), duracao, ocupados,
                    slotsDisponiveis);
            gerarSlotsNoTurno(data, configDia.get("abertura2"), configDia.get("fechamento2"), duracao, ocupados,
                    slotsDisponiveis);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return slotsDisponiveis;
    }

    private void gerarSlotsNoTurno(LocalDate data, String abertura, String fechamento, int duracaoServico,
            List<Agendamento> ocupados, List<String> slots) {
        if (abertura == null || fechamento == null || abertura.isBlank() || fechamento.isBlank())
            return;

        LocalTime horaAtual = LocalTime.parse(abertura);
        LocalTime horaFimTurno = LocalTime.parse(fechamento);

        while (horaAtual.plusMinutes(duracaoServico).compareTo(horaFimTurno) <= 0) {
            LocalDateTime inicioSlot = data.atTime(horaAtual);

            if (inicioSlot.isBefore(LocalDateTime.now())) {
                horaAtual = horaAtual.plusMinutes(duracaoServico);
                continue;
            }

            LocalDateTime fimSlot = inicioSlot.plusMinutes(duracaoServico);

            boolean conflito = ocupados.stream().anyMatch(ag -> {
                LocalDateTime agInicio = ag.getDataHoraInicio();
                LocalDateTime agFim = agInicio.plusMinutes(ag.getServico().getDuracaoMinutos());
                return inicioSlot.isBefore(agFim) && fimSlot.isAfter(agInicio);
            });

            if (!conflito) {
                slots.add(horaAtual.toString().substring(0, 5));
            }

            horaAtual = horaAtual.plusMinutes(duracaoServico);
        }
    }

    @Transactional
    public Optional<Agendamento> atualizarStatus(Long id, StatusAgendamento novoStatus, Long usuarioLogadoId) {
        return repository.findById(id).map(ag -> {
            if (!ag.getEstabelecimento().getId().equals(usuarioLogadoId)) {
                throw new SecurityException(
                        "Operação não permitida. Este agendamento pertence a outro estabelecimento.");
            }

            if (ag.getStatus() == StatusAgendamento.CONCLUIDO || ag.getStatus() == StatusAgendamento.CANCELADO) {
                throw new IllegalArgumentException(
                        "Não é possível alterar o status de um agendamento já finalizado ou cancelado.");
            }

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
            if (ag.getStatus() != StatusAgendamento.CONCLUIDO) {
                throw new IllegalArgumentException("Apenas serviços concluídos podem ser avaliados.");
            }
            if (ag.getNotaAvaliacao() != null) {
                throw new IllegalArgumentException("Este agendamento já foi avaliado e não pode ser alterado.");
            }
            ag.setNotaAvaliacao(nota);
            ag.setComentarioAvaliacao(comentario);
            ag.setDataAvaliacao(LocalDateTime.now());
            Agendamento salvo = repository.save(ag);

            Estabelecimento est = (Estabelecimento) ag.getEstabelecimento();
            List<Agendamento> agsAvaliados = repository.findByEstabelecimentoId(est.getId()).stream()
                    .filter(a -> a.getNotaAvaliacao() != null)
                    .toList();

            double soma = agsAvaliados.stream().mapToInt(Agendamento::getNotaAvaliacao).sum();
            int total = agsAvaliados.size();

            est.setTotalAvaliacoes(total);
            est.setNotaMedia(total > 0 ? soma / total : 0.0);
            usuarioRepository.save(est);

            return salvo;
        });
    }

    @Transactional
    public Optional<Agendamento> responderAvaliacao(Long id, String resposta, Long usuarioLogadoId) {
        return repository.findById(id).map(ag -> {
            if (!ag.getEstabelecimento().getId().equals(usuarioLogadoId)) {
                throw new SecurityException("Operação não permitida.");
            }

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
        if (novaDataHora.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Não é possível reagendar para um horário no passado.");
        }

        return repository.findById(id).map(agendamento -> {
            LocalDateTime fimNovo = novaDataHora.plusMinutes(agendamento.getServico().getDuracaoMinutos());
            LocalDateTime inicioDia = novaDataHora.toLocalDate().atStartOfDay();
            LocalDateTime fimDia = inicioDia.plusDays(1).minusNanos(1);

            List<Agendamento> agendamentosDoDia = repository.buscarAtivosPorBarbeiroEDia(
                    agendamento.getBarbeiro().getId(), inicioDia, fimDia);

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
        if (novaDataHora.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Não é possível propor um horário no passado.");
        }

        return repository.findById(id).map(agendamento -> {
            LocalDateTime fimNovo = novaDataHora.plusMinutes(agendamento.getServico().getDuracaoMinutos());
            LocalDateTime inicioDia = novaDataHora.toLocalDate().atStartOfDay();
            LocalDateTime fimDia = inicioDia.plusDays(1).minusNanos(1);

            List<Agendamento> agendamentosDoDia = repository.buscarAtivosPorBarbeiroEDia(
                    agendamento.getBarbeiro().getId(), inicioDia, fimDia);

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
                LocalDateTime novaDataHora = agendamento.getDataHoraProposta();
                LocalDateTime fimNovo = novaDataHora.plusMinutes(agendamento.getServico().getDuracaoMinutos());
                LocalDateTime inicioDia = novaDataHora.toLocalDate().atStartOfDay();
                LocalDateTime fimDia = inicioDia.plusDays(1).minusNanos(1);

                List<Agendamento> agendamentosDoDia = repository.buscarAtivosPorBarbeiroEDia(
                        agendamento.getBarbeiro().getId(), inicioDia, fimDia);

                for (Agendamento existente : agendamentosDoDia) {
                    if (existente.getId().equals(agendamento.getId()))
                        continue;

                    LocalDateTime inicioExistente = existente.getDataHoraInicio();
                    LocalDateTime fimExistente = inicioExistente
                            .plusMinutes(existente.getServico().getDuracaoMinutos());

                    if (novaDataHora.isBefore(fimExistente) && fimNovo.isAfter(inicioExistente)) {
                        throw new IllegalArgumentException(
                                "O horário sugerido já foi ocupado por outro cliente neste meio tempo. Inicie um novo reagendamento.");
                    }
                }

                agendamento.setDataHoraInicio(novaDataHora);
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

        List<Agendamento> agendamentos = repository.buscarAtivosPorEstabelecimentoEDia(estabelecimentoId, inicioDia,
                fimDia);
        int cancelados = 0;

        for (Agendamento ag : agendamentos) {
            if (ag.getStatus() == StatusAgendamento.CONCLUIDO) {
                continue;
            }

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

        Usuario user = usuarioRepository.findById(estabelecimentoId).orElse(null);
        if (user instanceof Estabelecimento) {
            Estabelecimento est = (Estabelecimento) user;
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
        Usuario user = usuarioRepository.findById(estabelecimentoId).orElse(null);
        if (user instanceof Estabelecimento) {
            Estabelecimento est = (Estabelecimento) user;
            if (est.getDiasFechados() != null && !est.getDiasFechados().isBlank()) {
                List<String> dias = new ArrayList<>(Arrays.asList(est.getDiasFechados().split(",")));
                dias.remove(data.toString());
                est.setDiasFechados(String.join(",", dias));
                usuarioRepository.save(est);
            }
        } else if (user != null) {
            throw new IllegalArgumentException("O ID fornecido não pertence a um Estabelecimento.");
        }
    }

    @Transactional
    public void atualizarStatusPagamento(Long id, StatusPagamento novoStatus) {
        Agendamento agendamento = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado."));
        agendamento.setStatusPagamento(novoStatus);
        repository.save(agendamento);
    }

    public boolean podePagarEmDinheiro(Long clienteId) {
        long atendimentosFinalizados = repository.countByClienteIdAndStatus(clienteId,
                StatusAgendamento.CONCLUIDO);
        return atendimentosFinalizados > 0;
    }

    @Transactional
    public Agendamento avaliarCliente(Long id, Map<String, Object> payload) {
        Agendamento ag = repository.findById(id).orElseThrow(() -> new RuntimeException("Agendamento não encontrado"));

        if (payload.containsKey("notaCliente") && payload.get("notaCliente") != null) {
            Integer nota = Integer.parseInt(payload.get("notaCliente").toString());
            ag.setNotaCliente(nota);
            ag = repository.save(ag);

            Usuario cliente = ag.getCliente();
            if (cliente != null) {
                List<Agendamento> historicoCliente = repository.findByClienteId(cliente.getId());
                double soma = 0;
                int cont = 0;
                for (Agendamento a : historicoCliente) {
                    if (a.getNotaCliente() != null) {
                        soma += a.getNotaCliente();
                        cont++;
                    }
                }

                cliente.setTotalAvaliacoes(cont);
                cliente.setNotaMedia(cont > 0 ? soma / cont : 0.0);
                usuarioRepository.save(cliente);
            }
        }

        return ag;
    }
}