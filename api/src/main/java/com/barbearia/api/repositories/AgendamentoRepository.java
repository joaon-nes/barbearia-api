package com.barbearia.api.repositories;

import com.barbearia.api.models.Agendamento;
import com.barbearia.api.models.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    List<Agendamento> findByClienteId(Long clienteId);

    List<Agendamento> findByEstabelecimentoId(Long estabelecimentoId);

    @Query("SELECT a FROM Agendamento a WHERE a.estabelecimento.id = :estabelecimentoId AND a.dataHoraInicio >= :inicio AND a.dataHoraInicio <= :fim AND a.status != :statusExcluido")
    List<Agendamento> buscarAtivosPorEstabelecimentoEDia(@Param("estabelecimentoId") Long estabelecimentoId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim,
            @Param("statusExcluido") StatusAgendamento statusExcluido);

    List<Agendamento> findByDataHoraInicioBetweenAndStatus(LocalDateTime inicio, LocalDateTime fim,
            StatusAgendamento status);
}