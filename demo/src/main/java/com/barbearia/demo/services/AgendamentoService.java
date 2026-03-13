package com.barbearia.demo.services;

import com.barbearia.demo.models.Agendamento;
import com.barbearia.demo.models.StatusAgendamento;
import com.barbearia.demo.repositories.AgendamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgendamentoService {

    @Autowired
    private AgendamentoRepository repository;

    public List<Agendamento> listarTodos() {
        return repository.findAll();
    }

    public Agendamento criar(Agendamento agendamento) {
        if (repository.existsByDataHoraInicioAndStatus(agendamento.getDataHoraInicio(), StatusAgendamento.AGENDADO)) {
            throw new RuntimeException("Horário indisponível!");
        }
        agendamento.setStatus(StatusAgendamento.AGENDADO);
        return repository.save(agendamento);
    }
}