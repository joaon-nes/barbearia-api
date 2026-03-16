package com.barbearia.demo.repositories;

import com.barbearia.demo.models.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    @Query("SELECT a FROM Agendamento a WHERE a.estabelecimento.id = :estabelecimentoId " +
            "AND a.status <> 'CANCELADO' " +
            "AND a.dataHoraInicio >= :inicioDia AND a.dataHoraInicio <= :fimDia")
    List<Agendamento> buscarAtivosPorEstabelecimentoEDia(
            Long estabelecimentoId, LocalDateTime inicioDia, LocalDateTime fimDia);
}