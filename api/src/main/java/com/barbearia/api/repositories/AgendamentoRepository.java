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
    
    @Query("SELECT a FROM Agendamento a WHERE a.estabelecimento.id = :estabelecimentoId " +
           "AND a.status <> :statusCancelado " +
           "AND a.dataHoraInicio >= :inicioDia AND a.dataHoraInicio <= :fimDia")
    List<Agendamento> buscarAtivosPorEstabelecimentoEDia(
            @Param("estabelecimentoId") Long estabelecimentoId, 
            @Param("inicioDia") LocalDateTime inicioDia, 
            @Param("fimDia") LocalDateTime fimDia,
            @Param("statusCancelado") StatusAgendamento statusCancelado);

    List<Agendamento> findByClienteId(Long clienteId);
    List<Agendamento> findByEstabelecimentoId(Long estabelecimentoId);
}