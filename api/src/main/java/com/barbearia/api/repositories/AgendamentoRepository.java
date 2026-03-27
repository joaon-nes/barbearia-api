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

        int countByClienteIdAndStatus(Long clienteId, StatusAgendamento status);

        @Query("SELECT a FROM Agendamento a WHERE a.estabelecimento.id = :estabelecimentoId AND a.dataHoraInicio >= :inicio AND a.dataHoraInicio <= :fim AND a.status IN ('AGENDADO', 'PENDENTE_PAGAMENTO', 'REAGENDAMENTO_PENDENTE', 'CONCLUIDO')")
        List<Agendamento> buscarAtivosPorEstabelecimentoEDia(
                        @Param("estabelecimentoId") Long estabelecimentoId,
                        @Param("inicio") java.time.LocalDateTime inicio,
                        @Param("fim") java.time.LocalDateTime fim);

        @Query("SELECT a FROM Agendamento a WHERE a.barbeiro.id = :barbeiroId AND a.dataHoraInicio >= :inicio AND a.dataHoraInicio <= :fim AND a.status IN ('AGENDADO', 'PENDENTE_PAGAMENTO', 'REAGENDAMENTO_PENDENTE', 'CONCLUIDO')")
        List<Agendamento> buscarAtivosPorBarbeiroEDia(
                        @Param("barbeiroId") Long barbeiroId,
                        @Param("inicio") java.time.LocalDateTime inicio,
                        @Param("fim") java.time.LocalDateTime fim);

        List<Agendamento> findByDataHoraInicioBetweenAndStatus(LocalDateTime inicio, LocalDateTime fim,
                        StatusAgendamento status);

        long countByEstabelecimentoIdAndStatus(Long estabelecimentoId, StatusAgendamento status);
}