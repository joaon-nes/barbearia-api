package com.barbearia.demo.repositories;

import com.barbearia.demo.models.Agendamento;
import com.barbearia.demo.models.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    boolean existsByDataHoraInicioAndStatus(String dataHoraInicio, StatusAgendamento status);

}